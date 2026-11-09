package com.jaymospowers.client;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.ThrowWeight;
import com.jaymospowers.power.web.KitchenCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "thrown entities should start their ragdoll in midair right after they are thrown, right now they fly
 * statically through the air". A body thrown from a carry ({@link Carry#THROWN}) goes limp the moment it leaves your
 * hands: it tumbles end over end along its flight (faster the lighter it is, {@link ThrowWeight}), its limbs flail and
 * trail behind the turn, and when it lands it eases onto its side or back, lies there a moment and gets up (a cat or
 * anything that light lands on its feet). Drawn the way {@link CarryPose} and the web's ragdoll draw a limp creature:
 * the whole body turned about its middle, then each limb loosened after the model animates. A thrown player is Jaymo's
 * Kitchen's limp ragdoll instead (the server knocks them limp), which this keeps off.
 */
public final class ThrownFlight {
	/** Longest a throw is drawn (ticks), however long it's in the air. */
	static final int MAX_FLIGHT = 80;
	/** It can only count as landed after this many ticks (it's still at the carrier's feet on the first). */
	static final float MIN_AIR = 2.0F;
	/** The tumble: degrees a tick for a person, times the square root of its throw scale (a cricket turns faster). */
	static final float SPIN_PER_TICK = 20.0F;
	/** Landed: easing onto its side or back, lying there, and getting up (ticks). */
	static final float LAND_EASE = 4.0F, LIE = 12.0F, RISE = 8.0F;
	/** This light (throw scale) or lighter lands on its feet: it turns upright as it comes down instead. */
	static final double FEET_FIRST = 2.0;
	/** The limbs: a flail at rest, more per block a tick of speed (most 1), and how far they trail the turn (radians). */
	static final float FLAIL = 0.35F, FLAIL_PER_SPEED = 1.4F, TRAIL = 0.8F;
	/** Under this speed along the ground (b/t) the flight's way isn't known yet. */
	static final double HEADING_SPEED = 0.02;

	/** The parts that flail, wherever a model has them (on its root, or on its body). */
	private static final String[] LIMBS = {
		"head", "right_arm", "left_arm", "right_leg", "left_leg",
		"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg",
		"right_wing", "left_wing", "tail", "right_ear", "left_ear"
	};

	/**
	 * One frame of a flight: ticks since the throw; the tumble (degrees, the top over forward along the flight); the
	 * flight's way against the body's facing (degrees); how hard the limbs flail (0-1) and how far they trail (radians).
	 */
	public record Flight(float since, float tumble, float turn, float flail, float trail) {
	}

	/** What each client knows of a throw it's drawing. */
	private static final class Track {
		final long thrownAt;
		final float spinRate;
		final boolean feetFirst;
		float heading = Float.NaN;
		float landedAt = -1.0F;

		Track(long thrownAt, double scale) {
			this.thrownAt = thrownAt;
			this.spinRate = SPIN_PER_TICK * (float) Math.sqrt(scale);
			this.feetFirst = scale >= FEET_FIRST;
		}
	}

	private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

	private ThrownFlight() {
	}

	/** From the renderer's extractRenderState (after the body's facing is set): this frame of its flight, or null. */
	public static @Nullable Flight of(LivingEntity entity, float bodyRot, float partialTicks) {
		Long thrown = entity.getAttached(Carry.THROWN);
		if (thrown == null || Carry.isCarried(entity) || KitchenCompat.limp(entity)) {
			TRACKS.remove(entity);
			return null;
		}
		float since = entity.level().getGameTime() - thrown + partialTicks;
		if (since < 0.0F || since >= MAX_FLIGHT) {
			TRACKS.remove(entity);
			return null;
		}
		Track track = TRACKS.get(entity);
		if (track == null || track.thrownAt != thrown) {
			track = new Track(thrown, ThrowWeight.throwScale(entity));
			TRACKS.put(entity, track);
		}
		double vx = entity.getX() - entity.xo, vy = entity.getY() - entity.yo, vz = entity.getZ() - entity.zo;
		double flat = Math.hypot(vx, vz);
		if (Float.isNaN(track.heading) && flat > HEADING_SPEED) {
			// the way it was thrown (a yaw, as the game reckons them: forward is -sin, cos)
			track.heading = (float) (Mth.atan2(-vx, vz) * Mth.RAD_TO_DEG);
		}
		if (track.landedAt < 0.0F && since >= MIN_AIR && (entity.onGround() || entity.isInWater() || entity.isInLava())) {
			track.landedAt = since;
		}
		float turn = Float.isNaN(track.heading) ? 0.0F : Mth.wrapDegrees(track.heading - bodyRot);
		float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (track.landedAt < 0.0F) {
			// in the air: turning over and over, limbs everywhere
			float flail = Math.min(1.0F, FLAIL + speed * FLAIL_PER_SPEED);
			float trail = TRAIL * Math.min(1.0F, speed * 3.0F);
			return new Flight(since, track.spinRate * since, turn, flail, trail);
		}
		float spun = track.spinRate * track.landedAt;
		float after = since - track.landedAt;
		float upright = 360.0F * Math.round(spun / 360.0F);
		if (track.feetFirst) {
			// light and quick: it rights itself and lands on its feet
			float t = after / (LAND_EASE + RISE * 0.5F);
			if (t >= 1.0F) {
				return null;
			}
			return new Flight(since, Mth.lerp(ease(t), spun, upright), turn, FLAIL * (1.0F - t), 0.0F);
		}
		// down on its side, front or back, whichever it came down nearest, then up again
		float lie = 180.0F * Math.round((spun - 90.0F) / 180.0F) + 90.0F;
		float rise = 360.0F * Math.round(lie / 360.0F);
		float tumble;
		if (after < LAND_EASE) {
			tumble = Mth.lerp(ease(after / LAND_EASE), spun, lie);
		} else if (after < LAND_EASE + LIE) {
			tumble = lie;
		} else if (after < LAND_EASE + LIE + RISE) {
			tumble = Mth.lerp(ease((after - LAND_EASE - LIE) / RISE), lie, rise);
		} else {
			return null;
		}
		float fade = Math.max(0.0F, 1.0F - after / (LAND_EASE + LIE * 0.5F));
		return new Flight(since, tumble, turn, FLAIL * fade, 0.0F);
	}

	private static float ease(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** After the body faces its way: the tumble, about its middle, around the side-to-side axis of the flight. */
	public static void rotate(Flight flight, LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		// into the flight's frame, over (a negative turn about X tips the top forward), and back
		poseStack.rotateDegrees(Axis.YP, -flight.turn());
		poseStack.rotateDegrees(Axis.XP, -flight.tumble());
		poseStack.rotateDegrees(Axis.YP, flight.turn());
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: every limb flails on its own beat and trails behind the turn. */
	public static void applyModel(Model<?> model, Flight flight) {
		ModelPart root = model.root();
		limbs(root, flight, 0);
		if (root.hasChild("body")) {
			limbs(root.getChild("body"), flight, LIMBS.length);
		}
	}

	private static void limbs(ModelPart parent, Flight flight, int offset) {
		float trail = flight.trail();
		for (int i = 0; i < LIMBS.length; i++) {
			if (!parent.hasChild(LIMBS[i])) {
				continue;
			}
			ModelPart limb = parent.getChild(LIMBS[i]);
			float phase = flight.since() * 1.1F + (i + offset) * 1.9F;
			float give = flight.flail() * (i == 0 ? 0.4F : 1.0F);
			limb.xRot += Mth.sin(phase) * give;
			limb.zRot += Mth.cos(phase * 1.3F) * give * 0.6F;
			String name = LIMBS[i];
			float side = name.startsWith("right") ? 1.0F : name.startsWith("left") ? -1.0F : 0.0F;
			if (name.equals("head")) {
				// lolled back against the turn
				limb.xRot -= trail * 0.4F;
			} else if (name.endsWith("arm") || name.endsWith("wing")) {
				// flung back and out
				limb.xRot += trail;
				limb.zRot += side * trail * 0.6F;
			} else if (name.endsWith("leg")) {
				// the feet lag behind as the body goes over, and splay
				limb.xRot -= trail * 0.5F;
				limb.zRot += side * trail * 0.3F;
			} else if (name.equals("tail")) {
				limb.xRot += trail;
			}
		}
	}
}
