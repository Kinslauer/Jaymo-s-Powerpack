package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (0.14.6) Geometry shared by the newer freerun moves (the vertical wall run, wall transitions and the pole climb), both
 * sides. Kept out of {@link Freerun} so that stays readable.
 */
public final class FreerunWalls {
	/** Pole climb: what can be grabbed and climbed (vertical runs of these). */
	public static final TagKey<Block> POLES = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_poles"));

	private FreerunWalls() {
	}

	/** Like {@code WebWallRun.wallAt}, for any box (a spot the player is about to be at): solid both knee-high and chest-high on side {@code face}. */
	public static boolean wallAtBox(Level level, Player player, AABB box, Direction face, double reach) {
		double dx = face.getStepX() * reach, dz = face.getStepZ() * reach;
		AABB low = new AABB(box.minX, box.minY + 0.3, box.minZ, box.maxX, box.minY + 0.9, box.maxZ).move(dx, 0.0, dz);
		AABB high = new AABB(box.minX, box.minY + 1.0, box.minZ, box.maxX, box.maxY - 0.15, box.maxZ).move(dx, 0.0, dz);
		return !level.noCollision(player, low) && !level.noCollision(player, high);
	}

	/** Whether a block is one nobody climbs or runs up (ice, glass, leaves and {@link Freerun#NO_CLIMB}). */
	public static boolean slippery(BlockState state) {
		return state.is(Freerun.NO_CLIMB) || state.is(BlockTags.ICE) || state.is(BlockTags.LEAVES) || state.getSoundType() == SoundType.GLASS;
	}

	/** A wall you can run up: there on side {@code face} within {@code reach}, tall enough, and nothing slippery in it. */
	public static boolean runnable(Level level, Player player, Direction face, double reach) {
		if (!com.jaymospowers.power.web.WebWallRun.wallAt(level, player, face, reach)) {
			return false;
		}
		for (double up : new double[] {0.5, 1.2}) {
			BlockPos pos = BlockPos.containing(player.getX() + face.getStepX() * (player.getBbWidth() * 0.5 + reach),
				player.getY() + up, player.getZ() + face.getStepZ() * (player.getBbWidth() * 0.5 + reach));
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() && slippery(state)) {
				return false;
			}
		}
		return true;
	}

	/** The reaches (blocks) a wall is looked for at when it may still be a step or two off (a run-up to a wall). */
	private static final double[] NEAR = {0.35, 0.8, 1.2, 1.6};

	/** A wall within a couple of blocks ahead (any of {@link #NEAR}), for the run-up to a vertical wall run. */
	public static boolean wallNear(Level level, Player player, Direction face) {
		for (double reach : NEAR) {
			if (com.jaymospowers.power.web.WebWallRun.wallAt(level, player, face, reach)) {
				return true;
			}
		}
		return false;
	}

	/** {@link #runnable} at any of {@link #NEAR}. */
	public static boolean runnableNear(Level level, Player player, Direction face) {
		for (double reach : NEAR) {
			if (runnable(level, player, face, reach)) {
				return true;
			}
		}
		return false;
	}

	/** Pole climb: a vertical run of poles on side {@code face} (knee-high and chest-high both), touching the body within {@code reach}. */
	public static boolean poleAt(Level level, Player player, Direction face, double reach) {
		AABB box = player.getBoundingBox();
		double dx = face.getStepX() * reach, dz = face.getStepZ() * reach;
		if (level.noCollision(player, new AABB(box.minX, box.minY + 0.9, box.minZ, box.maxX, box.minY + 1.3, box.maxZ).move(dx, 0.0, dz))) {
			return false;
		}
		for (double up : new double[] {0.5, 1.2}) {
			if (!poleBlock(level, player, face, reach, up)) {
				return false;
			}
		}
		return true;
	}

	private static boolean poleBlock(Level level, Player player, Direction face, double reach, double up) {
		double half = player.getBbWidth() * 0.5;
		// (a thin post can sit in the cell the player's middle is in)
		for (double d : new double[] {0.0, half + reach * 0.5, half + reach}) {
			BlockPos pos = BlockPos.containing(player.getX() + face.getStepX() * d, player.getY() + up, player.getZ() + face.getStepZ() * d);
			BlockState state = level.getBlockState(pos);
			if (state.is(POLES) && !state.getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
