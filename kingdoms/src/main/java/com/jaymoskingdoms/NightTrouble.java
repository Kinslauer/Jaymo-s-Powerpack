package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Trouble in the night</b>: a town keeps a watch horn. Blow a goat horn inside a settlement at night and a frightened
 * neighbour tells you, in a speech bubble over their head, which way the trouble is (a compass word and a line): either a
 * house being broken into (the crooks gather at a door and batter it) or someone set on in the street. Two to four crooks (our
 * own crooks, {@link Crooks#CROOK}) come to it from {@link #SPAWN_FROM} blocks off, work at it for {@link #WORK_TICKS},
 * then run and are gone. Beat one before it runs (to {@link #BEATEN_AT} of its health, or down it) and it drops a bundle of
 * evidence ({@link #EVIDENCE}), which any guard of the town pays for in emeralds and the town's regard. A town has one such
 * night's trouble a night.
 */
final class NightTrouble {
	/** The blow of the horn: how far (blocks) round the player the trouble and a neighbour are looked for. */
	static final double NEAR = 48.0;
	/** The crooks start this far (blocks) from the scene. */
	static final int SPAWN_FROM = 16;
	/** How many crooks: this many and up to this many more. */
	static final int CROOKS_MIN = 2, CROOKS_MORE = 3;
	/** Ticks the crooks are given to arrive, to work at it, to run before they vanish; between hints from the neighbour. */
	static final long ARRIVE_TICKS = 400L, WORK_TICKS = 900L, FLEE_TICKS = 300L, HINT_EVERY = 300L;
	/** A crook beaten to this share of his health drops his evidence. */
	static final float BEATEN_AT = 0.5F;
	/** What a guard pays for a bundle (emeralds) and the town's regard it earns. */
	static final int PAY_MIN = 2, PAY_MORE = 3, REPUTATION = 12;
	/** Lines each (lang {@code speech.jaymoskingdoms.watch.<key>.<n>}). */
	static final int LINES = 3;
	/** Tags: a night crook, and the evidence stays stamped. */
	static final String TAG = "jaymoskingdoms_night_crook";

	public static Item EVIDENCE;

	private enum Phase {
		ARRIVE, WORK, FLEE
	}

	/** One night's trouble: where, what, whose crooks, and how far it's got. */
	private static final class Gang {
		final int faction;
		final BlockPos scene;
		final boolean breakIn;
		final @Nullable UUID victim;
		final @Nullable UUID neighbour;
		final List<UUID> crooks = new ArrayList<>();
		final long start;
		Phase phase = Phase.ARRIVE;
		long phaseAt;
		long nextHint;

		Gang(int faction, BlockPos scene, boolean breakIn, @Nullable UUID victim, @Nullable UUID neighbour, long start) {
			this.faction = faction;
			this.scene = scene;
			this.breakIn = breakIn;
			this.victim = victim;
			this.neighbour = neighbour;
			this.start = start;
			this.phaseAt = start;
			this.nextHint = start + HINT_EVERY;
		}
	}

	private static final List<Gang> GANGS = new ArrayList<>();
	/** Faction → the night (overworld clock day) it last had trouble. */
	private static final Map<Integer, Long> NIGHTS = new HashMap<>();
	/** Crooks who've dropped their evidence already. */
	private static final java.util.Set<UUID> DROPPED = new java.util.HashSet<>();

	private NightTrouble() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("crook_evidence"));
		EVIDENCE = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(EVIDENCE));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!GANGS.isEmpty() && server.getTickCount() % 10 == 0) {
				tick(Factions.level(server));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GANGS.clear();
			NIGHTS.clear();
			DROPPED.clear();
		});
		// (a night crook left in the world from before is gone)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && GANGS.stream().noneMatch(g -> g.crooks.contains(entity.getUUID()))) {
				entity.discard();
			}
		});
		// the horn
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.GOAT_HORN) && player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
				blown(level, sp);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof Villager crook && crook.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
				struck(level, crook, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager crook && crook.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
				drop(level, crook);
			}
		});
		// a guard pays for a bundle of evidence
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !player.getMainHandItem().is(EVIDENCE) || !(entity instanceof LivingEntity guard) || !Downed.isGuard(guard)) {
				return InteractionResult.PASS;
			}
			return pay(level, sp, guard) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	// ---- the horn ------------------------------------------------------------------------------------------------------

	/** Whether it's night in {@code level} (the same hours crooks work in). */
	static boolean night(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= 12500L && t < 23500L;
	}

	/** {@code player} blew a goat horn: at night inside a settlement, with no trouble yet tonight, some starts. */
	private static void blown(ServerLevel level, ServerPlayer player) {
		if (!night(level) || player.isSpectator()) {
			return;
		}
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		long night = Math.floorDiv(level.getOverworldClockTime() - 12500L, 24000L);
		Long last = NIGHTS.get(f.id());
		if (last != null && last == night) {
			return;
		}
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR),
			v -> v.isAlive() && !v.isBaby() && !Crooks.isCrook(v) && !Downed.isGuard(v) && !v.entityTags().contains(TAG));
		if (folk.size() < 2) {
			return;
		}
		Villager neighbour = null;
		for (Villager v : folk) {
			if (neighbour == null || v.distanceToSqr(player) < neighbour.distanceToSqr(player)) {
				neighbour = v;
			}
		}
		folk.remove(neighbour);
		Villager victim = folk.get(level.getRandom().nextInt(folk.size()));
		boolean breakIn = level.getRandom().nextBoolean();
		BlockPos scene = victim.blockPosition();
		if (breakIn) {
			scene = victim.getBrain().getMemory(MemoryModuleType.HOME).filter(h -> h.dimension() == level.dimension()).map(h -> h.pos()).orElse(scene);
			if (scene.distSqr(victim.blockPosition()) > 64.0 * 64.0) {
				scene = victim.blockPosition();
			}
		}
		Gang gang = new Gang(f.id(), scene, breakIn, victim.getUUID(), neighbour.getUUID(), level.getGameTime());
		int count = CROOKS_MIN + level.getRandom().nextInt(CROOKS_MORE);
		for (int i = 0; i < count; i++) {
			Villager crook = spawn(level, scene);
			if (crook != null) {
				gang.crooks.add(crook.getUUID());
			}
		}
		if (gang.crooks.isEmpty()) {
			return;
		}
		NIGHTS.put(f.id(), night);
		GANGS.add(gang);
		hint(level, gang, neighbour);
	}

	private static @Nullable Villager spawn(ServerLevel level, BlockPos scene) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		BlockPos far = scene.offset((int)(Math.cos(angle) * SPAWN_FROM), 0, (int)(Math.sin(angle) * SPAWN_FROM));
		BlockPos at = Construction.standable(level, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far), 4);
		if (at == null || !level.isPositionEntityTicking(at)) {
			return null;
		}
		Villager crook = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), null, false);
		if (crook == null) {
			return null;
		}
		crook.setAttached(Crooks.CROOK, true);
		crook.addTag(TAG);
		crook.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
		return crook;
	}

	/** The neighbour's frightened word: which way, and what. */
	private static void hint(ServerLevel level, Gang gang, @Nullable Villager neighbour) {
		if (neighbour == null || !neighbour.isAlive()) {
			return;
		}
		Vec3 d = Vec3.atCenterOf(gang.scene).subtract(neighbour.position());
		int sector = Math.floorMod((int)Math.round(Math.atan2(d.x, -d.z) / (Math.PI / 4.0)), 8);
		Component way = Component.translatable("speech.jaymoskingdoms.watch.way." + sector);
		neighbour.getLookControl().setLookAt(gang.scene.getX() + 0.5, gang.scene.getY() + 1.0, gang.scene.getZ() + 0.5);
		Speech.say(level, neighbour, Component.translatable("speech.jaymoskingdoms.watch." + (gang.breakIn ? "break_in" : "set_on") + "."
			+ level.getRandom().nextInt(LINES), way), Speech.RED, Speech.OVERHEARD_RANGE * 2.0);
	}

	// ---- the night's work ----------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (Iterator<Gang> it = GANGS.iterator(); it.hasNext();) {
			Gang g = it.next();
			List<Villager> crooks = new ArrayList<>();
			for (UUID id : g.crooks) {
				if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
					crooks.add(v);
				}
			}
			if (crooks.isEmpty()) {
				it.remove();
				continue;
			}
			Vec3 scene = Vec3.atBottomCenterOf(g.scene);
			switch (g.phase) {
				case ARRIVE -> {
					boolean all = true;
					for (Villager c : crooks) {
						if (c.distanceToSqr(scene) > 9.0) {
							all = false;
							BehaviorUtils.setWalkAndLookTargetMemories(c, g.scene, 0.75F, 1);
						}
					}
					if (all || now - g.phaseAt > ARRIVE_TICKS) {
						g.phase = Phase.WORK;
						g.phaseAt = now;
					}
				}
				case WORK -> {
					work(level, g, crooks, scene, now);
					if (now - g.phaseAt > WORK_TICKS) {
						g.phase = Phase.FLEE;
						g.phaseAt = now;
						for (Villager c : crooks) {
							Crooks.flee(level, c, scene);
						}
					}
				}
				case FLEE -> {
					boolean watched = level.getNearestPlayer(scene.x, scene.y, scene.z, 64.0, false) != null;
					if (now - g.phaseAt > FLEE_TICKS || !watched) {
						for (Villager c : crooks) {
							level.sendParticles(ParticleTypes.POOF, c.getX(), c.getY(0.5), c.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
							c.discard();
						}
						it.remove();
						continue;
					}
				}
			}
			if (now >= g.nextHint && g.phase != Phase.FLEE) {
				g.nextHint = now + HINT_EVERY;
				if (g.neighbour != null && level.getEntity(g.neighbour) instanceof Villager n) {
					hint(level, g, n);
				}
			}
		}
	}

	private static void work(ServerLevel level, Gang g, List<Villager> crooks, Vec3 scene, long now) {
		if (now % 20 != 0) {
			return;
		}
		if (g.breakIn) {
			level.playSound(null, g.scene, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		} else if (g.victim != null && level.getEntity(g.victim) instanceof Villager v) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 1.0F);
			if (now % 100 == 0) {
				Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.watch.cry." + level.getRandom().nextInt(LINES)), Speech.RED, Speech.OVERHEARD_RANGE);
			}
		}
		for (Villager c : crooks) {
			if (c.distanceToSqr(scene) > 16.0) {
				BehaviorUtils.setWalkAndLookTargetMemories(c, g.scene, 0.75F, 1);
			}
		}
	}

	/** A night crook was hit: beaten enough, he drops his evidence; either way he runs. */
	private static void struck(ServerLevel level, Villager crook, DamageSource source) {
		Entity by = source.getEntity();
		if (!(by instanceof ServerPlayer) && !(by instanceof net.minecraft.world.entity.OwnableEntity)) {
			return;
		}
		if (crook.getHealth() <= crook.getMaxHealth() * BEATEN_AT || Downed.isDowned(crook)) {
			drop(level, crook);
		}
		for (Gang g : GANGS) {
			if (g.crooks.contains(crook.getUUID())) {
				Crooks.flee(level, crook, by.position());
				return;
			}
		}
	}

	/** {@code crook}'s evidence falls at his feet (once). */
	private static void drop(ServerLevel level, Villager crook) {
		if (DROPPED.add(crook.getUUID())) {
			crook.spawnAtLocation(level, new ItemStack(EVIDENCE));
			level.playSound(null, crook.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8F, 0.7F);
		}
	}

	/** {@code guard} pays {@code player} for a bundle: emeralds, and the town's regard. */
	private static boolean pay(ServerLevel level, ServerPlayer player, LivingEntity guard) {
		Faction f = Factions.around(level.getServer(), level, guard.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return false;
		}
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		int pay = PAY_MIN + level.getRandom().nextInt(PAY_MORE);
		ItemStack coins = new ItemStack(Items.EMERALD, pay);
		if (!player.getInventory().add(coins)) {
			player.spawnAtLocation(level, coins);
		}
		Reputation.change(player, f, REPUTATION);
		Speech.tell(level, guard, player, Component.translatable("speech.jaymoskingdoms.watch.paid." + level.getRandom().nextInt(LINES)), Speech.WHITE);
		level.playSound(null, guard.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
		return true;
	}
}
