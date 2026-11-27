package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.noise.FastNoiseLite;
import com.jaymospowers.noise.Noises;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Pale Moon's craters (Andy's yes to FastNoiseLite in the worldgen): bowls pressed into the grey dust, each with a
 * raised lip, in two sizes, big ones about every hundred blocks and small ones peppered between and inside them.
 * <p>
 * Each size is FastNoiseLite cellular noise: every cell of the field has one point, the crater's middle, and the noise
 * says how far a column is from its nearest point and which point that is (its cell value), so every crater is the same
 * crater from whichever chunk reads it. A cell's value also decides whether it has a crater at all and how big it is;
 * where two craters overlap, the nearer middle wins, so a younger crater cuts into an older one's wall. The rim's
 * distance is wobbled by a little OpenSimplex2 so no lip is a perfect circle.
 * <p>
 * It runs first in the moon's decoration ({@code raw_generation}, worldgen/placed_feature/moon_craters.json), after the
 * surface is laid and before ores and anything built, and each chunk writes only its own 16 by 16 columns, so it never
 * reaches past what is being generated. A crater's floor keeps the moon's dust (the old top block is laid on the new
 * floor) and its rim is heaped from the same dust. A moon already explored keeps its ground: only new chunks get craters.
 */
public record MoonCraterFeature() implements Feature {
	public static final MapCodec<MoonCraterFeature> CODEC = MapCodec.unit(MoonCraterFeature::new);

	/** One size of crater: blocks per cell, the share of cells that hold one, the radius range (a share of a cell) and the deepest bowl (blocks). */
	record Size(int cell, double share, double minRadius, double maxRadius, double depth, int layer) {
	}

	static final Size BIG = new Size(112, 0.45, 0.16, 0.36, 13.0, 0);
	static final Size SMALL = new Size(26, 0.55, 0.14, 0.34, 4.0, 1);
	/** The rim stands this share of the bowl's depth high, peaks this far past the edge and fades by {@link #RIM_OUT} (both in radii). */
	static final double RIM_HEIGHT = 0.28, RIM_PEAK = 1.12, RIM_OUT = 1.45;
	/** How much the rim's distance wobbles (a share of the radius), and the wobble's blocks per wave. */
	static final double WOBBLE = 0.09, WOBBLE_WAVE = 9.0;
	/** A crater never digs closer than this to the bottom of the world. */
	static final int FLOOR_MARGIN = 8;

	private static final Noises.Seeded DISTANCE = new Noises.Seeded(0xC4A7E4L, noise -> cellular(noise, FastNoiseLite.CellularReturnType.Distance));
	private static final Noises.Seeded WHICH = new Noises.Seeded(0xC4A7E4L, noise -> cellular(noise, FastNoiseLite.CellularReturnType.CellValue));
	private static final Noises.Seeded WOBBLY = new Noises.Seeded(0x0BB1EL, noise -> {
		noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
		noise.SetFrequency((float)(1.0 / WOBBLE_WAVE));
	});

	/** Cells of size one (the caller divides by the cell), points kept near their cell's middle so craters seldom crowd. */
	private static void cellular(FastNoiseLite noise, FastNoiseLite.CellularReturnType type) {
		noise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
		noise.SetFrequency(1.0F);
		noise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
		noise.SetCellularReturnType(type);
		noise.SetCellularJitter(0.55F);
	}

	@Override
	public MapCodec<MoonCraterFeature> codec() {
		return CODEC;
	}

	/** How far the ground at block (x, z) moves: below zero a bowl dug that deep, above zero a rim heaped that high. */
	static double offset(long seed, double x, double z) {
		double wobble = 1.0 + WOBBLE * WOBBLY.at(seed, 0).GetNoise(x, z);
		return crater(seed, BIG, x, z, wobble) + crater(seed, SMALL, x, z, wobble);
	}

	private static double crater(long seed, Size size, double x, double z, double wobble) {
		double cx = x / size.cell, cz = z / size.cell;
		// the cell value is -1 to 1: the lowest share of cells have no crater, the rest grow with it
		double which = (WHICH.at(seed, size.layer).GetNoise(cx, cz) + 1.0) * 0.5;
		if (which < 1.0 - size.share) {
			return 0.0;
		}
		double grow = (which - (1.0 - size.share)) / size.share;
		double radius = Mth.lerp(grow, size.minRadius, size.maxRadius);
		double depth = size.depth * (0.55 + 0.45 * grow);
		// Distance returns the distance to the nearest point less one
		double t = (DISTANCE.at(seed, size.layer).GetNoise(cx, cz) + 1.0) / radius * wobble;
		if (t < 1.0) {
			return -depth * (1.0 - t * t);
		}
		if (t < RIM_OUT) {
			double away = (t - RIM_PEAK) / (t < RIM_PEAK ? RIM_PEAK - 1.0 : RIM_OUT - RIM_PEAK);
			return depth * RIM_HEIGHT * Math.max(0.0, 1.0 - away * away);
		}
		return 0.0;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
		long seed = level.getSeed();
		int bottom = level.getMinY() + FLOOR_MARGIN;
		boolean changed = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				int move = (int)Math.round(offset(seed, x + 0.5, z + 0.5));
				if (move == 0) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				if (top <= bottom) {
					continue;
				}
				BlockState dust = level.getBlockState(at.set(x, top, z));
				if (dust.isAir() || !dust.getFluidState().isEmpty()) {
					continue;
				}
				if (move < 0) {
					int floor = Math.max(bottom, top + move);
					for (int y = top; y > floor; y--) {
						level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					level.setBlock(at.set(x, floor, z), dust, Block.UPDATE_CLIENTS);
				} else {
					BlockState under = level.getBlockState(at.set(x, top - 1, z));
					if (!under.isAir()) {
						level.setBlock(at.set(x, top, z), under, Block.UPDATE_CLIENTS);
					}
					int peak = Math.min(level.getMaxY(), top + move);
					for (int y = top + 1; y <= peak; y++) {
						level.setBlock(at.set(x, y, z), y == peak ? dust : under.isAir() ? dust : under, Block.UPDATE_CLIENTS);
					}
				}
				changed = true;
			}
		}
		return changed;
	}
}
