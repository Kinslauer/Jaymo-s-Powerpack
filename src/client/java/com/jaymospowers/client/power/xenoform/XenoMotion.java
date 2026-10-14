package com.jaymospowers.client.power.xenoform;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * How an alien's body has been moving, for its extra parts (wings, tails, mantles, ears) to follow in the manner of
 * Fresh Animations: they lag behind turns, flare as it falls, whip on landing and breathe at rest, instead of running
 * on a bare sine. Kept per entity between frames and stepped by its age, so a second draw in the same frame (the
 * inventory doll, first-person passes) changes nothing.
 */
final class XenoMotion {
	/** Springs: how hard each follower pulls toward its target, and how much of its speed is left after a tick. */
	static final float TURN_PULL = 0.35F, TURN_KEEP = 0.55F, FALL_PULL = 0.3F, FALL_KEEP = 0.6F, SWAY_PULL = 0.18F, SWAY_KEEP = 0.8F;
	/** A turn of this many degrees a tick reads as a full swing of the trailing parts. */
	static final float FULL_TURN = 18.0F;
	/** Falling at this many blocks a tick reads as fully falling. */
	static final float FULL_FALL = 0.6F;
	/** A landing's jolt: how much of the fall speed it takes, and how fast it dies (per tick). */
	static final float LAND_GAIN = 1.6F, LAND_FADE = 0.82F;
	/** Breaths per tick (radians) and how long an entity can go undrawn before its state is dropped (ticks). */
	static final float BREATH_RATE = 0.09F, FORGET_TICKS = 100.0F;

	/** What a part reads: each about -1..1 (turn: + to the body's left), smoothed. */
	record Read(float turn, float trail, float fall, float air, float land, float breath, float speed, boolean crouching) {
		static final Read STILL = new Read(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
	}

	private static final class Track {
		float age = Float.NaN, yaw, turn, turnV, trail, trailV, fall, fallV, air, land;
		boolean wasGround = true;
		/** The level's game time when it was last drawn (ages are each entity's own, so can't be compared). */
		long seen;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();

	private XenoMotion() {
	}

	static Read read(AvatarRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(state.id);
		if (entity == null) {
			return Read.STILL;
		}
		Track t = TRACKS.computeIfAbsent(state.id, id -> new Track());
		long gameTime = minecraft.level.getGameTime();
		t.seen = gameTime;
		float now = state.ageInTicks;
		if (Float.isNaN(t.age) || now < t.age || now - t.age > FORGET_TICKS) {
			t.age = now;
			t.yaw = state.bodyRot;
		}
		float dt = Math.min(now - t.age, 3.0F);
		if (dt > 0.0F) {
			t.age = now;
			// turning: the body's yaw rate this frame
			float rate = Mth.wrapDegrees(state.bodyRot - t.yaw) / dt;
			t.yaw = state.bodyRot;
			float turnTarget = Mth.clamp(rate / FULL_TURN, -1.0F, 1.0F);
			t.turnV = spring(t.turnV, turnTarget - t.turn, TURN_PULL, TURN_KEEP, dt);
			t.turn += t.turnV * dt;
			// the trail follows the turn a beat later, overshooting a little: tails and tips
			t.trailV = spring(t.trailV, t.turn - t.trail, SWAY_PULL, SWAY_KEEP, dt);
			t.trail += t.trailV * dt;
			// falling (rising is negative), from where it was a tick ago
			double vy = entity.getY() - entity.yo;
			float fallTarget = Mth.clamp((float)(-vy / FULL_FALL), -1.0F, 1.0F);
			t.fallV = spring(t.fallV, fallTarget - t.fall, FALL_PULL, FALL_KEEP, dt);
			t.fall += t.fallV * dt;
			boolean ground = entity.onGround();
			t.air = Mth.lerp(1.0F - (float)Math.pow(0.7F, dt), t.air, ground ? 0.0F : 1.0F);
			if (ground && !t.wasGround) {
				t.land = Math.max(t.land, Mth.clamp(t.fall * LAND_GAIN, 0.0F, 1.0F));
			}
			t.wasGround = ground;
			t.land *= (float)Math.pow(LAND_FADE, dt);
		}
		if (TRACKS.size() > 64) {
			TRACKS.values().removeIf(old -> gameTime - old.seen > FORGET_TICKS);
		}
		return new Read(t.turn, t.trail, t.fall, t.air, t.land, Mth.sin(now * BREATH_RATE), state.walkAnimationSpeed, state.isCrouching);
	}

	private static float spring(float velocity, float toTarget, float pull, float keep, float dt) {
		return (velocity + toTarget * pull * dt) * (float)Math.pow(keep, dt);
	}
}
