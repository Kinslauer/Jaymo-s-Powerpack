package com.jaymoskitchen.cooking;

import static com.jaymoskitchen.cooking.Step.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * A dish the minigame can cook: the exact button sequence, its food stats at Good quality,
 * and the buff a Perfect cook adds.
 */
public enum Dish {
	HEARTY_STEW(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, BEEF, POTATO, CARROT, STIR, SIMMER), 10, 0.8F, true,
		List.of(new Buff(MobEffects.RESISTANCE, 60 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	MUSHROOM_RISOTTO(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, FLOUR, BROWN_MUSHROOM, STIR, RED_MUSHROOM, STIR, SIMMER), 8, 0.7F,
		true, List.of(new Buff(MobEffects.HASTE, 120 * 20, 0))),
	SWEET_BERRY_PIE(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(FLOUR, EGG, SUGAR, STIR, SWEET_BERRIES, BAKE), 9, 0.6F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0))),
	GRILLED_SALMON(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(SALMON, DRIED_KELP, FLIP, FLIP), 8, 0.8F, false,
		List.of(new Buff(MobEffects.WATER_BREATHING, 180 * 20, 0))),
	HONEY_GLAZED_CHICKEN(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(CHICKEN, FLIP, HONEY_BOTTLE, FLIP), 9, 0.8F,
		false, List.of(new Buff(MobEffects.STRENGTH, 90 * 20, 0))),
	PIZZA(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, BEETROOT, BROWN_MUSHROOM), 12, 0.8F, false,
		List.of(new Buff(MobEffects.ABSORPTION, 120 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	// --- content drop 2 ---
	FISH_STEW(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, COD, POTATO, STIR, DRIED_KELP, SIMMER),
		9, 0.7F, true, List.of(new Buff(MobEffects.DOLPHINS_GRACE, 90 * 20, 0))),
	PUMPKIN_SOUP(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, PUMPKIN, SIMMER, SUGAR, STIR, STIR),
		8, 0.6F, true, List.of(new Buff(MobEffects.NIGHT_VISION, 180 * 20, 0))),
	MUTTON_KEBAB(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(MUTTON, CARROT, FLIP, FLIP), 9, 0.8F, false,
		List.of(new Buff(MobEffects.JUMP_BOOST, 120 * 20, 0))),
	GLAZED_PORK_CHOP(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(PORKCHOP, SWEET_BERRIES, FLIP, FLIP), 10, 0.8F,
		false, List.of(new Buff(MobEffects.FIRE_RESISTANCE, 180 * 20, 0))),
	RUSTIC_BREAD(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH), 7, 0.7F, false,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 120 * 20, 0))),
	APPLE_TART(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, APPLE, SUGAR), 9, 0.6F, false,
		List.of(new Buff(MobEffects.REGENERATION, 20 * 20, 0), new Buff(MobEffects.LUCK, 300 * 20, 0))),
	SMOKED_HAM(Method.SMOKE, EnumSet.of(Station.SMOKER), List.of(PORKCHOP, SUGAR), 11, 0.9F, false,
		List.of(new Buff(MobEffects.ABSORPTION, 60 * 20, 1), new Buff(MobEffects.RESISTANCE, 30 * 20, 0))),
	BEEF_JERKY(Method.SMOKE, EnumSet.of(Station.SMOKER), List.of(BEEF, SUGAR), 7, 0.9F, false,
		List.of(new Buff(MobEffects.SPEED, 180 * 20, 0))),
	// --- content drop 3: the nether kitchen ---
	BLAZING_STEAK(Method.SEAR, EnumSet.of(Station.BLAST_FURNACE), List.of(BEEF, BLAZE_POWDER), 12, 0.9F, false,
		List.of(new Buff(MobEffects.FIRE_RESISTANCE, 180 * 20, 0), new Buff(MobEffects.STRENGTH, 60 * 20, 0))),
	MAGMA_RABBIT(Method.SEAR, EnumSet.of(Station.BLAST_FURNACE), List.of(RABBIT, MAGMA_CREAM), 10, 0.8F, false,
		List.of(new Buff(MobEffects.FIRE_RESISTANCE, 120 * 20, 0), new Buff(MobEffects.JUMP_BOOST, 30 * 20, 1))),
	CRIMSON_SKEWER(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(MUTTON, CRIMSON_FUNGUS, FLIP, FLIP), 10, 0.8F, false,
		List.of(new Buff(MobEffects.FIRE_RESISTANCE, 120 * 20, 0), new Buff(MobEffects.RESISTANCE, 30 * 20, 0))),
	NETHER_WART_STEW(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER),
		List.of(BOWL, NETHER_WART, CRIMSON_FUNGUS, STIR, WARPED_FUNGUS, SIMMER, STIR), 10, 0.7F, true,
		List.of(new Buff(MobEffects.FIRE_RESISTANCE, 180 * 20, 0), new Buff(MobEffects.HASTE, 60 * 20, 0))),
	// --- content drop 4: the plain larder (beetroot, carrots, chicken, mutton) and three new minigames ---
	BORSCHT(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, BEETROOT, BEETROOT, POTATO), 9, 0.7F, true,
		List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0))),
	BEETROOT_HASH(Method.CHOP, EnumSet.of(Station.CAMPFIRE, Station.FURNACE), List.of(BEETROOT, BEETROOT, POTATO), 8, 0.7F, false,
		List.of(new Buff(MobEffects.HASTE, 90 * 20, 0))),
	CARROT_SOUP(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, CARROT, CARROT, SUGAR), 8, 0.6F, true,
		List.of(new Buff(MobEffects.NIGHT_VISION, 240 * 20, 0))),
	GLAZED_CARROTS(Method.CHOP, EnumSet.of(Station.CAMPFIRE, Station.FURNACE), List.of(CARROT, CARROT, HONEY_BOTTLE), 7, 0.7F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0))),
	CARROT_CAKE(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, CARROT, SUGAR, EGG), 10, 0.6F, false,
		List.of(new Buff(MobEffects.ABSORPTION, 60 * 20, 0), new Buff(MobEffects.LUCK, 300 * 20, 0))),
	ROAST_CHICKEN(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(CHICKEN, POTATO), 11, 0.9F, false,
		List.of(new Buff(MobEffects.STRENGTH, 90 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	CHICKEN_SOUP(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, CHICKEN, CARROT, POTATO), 10, 0.8F, true,
		List.of(new Buff(MobEffects.REGENERATION, 20 * 20, 0), new Buff(MobEffects.HEALTH_BOOST, 120 * 20, 0))),
	ROAST_MUTTON(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(MUTTON, SWEET_BERRIES), 11, 0.9F, false,
		List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0), new Buff(MobEffects.STRENGTH, 30 * 20, 0))),
	SHEPHERDS_PIE(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(MUTTON, POTATO, CARROT), 12, 0.8F, false,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 180 * 20, 0), new Buff(MobEffects.ABSORPTION, 60 * 20, 0)));

	/** Which minigame cooks the dish. */
	public enum Method {
		/** Press the recipe steps in order (furnace / smoker). */
		SEQUENCE,
		/** Flip it over the campfire until both sides are golden. */
		GRILL,
		/** Slide it into the oven and pull it out at the right moment. */
		OVEN,
		/** Keep the smoker's heat in the band by fanning the coals until the meat is cured. */
		SMOKE,
		/** Hit the sweeping marker inside the target, round after round (blast furnace). */
		SEAR,
		/** Chop on the marks as the vegetables slide under the knife (see {@link Minigames}). */
		CHOP,
		/** Turn the spit over the fire so every side browns evenly (see {@link Minigames}). */
		ROAST,
		/** Stir the pot round and round with the mouse, faster when it boils up (see {@link Minigames}). */
		STIR
	}

	public record Buff(Holder<MobEffect> effect, int ticks, int amplifier) {
		public MobEffectInstance instance() {
			return new MobEffectInstance(effect, ticks, amplifier);
		}
	}

	/** Every ingredient that can show up as a wrong-answer button. */
	public static final List<Step> DECOY_POOL = List.of(BEEF, POTATO, CARROT, FLOUR, BROWN_MUSHROOM, RED_MUSHROOM,
		EGG, SUGAR, SWEET_BERRIES, SALMON, DRIED_KELP, CHICKEN, BEETROOT, MUTTON, COD, APPLE, PUMPKIN, NETHER_WART, CRIMSON_FUNGUS, WARPED_FUNGUS);

	private final Method method;
	private final Set<Station> stations;
	private final List<Step> steps;
	private final int nutrition;
	private final float saturation;
	private final boolean bowlFood;
	private final List<Buff> perfectBuffs;

	Dish(Method method, Set<Station> stations, List<Step> steps, int nutrition, float saturation, boolean bowlFood,
		List<Buff> perfectBuffs) {
		this.method = method;
		this.stations = stations;
		this.steps = steps;
		this.nutrition = nutrition;
		this.saturation = saturation;
		this.bowlFood = bowlFood;
		this.perfectBuffs = perfectBuffs;
	}

	public Method method() {
		return method;
	}

	public Set<Station> stations() {
		return stations;
	}

	public List<Step> steps() {
		return steps;
	}

	public int nutrition() {
		return nutrition;
	}

	public float saturation() {
		return saturation;
	}

	/** Eating it gives the bowl back (stews). */
	public boolean bowlFood() {
		return bowlFood;
	}

	public List<Buff> perfectBuffs() {
		return perfectBuffs;
	}

	/** How many of each ingredient the recipe consumes (actions are free). */
	public Map<Step, Integer> ingredients() {
		Map<Step, Integer> counts = new EnumMap<>(Step.class);
		for (Step step : steps) {
			if (!step.isAction()) {
				counts.merge(step, 1, Integer::sum);
			}
		}
		return counts;
	}

	/** Grill: ticks face-down for one side to cook through. Oven: ticks to bake through. */
	public int grillTicks() {
		return switch (this) {
			case HONEY_GLAZED_CHICKEN -> 140;
			case PIZZA -> 200;
			case RUSTIC_BREAD -> 160;
			case APPLE_TART -> 180;
			case MUTTON_KEBAB -> 120;
			case GLAZED_PORK_CHOP -> 130;
			case CRIMSON_SKEWER -> 120;
			case CARROT_CAKE -> 180;
			case SHEPHERDS_PIE -> 190;
			// a roast's side
			case ROAST_CHICKEN -> 90;
			case ROAST_MUTTON -> 80;
			default -> 100;
		};
	}

	/** Ticks a Perfect cook must finish within. */
	public int parTicks(Station station) {
		return station.scaleTicks(steps.size() * 34);
	}

	/** Ticks before the dish burns. */
	public int limitTicks(Station station) {
		return station.scaleTicks(steps.size() * 70 + 80);
	}

	/** Slow dishes (pot and oven) take 1 to 5 minutes on the oven, pan dishes 15 to 45 seconds. */
	private static final int SLOW_MIN = 60 * 20, SLOW_MAX = 5 * 60 * 20, QUICK_MIN = 15 * 20, QUICK_MAX = 45 * 20;

	/** Pot stews and soups, baking and curing take their time; grilling and searing in a pan is quick. */
	public boolean slowCook() {
		return method != Method.GRILL && method != Method.SEAR && method != Method.CHOP;
	}

	/** How rewarding the dish is: its hunger, its saturation, and what a Perfect cook adds. */
	public float reward() {
		return nutrition + saturation * 10.0F + perfectBuffs.size() * 3.0F;
	}

	/**
	 * Ticks the dish keeps cooking on the oven once its minigame is done: scaled by how rewarding it is among the dishes
	 * cooked the same way (slow or quick), so the best pot or oven dish takes 5 minutes. Quality doesn't change it.
	 */
	public int hobTicks() {
		float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
		for (Dish dish : values()) {
			// ranked among the dishes cooked the same way (smoking, never done on an oven, ranks on its own)
			if (dish.slowCook() == slowCook() && (dish.method == Method.SMOKE) == (method == Method.SMOKE)) {
				lo = Math.min(lo, dish.reward());
				hi = Math.max(hi, dish.reward());
			}
		}
		float t = hi > lo ? (reward() - lo) / (hi - lo) : 1.0F;
		int min = slowCook() ? SLOW_MIN : QUICK_MIN, max = slowCook() ? SLOW_MAX : QUICK_MAX;
		// to the nearest 5 seconds
		return Math.round((min + t * (max - min)) / 100.0F) * 100;
	}

	public String id() {
		return name().toLowerCase();
	}

	public static Dish byIndex(int index) {
		Dish[] all = values();
		return index >= 0 && index < all.length ? all[index] : null;
	}
}
