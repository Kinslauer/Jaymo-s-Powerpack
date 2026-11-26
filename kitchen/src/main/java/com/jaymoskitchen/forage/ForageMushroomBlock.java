package com.jaymoskitchen.forage;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.season.Season;
import com.jaymoskitchen.season.SeasonalCrops;
import com.jaymoskitchen.season.Seasons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A small wild mushroom with a 3D model (from Spore &amp; Stem, Apache-2.0, by NightBeam; see licenses/spore-and-stem;
 * the code here is our own). It stands on dirt-like ground, moss, mycelium and Mycelial Soil in any light, and on any
 * solid block in the dark.
 * <p>
 * It keeps the year ({@link SeasonalCrops}: the block tags {@code jaymoskitchen:seasons/*}): in its season it spreads
 * like any mushroom, and in autumn the flush spreads it much more; out of season it doesn't spread, and a winter in the
 * open rots it away. A mushroom in a cave, under a roof or on Mycelial Soil keeps no season, so a kept bed fruits all year.
 */
public class ForageMushroomBlock extends MushroomBlock {
	/** Extra spread tries an autumn random tick makes, each succeeding one time in {@link #FLUSH_ODDS}. */
	private static final int FLUSH_TRIES = 3;
	private static final int FLUSH_ODDS = 8;
	/** A winter random tick rots an out-of-season mushroom in the open one time in this. */
	private static final int WINTER_ROT_ODDS = 5;
	/** Bone meal succeeds with this chance. */
	private static final float BONEMEAL_CHANCE = 0.6F;
	/** Below this light a wild mushroom grows on any solid block. */
	private static final int DARK_LIGHT = 13;
	/** Where mushrooms stand in any light (dirt-like ground, moss, mycelium, Mycelial Soil). */
	public static final TagKey<Block> FUNGUS_SOIL = TagKey.create(Registries.BLOCK, JaymosKitchen.id("fungus_soil"));

	private final VoxelShape shape;
	/** In summer only the last part of it (the chanterelles). */
	private final boolean lateSummerOnly;

	public ForageMushroomBlock(VoxelShape shape, boolean lateSummerOnly, Properties properties) {
		// MushroomBlock wants a huge-mushroom feature; none of these grows into one (growMushroom is overridden)
		super(TreeFeatures.HUGE_BROWN_MUSHROOM, properties);
		this.shape = shape;
		this.lateSummerOnly = lateSummerOnly;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Vec3 offset = state.getOffset(pos);
		return shape.move(offset.x, offset.y, offset.z);
	}

	public static boolean isFungusSoil(BlockState below) {
		return below.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || below.is(FUNGUS_SOIL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		if (isFungusSoil(below)) {
			return true;
		}
		return level.getRawBrightness(pos, 0) < DARK_LIGHT && mayPlaceOn(below, level, belowPos);
	}

	/**
	 * The season this mushroom feels, or null where it feels none: lands with no seasons, caves and roofs, and
	 * Mycelial Soil (a kept bed).
	 */
	private static @Nullable Season feltSeason(ServerLevel level, BlockPos pos) {
		if (!Seasons.temperate(level.getBiome(pos).value().getBaseTemperature())
			|| level.getBlockState(pos.below()).is(Forage.MYCELIAL_SOIL) || SeasonalCrops.sheltered(level, pos)) {
			return null;
		}
		return Seasons.of(level);
	}

	/** Whether it fruits now: its season (the block tags), and for the chanterelle only late summer. */
	private boolean fruiting(BlockState state, ServerLevel level, Season season) {
		if (!SeasonalCrops.inSeason(state, season)) {
			return false;
		}
		return !(lateSummerOnly && season == Season.SUMMER && Seasons.part(Seasons.dayOfYear(level)) < Seasons.PARTS - 1);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Season season = feltSeason(level, pos);
		if (season != null && !fruiting(state, level, season)) {
			if (season == Season.WINTER && random.nextInt(WINTER_ROT_ODDS) == 0) {
				level.destroyBlock(pos, false);
			}
			return;
		}
		BlockState below = level.getBlockState(pos.below());
		if (!(below.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || level.getRawBrightness(pos, 0) < DARK_LIGHT)) {
			return;
		}
		super.randomTick(state, level, pos, random);
		if (season == Season.AUTUMN) {
			for (int i = 0; i < FLUSH_TRIES; i++) {
				if (random.nextInt(FLUSH_ODDS) == 0) {
					spread(level, pos, state, random);
				}
			}
		}
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return random.nextFloat() < BONEMEAL_CHANCE;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		spread(level, pos, state, random);
	}

	/** None of these grows into a huge mushroom. */
	@Override
	public boolean growMushroom(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		return false;
	}

	/** Puts a copy on a free spot within three blocks where it can stand; whether it did. */
	public static boolean spread(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			BlockPos target = pos.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
			if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
				level.setBlock(target, state, Block.UPDATE_ALL);
				return true;
			}
		}
		return false;
	}
}
