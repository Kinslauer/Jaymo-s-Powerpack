package com.jaymospowers.fishing;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Track C of docs/Plan-fishing.md: every kind of fish (and the critters a pot or a net takes). A fish's {@code kind} in the
 * fight and in a bait's table is its ordinal here: the first six are the kinds the fishing had from the start (cod, salmon,
 * pufferfish, tropical fish, any other mod's shark, skipjack tuna), and keep their numbers.
 *
 * <p>Each species lives in its own water ({@link Water}) and climate (a band of the biome's base temperature), feeds at its
 * own hours ({@link Feeds}: eels and catfish after dark, zander at dusk), keeps to its own depth ({@link Depth}), has a size
 * range ({@link #kgMin} to {@link #kgMax}: half of all catches are in the bottom fifth of it, about one in a hundred in the
 * top tenth, {@link FishCatch#roll}), fights as hard as {@link #strength}, likes some baits more than others
 * ({@link #likes}, by {@link Tackle.Cat}) and some seasons more than others ({@link #seasons}: spring, summer, autumn,
 * winter; Jaymo's Kitchen's seasons). Ideas from Reel to Fish, Aquaculture, True Fishing and Advanced Fishing (all rights
 * reserved: nothing copied); names, models and textures our own (tools/gen_fishing_species.py).
 */
public enum Species {
	//         id            water        tMin  tMax  feeds            depth          kgMin  kgMax   str   flags                          school  wt fillet        WORM PLANT MEAT LIVE LURE HEAVY   spring summer autumn winter   milli/kg
	COD("cod", Water.SEA, -0.6F, 0.9F, Feeds.DAWN_DUSK, Depth.MID, 0.8F, 7.0F, 0.35F, 0, 0, 0, 0, Fillet.WHITE, l(3, 1, 2, 2, 2, 3), s(1, 1, 1, 1), 100),
	SALMON("salmon", Water.BOTH, -0.6F, 0.8F, Feeds.DAWN_DUSK, Depth.MID, 1.0F, 9.0F, 0.5F, 0, 0, 0, 0, Fillet.OILY, l(3, 2, 1, 2, 3, 1), s(1, 1, 1.3F, 0.8F), 200),
	PUFFERFISH("pufferfish", Water.SEA, 0.5F, 2.0F, Feeds.ALWAYS, Depth.MID, 0.16F, 1.3F, 0.75F, 0, 0, 0, 0, Fillet.NONE, l(2, 0, 2, 0, 1, 0), s(1, 1, 1, 1), 60),
	TROPICAL("tropical_fish", Water.SEA, 0.5F, 2.0F, Feeds.DAY, Depth.MID, 0.08F, 0.7F, 0.6F, 0, 0, 0, 0, Fillet.NONE, l(2, 1, 1, 0, 0, 0), s(1, 1, 1, 1), 40),
	SHARK("shark", Water.SEA, -0.5F, 2.0F, Feeds.ALWAYS, Depth.MID, 80.0F, 650.0F, 1.0F, Species.BIG | Species.PREDATOR, 0, 0, 0, Fillet.NONE, l(0, 0, 3, 4, 0, 3), s(1, 1, 1, 1), 60),
	TUNA("skipjack", Water.SEA, 0.3F, 2.0F, Feeds.DAY, Depth.SURFACE, 1.0F, 9.0F, 0.8F, Species.BIG | Species.PREDATOR, 0, 0, 0, Fillet.OILY, l(0, 0, 1, 4, 3, 4), s(1, 1.2F, 1, 0.7F), 300),
	// freshwater
	CARP("carp", Water.FRESH, 0.5F, 2.0F, Feeds.ALWAYS, Depth.BOTTOM, 1.0F, 25.0F, 0.6F, Species.OURS | Species.LEGEND, 2, 4, 4, Fillet.WHITE, l(2, 4, 0, 0, 0, 0), s(1, 1.4F, 1, 0.3F), 120),
	PIKE("pike", Water.FRESH, -0.3F, 0.95F, Feeds.DAY, Depth.MID, 1.0F, 20.0F, 0.65F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 1, 2, Fillet.WHITE, l(1, 0, 2, 4, 4, 1), s(1.1F, 0.8F, 1.4F, 1.2F), 200),
	PERCH("perch", Water.FRESH, 0.0F, 1.0F, Feeds.DAY, Depth.MID, 0.1F, 2.0F, 0.35F, Species.OURS | Species.PREDATOR, 3, 6, 6, Fillet.WHITE, l(4, 0, 1, 2, 3, 0), s(1, 1, 1, 0.9F), 180),
	ZANDER("zander", Water.FRESH, 0.4F, 1.0F, Feeds.DUSK, Depth.BOTTOM, 1.0F, 12.0F, 0.55F, Species.OURS | Species.PREDATOR | Species.LEGEND, 2, 3, 2, Fillet.WHITE, l(1, 0, 2, 4, 3, 1), s(1, 0.9F, 1.3F, 1), 250),
	BREAM("bream", Water.FRESH, 0.4F, 1.2F, Feeds.ALWAYS, Depth.BOTTOM, 0.5F, 5.0F, 0.3F, Species.OURS, 4, 8, 5, Fillet.WHITE, l(4, 3, 0, 0, 0, 0), s(1, 1.2F, 1, 0.5F), 60),
	TENCH("tench", Water.FRESH, 0.5F, 1.2F, Feeds.DAWN_DUSK, Depth.BOTTOM, 0.5F, 4.0F, 0.5F, Species.OURS, 1, 3, 3, Fillet.WHITE, l(4, 2, 0, 0, 0, 0), s(1.1F, 1.4F, 0.8F, 0.2F), 90),
	CATFISH("catfish", Water.FRESH, 0.6F, 2.0F, Feeds.NIGHT, Depth.BOTTOM, 2.0F, 60.0F, 0.8F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 1, 2, Fillet.WHITE, l(2, 1, 4, 3, 1, 1), s(0.9F, 1.4F, 1, 0.2F), 140),
	EEL("eel", Water.FRESH, -0.2F, 1.2F, Feeds.NIGHT, Depth.BOTTOM, 0.2F, 3.0F, 0.55F, Species.OURS, 1, 2, 3, Fillet.OILY, l(4, 0, 3, 2, 0, 0), s(1, 1.3F, 1.1F, 0.1F), 400),
	TROUT("trout", Water.FRESH, -0.4F, 0.8F, Feeds.DAY, Depth.SURFACE, 0.3F, 6.0F, 0.5F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 3, 5, Fillet.OILY, l(4, 0, 1, 2, 4, 0), s(1.3F, 0.8F, 1.2F, 0.7F), 300),
	CHUB("chub", Water.FRESH, 0.3F, 1.0F, Feeds.DAY, Depth.SURFACE, 0.3F, 4.0F, 0.45F, Species.OURS, 2, 5, 4, Fillet.WHITE, l(3, 3, 1, 1, 1, 0), s(1, 1.2F, 1, 0.7F), 50),
	// northern
	CHAR("char", Water.FRESH, -1.0F, 0.3F, Feeds.DAY, Depth.MID, 0.5F, 8.0F, 0.5F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 3, 4, Fillet.OILY, l(3, 0, 1, 2, 4, 1), s(1, 0.7F, 1.1F, 1.4F), 350),
	GRAYLING("grayling", Water.FRESH, -0.7F, 0.4F, Feeds.DAY, Depth.SURFACE, 0.2F, 2.0F, 0.4F, Species.OURS, 2, 5, 4, Fillet.WHITE, l(4, 1, 0, 0, 3, 0), s(1.2F, 1, 1.1F, 0.9F), 220),
	WHITEFISH("whitefish", Water.FRESH, -1.0F, 0.3F, Feeds.DAY, Depth.MID, 0.3F, 3.0F, 0.35F, Species.OURS, 4, 8, 5, Fillet.WHITE, l(4, 1, 0, 0, 1, 0), s(1, 0.8F, 1.1F, 1.4F), 160),
	BURBOT("burbot", Water.FRESH, -1.0F, 0.3F, Feeds.NIGHT, Depth.BOTTOM, 0.5F, 8.0F, 0.5F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 1, 2, Fillet.WHITE, l(2, 0, 4, 3, 0, 0), s(0.6F, 0.4F, 1, 2.0F), 200),
	// sea
	MACKEREL("mackerel", Water.SEA, 0.3F, 1.0F, Feeds.DAY, Depth.SURFACE, 0.2F, 2.0F, 0.45F, Species.OURS | Species.PREDATOR, 5, 10, 8, Fillet.OILY, l(2, 0, 2, 1, 4, 2), s(1.2F, 1.4F, 1, 0.6F), 120),
	HERRING("herring", Water.SEA, -0.5F, 0.6F, Feeds.ALWAYS, Depth.SURFACE, 0.1F, 0.8F, 0.3F, Species.OURS, 6, 12, 8, Fillet.OILY, l(3, 1, 1, 0, 1, 0), s(1, 1, 1.2F, 0.9F), 80),
	POLLOCK("pollock", Water.SEA, -0.5F, 0.7F, Feeds.ALWAYS, Depth.MID, 0.5F, 12.0F, 0.55F, Species.OURS | Species.PREDATOR, 2, 5, 4, Fillet.WHITE, l(2, 0, 2, 3, 3, 4), s(1, 1, 1.1F, 1), 150),
	SEA_BASS("sea_bass", Water.SEA, 0.5F, 1.0F, Feeds.DUSK, Depth.MID, 0.5F, 8.0F, 0.6F, Species.OURS | Species.PREDATOR | Species.LEGEND, 1, 3, 3, Fillet.WHITE, l(3, 0, 2, 4, 4, 1), s(1.1F, 1.3F, 1.1F, 0.5F), 450),
	HALIBUT("halibut", Water.SEA, -0.6F, 0.6F, Feeds.ALWAYS, Depth.BOTTOM, 2.0F, 150.0F, 0.85F, Species.OURS | Species.BIG | Species.PREDATOR | Species.LEGEND | Species.DEEP, 1, 1, 1, Fillet.WHITE, l(1, 0, 3, 4, 1, 4), s(1, 1, 1, 1), 350),
	SWORDFISH("swordfish", Water.SEA, 0.5F, 2.0F, Feeds.NIGHT, Depth.MID, 30.0F, 300.0F, 0.95F, Species.OURS | Species.BIG | Species.PREDATOR | Species.LEGEND | Species.DEEP, 1, 1, 1, Fillet.OILY, l(0, 0, 1, 4, 0, 4), s(1, 1.2F, 1, 0.8F), 500),
	// sharks
	CATSHARK("catshark", Water.SEA, 0.3F, 1.0F, Feeds.NIGHT, Depth.BOTTOM, 0.5F, 2.0F, 0.45F, Species.OURS | Species.SHARKISH, 1, 3, 3, Fillet.WHITE, l(3, 0, 4, 2, 0, 1), s(1, 1, 1, 1), 60),
	DOGFISH("dogfish", Water.SEA, -0.3F, 0.8F, Feeds.ALWAYS, Depth.BOTTOM, 1.0F, 9.0F, 0.55F, Species.OURS | Species.PREDATOR | Species.SHARKISH, 3, 6, 3, Fillet.WHITE, l(2, 0, 4, 3, 1, 2), s(1, 1, 1, 1), 80),
	BLUE_SHARK("blue_shark", Water.SEA, 0.5F, 1.5F, Feeds.ALWAYS, Depth.SURFACE, 30.0F, 200.0F, 0.9F, Species.OURS | Species.BIG | Species.PREDATOR | Species.DEEP | Species.SHARKISH, 1, 2, 1, Fillet.NONE, l(0, 0, 3, 4, 0, 3), s(1, 1.3F, 1, 0.6F), 60),
	HAMMERHEAD("hammerhead", Water.SEA, 0.8F, 2.0F, Feeds.DUSK, Depth.MID, 50.0F, 450.0F, 0.95F, Species.OURS | Species.BIG | Species.PREDATOR | Species.DEEP | Species.SHARKISH, 1, 3, 1, Fillet.NONE, l(0, 0, 3, 4, 0, 3), s(1, 1.2F, 1, 0.7F), 60),
	GREAT_WHITE("great_white", Water.SEA, 0.3F, 1.0F, Feeds.ALWAYS, Depth.MID, 300.0F, 1500.0F, 1.0F, Species.OURS | Species.BIG | Species.PREDATOR | Species.LEGEND | Species.DEEP | Species.SHARKISH, 1, 1, 1, Fillet.NONE, l(0, 0, 4, 4, 0, 3), s(1, 1.2F, 1.1F, 0.8F), 60),
	// critters: a pot or a net takes them, not a hook
	SHRIMP("shrimp", Water.SEA, 0.0F, 1.5F, Feeds.ALWAYS, Depth.BOTTOM, 0.01F, 0.05F, 0.1F, Species.OURS | Species.CRITTER, 6, 12, 6, Fillet.SHELL, l(0, 1, 3, 0, 0, 0), s(1, 1.2F, 1, 0.8F), 900),
	CRAYFISH("crayfish", Water.FRESH, 0.2F, 1.2F, Feeds.NIGHT, Depth.BOTTOM, 0.03F, 0.15F, 0.1F, Species.OURS | Species.CRITTER | Species.WALKER, 1, 3, 4, Fillet.SHELL, l(1, 1, 4, 0, 0, 0), s(1, 1.3F, 1, 0.3F), 800),
	CRAB("crab", Water.SEA, 0.2F, 1.5F, Feeds.NIGHT, Depth.BOTTOM, 0.2F, 2.0F, 0.1F, Species.OURS | Species.CRITTER | Species.WALKER, 1, 3, 5, Fillet.SHELL, l(1, 0, 4, 1, 0, 0), s(1, 1.2F, 1, 0.6F), 500),
	LOBSTER("lobster", Water.SEA, -0.3F, 0.8F, Feeds.NIGHT, Depth.BOTTOM, 0.5F, 6.0F, 0.1F, Species.OURS | Species.CRITTER | Species.WALKER, 1, 1, 2, Fillet.SHELL, l(0, 0, 4, 2, 0, 0), s(1, 1.2F, 1, 0.7F), 1500),
	JELLYFISH("jellyfish", Water.SEA, 0.3F, 2.0F, Feeds.ALWAYS, Depth.SURFACE, 0.1F, 3.0F, 0.1F, Species.OURS | Species.CRITTER, 1, 4, 3, Fillet.NONE, l(0, 0, 0, 0, 0, 0), s(1, 1.4F, 1, 0.5F), 0),
	// any other mod's fish we have no species for (True Fishing's idea: their fish count too)
	OTHER("other", Water.BOTH, -2.0F, 3.0F, Feeds.ALWAYS, Depth.MID, 0.3F, 3.0F, 0.45F, 0, 0, 0, 0, Fillet.NONE, l(2, 2, 2, 1, 2, 0), s(1, 1, 1, 1), 80);

	/** Too big for any rod but the sea rod (the metal rod); hooked, it pulls the angler toward it. */
	public static final int BIG = 1;
	/** Hunts smaller fish ({@link FishSchools}), and goes for live bait and lures. */
	public static final int PREDATOR = 2;
	/** One of ours: its own entity, spawns, model and items. */
	public static final int OURS = 4;
	/** Each water's legendary giant may be one of these ({@link FishLegends}). */
	public static final int LEGEND = 8;
	/** Only in deep oceans. */
	public static final int DEEP = 16;
	/** Taken by a crab pot or a landing net, not a hook. */
	public static final int CRITTER = 32;
	/** Walks the bottom rather than swimming. */
	public static final int WALKER = 64;
	/** A shark: cut up on the shore if big, and the jaw kept as a trophy. */
	public static final int SHARKISH = 128;

	/** Where it lives. */
	public enum Water { FRESH, SEA, BOTH }

	/** When it feeds: by day (keenest at dawn and dusk), at dawn and dusk, at dusk, after dark, or whenever. */
	public enum Feeds { DAY, DAWN_DUSK, DUSK, NIGHT, ALWAYS }

	/** The water it keeps to. */
	public enum Depth { SURFACE, MID, BOTTOM }

	/** What its flesh cuts into on Kitchen's board (white fish like cod, oily like salmon), or a shellfish. */
	public enum Fillet { WHITE, OILY, SHELL, NONE }

	public final String id;
	public final Water water;
	public final float tMin, tMax;
	public final Feeds feeds;
	public final Depth depth;
	public final float kgMin, kgMax;
	public final float strength;
	final int flags;
	public final int schoolMin, schoolMax, spawnWeight;
	public final Fillet fillet;
	/** How much it likes each kind of bait ({@link Tackle.Cat}: worm, plant, meat, live fish, lure, heavy lure), 0 = never. */
	final int[] likes;
	/** How keen it is in spring, summer, autumn and winter. */
	final float[] seasons;
	/** What a fisherman pays for it (thousandths of an emerald per kg). */
	public final int pricePerKg;

	Species(String id, Water water, float tMin, float tMax, Feeds feeds, Depth depth, float kgMin, float kgMax, float strength, int flags,
		int schoolMin, int schoolMax, int spawnWeight, Fillet fillet, int[] likes, float[] seasons, int pricePerKg) {
		this.id = id;
		this.water = water;
		this.tMin = tMin;
		this.tMax = tMax;
		this.feeds = feeds;
		this.depth = depth;
		this.kgMin = kgMin;
		this.kgMax = kgMax;
		this.strength = strength;
		this.flags = flags;
		this.schoolMin = schoolMin;
		this.schoolMax = schoolMax;
		this.spawnWeight = spawnWeight;
		this.fillet = fillet;
		this.likes = likes;
		this.seasons = seasons;
		this.pricePerKg = pricePerKg;
	}

	private static int[] l(int... v) {
		return v;
	}

	private static float[] s(float... v) {
		return v;
	}

	private static final Species[] VALUES = values();
	private static final Map<String, Species> BY_ID = new HashMap<>();

	static {
		for (Species sp : VALUES) {
			BY_ID.put(sp.id, sp);
		}
	}

	/** The species of {@code kind} (an ordinal), or null. */
	public static @Nullable Species of(int kind) {
		return kind >= 0 && kind < VALUES.length ? VALUES[kind] : null;
	}

	/** The species called {@code id}, or null. */
	public static @Nullable Species byId(String id) {
		return BY_ID.get(id);
	}

	public int kind() {
		return ordinal();
	}

	public boolean big() {
		return (flags & BIG) != 0;
	}

	public boolean predator() {
		return (flags & PREDATOR) != 0;
	}

	public boolean ours() {
		return (flags & OURS) != 0;
	}

	public boolean legendary() {
		return (flags & LEGEND) != 0;
	}

	public boolean deep() {
		return (flags & DEEP) != 0;
	}

	public boolean critter() {
		return (flags & CRITTER) != 0;
	}

	public boolean walker() {
		return (flags & WALKER) != 0;
	}

	public boolean sharkish() {
		return (flags & SHARKISH) != 0;
	}

	/** A rod can hook it. */
	public boolean hookable() {
		return !critter();
	}

	/** A landing net scoops it from the water as it swims: the critters (but not a jellyfish's sting... it's a net) and the little ones. */
	public boolean nettable() {
		return critter() || this == TROPICAL || this == PUFFERFISH || !predator() && kgMax <= 1.0F;
	}

	/** The weight a fish of this kind usually is (the size its model and hitbox are drawn at). */
	public float usualKg() {
		return kgMin + (kgMax - kgMin) * 0.24F;
	}

	/** How much it likes bait of {@code cat}. */
	public int likes(Tackle.Cat cat) {
		return likes[cat.ordinal()];
	}

	/** The translation key of its name. */
	public String nameKey() {
		return switch (this) {
			case COD, SALMON, PUFFERFISH, TROPICAL -> "item.minecraft." + id;
			case SHARK -> "species.jaymospowers.shark";
			case TUNA -> "entity.jaymospowers.skipjack";
			case OTHER -> "species.jaymospowers.other";
			default -> "entity.jaymospowers." + id;
		};
	}

	/** Whether the water at {@code pos} is the sea (an ocean or a beach). */
	public static boolean sea(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_DEEP_OCEAN);
	}

	/** It could live in this biome's water. */
	public boolean livesIn(Holder<Biome> biome) {
		boolean sea = sea(biome);
		if (water == Water.SEA && !sea || water == Water.FRESH && sea) {
			return false;
		}
		if (deep() && !biome.is(BiomeTags.IS_DEEP_OCEAN)) {
			return false;
		}
		float t = biome.value().getBaseTemperature();
		return t >= tMin && t <= tMax;
	}

	/**
	 * How ready a fish of this kind is to feed at {@code pos} just now: by its hours (an eel by night, a zander at dusk),
	 * its season (Kitchen's, when it's there) and the rain.
	 */
	public float activity(ServerLevel level, BlockPos pos) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean dawn = day >= 23000 || day < 1500, dusk = day >= 11000 && day < 13500, night = day >= 13500 && day < 23000;
		float f = switch (feeds) {
			case DAY -> dawn || dusk ? 1.6F : night ? 0.3F : 1.0F;
			case DAWN_DUSK -> dawn || dusk ? 1.6F : 1.0F;
			case DUSK -> dusk ? 2.5F : night ? 1.0F : dawn ? 1.3F : 0.5F;
			case NIGHT -> night ? 2.2F : dawn || dusk ? 1.4F : 0.25F;
			case ALWAYS -> dawn || dusk ? 1.3F : 1.0F;
		};
		int season = FishSeasons.season(level);
		if (season >= 0) {
			f *= seasons[season];
		}
		if (level.isRainingAt(pos.above())) {
			f *= FishWaters.RAIN_BITES;
		}
		return f;
	}

	/** A weight drawn on the size curve, or a legend's (a third to two thirds over the top of the range). */
	public float rollKg(RandomSource random, boolean legend) {
		if (legend) {
			return Math.round(kgMax * (1.3F + random.nextFloat() * 0.35F) * 10.0F) / 10.0F;
		}
		return FishCatch.curve(kgMin, kgMax, random);
	}

	/** Length (cm) of a fish of this kind weighing {@code kg}: grows with the cube root of its weight. */
	public int lengthCm(float kg) {
		float usualCm = switch (this) {
			case COD -> 55; case SALMON -> 60; case PUFFERFISH -> 25; case TROPICAL -> 15; case SHARK -> 300; case TUNA -> 60;
			case CARP -> 55; case PIKE -> 75; case PERCH -> 25; case ZANDER -> 55; case BREAM -> 42; case TENCH -> 36;
			case CATFISH -> 130; case EEL -> 65; case TROUT -> 42; case CHUB -> 38; case CHAR -> 45; case GRAYLING -> 36;
			case WHITEFISH -> 38; case BURBOT -> 50; case MACKEREL -> 35; case HERRING -> 26; case POLLOCK -> 62; case SEA_BASS -> 50;
			case HALIBUT -> 110; case SWORDFISH -> 240; case CATSHARK -> 60; case DOGFISH -> 90; case BLUE_SHARK -> 260;
			case HAMMERHEAD -> 300; case GREAT_WHITE -> 400; case SHRIMP -> 6; case CRAYFISH -> 12; case CRAB -> 15; case LOBSTER -> 40;
			case JELLYFISH -> 25; case OTHER -> 40;
		};
		return Math.max(1, Math.round(usualCm * (float) Math.cbrt(kg / usualKg())));
	}

	/** How much bigger than its kind's usual a fish of {@code kg} is drawn (the scale attribute). */
	public float scaleFor(float kg) {
		return Mth.clamp((float) Math.cbrt(kg / usualKg()), 0.45F, 2.6F);
	}
}
