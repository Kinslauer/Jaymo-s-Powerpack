package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Andy (2026-11-21): villagers "should always be doing something to satisfy a need (hunger, thirst, boredom)". Every
 * townsperson of a realm carries three needs, 0 to 100, that rise through the day and fall when they're seen to:
 * <ul>
 * <li><b>hunger</b> rises about a meal's worth between {@link Meals}' lunch and supper, slowly while they sleep, so the
 * two meals a day keep it down; it goes to nothing when a meal is actually served ({@link #fed}, from
 * {@link TownHunger#ate}). Pressing outside mealtimes, they go for a bite, which comes out of the storehouse as a meal
 * does ({@link Meals#snack}).</li>
 * <li><b>thirst</b> rises faster, faster still in hot land; a meal takes some of it, a drink all of it
 * ({@link #drank}): a water cauldron by their bed (it goes down a level, and rain or a bucket fills it again), the
 * tavern, else the nearest open water (Routines' drink).</li>
 * <li><b>boredom</b> rises while nothing has them and falls while something does; the more bored, the sooner they take
 * up one of {@link Routines}' pastimes ({@link #startChance}).</li>
 * </ul>
 * The highest pressing need picks what they do next when they're free ({@link #pressing}). Work hours still win, and
 * night is MCA's. {@link Spirits}' grumbling reads from here. The needs are kept while the server runs; after a
 * restart everyone starts again somewhere low.
 */
final class Needs {
	/** How often needs are thought about (ticks); how far round a seat. */
	static final int EVERY = 100;
	static final double WATCHED = 96.0;
	/** The ticks awake each need takes to go from nothing to full. */
	static final float HUNGER_TICKS = 12000F, THIRST_TICKS = 6000F, BOREDOM_TICKS = 3000F;
	/** Asleep, hunger and thirst rise at this share; busy, boredom falls this many times faster than it rose. */
	static final float ASLEEP = 0.25F, BUSY_FALL = 3F;
	/** Hot land (a biome warmer than this) makes them thirsty this much faster. */
	static final float HOT = 1.0F, HOT_THIRST = 1.5F;
	/** A need this high is pressing; this high and they grumble about it ({@link Spirits}). */
	static final float PRESSING = 60F, GRUMBLE = 85F;
	/** A meal takes this much thirst with it. */
	static final float MEAL_DRINK = 30F;
	/** Boredom's pull on Routines: at full boredom this much is added to its chance of taking something up. */
	static final float BORED_CHANCE = 0.3F;
	/** Where needs start for someone first seen (random below this). */
	static final float START_MOST = 40F;

	enum Need { HUNGER, THIRST, BOREDOM }

	private static final Map<UUID, float[]> NEEDS = new HashMap<>();

	private Needs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Needs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEEDS.clear());
	}

	static float get(Villager v, Need need) {
		float[] n = NEEDS.get(v.getUUID());
		return n == null ? 0F : n[need.ordinal()];
	}

	/** The highest of {@code v}'s needs if it's pressing, else null (on a tie the later one, boredom last, wins). */
	static @org.jspecify.annotations.Nullable Need pressing(Villager v) {
		float[] n = NEEDS.get(v.getUUID());
		if (n == null) {
			return null;
		}
		Need best = null;
		float most = PRESSING;
		for (Need need : Need.values()) {
			if (n[need.ordinal()] >= most) {
				most = n[need.ordinal()];
				best = need;
			}
		}
		return best;
	}

	/** Routines' chance this think that {@code v} takes something up: its own, and more the more bored they are. */
	static float startChance(Villager v) {
		return Routines.START_CHANCE + get(v, Need.BOREDOM) / 100F * BORED_CHANCE;
	}

	/** A meal was served to {@code v}: no longer hungry, and a little less thirsty. */
	static void fed(Villager v) {
		float[] n = NEEDS.get(v.getUUID());
		if (n != null) {
			n[Need.HUNGER.ordinal()] = 0F;
			n[Need.THIRST.ordinal()] = Math.max(0F, n[Need.THIRST.ordinal()] - MEAL_DRINK);
		}
	}

	/** {@code v} had a drink. */
	static void drank(Villager v) {
		float[] n = NEEDS.get(v.getUUID());
		if (n != null) {
			n[Need.THIRST.ordinal()] = 0F;
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive)) {
				if (!seen.add(v.getUUID())) {
					continue;
				}
				float[] n = NEEDS.computeIfAbsent(v.getUUID(), id -> new float[] {
					level.getRandom().nextFloat() * START_MOST, level.getRandom().nextFloat() * START_MOST, level.getRandom().nextFloat() * START_MOST});
				float awake = v.isSleeping() ? ASLEEP : 1F;
				float hot = level.getBiome(v.blockPosition()).value().getBaseTemperature() > HOT ? HOT_THIRST : 1F;
				rise(n, Need.HUNGER, EVERY / HUNGER_TICKS * 100F * awake);
				rise(n, Need.THIRST, EVERY / THIRST_TICKS * 100F * awake * hot);
				// (bored only while nothing has them: asleep, at work, at a meal or a pastime, or about anything of ours, it falls)
				boolean free = !v.isSleeping() && Routines.free(v, t) && !Routines.busy(v);
				rise(n, Need.BOREDOM, EVERY / BOREDOM_TICKS * 100F * (free ? 1F : -BUSY_FALL));
			}
		}
	}

	private static void rise(float[] n, Need need, float by) {
		n[need.ordinal()] = Math.max(0F, Math.min(100F, n[need.ordinal()] + by));
	}
}
