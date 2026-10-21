package com.jaymoskingdoms;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.23) Jaymo's Kitchen (optional), for {@link Undertakers}: its corpses, reached by reflection so Kingdoms needs no
 * Kitchen jar to build or run. Without Kitchen 0.4.28 or later ({@code CorpseEntity.carryBy} and {@code throwAt}) every
 * call fails soft: nothing is a corpse.
 */
final class KitchenCompat {
	static final String MOD = "jaymoskitchen";
	private static final @Nullable Class<?> CORPSE;
	private static final @Nullable Method HOSTILE, CARRIER, CARRY_BY, THROW_AT;
	/** (0.5.37) For necromancers: what a corpse was, and whether it's a player's. */
	private static final @Nullable Method SOURCE_TYPE, PLAYER_CORPSE;
	/** (0.5.51) For gossip: Kitchen's {@code Butchers.gossip(ServerPlayer, Entity)}, word of a good or bad butcher. */
	private static final @Nullable Method BUTCHER_GOSSIP;
	/** (0.5.48) For golems: whether fire takes a corpse (a heavy iron one it doesn't, and can't be lifted). */
	private static final @Nullable Method BURNS;
	/** (0.7.118) Kitchen's seasons: {@code Seasons.seasonName(Level)}, {@code dayOfYear(Level)}, {@code temperate(float)}. */
	private static final @Nullable Method SEASON_NAME, DAY_OF_YEAR, TEMPERATE;

	static {
		Class<?> corpse = null;
		Method hostile = null, carrier = null, carryBy = null, throwAt = null;
		if (FabricLoader.getInstance().isModLoaded(MOD)) {
			try {
				corpse = Class.forName("com.jaymoskitchen.corpse.CorpseEntity");
				hostile = corpse.getMethod("isHostile");
				carrier = corpse.getMethod("carrier");
				carryBy = corpse.getMethod("carryBy", Entity.class);
				throwAt = corpse.getMethod("throwAt", Vec3.class);
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for guards to clear corpses (needs 0.4.28): {}", e.toString());
				corpse = null;
			}
		}
		CORPSE = corpse;
		HOSTILE = hostile;
		CARRIER = carrier;
		CARRY_BY = carryBy;
		THROW_AT = throwAt;
		Method sourceType = null, playerCorpse = null;
		if (corpse != null) {
			try {
				sourceType = corpse.getMethod("sourceType");
				playerCorpse = corpse.getMethod("isPlayerCorpse");
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for necromancers to raise corpses: {}", e.toString());
			}
		}
		SOURCE_TYPE = sourceType;
		PLAYER_CORPSE = playerCorpse;
		Method butcherGossip = null;
		if (corpse != null) {
			try {
				butcherGossip = Class.forName("com.jaymoskitchen.corpse.Butchers")
					.getMethod("gossip", net.minecraft.server.level.ServerPlayer.class, Entity.class);
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for gossip about butchers: {}", e.toString());
			}
		}
		BUTCHER_GOSSIP = butcherGossip;
		Method burns = null;
		if (corpse != null) {
			try {
				burns = corpse.getMethod("burns");
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for iron golems to burn corpses: {}", e.toString());
			}
		}
		BURNS = burns;
		Method seasonName = null, dayOfYear = null, temperate = null;
		if (FabricLoader.getInstance().isModLoaded(MOD)) {
			try {
				Class<?> seasons = Class.forName("com.jaymoskitchen.season.Seasons");
				seasonName = seasons.getMethod("seasonName", net.minecraft.world.level.Level.class);
				dayOfYear = seasons.getMethod("dayOfYear", net.minecraft.world.level.Level.class);
				temperate = seasons.getMethod("temperate", float.class);
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for seasons (needs 0.5.4): {}", e.toString());
				seasonName = null;
			}
		}
		SEASON_NAME = seasonName;
		DAY_OF_YEAR = dayOfYear;
		TEMPERATE = temperate;
	}

	/**
	 * (0.7.118) Kitchen's seasons ({@code com.jaymoskitchen.season.Seasons}): "spring", "summer", "autumn" or "winter", or
	 * null without Kitchen 0.5.4.
	 */
	static @Nullable String season(net.minecraft.world.level.Level level) {
		return invoke(SEASON_NAME, level) instanceof String name ? name : null;
	}

	/** (0.7.118) The day of Kitchen's year (from 0; a season is 21 days), or -1 without seasons. */
	static int dayOfYear(net.minecraft.world.level.Level level) {
		return invoke(DAY_OF_YEAR, level) instanceof Integer day ? day : -1;
	}

	/** (0.7.118) Whether a land this warm at its base has seasons (plains, forests, swamps); false without seasons. */
	static boolean temperate(float baseTemperature) {
		return Boolean.TRUE.equals(invoke(TEMPERATE, baseTemperature));
	}

	private static @Nullable Object invoke(@Nullable Method method, Object arg) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(null, arg);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Kitchen seasons call failed: {}", e.toString());
			return null;
		}
	}

	private KitchenCompat() {
	}

	/** (0.5.52) Tells Kitchen who's experienced with butchering: anyone with a rank of Forager, the first Survival perk. */
	static void skills() {
		if (CORPSE == null) {
			return;
		}
		try {
			Class.forName("com.jaymoskitchen.corpse.Butchers").getMethod("setExperienced", java.util.function.Predicate.class)
				.invoke(null, (java.util.function.Predicate<net.minecraft.world.entity.player.Player>) player -> Skills.rank(player, Skills.Perk.FORAGER) > 0);
		} catch (ReflectiveOperationException e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old to know who's experienced with butchering (needs 0.4.32): {}", e.toString());
		}
		// (0.7.118) winter's cold doesn't touch a Frostborn
		if (SEASON_NAME != null) {
			try {
				Class.forName("com.jaymoskitchen.season.WinterCold").getMethod("setColdProof", java.util.function.Predicate.class)
					.invoke(null, (java.util.function.Predicate<net.minecraft.world.entity.player.Player>) player -> Heritage.is(player, Heritage.Kind.FROSTBORN));
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for Frostborn to shrug off winter: {}", e.toString());
			}
		}
	}

	/** Kitchen is here, and new enough. */
	static boolean loaded() {
		return CORPSE != null;
	}

	/** A Kitchen corpse of a monster (a zombie, a skeleton, a spider...), lying free: nobody carrying it, not in a cart. */
	static boolean isFreeMonsterCorpse(Entity entity) {
		return CORPSE != null && CORPSE.isInstance(entity) && !entity.isRemoved() && !entity.isPassenger()
			&& Boolean.TRUE.equals(call(HOSTILE, entity)) && call(CARRIER, entity) == null;
	}

	/**
	 * (0.5.48) A corpse an iron golem may take to the fire: lying free, not a player's (their things stay safe), and one
	 * fire takes (not a heavy iron golem's, which can't be lifted either).
	 */
	static boolean isBurnableCorpse(Entity entity) {
		return CORPSE != null && BURNS != null && PLAYER_CORPSE != null && CORPSE.isInstance(entity) && !entity.isRemoved()
			&& !entity.isPassenger() && call(CARRIER, entity) == null && !Boolean.TRUE.equals(call(PLAYER_CORPSE, entity))
			&& Boolean.TRUE.equals(call(BURNS, entity));
	}

	/** (0.5.48) A monster's corpse. */
	static boolean isHostile(Entity corpse) {
		return Boolean.TRUE.equals(call(HOSTILE, corpse));
	}

	/** (0.5.37) A corpse a necromancer can raise: lying free, not a player's (their things stay safe). */
	static boolean isRaisableCorpse(Entity entity) {
		return CORPSE != null && SOURCE_TYPE != null && CORPSE.isInstance(entity) && !entity.isRemoved() && !entity.isPassenger()
			&& call(CARRIER, entity) == null && !Boolean.TRUE.equals(call(PLAYER_CORPSE, entity));
	}

	/** (0.5.37) What the corpse was in life, or null. */
	static net.minecraft.world.entity.@Nullable EntityType<?> sourceType(Entity corpse) {
		return call(SOURCE_TYPE, corpse) instanceof net.minecraft.world.entity.EntityType<?> type ? type : null;
	}

	/** Who's carrying this corpse, if anyone. */
	static @Nullable Entity carrier(Entity corpse) {
		return call(CARRIER, corpse) instanceof Entity e ? e : null;
	}

	/** {@code by} takes the corpse up in its arms; false if it couldn't (someone has it, it was just let go...). */
	static boolean carry(Entity corpse, Entity by) {
		return Boolean.TRUE.equals(call(CARRY_BY, corpse, by));
	}

	/** Whoever is carrying the corpse throws it at {@code at}. */
	static void throwAt(Entity corpse, Vec3 at) {
		call(THROW_AT, corpse, at);
	}

	/**
	 * (0.5.51) Something {@code speaker} has heard about a butcher (their own village's, or a far-off master or botcher), for
	 * {@code listener}, who knows that butcher's skill from then on. Null without Kitchen 0.4.32, or with nothing to tell.
	 */
	static net.minecraft.network.chat.@Nullable Component butcherGossip(net.minecraft.server.level.ServerPlayer listener, Entity speaker) {
		if (BUTCHER_GOSSIP == null) {
			return null;
		}
		try {
			return BUTCHER_GOSSIP.invoke(null, listener, speaker) instanceof net.minecraft.network.chat.Component line ? line : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Kitchen butcher gossip failed: {}", e.toString());
			return null;
		}
	}

	private static @Nullable Object call(@Nullable Method method, Entity corpse, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(corpse, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Kitchen corpse call {} failed: {}", method.getName(), e.toString());
			return null;
		}
	}
}
