package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.36) Every walking mob (vanilla, MCA villagers, our folk) that has a path but makes no headway along it works
 * itself loose: first a hop toward its next step (a slab, a carpet, a fence post's corner), then a step back to the
 * middle of its block and a fresh path, and at last the walk is dropped so its AI picks something else. Our own folk,
 * out of a fight, turn up where they were going in a puff instead, as builders always have.
 *
 * <p>Vanilla's own check can't see this: it resets every time a goal asks for the path again, and chasing or
 * wandering goals ask every few ticks, so a mob snagged on a corner stays snagged.
 */
public final class Unstick {
	/** It has made no headway (moved less than {@link #HEADWAY} blocks across) for this long: it hops (and again every as long). */
	static final int HOP_TICKS = 30;
	/** Still none: back to the middle of its block and a fresh path. */
	static final int REPATH_TICKS = 70;
	/** Still none: the walk is dropped (not our folk; they puff). */
	static final int GIVE_UP_TICKS = 140;
	/** Our folk, out of a fight, still stuck after this long turn up where they were going. */
	static final int PUFF_TICKS = 240;
	/** Moving this far across counts as headway. */
	static final double HEADWAY = 0.6;
	/** Closer than this to where it's going and it's arrived, not stuck. */
	static final double ARRIVED = 1.5;
	/** The sideways push of a hop, and of the step back to the middle of its block. */
	static final double HOP_PUSH = 0.18, CENTRE_PUSH = 0.12;

	/** One mob's headway, kept on its navigation. */
	public static final class State {
		@Nullable Vec3 from;
		int still;

		void reset() {
			this.from = null;
			this.still = 0;
		}
	}

	private Unstick() {
	}

	/** From the end of {@link PathNavigation#tick}. */
	public static void tick(Mob mob, PathNavigation nav, State s, double speed, int reach) {
		if (!(mob.level() instanceof ServerLevel level) || nav.isDone() || mob.isPassenger() || mob.isNoAi() || mob.isVehicle() && mob.getControllingPassenger() != null) {
			s.reset();
			return;
		}
		BlockPos target = nav.getTargetPos();
		Vec3 here = mob.position();
		if (target == null || here.distanceTo(Vec3.atBottomCenterOf(target)) < ARRIVED) {
			s.reset();
			return;
		}
		if (s.from == null || horizontal(here, s.from) > HEADWAY) {
			s.from = here;
			s.still = 0;
			return;
		}
		s.still++;
		boolean ground = nav instanceof GroundPathNavigation;
		if (mob instanceof Folk folk && s.still >= PUFF_TICKS && puff(level, folk, target)) {
			s.reset();
			return;
		}
		if (s.still == REPATH_TICKS) {
			if (ground && mob.onGround()) {
				push(mob, Vec3.atBottomCenterOf(mob.blockPosition()).subtract(here), CENTRE_PUSH, false);
			}
			nav.stop();
			nav.moveTo(nav.createPath(target, reach), speed);
			return;
		}
		if (s.still >= GIVE_UP_TICKS && !(mob instanceof Folk)) {
			nav.stop();
			s.reset();
			return;
		}
		if (ground && s.still % HOP_TICKS == 0 && mob.onGround()) {
			Path path = nav.getPath();
			if (path != null && !path.isDone()) {
				push(mob, next(path, here).subtract(here), HOP_PUSH, true);
			}
		}
	}

	/** The next step it should be heading for (the one after, if it's standing on the next). */
	private static Vec3 next(Path path, Vec3 here) {
		Vec3 node = Vec3.atBottomCenterOf(path.getNextNodePos());
		if (horizontal(here, node) < 0.5 && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
			node = Vec3.atBottomCenterOf(path.getNodePos(path.getNextNodeIndex() + 1));
		}
		return node;
	}

	private static void push(Mob mob, Vec3 toward, double strength, boolean hop) {
		Vec3 across = new Vec3(toward.x, 0.0, toward.z);
		if (across.lengthSqr() < 1.0E-4) {
			return;
		}
		across = across.normalize().scale(strength);
		mob.setDeltaMovement(mob.getDeltaMovement().add(across));
		if (hop) {
			mob.getJumpControl().jump();
		}
	}

	/** Our folk out of a fight, not breaking in and not on a caravan (they have their own), turn up where they were going. */
	private static boolean puff(ServerLevel level, Folk folk, BlockPos target) {
		if (folk.getTarget() != null || Assault.attacker(folk) || Caravans.isCaravanFolk(folk) || Attention.held(folk)) {
			return false;
		}
		BlockPos at = Construction.standable(level, target, 3);
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		folk.getNavigation().stop();
		folk.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
		return true;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
