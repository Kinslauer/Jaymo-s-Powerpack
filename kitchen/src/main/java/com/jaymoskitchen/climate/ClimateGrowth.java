package com.jaymoskitchen.climate;

import com.jaymoskitchen.Stacked;
import com.jaymoskitchen.delight.RichSoilFarmlandBlock;
import com.jaymoskitchen.farm.FertileFarmlandBlock;
import com.jaymoskitchen.farm.SlowFarms;
import com.jaymoskitchen.season.SeasonalCrops;
import com.jaymoskitchen.season.Seasons;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Climate based growth (after Climate Based Growth by me.user, CC0): every crop and sapling has a climate it likes, and
 * grows fastest there, slower the further the land is from it. Wheat and potatoes like the temperate middle, melons,
 * cane, cocoa and acacia the hot lands, spruce and sweet berries the cold. Nothing stops: at the worst it grows at
 * {@link #LEAST} of its pace, and every common crop is near its best in the temperate lands the stories start in.
 * <p>
 * How warm the land is comes from our own climate, not the biome's flat number: the land's feel tags ({@link Warmth#feel}),
 * then the continental heat map the Overworld's lands were laid out by (when Jaymo's Kingdoms is here,
 * {@link Warmth#CLIMATE_MAP}: the climate's own temperature, smooth across biome borders), else the biome's temperature;
 * then the season's chill in the temperate lands ({@link Seasons#chill}) and the cold of height.
 * <p>
 * Stewardship: a farmer can help a plant out of its climate. Under glass (a greenhouse, {@link SeasonalCrops#GREENHOUSE_ROOF})
 * or in a room a hearth warms or an icebox cools, it feels most of the way to the climate it likes ({@link #GLASS},
 * {@link #ROOM}); under any other roof a little ({@link #ROOF}). Watered farmland widens what it will put up with
 * ({@link #WATERED}), fertile or rich soil more ({@link #FERTILE}).
 * <p>
 * {@code ClimateGrowthMixin} asks {@link #growth} before each random tick of a plant in the table (or any crop or sapling
 * not in it, at {@link #ANY}): under 1 it may skip the tick, over 1 it may give one more. On top of the slow farms and
 * the seasons, which still apply; a Time Bloom's ticks ({@link SlowFarms#hurry}) aren't touched.
 */
public final class ClimateGrowth {
	/** The least a plant grows, as a share of its pace, however far from its climate. */
	static final float LEAST = 0.5F;
	/** Under glass, or in a warmed or cooled room, how much of the way to the climate it likes a plant feels. */
	static final float GLASS = 0.6F, ROOM = 0.6F;
	/** Under another roof. */
	static final float ROOF = 0.2F;
	/** How much wider a plant's tolerance is on watered farmland, and on fertile or rich soil. */
	static final float WATERED = 1.25F, FERTILE = 1.5F;
	/** The cold of height: warmth lost a block above {@link #HEIGHT_FROM} (the game's own rate). */
	static final float HEIGHT_COLD = 0.00125F;
	static final int HEIGHT_FROM = 80;
	/**
	 * The climate's temperature (-1 to 1, the heat map's) as the warmth biomes are given (the scale the table is in):
	 * the ice, the cold, the temperate middle, the warm and the burning sands.
	 */
	static final float[] MAP_AT = {-1.0F, -0.45F, -0.15F, 0.0F, 0.2F, 0.55F, 1.0F};
	static final float[] MAP_WARMTH = {-0.5F, 0.0F, 0.3F, 0.75F, 0.95F, 1.5F, 2.0F};
	/** The feel tags' lands ({@link Warmth#FEEL_PUSH}, scorching to frozen) as warmth. */
	static final float[] FEEL_WARMTH = {2.0F, 1.5F, 1.0F, 0.8F, 0.4F, 0.0F, -0.5F};

	/** A plant's liking: the warmth it grows best in, how far off it still does well, and its pace there. */
	record Liking(float best, float tolerance, float most) {
	}

	/** Any crop or sapling the table doesn't name: a temperate field plant. */
	static final Liking ANY = new Liking(0.8F, 0.85F, 1.05F);
	/** Each plant's liking, by block id (Climate Based Growth's table, with the Kitchen's own crops). */
	static final Map<String, Liking> LIKINGS = Map.ofEntries(
		Map.entry("minecraft:wheat", new Liking(0.8F, 0.85F, 1.05F)),
		Map.entry("minecraft:carrots", new Liking(0.8F, 0.8F, 1.05F)),
		Map.entry("minecraft:potatoes", new Liking(0.85F, 0.7F, 1.1F)),
		Map.entry("minecraft:beetroots", new Liking(0.8F, 0.75F, 1.15F)),
		Map.entry("minecraft:torchflower_crop", new Liking(1.0F, 0.7F, 1.1F)),
		Map.entry("minecraft:pitcher_crop", new Liking(1.0F, 0.7F, 1.1F)),
		Map.entry("minecraft:pumpkin_stem", new Liking(1.1F, 0.7F, 1.2F)),
		Map.entry("minecraft:melon_stem", new Liking(1.5F, 0.8F, 1.25F)),
		Map.entry("minecraft:sugar_cane", new Liking(1.6F, 0.8F, 1.3F)),
		Map.entry("minecraft:bamboo", new Liking(1.2F, 0.75F, 1.2F)),
		Map.entry("minecraft:cocoa", new Liking(1.4F, 0.7F, 1.25F)),
		Map.entry("minecraft:cactus", new Liking(1.8F, 0.8F, 1.2F)),
		Map.entry("minecraft:oak_sapling", new Liking(0.8F, 0.85F, 1.1F)),
		Map.entry("minecraft:birch_sapling", new Liking(0.7F, 0.8F, 1.1F)),
		Map.entry("minecraft:spruce_sapling", new Liking(0.25F, 0.9F, 1.05F)),
		Map.entry("minecraft:dark_oak_sapling", new Liking(0.9F, 0.75F, 1.1F)),
		Map.entry("minecraft:pale_oak_sapling", new Liking(0.7F, 0.75F, 1.1F)),
		Map.entry("minecraft:jungle_sapling", new Liking(1.3F, 0.9F, 1.15F)),
		Map.entry("minecraft:acacia_sapling", new Liking(1.7F, 0.8F, 1.2F)),
		Map.entry("minecraft:mangrove_propagule", new Liking(1.1F, 0.75F, 1.15F)),
		Map.entry("minecraft:cherry_sapling", new Liking(0.6F, 0.8F, 1.1F)),
		Map.entry("minecraft:azalea", new Liking(0.8F, 0.7F, 1.1F)),
		Map.entry("minecraft:flowering_azalea", new Liking(0.8F, 0.7F, 1.1F)),
		Map.entry("minecraft:brown_mushroom", new Liking(0.7F, 0.95F, 1.05F)),
		Map.entry("minecraft:red_mushroom", new Liking(0.7F, 0.95F, 1.05F)),
		Map.entry("minecraft:crimson_fungus", new Liking(2.0F, 1.0F, 1.1F)),
		Map.entry("minecraft:warped_fungus", new Liking(2.0F, 1.0F, 1.1F)),
		Map.entry("minecraft:nether_wart", new Liking(2.0F, 1.0F, 1.05F)),
		Map.entry("minecraft:sweet_berry_bush", new Liking(0.25F, 0.9F, 1.1F)),
		Map.entry("minecraft:kelp", new Liking(0.5F, 0.85F, 1.1F)),
		Map.entry("jaymoskitchen:cabbages", new Liking(0.6F, 0.8F, 1.1F)),
		Map.entry("jaymoskitchen:onions", new Liking(0.75F, 0.8F, 1.1F)),
		Map.entry("jaymoskitchen:tomatoes", new Liking(1.1F, 0.7F, 1.15F)),
		Map.entry("jaymoskitchen:budding_tomatoes", new Liking(1.1F, 0.7F, 1.15F)),
		Map.entry("jaymoskitchen:rice", new Liking(1.2F, 0.75F, 1.2F)),
		Map.entry("jaymoskitchen:rice_panicles", new Liking(1.2F, 0.75F, 1.2F)),
		Map.entry("jaymoskitchen:hemp", new Liking(0.9F, 0.9F, 1.1F)));

	/** Each chunk's warmth on the heat map, per world (it never changes); dropped when it grows past {@link #CACHE_MOST}. */
	private static final Map<ServerLevel, Long2FloatOpenHashMap> MAP = new WeakHashMap<>();
	private static final int CACHE_MOST = 65536;
	/** Set while the extra tick a plant was given runs, so it isn't weighed again. */
	private static boolean extra;

	private ClimateGrowth() {
	}

	/** {@code state}'s liking, or null when it's not a plant this weighs. */
	static Liking liking(BlockState state) {
		Liking named = LIKINGS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (named != null) {
			return named;
		}
		return state.is(SlowFarms.SLOW_GROWING) || SeasonalCrops.seasonal(state) ? ANY : null;
	}

	/**
	 * What this random tick of {@code state} comes to: -1 it's skipped, 1 it grows once more after this one, 0 it runs as it
	 * would.
	 */
	public static int growth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (extra || SlowFarms.hurried()) {
			return 0;
		}
		Liking liking = liking(state);
		if (liking == null) {
			return 0;
		}
		float pace = pace(liking, level, pos);
		if (pace < 1.0F) {
			return random.nextFloat() >= pace ? -1 : 0;
		}
		return random.nextFloat() < pace - 1.0F ? 1 : 0;
	}

	/** Runs the extra tick a plant in its best climate was given ({@link #growth} said 1). */
	public static void again(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		extra = true;
		try {
			BlockState now = level.getBlockState(pos);
			if (now.is(state.getBlock())) {
				now.randomTick(level, pos, random);
			}
		} finally {
			extra = false;
		}
	}

	/** The plant's pace at {@code pos}: {@link #LEAST} far from its climate, up to its {@link Liking#most} in it. */
	static float pace(Liking liking, ServerLevel level, BlockPos pos) {
		float warmth = warmth(level, pos);
		float tolerance = liking.tolerance();
		BlockState soil = level.getBlockState(pos.below());
		if (soil.getBlock() instanceof FertileFarmlandBlock || soil.getBlock() instanceof RichSoilFarmlandBlock) {
			tolerance *= FERTILE;
		} else if (soil.getBlock() instanceof FarmlandBlock && soil.getValue(FarmlandBlock.MOISTURE) >= FarmlandBlock.MAX_MOISTURE) {
			tolerance *= WATERED;
		}
		warmth = Mth.lerp(shelter(level, pos), warmth, liking.best());
		float off = (warmth - liking.best()) / tolerance;
		float near = (float) Math.exp(-0.5 * off * off);
		return LEAST + (liking.most() - LEAST) * near;
	}

	/** How much of the way to its liked climate the plant at {@code pos} is brought by the roof over it or the room it's in. */
	private static float shelter(ServerLevel level, BlockPos pos) {
		if (Warmth.roomAt(level, pos, -1.0F) > -1.0F || Warmth.roomAt(level, pos, 1.0F) < 1.0F) {
			return ROOM;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		BlockState plant = level.getBlockState(pos);
		for (int i = 0; i < 24; i++) {
			at.move(0, 1, 0);
			if (level.isOutsideBuildHeight(at)) {
				return 0.0F;
			}
			BlockState above = level.getBlockState(at);
			if (above.isAir() || above.is(plant.getBlock())) {
				continue;
			}
			return above.is(SeasonalCrops.GREENHOUSE_ROOF) ? GLASS : above.canOcclude() ? ROOF : 0.0F;
		}
		return 0.0F;
	}

	/** How warm the land is at {@code pos}, on the biomes' scale (0.8 the plains, 2 the desert, below 0.15 it snows). */
	static float warmth(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		float base = biome.value().getBaseTemperature();
		float warmth = base;
		Float feel = Warmth.feel(biome);
		if (feel != null) {
			for (int i = 0; i < Warmth.FEEL_PUSH.length; i++) {
				if (Warmth.FEEL_PUSH[i] == feel) {
					warmth = FEEL_WARMTH[i];
				}
			}
		} else if (Warmth.CLIMATE_MAP && Stacked.realm(level, pos.getY()) == Level.OVERWORLD && level.dimension() == Level.OVERWORLD) {
			warmth = mapped(level, pos);
		}
		warmth += Seasons.chill(base);
		if (pos.getY() > HEIGHT_FROM) {
			warmth -= (pos.getY() - HEIGHT_FROM) * HEIGHT_COLD;
		}
		return warmth;
	}

	/** The heat map's warmth for {@code pos}'s chunk (cached). */
	private static float mapped(ServerLevel level, BlockPos pos) {
		Long2FloatOpenHashMap known = MAP.computeIfAbsent(level, l -> new Long2FloatOpenHashMap());
		long key = ChunkPos.pack(pos);
		if (known.containsKey(key)) {
			return known.get(key);
		}
		if (known.size() >= CACHE_MOST) {
			known.clear();
		}
		Climate.Sampler sampler = level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		ChunkPos chunk = ChunkPos.containing(pos);
		float t = Climate.unquantizeCoord(sampler.sample(chunk.getMiddleBlockX() >> 2, 0, chunk.getMiddleBlockZ() >> 2).temperature());
		float warmth = MAP_WARMTH[MAP_WARMTH.length - 1];
		for (int i = 1; i < MAP_AT.length; i++) {
			if (t <= MAP_AT[i]) {
				float f = (t - MAP_AT[i - 1]) / (MAP_AT[i] - MAP_AT[i - 1]);
				warmth = Mth.lerp(Mth.clamp(f, 0.0F, 1.0F), MAP_WARMTH[i - 1], MAP_WARMTH[i]);
				break;
			}
		}
		known.put(key, warmth);
		return warmth;
	}
}
