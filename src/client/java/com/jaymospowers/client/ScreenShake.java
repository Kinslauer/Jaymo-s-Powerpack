package com.jaymospowers.client;

import com.jaymospowers.core.Shakes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every power's screen shake, felt here (sent by {@link Shakes}, or started on this side). Several can run at once and
 * add up. A shake from somewhere kicks the view away from it first (a blow from the left tips your head right, a
 * landing underfoot drops it, one ahead throws it back) and then rattles; a shake from nowhere only rattles. Each
 * dies away over its own length. Applied with the hurt bob ({@code ScreenShakeMixin}), so the player's aim never moves.
 */
public final class ScreenShake {
	/** Overall strength (1 = as sent). */
	static final float INTENSITY = 1.0F;
	/** At most this many shakes at once (the weakest gives way), and never more than this many degrees in all. */
	static final int MAX_ACTIVE = 8;
	static final float MAX_DEGREES = 7.0F;
	/** The directional kick: its share of a shake's strength, how fast it swings (radians a tick), how fast it settles. */
	static final float KICK = 1.1F, KICK_SWING = 1.3F, KICK_SETTLE = 0.35F;
	/** The rattle under it: its share for a shake from somewhere, and its speed (radians a tick). */
	static final float DIRECTED_RATTLE = 0.55F, RATTLE_SPEED = 2.6F;
	/** A shake comes on over this many ticks rather than snapping in. */
	static final float ATTACK_TICKS = 1.2F;
	/** Shakes from within this many blocks of each other (or both from nowhere) are one shake; a new one adds this share. */
	static final double SAME_SPOT = 2.0;
	static final float BUILD_UP = 0.5F;
	/** Explosions: felt this many radii away, this many degrees per block of radius (at most {@code EXPLOSION_MAX}). */
	static final double EXPLOSION_REACH = 6.0;
	static final float EXPLOSION_PER_RADIUS = 0.6F, EXPLOSION_MAX = 4.0F;
	static final int EXPLOSION_TICKS = 18;
	/** Closer than this to the camera, a shake's spot counts as under you. */
	static final double UNDERFOOT = 1.2;

	private static final List<Active> ACTIVE = new ArrayList<>();
	private static int clock;

	private ScreenShake() {
	}

	private static final class Active {
		final float strength;
		final int ticks;
		final @Nullable Vec3 from;
		final float seed;
		int age;

		Active(float strength, int ticks, @Nullable Vec3 from, float seed) {
			this.strength = strength;
			this.ticks = ticks;
			this.from = from;
			this.seed = seed;
		}

		float left() {
			float t = this.age / (float)this.ticks;
			return t >= 1.0F ? 0.0F : this.strength * (1.0F - t) * (1.0F - t);
		}
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Shakes.Shake.TYPE, (payload, context) -> add(payload.strength(), payload.ticks(), payload.from()));
		ClientTickEvents.END_CLIENT_TICK.register(ScreenShake::tick);
	}

	/** A shake from nowhere in particular, {@code degrees} at its peak, over the usual length. */
	public static void add(float degrees) {
		add(degrees, Shakes.ticksFor(degrees), null);
	}

	/** A shake of {@code degrees} at its peak over {@code ticks}, kicked away from {@code from} (null: no direction). */
	public static void add(float degrees, int ticks, @Nullable Vec3 from) {
		if (degrees <= 0.02F) {
			return;
		}
		ticks = Math.clamp(ticks, 1, Shakes.MAX_TICKS);
		// from the same spot as one still running (a struggle, a flurry): they build on each other as one shake
		for (int i = 0; i < ACTIVE.size(); i++) {
			Active a = ACTIVE.get(i);
			if (from == null ? a.from == null : a.from != null && a.from.distanceToSqr(from) < SAME_SPOT * SAME_SPOT) {
				ACTIVE.set(i, new Active(Math.max(degrees, a.left() + degrees * BUILD_UP), Math.max(ticks, a.ticks - a.age), from, a.seed));
				return;
			}
		}
		if (ACTIVE.size() >= MAX_ACTIVE) {
			Active weakest = ACTIVE.getFirst();
			for (Active a : ACTIVE) {
				if (a.left() < weakest.left()) {
					weakest = a;
				}
			}
			if (weakest.left() >= degrees) {
				return;
			}
			ACTIVE.remove(weakest);
		}
		ACTIVE.add(new Active(degrees, ticks, from, (clock * 7 + ACTIVE.size() * 13) % 64 * 0.37F));
	}

	/** An explosion of {@code radius} went off at {@code at}: felt from there, weaker with distance. */
	public static void explosion(Vec3 at, float radius) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || radius <= 0.0F) {
			return;
		}
		double reach = radius * EXPLOSION_REACH;
		double d = minecraft.player.getEyePosition().distanceTo(at);
		if (d < reach) {
			add(Math.min(EXPLOSION_MAX, radius * EXPLOSION_PER_RADIUS) * (float)Math.pow(1.0 - d / reach, Shakes.FALLOFF_POWER), EXPLOSION_TICKS, at);
		}
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			ACTIVE.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		clock++;
		ACTIVE.removeIf(a -> ++a.age >= a.ticks);
	}

	/** Tips the view by every shake running (called with the hurt bob). */
	public static void apply(CameraRenderState camera, PoseStack poseStack) {
		if (ACTIVE.isEmpty() || camera.pos == null) {
			return;
		}
		float partialTicks = camera.cameraEntityPartialTicks;
		Vec3 eye = camera.pos;
		float yawRad = camera.yRot * Mth.DEG_TO_RAD;
		// the camera's own axes on the ground: ahead, and to its left
		double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
		double lx = Mth.cos(yawRad), lz = Mth.sin(yawRad);
		float now = clock + partialTicks;
		float pitch = 0.0F, yaw = 0.0F, roll = 0.0F;
		for (Active a : ACTIVE) {
			float age = a.age + partialTicks;
			float t = age / a.ticks;
			if (t >= 1.0F) {
				continue;
			}
			float s = a.strength * (1.0F - t) * (1.0F - t) * Math.min(1.0F, age / ATTACK_TICKS) * INTENSITY;
			float rattle = a.from == null ? s : s * DIRECTED_RATTLE;
			float p = now * RATTLE_SPEED + a.seed;
			pitch += rattle * (0.6F * Mth.sin(p * 1.17F) + 0.4F * Mth.sin(p * 2.03F + 1.3F));
			yaw += rattle * 0.6F * (0.6F * Mth.sin(p * 0.91F + 2.2F) + 0.4F * Mth.sin(p * 1.71F + 0.5F));
			roll += rattle * 0.8F * (0.6F * Mth.sin(p * 1.03F + 4.1F) + 0.4F * Mth.sin(p * 1.89F + 2.9F));
			if (a.from != null) {
				// where it came from, in the camera's terms: ahead, to the left, above (all -1..1)
				Vec3 d = a.from.subtract(eye);
				double ahead, left, up;
				if (d.lengthSqr() < UNDERFOOT * UNDERFOOT) {
					ahead = 0.0;
					left = 0.0;
					up = -1.0;
				} else {
					d = d.normalize();
					ahead = d.x * fx + d.z * fz;
					left = d.x * lx + d.z * lz;
					up = d.y;
				}
				// a quick swing away from it that settles back
				float kick = a.strength * KICK * INTENSITY * (float)Math.exp(-KICK_SETTLE * age) * Mth.sin(Math.min(age * KICK_SWING, Mth.PI * 1.5F));
				pitch += kick * (float)(-ahead - up);
				roll += kick * (float)left;
				yaw += kick * 0.3F * (float)left;
			}
		}
		float total = Math.abs(pitch) + Math.abs(yaw) + Math.abs(roll);
		if (total > MAX_DEGREES) {
			float scale = MAX_DEGREES / total;
			pitch *= scale;
			yaw *= scale;
			roll *= scale;
		}
		if (total > 0.01F) {
			poseStack.rotateDegrees(Axis.XP, pitch);
			poseStack.rotateDegrees(Axis.YP, yaw);
			poseStack.rotateDegrees(Axis.ZP, roll);
		}
	}
}
