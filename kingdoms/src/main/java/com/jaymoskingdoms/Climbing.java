package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.NodeEvaluatorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): paths go up and down ladders
 * and vines, and fighters climb them. The game's pathfinder only walks, steps up a block and drops, so a ladder is just air
 * to it: a mob climbs one only if something pushes it against it. A humanoid in a fight ({@link Passages#fighter}) now
 * plans climbs ({@code mixin.WalkNodeEvaluatorMixin}, at the end of {@code WalkNodeEvaluator.getNeighbors}):
 * <ul>
 * <li>up from where it stands into a climbable block over it ({@link #climbable}: ladders, vines, scaffolding and the like),
 * or into a wooden trapdoor over the top of the one it's on (opened on the way, {@link Doorways});</li>
 * <li>down into a climbable block under it, or through a trapdoor over one;</li>
 * <li>sideways from a floor into a climbable block beside it, or down into the top of one in a hole beside it.</li>
 * </ul>
 * Every climb step needs room for its body ({@link #room}) and costs {@link #CLIMB_MALUS} more than a step, so stairs are
 * taken when there are some. On the ladder ({@link #tick}, from the end of {@code PathNavigation.tick}) it climbs while its
 * next step is above it (jumping on a ladder climbs it, as a player's does), and going down it keeps to the ladder until it
 * is level with the way off at the bottom. A climber on a ladder starts its paths where it hangs, not at the foot
 * ({@link #start}), and keeps following its path up there ({@link #follows}).
 */
public final class Climbing {
	/** A climb step costs this much more than a walking step. */
	static final float CLIMB_MALUS = 1.0F;
	/** Going down with the way off lower still, it is held to the ladder until it's within this of the way off (blocks). */
	static final double LET_GO = 0.6;

	private Climbing() {
	}

	/** Who plans climbs: a humanoid in a fight. */
	public static boolean climber(@Nullable Mob mob) {
		return mob != null && Passages.fighter(mob);
	}

	/** Something to climb: a ladder, vines, scaffolding, the like. */
	static boolean climbable(BlockState s) {
		return s.is(BlockTags.CLIMBABLE);
	}

	private static boolean hatch(BlockState s) {
		return s.is(BlockTags.WOODEN_TRAPDOORS) && s.hasProperty(TrapDoorBlock.OPEN);
	}

	/** At the end of {@link WalkNodeEvaluator#getNeighbors}: the climbs from {@code pos} added after the {@code count} found. */
	public static int neighbors(WalkNodeEvaluator evaluator, Node[] out, int count, Node pos) {
		NodeEvaluatorAccessor acc = (NodeEvaluatorAccessor)evaluator;
		Mob mob = acc.jaymoskingdoms$mob();
		PathfindingContext ctx = acc.jaymoskingdoms$context();
		if (mob == null || ctx == null || !climber(mob)) {
			return count;
		}
		BlockPos here = new BlockPos(pos.x, pos.y, pos.z);
		BlockState at = ctx.getBlockState(here);
		// up: a ladder over its head (or a trapdoor over the top of the ladder it's on)
		BlockPos up = here.above();
		BlockState over = ctx.getBlockState(up);
		if ((climbable(over) || hatch(over) && climbable(at)) && room(ctx, mob, up)) {
			count = add(acc, out, count, up);
		}
		// down: a ladder under it (or a trapdoor over one)
		BlockPos down = here.below();
		if (rungOrHatch(ctx, down) && room(ctx, mob, down)) {
			count = add(acc, out, count, down);
		}
		// sideways onto a ladder beside it, or down into the top of one in a hole beside it
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = here.relative(d);
			if (climbable(ctx.getBlockState(side))) {
				if (room(ctx, mob, side)) {
					count = add(acc, out, count, side);
				}
			} else if (rungOrHatch(ctx, side.below()) && room(ctx, mob, side) && room(ctx, mob, side.below())) {
				count = add(acc, out, count, side.below());
			}
		}
		return count;
	}

	/** A climbable block at {@code p}, or a wooden trapdoor over one. */
	private static boolean rungOrHatch(PathfindingContext ctx, BlockPos p) {
		BlockState s = ctx.getBlockState(p);
		return climbable(s) || hatch(s) && climbable(ctx.getBlockState(p.below()));
	}

	private static int add(NodeEvaluatorAccessor acc, Node[] out, int count, BlockPos p) {
		if (count >= out.length) {
			return count;
		}
		Node n = acc.jaymoskingdoms$node(p.getX(), p.getY(), p.getZ());
		if (n.closed) {
			return count;
		}
		for (int i = 0; i < count; i++) {
			if (out[i] == n) {
				return count;
			}
		}
		n.type = PathType.WALKABLE;
		n.costMalus = Math.max(n.costMalus, CLIMB_MALUS);
		out[count] = n;
		return count + 1;
	}

	/**
	 * Room for {@code mob}'s body standing in the middle of {@code p}: nothing it would collide with there or over it (a ladder
	 * is a thin plate at the block's side, so it fits), no lava, and a shut wooden trapdoor counts as open (it'll be opened)
	 * unless it's locked against it.
	 */
	static boolean room(PathfindingContext ctx, Mob mob, BlockPos p) {
		double half = mob.getBbWidth() / 2.0;
		AABB body = new AABB(p.getX() + 0.5 - half, p.getY() + 0.01, p.getZ() + 0.5 - half, p.getX() + 0.5 + half, p.getY() + mob.getBbHeight(), p.getZ() + 0.5 + half);
		int top = Mth.ceil(mob.getBbHeight());
		for (int dy = 0; dy < top; dy++) {
			BlockPos q = p.above(dy);
			BlockState s = ctx.getBlockState(q);
			if (s.getFluidState().is(FluidTags.LAVA)) {
				return false;
			}
			if (hatch(s) && !s.getValue(TrapDoorBlock.OPEN) && !Locks.barred(mob.level(), q, mob)) {
				continue;
			}
			for (AABB box : s.getCollisionShape(ctx.level(), q).toAabbs()) {
				if (box.move(q).intersects(body)) {
					return false;
				}
			}
		}
		return true;
	}

	/** At the start of {@code WalkNodeEvaluator.getStart}: a climber on a ladder starts where it hangs (null: the game's own start). */
	public static @Nullable BlockPos start(@Nullable Mob mob) {
		return mob != null && !mob.onGround() && mob.onClimbable() && climber(mob) ? mob.blockPosition() : null;
	}

	/** From {@code GroundPathNavigation.canUpdatePath}: a climber on a ladder keeps following its path, off the ground as it is. */
	public static boolean follows(Mob mob) {
		return mob.onClimbable() && climber(mob);
	}

	/** From the end of {@link PathNavigation#tick}: on a ladder, it climbs while its next step is above it, and holds on going down. */
	public static void tick(Mob mob, PathNavigation nav, double speed) {
		if (!(nav instanceof GroundPathNavigation) || nav.isDone() || mob.isPassenger() || !Temperaments.humanoid(mob) || !mob.onClimbable()) {
			return;
		}
		Path path = nav.getPath();
		if (path == null || path.isDone()) {
			return;
		}
		BlockPos next = path.getNextNodePos();
		boolean column = next.getX() == mob.getBlockX() && next.getZ() == mob.getBlockZ();
		if (next.getY() > mob.getY() + 0.05) {
			// up: jumping on a ladder climbs it
			mob.getJumpControl().jump();
		} else if (!column && next.getY() < mob.getY() - LET_GO) {
			// down, with the way off lower still: it keeps to the ladder till it's down there
			mob.getMoveControl().setWantedPosition(mob.getBlockX() + 0.5, mob.getY(), mob.getBlockZ() + 0.5, speed);
		}
	}
}
