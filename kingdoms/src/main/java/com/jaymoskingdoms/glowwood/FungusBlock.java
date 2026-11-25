package com.jaymoskingdoms.glowwood;

import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small placeable mushroom (glowcap, bouncecap, inkcap) with a real 3D model, ported from Spore & Stem (Apache-2.0).
 * It survives in any light on fungus soil (dirt-like blocks, moss, mycelium) and on any solid block in the dark. Bone
 * meal grows it into its giant fungus (when there is room); otherwise it spreads a copy. It only spreads by itself in
 * the dark or on mycelium.
 */
public class FungusBlock extends MushroomBlock {
	/** Bone meal's chance to grow the giant one. */
	private static final float GROW_CHANCE = 0.4F;
	/** Light at or above which it does not spread or survive on plain ground. */
	private static final int TOO_BRIGHT = 13;
	/** How far (blocks) and how many tries a spreading copy looks. */
	private static final int SPREAD_REACH = 3, SPREAD_TRIES = 12;

	private final ResourceKey<Feature> hugeFeature;
	private final VoxelShape shape;

	public FungusBlock(ResourceKey<Feature> hugeFeature, VoxelShape shape, Properties properties) {
		super(hugeFeature, properties);
		this.hugeFeature = hugeFeature;
		this.shape = shape;
	}

	public ResourceKey<Feature> hugeFeature() {
		return this.hugeFeature;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Vec3 offset = state.getOffset(pos);
		return this.shape.move(offset.x, offset.y, offset.z);
	}

	public static boolean isFungusSoil(BlockState below) {
		return below.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || below.is(GlowWoods.FUNGUS_SOIL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		if (isFungusSoil(below)) {
			return true;
		}
		return level.getRawBrightness(pos, 0) < TOO_BRIGHT && this.mayPlaceOn(below, level, belowPos);
	}

	/** Feedback: it only spreads on shaded ground (dark, or mycelium). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		if (below.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || level.getRawBrightness(pos, 0) < TOO_BRIGHT) {
			super.randomTick(state, level, pos, random);
		}
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return random.nextFloat() < GROW_CHANCE;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		this.growMushroom(level, pos, state, random);
	}

	/** Places a copy on a free, survivable spot within a few blocks. */
	public static boolean spread(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		for (int i = 0; i < SPREAD_TRIES; i++) {
			BlockPos target = pos.offset(random.nextInt(SPREAD_REACH * 2 + 1) - SPREAD_REACH, random.nextInt(3) - 1, random.nextInt(SPREAD_REACH * 2 + 1) - SPREAD_REACH);
			if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
				level.setBlock(target, state, 3);
				return true;
			}
		}
		return false;
	}
}
