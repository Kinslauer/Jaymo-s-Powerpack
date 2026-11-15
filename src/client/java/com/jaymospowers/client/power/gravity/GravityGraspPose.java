package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityOverturn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Overturn (GravityOverturn), drawn for everyone. An overturned creature is drawn upside down while it falls up. One
 * that resists ({@link GravityOverturn#GRASP}) throws itself down on its knees, bent low, both hands dug into the
 * ground, head straining up, and the pull tugs at it: its body lifts and shakes the whole time. When the pull gives up
 * it gets back up ({@link #GET_UP} ticks). The pose fits players and humanoid mobs; anything else only flips.
 */
public final class GravityGraspPose {
	/** Going down onto the ground, and getting back up (ticks). */
	static final float DOWN = 5.0F, GET_UP = 10.0F;
	/** The hips over the feet when standing (blocks). */
	static final float HIP = 0.75F;
	/** Fully down: how far it sinks (blocks), bends forward (degrees), the legs' swing back, the arms' reach and spread, the head. */
	static final float SINK = 0.55F, PITCH = 62.0F, LEG = 1.45F, ARM_X = -1.3F, ARM_Z = 0.28F, HEAD = -0.75F;
	/** The pull's tug: how far it lifts the body (blocks) and how hard it shakes. */
	static final float TUG = 0.06F, SHAKE = 0.07F;

	/** {@code down}: 0 standing to 1 fully down; {@code time}: game time, for the shaking. */
	public record Grasp(float down, float time) {
	}

	public static final RenderStateDataKey<Grasp> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_grasp");

	private static final class Track {
		float down;
		float releasedAt = -1.0F;
		float releasedDown;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private GravityGraspPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity.getAttached(GravityOverturn.FLIPPED) != null) {
			state.isUpsideDown = true;
		}
		Long since = entity.getAttached(GravityOverturn.GRASP);
		Track track = TRACKS.get(entity.getId());
		if (since == null && track == null || !humanoid(entity)) {
			state.setData(KEY, null);
			return;
		}
		float now = entity.level().getGameTime() + partialTicks;
		if (since != null) {
			if (track == null) {
				track = new Track();
				TRACKS.put(entity.getId(), track);
			}
			track.releasedAt = -1.0F;
			float p = Mth.clamp((now - since) / DOWN, 0.0F, 1.0F);
			// thrown down fast, onto the knees
			track.down = p * p;
			state.setData(KEY, new Grasp(track.down, now));
			return;
		}
		if (track.releasedAt < 0.0F) {
			track.releasedAt = now;
			track.releasedDown = track.down;
		}
		float up = Mth.clamp((now - track.releasedAt) / GET_UP, 0.0F, 1.0F);
		if (up >= 1.0F || entity.isRemoved()) {
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		track.down = track.releasedDown * (1.0F - up * up * (3.0F - 2.0F * up));
		state.setData(KEY, new Grasp(track.down, now));
	}

	private static boolean humanoid(LivingEntity entity) {
		return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?, ?> renderer
			&& renderer.getModel() instanceof HumanoidModel<?>;
	}

	/** After the body faces its way: down on the knees, bent low, the whole body tugged up and shaking. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Grasp grasp = state.getData(KEY);
		if (grasp == null || state.deathTime > 0.0F) {
			return;
		}
		float k = grasp.down();
		float sink = com.jaymospowers.client.KneelLegs.remap(SINK, LEG, LEG).sink();
		float tug = TUG * k * (0.6F + 0.4F * Mth.sin(grasp.time() * 0.9F));
		poseStack.translate(0.0F, -sink * k + tug, 0.0F);
		poseStack.translate(0.0F, HIP, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -PITCH * k);
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(grasp.time() * 2.9F) * 2.0F * k);
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** After the model animates: legs folded under, both arms reaching down into the ground, the head straining up. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Grasp grasp = living.getData(KEY);
		if (grasp == null) {
			return;
		}
		float k = Mth.clamp(grasp.down(), 0.0F, 1.0F);
		float pitch = PITCH * Mth.DEG_TO_RAD;
		com.jaymospowers.client.KneelLegs.Legs legs = com.jaymospowers.client.KneelLegs.remap(SINK, LEG, LEG);
		blend(h.rightLeg, legs.right() - pitch, 0.1F, 0.0F, k);
		blend(h.leftLeg, legs.left() - pitch, -0.1F, 0.0F, k);
		com.jaymospowers.client.KneelLegs.want(state, legs.rightKnee() * k, legs.leftKnee() * k);
		float shake = Mth.sin(grasp.time() * 2.3F) * SHAKE;
		blend(h.body, 0.0F, 0.0F, 0.0F, k);
		blend(h.rightArm, ARM_X + shake, 0.0F, ARM_Z, k);
		blend(h.leftArm, ARM_X - shake, 0.0F, -ARM_Z, k);
		h.head.xRot = Mth.lerp(k, h.head.xRot, HEAD);
		h.head.zRot += Mth.sin(grasp.time() * 1.7F) * 0.05F * k;
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
