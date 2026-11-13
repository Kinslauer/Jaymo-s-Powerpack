package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MobCombat;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Stacked;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fortress knight: one of the old war's dead, a champion of the demons' country still holding a Nether fortress
 * long after anyone told him to. One to a fortress, ever: the first time a player walks its halls he rises somewhere
 * in them, out of reach, and that fortress never has another ({@link #HELD}).
 * <p>
 * He is a wither skeleton grown to a champion's size in blackened plate, with the Long Vigil in his hands (Powers'
 * relic greatsword; netherite without Powers). Being a humanoid with a weapon he fights the way everyone armed does
 * ({@link MobCombat}: the greatsword's whole string, sidesteps, lunges), can be parried and has his posture broken
 * like any boss ({@link com.jaymoskingdoms.BossPosture}), and turns at half health ({@link com.jaymoskingdoms.BossTurns}).
 * His own: a long closing lunge whenever his foe stands off ({@link #REACH_FROM}..{@link #REACH_TO}), and, once he has
 * turned, the halls answering him: every {@link #CALL_EVERY} ticks a haunt rises near by ({@link FortressHaunts}), up
 * to {@link #CALLS_MOST}. He always drops the sword, and it is found nowhere else. Ops: {@code /kingdom knight}.
 */
public final class FortressKnight {
	/** How often a player in a fortress is looked at (ticks). */
	static final int LOOK_EVERY = 20 * 5;
	/** His size, health, bare blow (the sword adds its own), armour and how hard he is to shove. */
	static final double SCALE = 1.35, HEALTH = 280.0, DAMAGE = 5.0, ARMOUR = 14.0, KNOCKBACK_RESIST = 0.85, SPEED = 0.27;
	/** His closing lunge: from how far, how often (ticks). */
	static final double REACH_FROM = 4.5, REACH_TO = 10.0;
	static final int REACH_EVERY = 20 * 7;
	/** The lunge's leap: speed for each block of the gap, the most, and its lift. */
	static final double REACH_SPEED_PER_BLOCK = 0.16, REACH_SPEED_MOST = 1.5, REACH_UP = 0.28;
	/** Below this share of his health the halls answer him: a haunt this often (ticks), this many at most, this far from him. */
	static final float CALL_BELOW = 0.5F;
	static final int CALL_EVERY = 20 * 14, CALLS_MOST = 3, CALL_OUT = 4;
	/** Souls rise off him this often (ticks). */
	static final int SOULS_EVERY = 8;
	private static final Identifier SWORD = Identifier.fromNamespaceAndPath("jaymospowers", "long_vigil");

	/** On the knight (saved). */
	public static final AttachmentType<Boolean> KNIGHT = AttachmentRegistry.create(JaymosKingdoms.id("fortress_knight"),
		builder -> builder.persistent(Codec.BOOL));
	/** On the Nether: the fortresses that have had their knight, by their start chunk. */
	public static final AttachmentType<List<Long>> HELD = AttachmentRegistry.create(JaymosKingdoms.id("fortress_knights"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private static final Set<Mob> LOADED = new HashSet<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, Long> REACH_READY = new HashMap<>(), CALL_READY = new HashMap<>();
	private static final Map<UUID, Integer> CALLED = new HashMap<>();

	private FortressKnight() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && is(mob)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			LOADED.remove(entity);
			ServerBossEvent bar = BARS.remove(entity.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(FortressKnight::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			REACH_READY.clear();
			CALL_READY.clear();
			CALLED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("knight").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(FortressKnight::riseHere))));
	}

	/** The fortress knight. */
	public static boolean is(Entity entity) {
		return entity.hasAttached(KNIGHT);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel nether = Stacked.level(server, Level.NETHER);
		if (nether == null) {
			return;
		}
		long now = nether.getGameTime();
		if (now % 10 == 0) {
			for (Mob knight : List.copyOf(LOADED)) {
				if (knight.isAlive() && knight.level() == nether) {
					fight(nether, knight, now);
				}
			}
		}
		if (now % SOULS_EVERY == 0) {
			for (Mob knight : LOADED) {
				if (knight.isAlive() && knight.level() == nether) {
					nether.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, knight.getX(), knight.getY() + knight.getBbHeight() * 0.7, knight.getZ(), 1, 0.3, 0.4,
						0.3, 0.01);
				}
			}
		}
		if (now % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : Stacked.players(server, Level.NETHER)) {
			if (player.isSpectator()) {
				continue;
			}
			StructureStart start = nether.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(BuiltinStructures.FORTRESS));
			if (!start.isValid()) {
				continue;
			}
			long key = start.getChunkPos().pack();
			List<Long> held = nether.getAttachedOrElse(HELD, List.of());
			if (held.contains(key)) {
				continue;
			}
			BlockPos at = FortressHaunts.site(nether, player.blockPosition());
			if (at != null && rise(nether, at) != null) {
				List<Long> all = new ArrayList<>(held);
				all.add(key);
				nether.setAttached(HELD, List.copyOf(all));
			}
		}
	}

	/** His bar, his closing lunge, and the halls answering him once he's turned. */
	private static void fight(ServerLevel nether, Mob knight, long now) {
		ServerBossEvent bar = BARS.computeIfAbsent(knight.getUUID(), id -> new ServerBossEvent(id, knight.getDisplayName(), BossEvent.BossBarColor.BLUE,
			BossEvent.BossBarOverlay.NOTCHED_10));
		bar.setProgress(knight.getHealth() / knight.getMaxHealth());
		BossBars.show(nether, knight, bar);
		LivingEntity foe = knight.getTarget();
		if (foe == null || !foe.isAlive()) {
			return;
		}
		UUID id = knight.getUUID();
		double d = knight.distanceTo(foe);
		if (d > REACH_FROM && d < REACH_TO && knight.onGround() && now >= REACH_READY.getOrDefault(id, 0L) && knight.hasLineOfSight(foe)
			&& !MobCombat.swinging(knight)) {
			REACH_READY.put(id, now + REACH_EVERY);
			PowersCompat.tellIncoming(knight, foe, 12, true);
			Vec3 to = foe.position().subtract(knight.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4 && MobCombat.lunge(knight, foe)) {
				knight.setDeltaMovement(to.normalize().scale(Math.min(REACH_SPEED_MOST, d * REACH_SPEED_PER_BLOCK)).add(0.0, REACH_UP, 0.0));
				knight.syncVelocity = true;
				knight.getLookControl().setLookAt(foe, 30.0F, 30.0F);
				nether.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.5F);
			}
		}
		if (knight.getHealth() < knight.getMaxHealth() * CALL_BELOW && CALLED.getOrDefault(id, 0) < CALLS_MOST) {
			Long ready = CALL_READY.get(id);
			if (ready == null) {
				CALL_READY.put(id, now + 60);
			} else if (now >= ready) {
				CALL_READY.put(id, now + CALL_EVERY);
				BlockPos at = FortressHaunts.site(nether, knight.blockPosition());
				if (at != null && FortressHaunts.rise(nether, at) != null) {
					CALLED.merge(id, 1, Integer::sum);
					nether.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.WITHER_SKELETON_HURT, SoundSource.HOSTILE, 2.0F, 0.4F);
				}
			}
		}
	}

	/** The knight rises at {@code at}. */
	static @Nullable Mob rise(ServerLevel level, BlockPos at) {
		WitherSkeleton knight = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (knight == null) {
			return null;
		}
		Vec3 feet = Vec3.atBottomCenterOf(at);
		knight.snapTo(feet.x, feet.y, feet.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		knight.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		knight.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		knight.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		knight.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE);
		knight.getAttribute(Attributes.ARMOR).setBaseValue(ARMOUR);
		knight.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(KNOCKBACK_RESIST);
		knight.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED);
		knight.setHealth(knight.getMaxHealth());
		Item sword = BuiltInRegistries.ITEM.getOptional(SWORD).orElse(Items.NETHERITE_SWORD);
		knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sword));
		knight.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		knight.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		knight.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		knight.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		knight.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			knight.setDropChance(slot, 0.0F);
		}
		knight.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		knight.setCustomName(Component.translatable("entity.jaymoskingdoms.fortress_knight"));
		knight.setPersistenceRequired();
		knight.setAttached(KNIGHT, true);
		level.addFreshEntity(knight);
		LOADED.add(knight);
		level.sendParticles(ParticleTypes.SOUL, feet.x, feet.y + 0.3, feet.z, 40, 0.5, 0.2, 0.5, 0.04);
		level.playSound(null, at, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.4F);
		JaymosKingdoms.LOGGER.info("The fortress knight rose at {}", at);
		return knight;
	}

	private static int riseHere(CommandContext<CommandSourceStack> c) {
		BlockPos here = BlockPos.containing(c.getSource().getPosition());
		if (rise(c.getSource().getLevel(), here) == null) {
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("The fortress knight rises at " + here.toShortString()), true);
		return 1;
	}
}
