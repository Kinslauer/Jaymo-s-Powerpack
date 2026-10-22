package com.jaymospowers.aether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A drifting aercloud: a random walk of {@code bounds} steps, each laying a small flattened blob of cloud blocks into
 * open air. {@code cloud} is "cold", "blue", "golden", "green", "purple" or "storm"; {@code spread} is the blob's widest
 * horizontal radius and {@code thickness} its vertical one. The data makes every colour by height band (the surface,
 * lower, middle and high bands of the dimension, docs/aether.md). A purple cloud's blocks each face a random way.
 *
 * <p>Clouds only take air, so one set among islands hugs them; and they only go where this generation step may write
 * (a walk that wanders too far from its chunk stops there).
 */
public record AercloudFeature(String cloud, int bounds, int spread, int thickness) implements Feature {
	public static final MapCodec<AercloudFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.optionalFieldOf("cloud", "cold").forGetter(AercloudFeature::cloud),
			Codec.INT.optionalFieldOf("bounds", 16).forGetter(AercloudFeature::bounds),
			Codec.INT.optionalFieldOf("spread", 2).forGetter(AercloudFeature::spread),
			Codec.INT.optionalFieldOf("thickness", 1).forGetter(AercloudFeature::thickness))
		.apply(i, AercloudFeature::new));

	/** Each step moves the blob this far sideways at most, and now and then (one step in this many) a block up or down. */
	private static final int STEP = 3;
	private static final int DRIFT_ONE_IN = 4;

	@Override
	public MapCodec<AercloudFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		if (!AetherFeatures.isAir(level, origin)) {
			return false;
		}
		Block block = switch (this.cloud) {
			case "blue" -> AetherBlocks.BLUE_AERCLOUD;
			case "golden" -> AetherBlocks.GOLDEN_AERCLOUD;
			case "green" -> AetherBlocks.GREEN_AERCLOUD;
			case "purple" -> AetherBlocks.PURPLE_AERCLOUD;
			case "storm" -> AetherBlocks.STORM_AERCLOUD;
			default -> AetherBlocks.COLD_AERCLOUD;
		};
		boolean purple = block == AetherBlocks.PURPLE_AERCLOUD;
		BlockState state = block.defaultBlockState();
		int x = origin.getX();
		int y = origin.getY();
		int z = origin.getZ();
		boolean placed = false;
		for (int step = 0; step < this.bounds; step++) {
			// the walk ends once its centre leaves the region this step may write
			if (!AetherFeatures.canWrite(level, new BlockPos(x, y, z))) {
				break;
			}
			int rx = 1 + random.nextInt(this.spread);
			int rz = 1 + random.nextInt(this.spread);
			// an ellipsoid of cloud, thin in y
			for (int dx = -rx; dx <= rx; dx++) {
				for (int dy = -this.thickness; dy <= this.thickness; dy++) {
					for (int dz = -rz; dz <= rz; dz++) {
						double d = (double)(dx * dx) / (rx * rx + 1) + (double)(dy * dy) / (this.thickness * this.thickness + 1) + (double)(dz * dz) / (rz * rz + 1);
						BlockPos at = new BlockPos(x + dx, y + dy, z + dz);
						if (d <= 1.0 && AetherFeatures.isAir(level, at)) {
							BlockState put = purple ? state.setValue(PurpleAercloudBlock.FACING, Direction.getRandom(random)) : state;
							placed |= AetherFeatures.put(level, at, put);
						}
					}
				}
			}
			x += random.nextInt(2 * STEP + 1) - STEP;
			z += random.nextInt(2 * STEP + 1) - STEP;
			if (random.nextInt(DRIFT_ONE_IN) == 0) {
				y += random.nextInt(3) - 1;
			}
		}
		return placed;
	}
}
