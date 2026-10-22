package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherFeatures;
import com.jaymospowers.aether.AetherFlora;
import com.jaymospowers.aether.mob.AetherAnimals;
import com.jaymospowers.aether.mob.AetherMobs;
import com.jaymospowers.aether.mob.Moa;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A moa nest: a ring of woven skyroot sticks about {@link #RADIUS} blocks across on open, level-ish ground (it follows a
 * rise or fall of {@link #SLOPE} block), with a bed of sticks inside it and a moa egg in the middle of the bed (it hatches
 * a wild chick in about 12000 ticks). Now and then a grown wild moa stands guard with the nest as its home. Placed at the
 * air block above the ground; it needs solid ground under every block of the circle.
 */
public record MoaNestFeature() implements Feature {
	public static final MapCodec<MoaNestFeature> CODEC = MapCodec.unit(MoaNestFeature::new);

	/** The ring's outer radius in blocks; the bed fills the circle inside it. */
	public static final double RADIUS = 2.6, BED_RADIUS = 1.6;
	/** One ring block in this many is left out, so the nest looks woven rather than machined. */
	public static final int GAP_ONE_IN = 6;
	/** A nest has an egg in this share of cases, and a grown moa guarding it in this share. */
	public static final float EGG_CHANCE = 0.75F, GUARD_CHANCE = 0.35F;

	/** The nest follows ground that rises or falls this much across it. */
	public static final int SLOPE = 1;

	/** The first free height in the column from {@code y + SLOPE} down to {@code y - SLOPE} that has sturdy ground under it, or MIN_VALUE. */
	private static int floorAt(WorldGenLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int h = y + SLOPE; h >= y - SLOPE; h--) {
			at.set(x, h, z);
			BlockPos below = at.below();
			BlockState here = level.getBlockState(at);
			if (AetherFeatures.canWrite(level, at) && (here.isAir() || here.canBeReplaced()) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return h;
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	public MapCodec<MoaNestFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		if (AetherFlora.WOVEN_SKYROOT_STICKS == null) {
			return false;
		}
		BlockState sticks = AetherFlora.WOVEN_SKYROOT_STICKS.defaultBlockState();
		int reach = (int)Math.ceil(RADIUS);
		// each block of the circle follows the ground under it (within SLOPE blocks of the origin); a nest needs all of them
		int size = 2 * reach + 1;
		int[][] floor = new int[size][size];
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				int y = floorAt(level, origin.getX() + dx, origin.getY(), origin.getZ() + dz);
				if (y == Integer.MIN_VALUE) {
					return false;
				}
				floor[dx + reach][dz + reach] = y;
			}
		}
		boolean placed = false;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double d = dx * dx + dz * dz;
				if (d > RADIUS * RADIUS) {
					continue;
				}
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int y = floor[dx + reach][dz + reach];
				if (d <= BED_RADIUS * BED_RADIUS) {
					placed |= AetherFeatures.put(level, new BlockPos(x, y - 1, z), sticks);
				} else if (random.nextInt(GAP_ONE_IN) != 0) {
					placed |= AetherFeatures.put(level, new BlockPos(x, y, z), sticks);
				}
			}
		}
		if (placed) {
			this.egg(level, random, origin, floor[reach][reach]);
		}
		return placed;
	}

	/** The egg in the middle of the bed, and maybe its mother. */
	private void egg(WorldGenLevel level, RandomSource random, BlockPos origin, int y) {
		BlockPos centre = new BlockPos(origin.getX(), y, origin.getZ());
		BlockState below = level.getBlockState(centre.below());
		if (AetherAnimals.MOA_EGG != null && random.nextFloat() < EGG_CHANCE && below.is(AetherFlora.WOVEN_SKYROOT_STICKS) && level.getBlockState(centre).isAir()) {
			BlockState egg = AetherAnimals.MOA_EGG.stateFor(Moa.randomKind(random), false, below);
			if (AetherFeatures.put(level, centre, egg)) {
				AetherAnimals.MOA_EGG.startClock(level, level, centre, egg, random);
			}
		}
		if (AetherMobs.MOA != null && random.nextFloat() < GUARD_CHANCE) {
			Moa moa = AetherMobs.MOA.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
			if (moa != null) {
				moa.snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				moa.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), EntitySpawnReason.CHUNK_GENERATION, null);
				moa.setHome(centre);
				moa.setPersistenceRequired();
				level.addFreshEntity(moa);
			}
		}
	}
}
