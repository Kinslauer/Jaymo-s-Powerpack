package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * An ice spike: a tall cone of arctic packed ice (a few blocks of clear arctic ice mixed in) standing on the ground
 * block it is placed on, sunk two blocks into it. One in ten is a mega spike with a wider foot. Tall and thin: the height
 * is about {@link #SLOPE} blocks per block of foot radius.
 */
public record IceSpikeFeature() implements Feature {
	public static final MapCodec<IceSpikeFeature> CODEC = MapCodec.unit(IceSpikeFeature::new);

	public static final double RADIUS = 4.25, MEGA_RADIUS = 6.25, SLOPE = 7.5;
	/** One spike in this many is a mega spike. */
	public static final int MEGA_ONE_IN = 10;
	/** How far down into the ground the foot reaches. */
	public static final int SINK = 2;
	/** One block in this many is clear arctic ice rather than packed. */
	public static final int CLEAR_ONE_IN = 7;

	@Override
	public MapCodec<IceSpikeFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		BlockState ground = level.getBlockState(origin);
		if (ground.isAir() || !ground.isFaceSturdy(level, origin, net.minecraft.core.Direction.UP)) {
			return false;
		}
		double radius = random.nextInt(MEGA_ONE_IN) == 0 ? MEGA_RADIUS : RADIUS;
		radius *= 0.75 + 0.25 * random.nextDouble();
		double height = radius * SLOPE * (0.6 + 0.4 * random.nextDouble());
		if (origin.getY() + height >= level.getMaxY() - 1) {
			return false;
		}
		BlockState packed = AetherBlocks.ARCTIC_PACKED_ICE.defaultBlockState();
		BlockState clear = AetherBlocks.ARCTIC_ICE.defaultBlockState();
		int reach = (int)Math.ceil(radius);
		boolean placed = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = -SINK; dy <= height; dy++) {
			double t = Math.max(0.0, dy) / height;
			double r = radius * Math.pow(1.0 - t, 1.2);
			if (dy < 0) {
				r = radius * (1.0 + 0.15 * dy);
			}
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz > r * r) {
						continue;
					}
					at.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState was = level.getBlockState(at);
					if (was.isAir() || was.canBeReplaced() || was.is(AetherBlocks.ARCTIC_SNOW_BLOCK) || was.is(AetherBlocks.AETHER_GRASS_BLOCK)
						|| was.is(AetherBlocks.AETHER_DIRT) || was.is(AetherBlocks.HOLYSTONE)) {
						placed |= AetherFeatures.put(level, at, random.nextInt(CLEAR_ONE_IN) == 0 ? clear : packed);
					}
				}
			}
		}
		return placed;
	}
}
