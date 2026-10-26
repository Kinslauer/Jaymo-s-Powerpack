package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Essence jars as a store other parts of alchemy draw on (the infusion altar pays its essences out of the jars around
 * it). Every call looks at the jars within {@code radius} blocks of a centre (a sphere), in loaded chunks only.
 */
public final class EssenceJars {
	private EssenceJars() {
	}

	/** How much of {@code essence} the jars near {@code centre} hold together. */
	public static int stored(ServerLevel level, BlockPos centre, int radius, Essence essence) {
		int sum = 0;
		for (JarEntity jar : near(level, centre, radius)) {
			if (jar.essence() == essence) {
				sum += jar.amount();
			}
		}
		return sum;
	}

	/** Everything the jars near {@code centre} hold together. */
	public static EssenceList stored(ServerLevel level, BlockPos centre, int radius) {
		EssenceList sum = EssenceList.EMPTY;
		for (JarEntity jar : near(level, centre, radius)) {
			if (jar.essence() != null) {
				sum = sum.plus(jar.essence(), jar.amount());
			}
		}
		return sum;
	}

	/**
	 * Takes up to {@code amount} of {@code essence} out of the jars near {@code centre}, nearest first; returns how much
	 * was taken (less than asked when they hold less).
	 */
	public static int drain(ServerLevel level, BlockPos centre, int radius, Essence essence, int amount) {
		java.util.List<JarEntity> jars = near(level, centre, radius);
		jars.sort(java.util.Comparator.comparingDouble(jar -> jar.getBlockPos().distSqr(centre)));
		int taken = 0;
		for (JarEntity jar : jars) {
			if (taken >= amount) {
				break;
			}
			if (jar.essence() == essence) {
				taken += jar.take(amount - taken);
			}
		}
		return taken;
	}

	private static java.util.List<JarEntity> near(ServerLevel level, BlockPos centre, int radius) {
		java.util.List<JarEntity> jars = new java.util.ArrayList<>();
		long limit = (long) radius * radius;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
			if (pos.distSqr(centre) > limit || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(Crucibles.ESSENCE_JAR)) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof JarEntity jar) {
				jars.add(jar);
			}
		}
		return jars;
	}
}
