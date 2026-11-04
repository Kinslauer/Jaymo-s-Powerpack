package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.533) Humanoids jump gaps. Andy's clip: two hired villagers following him stopped dead at the lip of a trench a
 * couple of blocks wide that he had just jumped. Vanilla's pathfinder only walks, steps up a block and drops; it never
 * plans a jump across empty air, so the best path it finds ends at the near edge and the mob stands there.
 *
 * <p>So when a walking humanoid (an MCA villager, our folk, an illager, a zombie, a skeleton, a piglin...) has walked to
 * the end of a path that falls short of where it was going, and where it was going is still a way off, it looks across
 * toward it (straight on and a few angles either side) for ground on the far side of a drop, up to {@link #REACH} blocks
 * away, a block higher at most or {@link #DROP} lower, with the air clear for its body all the way over. If there is
 * some, closer to its goal than it is now, it takes a running jump at it, sized to land there, and paths on from the
 * other side. It won't jump onto lava, fire, magma or cactus, or out of water.
 */
public final class GapJumps {
	/** How often (in ticks) a mob at the end of a short path looks for a jump. */
	static final int LOOK_EVERY = 5;
	/** After a jump (or a look that found nothing), it waits this long before the next look. */
	static final int AFTER_JUMP = 30, AFTER_NOTHING = 20;
	/** It must be this close (across) to its path's last step to count as standing at the end of it. */
	static final double AT_END = 1.3;
	/** And where it was going must still be at least this far off (across). */
	static final double STILL_FAR = 2.5;
	/** The landing must leave it at least this much closer to its goal. */
	static final double GAIN = 1.5;
	/** Farthest landing (blocks across from where it stands), the nearest to look at, and the step between looks. */
	static final double REACH = 5.0, NEAREST = 1.5, STEP = 0.5;
	/** Landings this much higher at most, this much lower at most. */
	static final int RISE = 1, DROP = 3;
	/** A drop at least this deep between here and the landing makes it a gap (a shallower dip the pathfinder walks). */
	static final int GAP_DEPTH = 2;
	/** Angles (degrees) off the straight line to its goal that it looks along, best first. */
	static final int[] ANGLES = {0, 20, -20, 40, -40, 65, -65, 90, -90};
	/** The jump: upward speed, the most sideways speed it can put into one, and how far past the landing's middle it aims. */
	static final double JUMP = 0.45, TOP_SPEED = 0.8, OVERSHOOT = 0.3;
	/** Vanilla's air physics: sideways drag per tick, gravity and vertical drag. */
	static final double AIR_DRAG = 0.91, GRAVITY = 0.08, FALL_DRAG = 0.98;

	/** One mob's next look, kept on its navigation. */
	public static final class State {
		long next;
	}

	private GapJumps() {
	}

	/** From the end of {@link PathNavigation#tick}. */
	public static void tick(Mob mob, PathNavigation nav, State s, double speed) {
		if (!(nav instanceof GroundPathNavigation) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (now < s.next || (now + mob.getId()) % LOOK_EVERY != 0) {
			return;
		}
		if (!mob.onGround() || mob.isPassenger() || mob.isVehicle() || mob.isNoAi() || mob.isInWater() || mob.isInLava() || !leaper(mob)) {
			return;
		}
		Path path = nav.getPath();
		if (path == null || path.canReach() || path.getNodeCount() == 0) {
			return;
		}
		Node end = path.getEndNode();
		BlockPos target = path.getTarget();
		if (end == null || target == null) {
			return;
		}
		Vec3 here = mob.position();
		if (across(here, Vec3.atBottomCenterOf(end.asBlockPos())) > AT_END) {
			return;
		}
		Vec3 goal = Vec3.atBottomCenterOf(target);
		double far = across(here, goal);
		if (far < STILL_FAR) {
			return;
		}
		Vec3 landing = landing(level, mob, here, goal, far);
		if (landing == null) {
			s.next = now + AFTER_NOTHING;
			return;
		}
		leap(mob, nav, here, landing, speed);
		s.next = now + AFTER_JUMP;
	}

	/** Who jumps gaps: the humanoids. Beasts and livestock keep to what they can walk. */
	static boolean leaper(Mob mob) {
		return Temperaments.humanoid(mob) || MobCombat.humanoid(mob);
	}

	/** The best ground across a gap toward {@code goal}: the landing that leaves it nearest its goal. Null if none. */
	static @Nullable Vec3 landing(ServerLevel level, Mob mob, Vec3 here, Vec3 goal, double far) {
		Vec3 straight = new Vec3(goal.x - here.x, 0.0, goal.z - here.z).normalize();
		AABB body = mob.getBoundingBox();
		if (!level.noCollision(mob, body.move(0.0, 1.0, 0.0))) {
			return null;
		}
		int floorY = Mth.floor(here.y + 0.01) - 1;
		Vec3 best = null;
		double bestLeft = far - GAIN;
		for (int angle : ANGLES) {
			Vec3 way = straight.yRot((float) Math.toRadians(angle));
			boolean gap = false;
			for (double r = NEAREST; r <= REACH + 1.0E-6; r += STEP) {
				Vec3 at = here.add(way.scale(r));
				if (!gap) {
					if (!level.noCollision(mob, body.move(at.x - here.x, 1.0, at.z - here.z))) {
						break;
					}
					gap = drop(level, at, floorY);
					continue;
				}
				Vec3 stand = stand(level, mob, at, floorY + 1);
				if (stand == null) {
					if (!level.noCollision(mob, body.move(at.x - here.x, 1.0, at.z - here.z))) {
						break;
					}
					continue;
				}
				double left = across(stand, goal);
				if (left < bestLeft && clear(level, mob, here, stand)) {
					best = stand;
					bestLeft = left;
				}
				break;
			}
		}
		return best;
	}

	/** Nothing to stand on at {@code at} for {@link #GAP_DEPTH} blocks down from the floor it stands on now. */
	private static boolean drop(ServerLevel level, Vec3 at, int floorY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(at.x), floorY, Mth.floor(at.z));
		for (int i = 0; i < GAP_DEPTH; i++) {
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
				return false;
			}
			pos.move(Direction.DOWN);
		}
		return true;
	}

	/** Where it can stand in the column at {@code at}, the highest from {@link #RISE} up to {@link #DROP} down. */
	private static @Nullable Vec3 stand(ServerLevel level, Mob mob, Vec3 at, int feetY) {
		int x = Mth.floor(at.x), z = Mth.floor(at.z);
		for (int dy = RISE; dy >= -DROP; dy--) {
			BlockPos feet = new BlockPos(x, feetY + dy, z);
			BlockPos below = feet.below();
			BlockState floor = level.getBlockState(below);
			if (!floor.isFaceSturdy(level, below, Direction.UP) || unsafe(floor) || !level.getFluidState(feet).isEmpty()
				|| level.getBlockState(feet).is(BlockTags.FIRE)) {
				continue;
			}
			Vec3 spot = new Vec3(x + 0.5, feet.getY(), z + 0.5);
			if (level.noCollision(mob, mob.getBoundingBox().move(spot.subtract(mob.position())))) {
				return spot;
			}
		}
		return null;
	}

	private static boolean unsafe(BlockState floor) {
		return floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(BlockTags.CAMPFIRES) || floor.is(Blocks.LAVA);
	}

	/** Room for its body along the arc: every half block over, at the height the jump carries it. */
	private static boolean clear(ServerLevel level, Mob mob, Vec3 from, Vec3 to) {
		AABB body = mob.getBoundingBox();
		double d = across(from, to);
		double lift = Math.max(0.0, to.y - from.y) + 1.0;
		for (double r = 0.5; r < d - 0.5; r += 0.5) {
			Vec3 at = from.lerp(to, r / d);
			if (!level.noCollision(mob, body.move(at.x - from.x, lift, at.z - from.z))) {
				return false;
			}
		}
		return true;
	}

	/** A running jump sized to come down at {@code to}. */
	static void leap(Mob mob, PathNavigation nav, Vec3 from, Vec3 to, double speed) {
		Vec3 way = new Vec3(to.x - from.x, 0.0, to.z - from.z);
		double d = way.length() + OVERSHOOT;
		int t = airTicks(to.y - from.y);
		double v = Math.min(TOP_SPEED, d * (1.0 - AIR_DRAG) / (1.0 - Math.pow(AIR_DRAG, t)));
		Vec3 push = way.normalize().scale(v);
		float yaw = (float) (Mth.atan2(way.z, way.x) * (180.0 / Math.PI)) - 90.0F;
		nav.stop();
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setDeltaMovement(push.x, JUMP, push.z);
		// Off the ground now, so this tick's move isn't slowed by ground friction.
		mob.setOnGround(false);
		mob.needsSync = true;
		mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, Math.max(speed, 1.0));
	}

	/** Ticks in the air before a jump of {@link #JUMP} comes down to {@code dy} above where it started. */
	static int airTicks(double dy) {
		double y = 0.0, vy = JUMP;
		for (int t = 1; t < 60; t++) {
			y += vy;
			vy = (vy - GRAVITY) * FALL_DRAG;
			if (vy < 0.0 && y <= dy) {
				return t;
			}
		}
		return 60;
	}

	private static double across(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
