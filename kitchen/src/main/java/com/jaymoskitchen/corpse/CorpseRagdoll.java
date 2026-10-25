package com.jaymoskitchen.corpse;

import com.jaymoskitchen.ragdoll.LevelRagdollWorld;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.1) The physics body of one free-lying corpse, on the server. The corpse entity is just where the body's middle
 * is (and as low as it reaches); this steps the body, pushes the entity after it, and sends the points to watchers.
 * Anything that moves the entity from outside (a blast's knockback, a tendril, a Gravity draw, a scavenger's yank, a
 * teleport) shows up as a difference from what was last written to it, and is passed on to the body.
 */
final class CorpseRagdoll {
	/** Differences smaller than this (blocks, blocks a tick) are the game's own noise (fluid push, rounding), not a shove. */
	private static final double SYNC_EPSILON = 1.0E-4;
	/** Moved further than this by something else (blocks): the body is rebuilt lying there rather than dragged. */
	private static final double REBUILD_DISTANCE = 8.0;
	/** Asleep, the points are sent again this often (ticks), for anyone who has just come into range. */
	private static final int RESEND_EVERY = 40;
	/** Moving at least this fast (blocks a tick) and off the ground counts as tumbling. */
	private static final double TUMBLE_SPEED = 0.05;
	/** A blow's share that spreads to the rest of the body, and the lift every blow adds (blocks a tick). */
	private static final float HIT_SHARE = 0.35F;
	private static final double SMACK_LIFT = 0.05;
	/** A jolt (an axe blow, rummaging, a bite) hits a random point this hard (blocks a tick), mostly upward. */
	private static final double JOLT_HARD = 0.12, JOLT_LIGHT = 0.05, JOLT_LIFT = 0.6;
	/**
	 * (0.5.24) Moved further than this at once by something else (blocks: a power's yank), or shoved harder (blocks a
	 * tick: a blast's knockback), a body comes off the butcher table it's pinned to.
	 */
	private static final double UNPIN_MOVE = 0.5, UNPIN_SHOVE = 0.4;
	/** (0.5.24) How often (ticks) a body lying loose looks for a table under it, and a pinned one that it's still there. */
	private static final int TABLE_EVERY = 5;

	RagdollBody body;
	private final LevelRagdollWorld world;
	/** What was last written to the entity (or seen on it at the start). */
	private Vec3 wrotePos = Vec3.ZERO, wroteVel = Vec3.ZERO;
	private boolean wasAsleep;
	private int sinceSent;
	private int sinceTable;

	CorpseRagdoll(CorpseEntity corpse, RagdollBody body) {
		this.body = body;
		this.world = new LevelRagdollWorld(corpse.level(), corpse);
		write(corpse);
		this.wasAsleep = body.asleep();
		this.sinceSent = RESEND_EVERY;
	}

	/** One tick: take in what pushed the entity, step the body, put the entity where it ended up, tell the watchers. */
	void tick(CorpseEntity corpse, net.minecraft.server.level.ServerLevel level) {
		Vec3 pos = corpse.position();
		Vec3 moved = pos.subtract(wrotePos);
		if (moved.lengthSqr() > REBUILD_DISTANCE * REBUILD_DISTANCE) {
			// teleported: lie down where it is now
			body = corpse.lyingBody();
			Vec3 v = corpse.getDeltaMovement();
			body.setSpeed(v.x, v.y, v.z);
			wasAsleep = false;
		} else if (moved.lengthSqr() > SYNC_EPSILON * SYNC_EPSILON) {
			if (moved.lengthSqr() > UNPIN_MOVE * UNPIN_MOVE) {
				body.unpin();
			}
			body.translate(moved.x, moved.y, moved.z);
			body.wake();
		}
		Vec3 pushed = corpse.getDeltaMovement().subtract(wroteVel);
		if (pushed.lengthSqr() > SYNC_EPSILON * SYNC_EPSILON) {
			if (pushed.lengthSqr() > UNPIN_SHOVE * UNPIN_SHOVE) {
				body.unpin();
			}
			body.shove(pushed.x, pushed.y, pushed.z);
		}
		body.step(world, corpse.isNoGravity() ? 0.0F : RagdollBody.GRAVITY);
		if (!body.sane()) {
			body = corpse.lyingBody();
		}
		// (0.5.24) a body that lands on a butcher table (set down, thrown, slid onto it) stays on it; the table broken, it falls
		if (++sinceTable >= TABLE_EVERY) {
			sinceTable = 0;
			if (!body.pinned()) {
				if (body.grounded()) {
					pinOnTable(corpse);
				}
			} else if (corpse.tableUnder(body) == null) {
				body.unpin();
				body.wake();
			}
		}
		boolean asleep = body.asleep();
		if (!asleep || !wasAsleep) {
			write(corpse);
		}
		double[] v = body.speed();
		corpse.setTumbling(!body.grounded() && v[0] * v[0] + v[1] * v[1] + v[2] * v[2] > TUMBLE_SPEED * TUMBLE_SPEED);
		// awake it's sent every tick; it falling asleep is sent once more; asleep, now and then
		sinceSent++;
		if (!asleep || !wasAsleep || sinceSent >= RESEND_EVERY) {
			send(corpse);
		}
		wasAsleep = asleep;
	}

	/** (0.5.24) Pins the body to the butcher table its torso lies on, if it lies on one. */
	void pinOnTable(CorpseEntity corpse) {
		double[] top = corpse.tableUnder(body);
		if (top != null) {
			body.pin(top[0], top[1], top[2], top[3]);
		}
	}

	/** Puts the entity where the body is, moving as it moves. */
	private void write(CorpseEntity corpse) {
		double[] m = body.middle();
		corpse.setPos(m[0], body.bottom(), m[2]);
		double[] v = body.speed();
		corpse.setDeltaMovement(v[0], v[1], v[2]);
		corpse.setOnGround(body.grounded());
		corpse.setYRot(body.yaw());
		wrotePos = corpse.position();
		wroteVel = corpse.getDeltaMovement();
	}

	/** Sends the points to everyone watching. */
	void send(CorpseEntity corpse) {
		sinceSent = 0;
		RagdollNet.sendAround(corpse, RagdollSnapshot.of(body), null);
	}

	/** A blow at {@code at} along {@code direction}: {@code strength} blocks a tick at the nearest part, a little lift too. */
	void smack(Vec3 at, Vec3 direction, double strength) {
		Vec3 d = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 0.0) : direction.normalize();
		body.hit(at.x, at.y, at.z, d.x * strength, d.y * strength + SMACK_LIFT, d.z * strength, HIT_SHARE);
	}

	/** A knock at a random part (an axe blow, rummaging, a bite), mostly upward. */
	void jolt(boolean hard, RandomSource random) {
		int i = random.nextInt(body.rig.count);
		double strength = hard ? JOLT_HARD : JOLT_LIGHT;
		double angle = random.nextDouble() * Math.PI * 2.0;
		body.hit(body.x[i], body.y[i], body.z[i], Math.cos(angle) * strength * (1.0 - JOLT_LIFT),
			(0.5 + random.nextDouble() * 0.5) * strength * (1.0 + JOLT_LIFT), Math.sin(angle) * strength * (1.0 - JOLT_LIFT),
			HIT_SHARE);
	}
}
