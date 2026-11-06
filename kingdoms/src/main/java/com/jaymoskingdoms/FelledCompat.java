package com.jaymoskingdoms;

import java.lang.reflect.Method;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.628) Andy: "https://www.curseforge.com/minecraft/mc-mods/felled ... it captures what i wanted for our trees". Felled
 * (Raiiiden, all rights reserved, shipped unchanged from CurseForge with its Zarith physics library) brings a tree down as
 * a real rigid body: it tips over its stump, rolls, snaps on a hard landing, floats, and can be stood on. When it's in the
 * pack, a player's chopping still goes through ours (the cut narrows, the chop meter, a woodcutter's credit), but the
 * stroke that would fell the tree hands it to Felled's {@code FellingHandler.fellFrom} instead of our {@link FallingTree}.
 * Everything else that brings trees down (blasts, giants, tornadoes, gatherers, Grimsby's axe) stays ours. Called by
 * reflection only, so Kingdoms runs the same without it.
 */
public final class FelledCompat {
	private static boolean looked;
	private static @Nullable Method fellFrom;

	private FelledCompat() {
	}

	/** Whether Felled is loaded (and its felling found). */
	public static boolean loaded() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("felled")) {
				try {
					fellFrom = Class.forName("com.raiiiden.felled.event.FellingHandler")
						.getMethod("fellFrom", ServerLevel.class, BlockPos.class, net.minecraft.world.entity.player.Player.class, Vec3.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Felled is loaded but its felling wasn't found; trees fall our way", e);
				}
			}
		}
		return fellFrom != null;
	}

	/**
	 * {@code player}'s stroke at {@code cut} fells the tree (its {@code logs} over the cut): Felled takes it, as it would a
	 * log broken whole. The chopped logs are made whole again first (Felled only knows logs), and the cut itself is broken
	 * as the player's. How many logs came down, or 0 if Felled didn't take it (too big for it, no physics): then the tree
	 * is left whole, for ours to fell.
	 */
	static int fell(ServerLevel level, BlockPos cut, List<BlockPos> logs, ServerPlayer player) {
		if (!loaded()) {
			return 0;
		}
		mend(level, cut);
		for (BlockPos p : logs) {
			mend(level, p);
		}
		int felled;
		try {
			felled = (int)fellFrom.invoke(null, level, cut, player, player.getEyePosition());
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Felled couldn't fell the tree at {}; it falls our way", cut, e);
			return 0;
		}
		if (felled > 0 && level.getBlockState(cut).is(BlockTags.LOGS)) {
			level.destroyBlock(cut, true, player);
		}
		return felled;
	}

	/**
	 * (0.7.628) Andy: "so if a player cuts a tree down and a giant tried to grab it it wouldnt work?" The nearest tree Felled
	 * brought down within {@code reach} of {@code at}, of no more than {@code mostLogs} logs, for a giant's grab; null if none.
	 */
	static @Nullable Entity near(ServerLevel level, Vec3 at, double reach, int mostLogs) {
		if (!loaded() || !groups()) {
			return null;
		}
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		// (its own box may be only its middle: Zarith's solid bounds are the whole tree)
		for (Entity e : level.getEntities((Entity)null, new AABB(at, at).inflate(reach + REACH_MORE), e -> e.isAlive() && treeClass.isInstance(e))) {
			double d = bounds(e).distanceToSqr(at);
			if (d <= reach * reach && d < bestD && logs(e) > 0 && logs(e) <= mostLogs) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * (0.7.628) Felled's {@code tree} made one of ours ({@link FallingTree#adopt}), so a giant can hold and throw it: its blocks
	 * as they stood (Felled keeps them in the tree's own upright grid), turned the way its trunk lay. Felled's is gone.
	 */
	static @Nullable FallingTree adopt(ServerLevel level, Entity tree) {
		try {
			Object grid = mGrid.invoke(tree);
			IntList ids = (IntList)mLiveIds.invoke(grid);
			List<BlockState> states = new ArrayList<>();
			List<int[]> at = new ArrayList<>();
			int footY = Integer.MAX_VALUE, footX = 0, footZ = 0, top = Integer.MIN_VALUE;
			for (int i = 0; i < ids.size(); i++) {
				int id = ids.getInt(i);
				BlockState state = (BlockState)mState.invoke(grid, id);
				int[] xyz = {(int)mX.invoke(grid, id), (int)mY.invoke(grid, id), (int)mZ.invoke(grid, id)};
				if (state == null || state.isAir()) {
					continue;
				}
				states.add(state);
				at.add(xyz);
				if (state.is(BlockTags.LOGS)) {
					if (xyz[1] < footY) {
						footY = xyz[1];
						footX = xyz[0];
						footZ = xyz[2];
					}
					top = Math.max(top, xyz[1]);
				}
			}
			if (footY == Integer.MAX_VALUE) {
				return null;
			}
			int middle = footY + (top - footY + 1) / 2;
			List<FallingTree.Piece> pieces = new ArrayList<>();
			for (int i = 0; i < states.size(); i++) {
				int[] xyz = at.get(i);
				pieces.add(new FallingTree.Piece(Block.getId(states.get(i)), xyz[0] - footX, xyz[1] - middle, xyz[2] - footZ));
			}
			Vec3 lay = (Vec3)mTrunk.invoke(tree);
			float heading = lay.horizontalDistanceSqr() < 1.0E-4 ? 0.0F : (float)Math.atan2(lay.z, lay.x);
			Vec3 from = bounds(tree).getCenter();
			FallingTree ours = FallingTree.adopt(level, from, pieces, heading);
			if (ours != null) {
				tree.discard();
			}
			return ours;
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't take Felled's tree for a giant", e);
			return null;
		}
	}

	/** How much further than a giant's reach to look for a Felled tree's middle. */
	private static final double REACH_MORE = 16.0;

	private static AABB bounds(Entity tree) {
		try {
			return (AABB)mBounds.invoke(tree);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return tree.getBoundingBox();
		}
	}

	/** How many logs Felled's {@code tree} still has. */
	private static int logs(Entity tree) {
		try {
			Object grid = mGrid.invoke(tree);
			IntList ids = (IntList)mLiveIds.invoke(grid);
			int n = 0;
			for (int i = 0; i < ids.size(); i++) {
				if (((BlockState)mState.invoke(grid, ids.getInt(i))).is(BlockTags.LOGS)) {
					n++;
				}
			}
			return n;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	private static boolean lookedGroups;
	private static @Nullable Class<?> treeClass;
	private static @Nullable Method mGrid, mLiveIds, mState, mX, mY, mZ, mTrunk, mBounds;

	/** Felled's tree and Zarith's block grid, for giants. */
	private static boolean groups() {
		if (!lookedGroups) {
			lookedGroups = true;
			try {
				treeClass = Class.forName("com.raiiiden.felled.entity.FallingTreeEntity");
				Class<?> grid = Class.forName("com.raiiiden.zarith.group.BlockGrid");
				mGrid = treeClass.getMethod("grid");
				mTrunk = treeClass.getMethod("trunkDirection");
				mBounds = treeClass.getMethod("solidBounds");
				mLiveIds = grid.getMethod("liveIds");
				mState = grid.getMethod("state", int.class);
				mX = grid.getMethod("x", int.class);
				mY = grid.getMethod("y", int.class);
				mZ = grid.getMethod("z", int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				treeClass = null;
				JaymosKingdoms.LOGGER.warn("Felled's trees weren't found; giants can't take them", e);
			}
		}
		return treeClass != null;
	}

	/** A chopped log back to the whole log it was. */
	private static void mend(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ChoppedLog.BLOCK)) {
			level.setBlock(pos, ChoppedLog.real(level, pos, state), Block.UPDATE_ALL);
		}
	}
}
