package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Ferrosite rock formations of the magnetic biomes. {@code kind} "spike" is a short, narrow cone; "pillar" is a thick
 * column with a bulging waist that narrows to a rounded top. Both stand on the ground block they are placed on, sunk
 * a little into it.
 */
public record FerrositeSpireFeature(String kind) implements Feature {
	public static final MapCodec<FerrositeSpireFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.optionalFieldOf("kind", "spike").forGetter(FerrositeSpireFeature::kind)).apply(i, FerrositeSpireFeature::new));

	/** Spikes: radius and height ranges. */
	public static final int SPIKE_RADIUS_MIN = 2, SPIKE_RADIUS_MAX = 3, SPIKE_HEIGHT_MIN = 6, SPIKE_HEIGHT_MAX = 14;
	/** Pillars: radius and height ranges, and how much wider the waist is than the foot. */
	public static final int PILLAR_RADIUS_MIN = 2, PILLAR_RADIUS_MAX = 4, PILLAR_HEIGHT_MIN = 10, PILLAR_HEIGHT_MAX = 28;
	public static final double PILLAR_BULGE = 0.35;
	public static final int SINK = 2;
	/** One block in this many is ichorite rock mixed into the ferrosite. */
	public static final int ICHORITE_ONE_IN = 14;

	@Override
	public MapCodec<FerrositeSpireFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		BlockState ground = level.getBlockState(origin);
		if (ground.isAir() || !ground.isFaceSturdy(level, origin, Direction.UP)) {
			return false;
		}
		boolean pillar = "pillar".equals(this.kind);
		int radius = pillar ? PILLAR_RADIUS_MIN + random.nextInt(PILLAR_RADIUS_MAX - PILLAR_RADIUS_MIN + 1)
			: SPIKE_RADIUS_MIN + random.nextInt(SPIKE_RADIUS_MAX - SPIKE_RADIUS_MIN + 1);
		int height = pillar ? PILLAR_HEIGHT_MIN + random.nextInt(PILLAR_HEIGHT_MAX - PILLAR_HEIGHT_MIN + 1)
			: SPIKE_HEIGHT_MIN + random.nextInt(SPIKE_HEIGHT_MAX - SPIKE_HEIGHT_MIN + 1);
		if (origin.getY() + height >= level.getMaxY() - 1) {
			return false;
		}
		BlockState rock = AetherBlocks.FERROSITE.defaultBlockState();
		BlockState brown = AetherBlocks.ICHORITE.defaultBlockState();
		int reach = (int)Math.ceil(radius * (1.0 + PILLAR_BULGE));
		boolean placed = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = -SINK; dy <= height; dy++) {
			double t = Math.max(0.0, dy) / (double)height;
			double r;
			if (pillar) {
				// a waist a third of the way up, then a rounded top
				double waist = 1.0 + PILLAR_BULGE * Math.sin(Math.min(1.0, t * 2.2) * Math.PI);
				double cap = t > 0.8 ? Math.sqrt(Math.max(0.0, 1.0 - (t - 0.8) / 0.2)) : 1.0;
				r = radius * waist * cap;
			} else {
				r = radius * (1.0 - t);
			}
			if (dy < 0) {
				r = radius;
			}
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz > r * r) {
						continue;
					}
					at.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState was = level.getBlockState(at);
					if (was.isAir() || was.canBeReplaced() || was.is(AetherBlocks.AETHER_GRASS_BLOCK) || was.is(AetherBlocks.AETHER_DIRT)
						|| was.is(AetherBlocks.HOLYSTONE) || was.is(AetherBlocks.FERROSITE)) {
						placed |= AetherFeatures.put(level, at, random.nextInt(ICHORITE_ONE_IN) == 0 ? brown : rock);
					}
				}
			}
		}
		return placed;
	}
}
