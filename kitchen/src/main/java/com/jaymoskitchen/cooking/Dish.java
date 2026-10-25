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
	BEETROOT_HASH(Method.CHOP, EnumSet.of(Station.BOARD), List.of(BEETROOT, BEETROOT, POTATO), 8, 0.7F, false,
		List.of(new Buff(MobEffects.HASTE, 90 * 20, 0))),
	CARROT_SOUP(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, CARROT, CARROT, SUGAR), 8, 0.6F, true,
		List.of(new Buff(MobEffects.NIGHT_VISION, 240 * 20, 0))),
	GLAZED_CARROTS(Method.CHOP, EnumSet.of(Station.BOARD), List.of(CARROT, CARROT, HONEY_BOTTLE), 7, 0.7F, false,
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
		List.of(new Buff(MobEffects.HEALTH_BOOST, 180 * 20, 0), new Buff(MobEffects.ABSORPTION, 60 * 20, 0))),
	// --- content drop 5: the orchard and the garden (Pantry for Blockheads' lemons, peaches, grapes and vegetables) ---
	LEMON_CHICKEN(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(CHICKEN, LEMON, LEMON), 12, 0.9F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0), new Buff(MobEffects.REGENERATION, 20 * 20, 0))),
	LEMON_COD(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(COD, LEMON, FLIP, FLIP), 9, 0.8F, false,
		List.of(new Buff(MobEffects.DOLPHINS_GRACE, 120 * 20, 0))),
	LEMON_TART(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, LEMON, SUGAR, EGG), 10, 0.6F, false,
		List.of(new Buff(MobEffects.HASTE, 120 * 20, 0), new Buff(MobEffects.LUCK, 300 * 20, 0))),
	PEACH_COBBLER(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, PEACH, PEACH, SUGAR), 10, 0.6F, false,
		List.of(new Buff(MobEffects.ABSORPTION, 90 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	FRUIT_SALAD(Method.CHOP, EnumSet.of(Station.BOARD), List.of(PEACH, GRAPES, APPLE), 7, 0.6F, false,
		List.of(new Buff(MobEffects.JUMP_BOOST, 120 * 20, 0))),
	RATATOUILLE(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, EGGPLANT, TOMATO, ONION), 9, 0.7F, true,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 120 * 20, 0), new Buff(MobEffects.RESISTANCE, 30 * 20, 0))),
	CHILI_CON_CARNE(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, BEEF, CHILI_PEPPER, TOMATO), 11, 0.8F,
		true, List.of(new Buff(MobEffects.FIRE_RESISTANCE, 120 * 20, 0), new Buff(MobEffects.STRENGTH, 60 * 20, 0))),
	// --- content drop 6: everything else no dish used (Naturalist's game and fish, Pantry's strawberries, turnips and peanuts, the Aether's fruit and meat, vanilla's cooked fish and meat) ---
	VENISON_STEW(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, VENISON, POTATO, BROWN_MUSHROOM), 11, 0.8F, true,
		List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0), new Buff(MobEffects.STRENGTH, 30 * 20, 0))),
	ROAST_DUCK(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(DUCK, APPLE), 11, 0.9F, false,
		List.of(new Buff(MobEffects.SLOW_FALLING, 120 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	BUSHMEAT_SKEWER(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(BUSHMEAT, CARROT, FLIP, FLIP), 9, 0.8F, false,
		List.of(new Buff(MobEffects.JUMP_BOOST, 90 * 20, 0))),
	MAMMOTH_ROAST(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(MAMMOTH_MEAT, POTATO, POTATO), 14, 1.0F, false,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 180 * 20, 0), new Buff(MobEffects.RESISTANCE, 60 * 20, 0))),
	HONEY_DRUMSTICKS(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(DRUMSTICK, HONEY_BOTTLE, FLIP, FLIP), 8, 0.8F, false,
		List.of(new Buff(MobEffects.HASTE, 90 * 20, 0))),
	MEAT_PASTY(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, MORSEL, MORSEL, POTATO), 9, 0.7F, false,
		List.of(new Buff(MobEffects.ABSORPTION, 60 * 20, 0))),
	FISH_CHOWDER(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, BASS, CATFISH, POTATO), 10, 0.8F, true,
		List.of(new Buff(MobEffects.WATER_BREATHING, 180 * 20, 0))),
	PIRANHA_SKEWER(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(PIRANHA, PIRANHA, FLIP, FLIP), 8, 0.7F, false,
		List.of(new Buff(MobEffects.STRENGTH, 60 * 20, 0))),
	DEEP_SEA_STEW(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, ANGLERFISH, BLOBFISH, DRIED_KELP), 10, 0.7F, true,
		List.of(new Buff(MobEffects.NIGHT_VISION, 240 * 20, 0), new Buff(MobEffects.CONDUIT_POWER, 60 * 20, 0))),
	SEAFOOD_BOIL(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, CRAB_MEAT, CLAM_MEAT, POTATO), 10, 0.8F, true,
		List.of(new Buff(MobEffects.DOLPHINS_GRACE, 90 * 20, 0), new Buff(MobEffects.RESISTANCE, 30 * 20, 0))),
	CRAB_CAKES(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(CRAB_MEAT, EGG, FLOUR, FLIP, FLIP), 9, 0.8F, false,
		List.of(new Buff(MobEffects.WATER_BREATHING, 120 * 20, 0))),
	LIZARD_JERKY(Method.SMOKE, EnumSet.of(Station.SMOKER), List.of(LIZARD_TAIL, SUGAR), 6, 0.9F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0))),
	STRAWBERRY_TART(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, STRAWBERRY, STRAWBERRY, SUGAR), 9, 0.6F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0), new Buff(MobEffects.LUCK, 300 * 20, 0))),
	PEANUT_BRITTLE(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(PEANUT, PEANUT, SUGAR), 5, 0.5F, false,
		List.of(new Buff(MobEffects.HASTE, 120 * 20, 0))),
	TURNIP_MASH(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, TURNIP, TURNIP, POTATO), 8, 0.7F, true,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 90 * 20, 0))),
	ORANGE_GLAZED_TAEGORE(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(TAEGORE_MEAT, ORANGE, FLIP, FLIP), 10, 0.8F, false,
		List.of(new Buff(MobEffects.STRENGTH, 90 * 20, 0))),
	SKY_FRUIT_SALAD(Method.CHOP, EnumSet.of(Station.BOARD), List.of(ORANGE, WYNDBERRY, BLUE_BERRY), 7, 0.6F, false,
		List.of(new Buff(MobEffects.SLOW_FALLING, 180 * 20, 0))),
	WHITE_APPLE_TART(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, WHITE_APPLE, SUGAR), 9, 0.6F, false,
		List.of(new Buff(MobEffects.REGENERATION, 30 * 20, 0), new Buff(MobEffects.LUCK, 300 * 20, 0))),
	KIRRID_ROAST(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(KIRRID_LOIN, SATIVAL_BULB), 12, 0.9F, false,
		List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0), new Buff(MobEffects.JUMP_BOOST, 60 * 20, 0))),
	PRISMALLARD_OMELETTE(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(PRISMALLARD_EGG, PRISMALLARD_EGG, FLIP), 7, 0.7F, false,
		List.of(new Buff(MobEffects.HASTE, 60 * 20, 0), new Buff(MobEffects.SPEED, 60 * 20, 0))),
	BURRUKAI_RIBS(Method.SMOKE, EnumSet.of(Station.SMOKER), List.of(BURRUKAI_RIB, SUGAR), 12, 0.9F, false,
		List.of(new Buff(MobEffects.STRENGTH, 90 * 20, 0), new Buff(MobEffects.ABSORPTION, 60 * 20, 0))),
	PRISMALLARD_STEW(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, PRISMALLARD_LEG, SATIVAL_BULB, WYNDBERRY), 10, 0.8F, true,
		List.of(new Buff(MobEffects.SLOW_FALLING, 120 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	FISH_PIE(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(DOUGH, COOKED_COD, COOKED_SALMON, POTATO), 12, 0.8F, false,
		List.of(new Buff(MobEffects.WATER_BREATHING, 180 * 20, 0), new Buff(MobEffects.ABSORPTION, 60 * 20, 0))),
	PLOUGHMANS_LUNCH(Method.CHOP, EnumSet.of(Station.BOARD), List.of(COOKED_PORKCHOP, COOKED_MUTTON, BREAD), 12, 0.9F, false,
		List.of(new Buff(MobEffects.RESISTANCE, 60 * 20, 0), new Buff(MobEffects.HEALTH_BOOST, 120 * 20, 0))),
	// --- (0.5.28) Farmer's Delight's cabbage, rice and cuts (see delight/Delight) ---
	COOKED_RICE(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, RICE, WATER_BOTTLE, SIMMER), 6, 0.5F, true,
		List.of(new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	FRIED_RICE(Method.SEQUENCE, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(BOWL, RICE, EGG, STIR, CARROT, STIR, SIMMER), 10, 0.7F, true,
		List.of(new Buff(MobEffects.HASTE, 120 * 20, 0))),
	CABBAGE_ROLLS(Method.STIR, EnumSet.of(Station.FURNACE, Station.SMOKER), List.of(CABBAGE_LEAF, CABBAGE_LEAF, MINCED_BEEF, RICE), 9, 0.7F,
		false, List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0))),
	HAMBURGER(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(MINCED_BEEF, BREAD, CABBAGE_LEAF, FLIP, FLIP), 12, 0.8F, false,
		List.of(new Buff(MobEffects.STRENGTH, 90 * 20, 0))),
	BACON_AND_EGGS(Method.GRILL, EnumSet.of(Station.CAMPFIRE), List.of(RAW_BACON, RAW_BACON, EGG, FLIP), 9, 0.7F, false,
		List.of(new Buff(MobEffects.SPEED, 120 * 20, 0))),
	MIXED_SALAD(Method.CHOP, EnumSet.of(Station.BOARD), List.of(CABBAGE, BEETROOT, CARROT), 7, 0.6F, false,
		List.of(new Buff(MobEffects.REGENERATION, 15 * 20, 0))),
	// feasts: set down as a block, four servings each (see delight/Feast)
	ROAST_CHICKEN_FEAST(Method.ROAST, EnumSet.of(Station.CAMPFIRE), List.of(CHICKEN, CHICKEN, POTATO, CARROT, BREAD), 11, 0.9F, false,
		List.of(new Buff(MobEffects.RESISTANCE, 90 * 20, 0), new Buff(MobEffects.REGENERATION, 10 * 20, 0))),
	SHEPHERDS_PIE_FEAST(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(MUTTON, MUTTON, POTATO, POTATO, CARROT), 12, 0.8F, false,
		List.of(new Buff(MobEffects.HEALTH_BOOST, 180 * 20, 0))),
	HONEY_GLAZED_HAM_FEAST(Method.SMOKE, EnumSet.of(Station.SMOKER), List.of(PORKCHOP, PORKCHOP, HONEY_BOTTLE, SWEET_BERRIES), 12, 0.9F,
		false, List.of(new Buff(MobEffects.ABSORPTION, 120 * 20, 0), new Buff(MobEffects.STRENGTH, 60 * 20, 0))),
	STUFFED_PUMPKIN_FEAST(Method.OVEN, EnumSet.of(Station.FURNACE), List.of(PUMPKIN, RICE, BROWN_MUSHROOM, CARROT, POTATO), 11, 0.8F, false,
		List.of(new Buff(MobEffects.RESISTANCE, 120 * 20, 0), new Buff(MobEffects.HASTE, 60 * 20, 0)));

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

	/** Every ingredient that can show up as a wrong-answer button (Pantry's only with Pantry; see {@link #decoys}). */
	public static final List<Step> DECOY_POOL = List.of(BEEF, POTATO, CARROT, FLOUR, BROWN_MUSHROOM, RED_MUSHROOM,
		EGG, SUGAR, SWEET_BERRIES, SALMON, DRIED_KELP, CHICKEN, BEETROOT, MUTTON, COD, APPLE, PUMPKIN, NETHER_WART, CRIMSON_FUNGUS, WARPED_FUNGUS,
		LEMON, PEACH, GRAPES, TOMATO, ONION, EGGPLANT, CHILI_PEPPER, STRAWBERRY, TURNIP, PEANUT, VENISON, DUCK, BUSHMEAT, MAMMOTH_MEAT,
		DRUMSTICK, MORSEL, BASS, CATFISH, PIRANHA, ANGLERFISH, BLOBFISH, CRAB_MEAT, CLAM_MEAT, LIZARD_TAIL, ORANGE, WYNDBERRY, BLUE_BERRY,
		WHITE_APPLE, SATIVAL_BULB, PRISMALLARD_EGG, TAEGORE_MEAT, BURRUKAI_RIB, KIRRID_LOIN, PRISMALLARD_LEG, COOKED_COD, COOKED_SALMON,
		COOKED_PORKCHOP, COOKED_MUTTON, BREAD, CABBAGE, CABBAGE_LEAF, RICE, MINCED_BEEF, RAW_BACON);

	/** The decoy pool, less the ingredients this game doesn't have. */
	public static List<Step> decoys() {
		return DECOY_POOL.stream().filter(Step::available).toList();
	}

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

	/** Whether every ingredient exists in this game (the Pantry dishes need Pantry for Blockheads). */
	public boolean available() {
		for (Step step : steps) {
			if (!step.isAction() && !step.available()) {
				return false;
			}
		}
		return true;
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
			case LEMON_COD -> 100;
			case LEMON_TART -> 180;
			case PEACH_COBBLER -> 190;
			case STRAWBERRY_TART, MEAT_PASTY -> 170;
			case WHITE_APPLE_TART -> 180;
			case FISH_PIE -> 190;
			case BUSHMEAT_SKEWER, PIRANHA_SKEWER -> 120;
			case HONEY_DRUMSTICKS, ORANGE_GLAZED_TAEGORE -> 130;
			case CRAB_CAKES, PRISMALLARD_OMELETTE -> 90;
			// a roast's side
			case ROAST_CHICKEN -> 90;
			case ROAST_MUTTON -> 80;
			case LEMON_CHICKEN, ROAST_DUCK -> 90;
			case MAMMOTH_ROAST -> 110;
			case KIRRID_ROAST -> 85;
			case HAMBURGER, BACON_AND_EGGS -> 100;
			case ROAST_CHICKEN_FEAST -> 110;
			case SHEPHERDS_PIE_FEAST, STUFFED_PUMPKIN_FEAST -> 220;
			default -> 100;
		};
	}

	/** A feast: it comes out as a block to set down, with servings to take from it (see delight/Feast). */
	public boolean feast() {
		return this == ROAST_CHICKEN_FEAST || this == SHEPHERDS_PIE_FEAST || this == HONEY_GLAZED_HAM_FEAST || this == STUFFED_PUMPKIN_FEAST;
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
