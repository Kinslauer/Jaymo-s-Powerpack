package com.jaymospowers.client.wounds;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Tending;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.15.96) A player treating someone ({@link Tending}), drawn for everyone: they drop onto one knee (the left), right
 * foot planted, and lean in with both hands at the wound, working at it. Over someone lying down (downed, or anything
 * short) they bend well forward and reach down; over someone standing they kneel upright and reach ahead. When the work
 * ends they get back up.
 */
public final class TendPose {
	/** Down onto the knee, and back up (ticks). */
	static final float KNEEL_IN = 7.0F, GET_UP = 8.0F;
	/** How far the body sinks onto the knee (blocks); the hips over the feet standing (blocks). */
	static final float SINK = 0.42F, HIP = 0.75F;
	/** The lean forward from the hips (degrees): over someone lying down, and over someone standing. */
	static final float LEAN_LOW = 32.0F, LEAN_HIGH = 6.0F;
	/** The arms' swing forward, relative to the leaning body (radians): reaching down, and reaching ahead. */
	static final float REACH_LOW = -0.45F, REACH_HIGH = -1.3F;
	/** Hands at work: how fast (radians a tick) and how far (radians) they move in turn. */
	static final float WORK_RATE = 0.45F, WORK_AMOUNT = 0.14F;
	/** Under this height (blocks) the one they treat counts as lying down. */
	static final float LOW_HEIGHT = 1.0F;

	/** {@code down}: 0 standing to 1 on the knee; {@code low}: 0 reaching ahead to 1 reaching down; {@code time}: ticks. */
	public record Tend(float down, float low, float time) {
	}

	public static final RenderStateDataKey<Tend> KEY = RenderStateDataKey.create(() -> "jaymospowers:tending");

	/** Each healer's last pose (to get up from) and when the work ended (game time, or -1). */
	private static final class Track {
		float down;
		float low;
		float releasedAt = -1.0F;
		float releasedDown;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private TendPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Tending.Tend tend = entity.getAttached(Tending.TEND);
		Track track = TRACKS.get(entity.getId());
		if (tend == null && track == null) {
			state.setData(KEY, null);
			return;
		}
		float now = entity.level().getGameTime() + partialTicks;
		if (tend != null) {
			if (track == null) {
				track = new Track();
				TRACKS.put(entity.getId(), track);
			}
			track.releasedAt = -1.0F;
			track.down = smooth((now - tend.since()) / KNEEL_IN);
			Entity target = entity.level().getEntity(tend.target());
			float low = target instanceof LivingEntity body && (Downed.isDowned(body) || body.getBbHeight() < LOW_HEIGHT) ? 1.0F : 0.0F;
			track.low = Mth.lerp(0.2F, track.low, low);
			state.setData(KEY, new Tend(track.down, track.low, now));
			return;
		}
		// done: back up, from however far down they were
		if (track.releasedAt < 0.0F) {
			track.releasedAt = now;
			track.releasedDown = track.down;
		}
		float up = (now - track.releasedAt) / GET_UP;
		if (up >= 1.0F || entity.isRemoved()) {
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		track.down = track.releasedDown * (1.0F - smooth(up));
		state.setData(KEY, new Tend(track.down, track.low, now));
	}

	/** After the body faces its way: sunk onto the knee, leaning in from the hips. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Tend tend = state.getData(KEY);
		if (tend == null || state.deathTime > 0.0F) {
			return;
		}
		poseStack.translate(0.0F, -SINK * tend.down(), 0.0F);
		poseStack.translate(0.0F, HIP, 0.0F);
		poseStack.rotate(Axis.XP.rotationDegrees(-lean(tend) * tend.down()));
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** After the model animates: right foot planted, left knee down, both hands reaching in and working. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		Tend tend = state.getData(KEY);
		if (tend == null) {
			return;
		}
		float k = Mth.clamp(tend.down(), 0.0F, 1.0F);
		float pitch = lean(tend) * Mth.DEG_TO_RAD;
		// the legs as the world sees them, so they stay planted under the lean
		blend(h.rightLeg, -1.15F - pitch, 0.1F, 0.0F, k);
		blend(h.leftLeg, 1.35F - pitch, -0.1F, 0.0F, k);
		blend(h.body, 0.0F, 0.0F, 0.0F, k);
		float reach = Mth.lerp(tend.low(), REACH_HIGH, REACH_LOW);
		float work = Mth.sin(tend.time() * WORK_RATE) * WORK_AMOUNT;
		// hands together at the wound, one then the other
		blend(h.rightArm, reach + work, -0.3F, 0.05F, k);
		blend(h.leftArm, reach - work, 0.3F, -0.05F, k);
	}

	private static float lean(Tend tend) {
		return Mth.lerp(tend.low(), LEAN_HIGH, LEAN_LOW);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
