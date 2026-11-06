package com.jaymoskingdoms;

import java.lang.reflect.Method;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;
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

	// ---- (0.7.637) knocking on ------------------------------------------------------------------------------------------

	/**
	 * (0.7.637) Andy: "the felled trees NEED to knock each other over still". Since 0.7.628 a player's tree comes down as
	 * Felled's rigid body, and Jolt only ever sees a standing tree as solid blocks, so it came to rest leaning on the next
	 * tree instead of knocking it over the way ours do ({@link FallingTree#knock}). Each tick, a Felled tree still moving
	 * (its top at least {@link #KNOCK_SPEED} blocks a tick, this tick or the last) looks just ahead of each of its logs
	 * along the way it's going ({@link #KNOCK_AHEAD}); an upright log of a natural tree there, no more than
	 * {@link FallingTree#KNOCK_SIZE} times its size, goes over that way ({@link Trees#knock}: as one of ours, which knocks
	 * on in turn). At most {@link FallingTree#KNOCKS_MOST} a tree; never the stump it was cut from.
	 */
	static final double KNOCK_SPEED = 0.06;
	static final double[] KNOCK_AHEAD = {0.0, 0.6, 1.1};
	/** How far (blocks, sideways) round where its trunk first stood counts as its own stump. */
	static final double STUMP = 1.6;

	/** Felled's trees in loaded levels. */
	private static final Set<Entity> TREES = Collections.newSetFromMap(new WeakHashMap<>());
	/** What each was doing last tick. */
	private static final Map<Entity, Motion> MOTION = new WeakHashMap<>();

	private static final class Motion {
		@Nullable Vec3 top, stump, way;
		double speed;
		int knocks;
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (loaded() && groups() && treeClass.isInstance(entity)) {
				TREES.add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			TREES.remove(entity);
			MOTION.remove(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(FelledCompat::knockOn);
	}

	private static void knockOn(MinecraftServer server) {
		if (TREES.isEmpty()) {
			return;
		}
		for (Entity tree : List.copyOf(TREES)) {
			if (!tree.isAlive() || !(tree.level() instanceof ServerLevel level)) {
				TREES.remove(tree);
				MOTION.remove(tree);
				continue;
			}
			try {
				knockOn(level, tree);
			} catch (ReflectiveOperationException | RuntimeException e) {
				JaymosKingdoms.LOGGER.warn("Couldn't knock on from Felled's tree", e);
				TREES.remove(tree);
			}
		}
	}

	/** {@code tree} (Felled's) knocks over a standing tree just ahead of its moving logs, if there is one. */
	private static void knockOn(ServerLevel level, Entity tree) throws ReflectiveOperationException {
		Motion m = MOTION.computeIfAbsent(tree, t -> new Motion());
		Vec3 top = (Vec3)mTop.invoke(tree);
		if (m.stump == null) {
			m.stump = (Vec3)mStart.invoke(tree);
		}
		double speed = m.top == null ? 0.0 : top.distanceTo(m.top);
		double was = m.speed;
		if (m.top != null && speed >= KNOCK_SPEED) {
			Vec3 flat = new Vec3(top.x - m.top.x, 0.0, top.z - m.top.z);
			if (flat.lengthSqr() > 1.0E-6) {
				m.way = flat.normalize();
			}
		}
		m.top = top;
		m.speed = speed;
		if (m.way == null || m.knocks >= FallingTree.KNOCKS_MOST || Math.max(speed, was) < KNOCK_SPEED) {
			return;
		}
		Object grid = mGrid.invoke(tree);
		IntList ids = (IntList)mLiveIds.invoke(grid);
		List<Vec3> logs = new ArrayList<>();
		Vector3d at = new Vector3d();
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.getInt(i);
			BlockState state = (BlockState)mState.invoke(grid, id);
			if (state != null && state.is(BlockTags.LOGS)) {
				mCellToWorld.invoke(tree, id, at);
				logs.add(new Vec3(at.x, at.y, at.z));
			}
		}
		if (logs.isEmpty()) {
			return;
		}
		float heading = (float)Math.atan2(m.way.z, m.way.x);
		heading = Math.round(heading / Mth.HALF_PI) * Mth.HALF_PI;
		int most = Math.max(1, Math.round(logs.size() * FallingTree.KNOCK_SIZE));
		for (Vec3 log : logs) {
			for (double ahead : KNOCK_AHEAD) {
				BlockPos pos = BlockPos.containing(log.x + m.way.x * ahead, log.y, log.z + m.way.z * ahead);
				double sx = pos.getX() + 0.5 - m.stump.x, sz = pos.getZ() + 0.5 - m.stump.z;
				if (sx * sx + sz * sz < STUMP * STUMP) {
					continue;
				}
				BlockState there = level.getBlockState(pos);
				if (!there.is(BlockTags.LOGS) || !there.hasProperty(BlockStateProperties.AXIS)
					|| there.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
					continue;
				}
				if (Trees.knock(level, pos, heading, null, most) > 0) {
					m.knocks++;
					level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.4F, 0.5F);
					level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
					return;
				}
			}
		}
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
	private static @Nullable Method mGrid, mLiveIds, mState, mX, mY, mZ, mTrunk, mBounds, mTop, mStart, mCellToWorld;

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
				mTop = treeClass.getMethod("trunkTop");
				mStart = treeClass.getMethod("trunkStart");
				mCellToWorld = treeClass.getMethod("cellToWorld", int.class, Vector3d.class);
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
