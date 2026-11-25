package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Placing Root Spikes on the ground: lines, rings and single show-spikes. */
public final class RootSpikes {

	private RootSpikes() {
	}

	/** The top of the first sturdy floor at x, z searching from maxY down to minY (the evoker's fang search), or NaN. */
	public static double floor(ServerLevel level, double x, double z, double minY, double maxY) {
		BlockPos pos = BlockPos.containing(x, maxY, z);
		do {
			BlockPos below = pos.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				double top = 0.0;
				if (!level.isEmptyBlock(pos)) {
					BlockState state = level.getBlockState(pos);
					VoxelShape shape = state.getCollisionShape(level, pos);
					if (!shape.isEmpty()) {
						top = shape.max(Direction.Axis.Y);
					}
				}
				return pos.getY() + top;
			}
			pos = below;
		} while (pos.getY() >= Mth.floor(minY) - 1);
		return Double.NaN;
	}

	public static boolean spawn(ServerLevel level, @Nullable LivingEntity owner, double x, double z, double minY, double maxY,
								int warmup, float damage, int rootedTicks, boolean harmless) {
		double y = floor(level, x, z, minY, maxY);
		if (Double.isNaN(y)) {
			return false;
		}
		float yRot = level.getRandom().nextFloat() * 360.0F;
		return level.addFreshEntity(new RootSpikeEntity(level, x, y, z, yRot, warmup, owner, damage, rootedTicks, harmless));
	}

	/** A wave of spikes running from (x, z) along yawRad, one tick apart, from start blocks out at spacing blocks. */
	public static void line(ServerLevel level, LivingEntity owner, double x, double z, double y, float yawRad, int count,
							double start, double spacing, float damage, int rootedTicks) {
		double dx = -Mth.sin(yawRad);
		double dz = Mth.cos(yawRad);
		for (int i = 0; i < count; i++) {
			double d = start + i * spacing;
			spawn(level, owner, x + dx * d, z + dz * d, y - 5, y + 3, 4 + i, damage, rootedTicks, false);
		}
	}

	public static void ring(ServerLevel level, @Nullable LivingEntity owner, double x, double z, double y, double radius,
							int count, int warmup, float damage, int rootedTicks, boolean harmless) {
		for (int i = 0; i < count; i++) {
			double a = (Math.PI * 2 * i) / count + level.getRandom().nextDouble() * 0.2;
			spawn(level, owner, x + Math.cos(a) * radius, z + Math.sin(a) * radius, y - 3, y + 3,
					warmup + level.getRandom().nextInt(4), damage, rootedTicks, harmless);
		}
	}
}
