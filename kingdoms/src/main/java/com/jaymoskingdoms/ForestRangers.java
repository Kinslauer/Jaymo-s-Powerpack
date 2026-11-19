package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.forms.Lasso;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Green Huntsman's rangers in the forests (Andy, 2026-11-17: "Ranger MCA can be found in the forests hunting and will
 * teach you or scold you and bring you to justice hogtied if you poach (kill too many animals)").
 * <ul>
 * <li><b>Poaching:</b> every wild beast a player kills out in the wilds (not tame, leashed or named, not where a realm's
 * law holds or on a homestead: that's farming) counts for an in-game day, a young one twice. From {@link #SCOLD_FROM}
 * they're scolded; from {@link #POACH_FROM} they're a poacher.</li>
 * <li><b>Met in the woods:</b> a player in a forest, out of any realm, now and then meets a ranger (an MCA villager where
 * MCA is loaded) with a bow, hunting: it shoots an adult wild beast now and then, never more than the wood can spare.
 * A poacher is sought out at once.</li>
 * <li><b>Spoken to:</b> one with no calling is offered the Ranger's; a Ranger, or anyone else, gets a hunter's lesson. One
 * who has killed too much is scolded instead.</li>
 * <li><b>Poachers:</b> the ranger walks them down and hogties them ({@link Lasso#bind}), then carries them to the nearest
 * realm's seat and gives them over to its guards, who sentence them as for any crime ({@link Busts#sentence}). With no
 * guard there to take them, the realm hears of the poaching ({@link Theft.Crime#POACHING}) and its guards come.</li>
 * </ul>
 * Nothing is saved: the tallies and rangers are forgotten with the server, and rangers wander off after
 * {@link #STAY_TICKS}.
 */
public final class ForestRangers {
	/** Beasts killed in a day (a young one counts twice) before a ranger scolds you, and before you're a poacher. */
	static final int SCOLD_FROM = 4, POACH_FROM = 7;
	/** How long a kill counts (ticks: one day). */
	static final long DAY_TICKS = 24000L;
	/** How often a player in the woods may meet a ranger (ticks between tries), and the chance each try. */
	static final int MEET_EVERY = 1200;
	static final float MEET_CHANCE = 0.15F;
	/** How far off a ranger turns up, how near another ranger may already be, and how long one stays (blocks, ticks). */
	static final int MEET_DISTANCE = 24, CROWD = 96;
	/** The share of rangers who are seasoned, in a mailed jack and cap rather than studded leather. */
	static final float SEASONED_RANGER = 0.3F;
	static final long STAY_TICKS = 12000L;
	/** Hunting: how far it looks for quarry, how close it shoots from, ticks between shots, a shot's speed and how many beasts in its sight it leaves alone. */
	static final double QUARRY_RANGE = 20.0, SHOT_RANGE = 14.0;
	static final int SHOT_EVERY = 40, SPARE = 3;
	static final float SHOT_SPEED = 2.4F;
	/** Running a poacher down: how close it hogties from, how long it holds them before carrying them off (ticks). */
	static final double TIE_REACH = 2.5;
	static final int CARRY_TICKS = 60;
	/** How far round the realm's seat a guard is looked for (blocks). */
	static final double GUARD_RANGE = 48.0;
	static final String TAG = "jaymoskingdoms_ranger";

	/** Player → the times of their wild kills (a young one is there twice). */
	private static final Map<UUID, Deque<Long>> KILLS = new HashMap<>();
	private static final Map<UUID, Ranger> RANGERS = new HashMap<>();

	private static final class Ranger {
		final long born;
		@Nullable UUID quarry;
		@Nullable UUID poacher;
		long caught = -1L;
		long nextShot;

		Ranger(long born) {
			this.born = born;
		}
	}

	private ForestRangers() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && entity instanceof Animal beast && wild(beast)) {
				Deque<Long> kills = KILLS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
				long now = player.level().getGameTime();
				kills.add(now);
				if (beast.isBaby()) {
					kills.add(now);
				}
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager v) || !RANGERS.containsKey(v.getUUID())) {
				return InteractionResult.PASS;
			}
			talk(sp, v);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ForestRangers::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KILLS.clear();
			RANGERS.clear();
		});
	}

	/** A beast of the wilds, not someone's stock: not tame, leashed or named, not where a realm's law holds nor on a homestead. */
	private static boolean wild(Animal beast) {
		if (beast instanceof TamableAnimal t && t.isTame() || beast.isLeashed() || beast.hasCustomName() || !(beast.level() instanceof ServerLevel level)) {
			return false;
		}
		return Theft.lawAt(level, beast.blockPosition()) == null && Homesteads.ownerAt(level, beast.blockPosition()) == null;
	}

	/** {@code player}'s wild kills in the last day (a young one counts twice). */
	static int tally(ServerPlayer player) {
		Deque<Long> kills = KILLS.get(player.getUUID());
		if (kills == null) {
			return 0;
		}
		long now = player.level().getGameTime();
		while (!kills.isEmpty() && now - kills.peekFirst() > DAY_TICKS) {
			kills.pollFirst();
		}
		return kills.size();
	}

	// ---- meeting one ------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % MEET_EVERY == 0) {
			for (ServerPlayer player : level.players()) {
				boolean poacher = tally(player) >= POACH_FROM;
				if (!player.isSpectator() && woods(level, player.blockPosition()) && Theft.lawAt(level, player.blockPosition()) == null
					&& near(level, player.position(), CROWD) == null && (poacher || level.getRandom().nextFloat() < MEET_CHANCE)) {
					meet(level, player, poacher);
				}
			}
		}
		if (now % 5 != 0) {
			return;
		}
		for (Map.Entry<UUID, Ranger> e : new ArrayList<>(RANGERS.entrySet())) {
			if (!(level.getEntity(e.getKey()) instanceof Villager v) || !v.isAlive()) {
				RANGERS.remove(e.getKey());
				continue;
			}
			Ranger r = e.getValue();
			if (r.poacher == null && now - r.born > STAY_TICKS && level.getNearestPlayer(v, MEET_DISTANCE * 2.0) == null) {
				RANGERS.remove(e.getKey());
				v.discard();
				continue;
			}
			if (r.poacher != null) {
				chase(level, v, r, now);
			} else {
				hunt(level, v, r, now);
			}
		}
	}

	private static boolean woods(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE);
	}

	private static @Nullable Villager near(ServerLevel level, Vec3 at, double range) {
		for (UUID id : RANGERS.keySet()) {
			if (level.getEntity(id) instanceof Villager v && v.position().distanceTo(at) < range) {
				return v;
			}
		}
		return null;
	}

	/** A ranger comes out of the trees near {@code player}: after them, if they're a poacher. */
	private static void meet(ServerLevel level, ServerPlayer player, boolean poacher) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int x = (int) (player.getX() + Math.cos(angle) * MEET_DISTANCE), z = (int) (player.getZ() + Math.sin(angle) * MEET_DISTANCE);
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		if (!level.getBlockState(at.below()).isSolid()) {
			return;
		}
		Villager v = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), null, false);
		if (v == null) {
			return;
		}
		v.addTag(TAG);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		// the Huntsman's rangers go in leather: studded for most, a mailed jack for a seasoned one (a poacher's hunter is always seasoned)
		String set = poacher || random.nextFloat() < SEASONED_RANGER ? LightLeather.MAILED : LightLeather.STUDDED;
		v.setItemSlot(EquipmentSlot.CHEST, LightLeather.piece(set, EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE)));
		v.setItemSlot(EquipmentSlot.HEAD, LightLeather.piece(set, EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)));
		v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		v.setDropChance(EquipmentSlot.CHEST, 0.0F);
		v.setDropChance(EquipmentSlot.HEAD, 0.0F);
		Ranger r = new Ranger(level.getGameTime());
		if (poacher) {
			r.poacher = player.getUUID();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.ranger.coming").withStyle(ChatFormatting.DARK_GREEN));
		}
		RANGERS.put(v.getUUID(), r);
	}

	// ---- hunting ----------------------------------------------------------------------------------------------------

	/** Shoots an adult wild beast now and then, if the wood has more than {@link #SPARE} of them in its sight. */
	private static void hunt(ServerLevel level, Villager v, Ranger r, long now) {
		LivingEntity quarry = r.quarry != null && level.getEntity(r.quarry) instanceof Animal a && a.isAlive() ? a : null;
		if (quarry == null) {
			r.quarry = null;
			if (now < r.nextShot) {
				return;
			}
			List<Animal> beasts = level.getEntitiesOfClass(Animal.class, v.getBoundingBox().inflate(QUARRY_RANGE), a -> wild(a) && !a.isBaby());
			if (beasts.size() <= SPARE) {
				r.nextShot = now + SHOT_EVERY * 10L;
				return;
			}
			beasts.sort((a, b) -> Double.compare(a.distanceToSqr(v), b.distanceToSqr(v)));
			r.quarry = beasts.get(0).getUUID();
			return;
		}
		v.getLookControl().setLookAt(quarry);
		if (v.distanceTo(quarry) > SHOT_RANGE || !v.hasLineOfSight(quarry)) {
			v.getNavigation().moveTo(quarry, 0.6);
			return;
		}
		v.getNavigation().stop();
		if (now >= r.nextShot) {
			shoot(level, v, quarry);
			r.nextShot = now + SHOT_EVERY;
		}
	}

	private static void shoot(ServerLevel level, Villager v, LivingEntity target) {
		Arrow arrow = new Arrow(level, v, new ItemStack(Items.ARROW), null);
		double dx = target.getX() - v.getX(), dz = target.getZ() - v.getZ();
		double dy = target.getY(0.5) - arrow.getY();
		arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, SHOT_SPEED, 2.0F);
		arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
		level.addFreshEntity(arrow);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	// ---- talk -------------------------------------------------------------------------------------------------------

	private static void talk(ServerPlayer player, Villager v) {
		Ranger r = RANGERS.get(v.getUUID());
		if (r.poacher != null) {
			return;
		}
		int tally = tally(player);
		if (tally >= SCOLD_FROM) {
			Menus.ask(player, v, Component.translatable("speech.jaymoskingdoms.ranger.scold", tally), List.of(
				Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.ranger.sorry"), p -> {
				})));
			return;
		}
		List<Menus.Choice> choices = new ArrayList<>();
		if (Classes.classOf(player) == null) {
			for (String foe : PlayerClass.RANGER.options) {
				choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.ranger.teach", Component.translatable("class.jaymoskingdoms.option." + foe)),
					p -> {
						if (Classes.classOf(p) == null) {
							Classes.set(p, PlayerClass.RANGER, foe);
							p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.ranger.taught").withStyle(ChatFormatting.DARK_GREEN));
						}
					}));
			}
			choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.ranger.not_now"), p -> {
			}));
			Menus.ask(player, v, Component.translatable("speech.jaymoskingdoms.ranger.offer"), choices);
			return;
		}
		int lesson = 1 + player.getRandom().nextInt(4);
		Menus.ask(player, v, Component.translatable("speech.jaymoskingdoms.ranger.lesson." + lesson), List.of(
			Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.ranger.thanks"), p -> {
			})));
	}

	// ---- the poacher ------------------------------------------------------------------------------------------------

	/** Walks the poacher down, hogties them, then carries them off to the nearest realm's guards. */
	private static void chase(ServerLevel level, Villager v, Ranger r, long now) {
		ServerPlayer poacher = level.getServer().getPlayerList().getPlayer(r.poacher);
		if (poacher == null || poacher.level() != level || !poacher.isAlive() || poacher.isCreative() || poacher.isSpectator()) {
			r.poacher = null;
			r.caught = -1L;
			return;
		}
		v.getLookControl().setLookAt(poacher);
		if (r.caught < 0L) {
			if (v.distanceTo(poacher) > TIE_REACH) {
				v.getNavigation().moveTo(poacher, 0.9);
				return;
			}
			v.getNavigation().stop();
			Lasso.bind(level, poacher, true);
			r.caught = now;
			poacher.sendSystemMessage(Component.translatable("speech.jaymoskingdoms.ranger.caught", v.getName()).withStyle(ChatFormatting.DARK_GREEN));
			return;
		}
		if (now - r.caught < CARRY_TICKS) {
			return;
		}
		deliver(level, poacher, v);
		RANGERS.remove(v.getUUID());
		v.discard();
	}

	/** The poacher given over at the nearest realm's seat: sentenced by its guards, or reported to them if none is there. */
	private static void deliver(ServerLevel level, ServerPlayer poacher, Villager ranger) {
		Faction court = null;
		double best = Double.MAX_VALUE;
		for (Faction f : Factions.all(level.getServer())) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			double d = f.seat().distSqr(poacher.blockPosition());
			if (d < best) {
				best = d;
				court = f;
			}
		}
		KILLS.remove(poacher.getUUID());
		Lasso.free(poacher);
		if (court == null) {
			poacher.sendSystemMessage(Component.translatable("speech.jaymoskingdoms.ranger.let_off", ranger.getName()).withStyle(ChatFormatting.DARK_GREEN));
			return;
		}
		BlockPos to = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, court.seat());
		poacher.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.<Relative>of(), poacher.getYRot(), poacher.getXRot(), false);
		poacher.sendSystemMessage(Component.translatable("message.jaymoskingdoms.ranger.delivered", ranger.getName(), court.name()).withStyle(ChatFormatting.DARK_GREEN));
		List<PathfinderMob> guards = level.getEntitiesOfClass(PathfinderMob.class, new AABB(to).inflate(GUARD_RANGE), Downed::isGuard);
		if (!guards.isEmpty()) {
			Busts.sentence(level, poacher, court, "poaching", guards.get(0), false);
		} else {
			Theft.commit(poacher, level, court, Theft.Crime.POACHING, to, null, true);
		}
	}
}
