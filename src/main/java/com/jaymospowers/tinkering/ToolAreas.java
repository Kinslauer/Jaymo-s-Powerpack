package com.jaymospowers.tinkering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.15.x) Which blocks a tool with an area ({@link ToolType.Aoe}) takes besides the one it broke: the scythe's box, the
 * kama's ball, the broad axe's tree, the vein hammer's ore. Only the shapes; {@link TraitEffects#areaBreak} decides which of
 * them the tool may really break and breaks them.
 */
final class ToolAreas {
	/** A felled tree: the most logs it brings down, and how far (blocks, each way) from the one you cut they may lie. */
	static final int TREE_MAX_LOGS = 128, TREE_RADIUS = 8;
	/** A vein: ore of the same kind this close to the last one counts as joined to it (2 skips a one-block gap), the most
	 * blocks it takes, and how far (blocks, each way) from the one you mined they may lie. */
	static final int VEIN_LINK = 2, VEIN_MAX = 48, VEIN_RADIUS = 8;

	private ToolAreas() {
	}

	/** The blocks an area takes, and whether they're a whole tree or vein (which the tool needn't be made for block by block). */
	record Area(List<BlockPos> blocks, boolean whole) {
	}

	/** The blocks the area takes besides {@code origin}, in the order to break them (nearest first). */
	static Area blocks(ServerLevel level, ServerPlayer player, ToolType.Aoe aoe, BlockPos origin, BlockState state, Direction face) {
		switch (aoe.kind()) {
			case BOX -> {
				if (aoe.tree() && state.is(BlockTags.LOGS)) {
					List<BlockPos> tree = tree(level, origin);
					if (!tree.isEmpty()) {
						return new Area(tree, true);
					}
				}
				return new Area(box(player, aoe, origin, face), false);
			}
			case CIRCLE -> {
				return new Area(circle(player, aoe, origin, face), false);
			}
			case VEIN -> {
				return new Area(state.is(ConventionalBlockTags.ORES) ? vein(level, origin, state) : List.of(), true);
			}
			default -> {
				return new Area(List.of(), false);
			}
		}
	}

	/** Across, along and into the face you hit: along is up on a wall, the way you look on a floor or ceiling. */
	private static Direction[] axes(ServerPlayer player, ToolType.Aoe aoe, Direction face) {
		Direction look = player.getDirection();
		if (face.getAxis() == Direction.Axis.Y) {
			return new Direction[] {look.getClockWise(), aoe.world() ? Direction.UP : look, face.getOpposite()};
		}
		return new Direction[] {face.getClockWise(), Direction.UP, face.getOpposite()};
	}

	private static List<BlockPos> box(ServerPlayer player, ToolType.Aoe aoe, BlockPos origin, Direction face) {
		Direction[] axes = axes(player, aoe, face);
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int k = 0; k <= aoe.depth(); k++) {
			for (int i = -aoe.down(); i <= aoe.up(); i++) {
				for (int j = -aoe.side(); j <= aoe.side(); j++) {
					out.add(origin.relative(axes[0], j).relative(axes[1], i).relative(axes[2], k).immutable());
				}
			}
		}
		out.remove(origin);
		return new ArrayList<>(out);
	}

	/** A disc in the face's plane, or a ball: the blocks whose centres are within the radius and a half of the origin's. */
	private static List<BlockPos> circle(ServerPlayer player, ToolType.Aoe aoe, BlockPos origin, Direction face) {
		int r = aoe.side();
		double reach = (r + 0.5) * (r + 0.5);
		Direction[] axes = axes(player, new ToolType.Aoe(aoe.kind(), 0, 0, 0, 0, false, false), face);
		List<BlockPos> out = new ArrayList<>();
		for (int i = -r; i <= r; i++) {
			for (int j = -r; j <= r; j++) {
				for (int k = aoe.world() ? -r : 0; k <= (aoe.world() ? r : 0); k++) {
					if ((i != 0 || j != 0 || k != 0) && i * i + j * j + k * k <= reach) {
						out.add(origin.relative(axes[0], j).relative(axes[1], i).relative(axes[2], k).immutable());
					}
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(origin)));
		return out;
	}

	/**
	 * The logs joined to this one (corners count), from it upward only. It's a tree only if one of them has leaves that
	 * grew rather than ones someone placed: a log cabin isn't felled.
	 */
	private static List<BlockPos> tree(ServerLevel level, BlockPos origin) {
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> logs = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin);
		queue.add(origin);
		boolean leafy = false;
		while (!queue.isEmpty() && logs.size() < TREE_MAX_LOGS) {
			BlockPos at = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockState near = level.getBlockState(at.relative(direction));
				if (near.is(BlockTags.LEAVES) && near.hasProperty(LeavesBlock.PERSISTENT) && !near.getValue(LeavesBlock.PERSISTENT)) {
					leafy = true;
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (Math.abs(next.getX() - origin.getX()) > TREE_RADIUS || Math.abs(next.getZ() - origin.getZ()) > TREE_RADIUS
							|| !seen.add(next.immutable()) || !level.getBlockState(next).is(BlockTags.LOGS)) {
							continue;
						}
						logs.add(next.immutable());
						queue.add(next.immutable());
					}
				}
			}
		}
		return leafy ? logs : List.of();
	}

	/** Ore of the same kind, joined within {@link #VEIN_LINK} of one another, at most {@link #VEIN_MAX} of it. */
	private static List<BlockPos> vein(ServerLevel level, BlockPos origin, BlockState ore) {
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> found = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin);
		queue.add(origin);
		while (!queue.isEmpty() && found.size() < VEIN_MAX) {
			BlockPos at = queue.poll();
			for (int dx = -VEIN_LINK; dx <= VEIN_LINK && found.size() < VEIN_MAX; dx++) {
				for (int dy = -VEIN_LINK; dy <= VEIN_LINK && found.size() < VEIN_MAX; dy++) {
					for (int dz = -VEIN_LINK; dz <= VEIN_LINK && found.size() < VEIN_MAX; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (Math.abs(next.getX() - origin.getX()) > VEIN_RADIUS || Math.abs(next.getY() - origin.getY()) > VEIN_RADIUS
							|| Math.abs(next.getZ() - origin.getZ()) > VEIN_RADIUS || !seen.add(next.immutable())
							|| level.getBlockState(next).getBlock() != ore.getBlock()) {
							continue;
						}
						found.add(next.immutable());
						queue.add(next.immutable());
					}
				}
			}
		}
		return found;
	}
}
