package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (gv_eat, after Guard Villagers' guards that eat: its idea only; a step beside {@link Comrades}) A town's guards get hungry.
 * One that has gone {@link #HUNGRY_AFTER} ticks without a meal and has no fight on goes for one: a villager who has food to
 * spare in their pockets (two or more of anything {@link Storage#isFood}) walks over and hands one to them; with nobody
 * to, the guard walks to the storehouse and takes one out of it. They eat it (the sound, the crumbs, its nutrition as
 * health), and are fed again for a day. A guard left unfed for {@link #STARVING_AFTER} ticks goes weak
 * ({@link MobEffects#WEAKNESS} until they eat): a town that keeps its larder full keeps its guards strong, a bare one finds
 * out in a raid, which is the stewardship of it.
 */
public final class GuardMeals {
	/** How often guards are looked at (ticks); how far round a seat they are watched. */
	static final int EVERY = 100;
	static final double WATCHED = 80.0;
	/** Without a meal this long (ticks) a guard is hungry; this long and weak. */
	static final long HUNGRY_AFTER = 18000L, STARVING_AFTER = 36000L;
	/** How far a friend with food is looked for (blocks); how close they must be to hand it over and a guard to the storehouse door. */
	static final double FRIEND_REACH = 14.0, HAND_OVER = 2.4, DEPOT_NEAR = 3.5;
	/** Walking pace of either, and the longest a trip may take (ticks) before it is given up for a while. */
	static final float WALK = 0.6F;
	static final int TRIP_TICKS = 900, RETRY_GAP = 1200;
	/** The weakness of a starving guard (ticks, kept up). */
	static final int WEAK_TICKS = 220;
	/** A friend keeps this many of their food for themselves. */
	static final int KEEP = 1;

	private static final class Trip {
		/** A friend bringing it, or null for the guard's own walk to the storehouse. */
		final @Nullable UUID friend;
		final long started;

		Trip(@Nullable UUID friend, long started) {
			this.friend = friend;
			this.started = started;
		}
	}

	/** When each guard last ate (game time); each guard's trip, and the earliest it may start another. */
	private static final Map<UUID, Long> ATE = new HashMap<>();
	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	private static final Map<UUID, Long> RETRY = new HashMap<>();

	private GuardMeals() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GuardMeals::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ATE.clear();
			TRIPS.clear();
			RETRY.clear();
		});
	}

	/** A guard on a trip for food: the guard's own routines leave them to it. */
	static boolean eating(LivingEntity guard) {
		return TRIPS.containsKey(guard.getUUID());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Mob guard : level.getEntitiesOfClass(Mob.class, new AABB(f.seat()).inflate(WATCHED), m -> m.isAlive() && !m.isBaby() && Downed.isGuard(m)
				&& !Downed.isDowned(m))) {
				think(level, f, guard, now);
			}
		}
		TRIPS.values().removeIf(t -> now - t.started > TRIP_TICKS + EVERY);
	}

	private static void think(ServerLevel level, Faction f, Mob guard, long now) {
		long ate = ATE.computeIfAbsent(guard.getUUID(), id -> now - level.getRandom().nextInt((int)HUNGRY_AFTER));
		long hungry = now - ate;
		if (hungry >= STARVING_AFTER) {
			guard.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, true, false));
		}
		boolean fighting = guard.getTarget() != null || Comrades.busy(guard) || Comrades.justHurt(guard) || guard.isSleeping();
		Trip trip = TRIPS.get(guard.getUUID());
		if (trip != null) {
			if (fighting || !go(level, f, guard, trip, now)) {
				TRIPS.remove(guard.getUUID());
				RETRY.put(guard.getUUID(), now + RETRY_GAP);
			}
			return;
		}
		if (hungry < HUNGRY_AFTER || fighting || now < RETRY.getOrDefault(guard.getUUID(), 0L)) {
			return;
		}
		Villager friend = friend(level, guard);
		if (friend != null) {
			TRIPS.put(guard.getUUID(), new Trip(friend.getUUID(), now));
			BehaviorUtils.setWalkAndLookTargetMemories(friend, guard.blockPosition(), WALK, 1);
		} else if (Storage.food(level, f) > 0) {
			TRIPS.put(guard.getUUID(), new Trip(null, now));
			BehaviorUtils.setWalkAndLookTargetMemories(guard, Builders.depot(f), WALK, 1);
		}
	}

	/** An idle villager (not a guard) near {@code guard} with food to spare. */
	private static @Nullable Villager friend(ServerLevel level, Mob guard) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, guard.getBoundingBox().inflate(FRIEND_REACH),
			v -> v.isAlive() && !v.isBaby() && v != guard && !Downed.isGuard(v) && Routines.idle(v))) {
			if (spare(v) != null) {
				return v;
			}
		}
		return null;
	}

	/** The slot of {@code v}'s pockets holding food they could spare, or null. */
	private static @Nullable Integer spare(Villager v) {
		SimpleContainer pockets = v.getInventory();
		int total = 0;
		Integer slot = null;
		for (int i = 0; i < pockets.getContainerSize(); i++) {
			ItemStack s = pockets.getItem(i);
			if (Storage.isFood(s)) {
				total += s.getCount();
				slot = i;
			}
		}
		return total > KEEP ? slot : null;
	}

	/** One step of a trip: false once it is done or cannot go on. */
	private static boolean go(ServerLevel level, Faction f, Mob guard, Trip trip, long now) {
		if (now - trip.started > TRIP_TICKS) {
			return false;
		}
		if (trip.friend != null) {
			if (!(level.getEntity(trip.friend) instanceof Villager v) || !v.isAlive() || spare(v) == null) {
				return false;
			}
			if (v.distanceToSqr(guard) > HAND_OVER * HAND_OVER) {
				if (v.getNavigation().isDone()) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, guard.blockPosition(), WALK, 1);
				}
				return true;
			}
			Integer slot = spare(v);
			ItemStack food = v.getInventory().getItem(slot);
			ItemStack one = food.split(1);
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			v.getLookControl().setLookAt(guard, 30.0F, 30.0F);
			eat(level, guard, one, now);
			return false;
		}
		BlockPos depot = Builders.depot(f);
		if (guard.blockPosition().distSqr(depot) > DEPOT_NEAR * DEPOT_NEAR) {
			if (guard.getNavigation().isDone()) {
				BehaviorUtils.setWalkAndLookTargetMemories(guard, depot, WALK, 1);
			}
			return true;
		}
		ItemStack food = Storage.take(level, f, Storage::isFood, 1);
		if (!food.isEmpty()) {
			eat(level, guard, food, now);
		}
		return false;
	}

	/** {@code guard} eats {@code food}: the sound, the crumbs, its nutrition as health, and fed for a day. */
	private static void eat(ServerLevel level, Mob guard, ItemStack food, long now) {
		FoodProperties props = food.get(DataComponents.FOOD);
		guard.heal(props == null ? 2.0F : props.nutrition());
		ATE.put(guard.getUUID(), now);
		guard.removeEffect(MobEffects.WEAKNESS);
		guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food.getItem()), guard.getX(), guard.getEyeY() - 0.2, guard.getZ(), 8, 0.15, 0.1, 0.15, 0.05);
		level.playSound(null, guard.getX(), guard.getY(), guard.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}
}
