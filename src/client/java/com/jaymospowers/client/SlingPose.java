package com.jaymospowers.client;

import com.jaymospowers.client.power.web.WebRagdoll;
import com.jaymospowers.core.Sling;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a body slung over a horse ({@link Sling}) looks, and the heave that puts it there. Every client holds the body at the
 * horse's feet each tick (the server does the same for a mob; a player's own client moves them, as in a carry), and here it's
 * drawn lying across the horse's back, face down, head to the side the carrier heaved from and legs to the other, arms and
 * legs hanging loose and swinging with the horse's stride. For {@link Sling#HEAVE_TICKS} after the heave it arcs up from the
 * carrier's side onto the back. The carrier plays the {@code heave} clip
 * ({@code geckolib/animations/entity/player_mount_heave.animation.json}, written by {@code tools/gen_mount_climb_clips.py})
 * through {@link KeyframeClip}.
 * <p>
 * Distances are blocks in the body's frame before the model is drawn (+X its right, +Y up, -Z its front), turned to the
 * horse's facing, as {@link CarryPose}'s are.
 */
public final class SlingPose {
	/** On a body slung over a horse: how it lies. */
	public static final RenderStateDataKey<Lying> LYING_KEY = RenderStateDataKey.create(() -> "jaymospowers:sling_lying");
	/** On whoever heaves: how far through the heave clip (0 to 1). */
	public static final RenderStateDataKey<Float> HEAVE_KEY = RenderStateDataKey.create(() -> "jaymospowers:sling_heave");

	/**
	 * {@code share}: how far through the heave (0 to 1). {@code side}: which side the carrier stood. {@code height}: the horse's.
	 * {@code time}: for the limbs' drift. {@code stride}, {@code pace}: the horse's walk cycle and speed, for their swing.
	 */
	public record Lying(float share, int side, float height, float time, float stride, float pace) {
	}

	/** Where the carrier holds it as the heave starts: out to their side and this high (blocks). */
	static final float START_SIDE = 0.9F, START_UP = 0.8F;
	/** The arc it rises over on the way up (blocks), and how far its belly sits above the middle of the horse's back. */
	static final float HEAVE_ARC = 0.7F, LIE_LIFT = 0.12F;
	/** Where a body's hips are, in its own frame (blocks): it lies across with these on the horse's back. */
	static final float HIP = 0.70F;
	/** The hanging limbs (radians, in the lying body's own frame): arms down over the head's side, legs down the other. */
	static final float ARMS_HANG = -1.75F, LEGS_HANG = -0.95F, HEAD_LOLL = -0.3F;
	/** How far the limbs drift loosely (radians), and swing with the horse's stride (radians per unit of its pace). */
	static final float LIMB_DRIFT = 0.07F, STRIDE_SWING = 0.5F;

	private static @Nullable KeyframeClip heave;
	private static boolean heaveLoaded;

	private SlingPose() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SlingPose::tick);
	}

	/** Every slung body is held at its horse's feet, this tick and last (so it's drawn moving with it). */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		for (Entity body : level.entitiesForRendering()) {
			Sling.Slung slung = body.getAttached(Sling.SLUNG);
			if (slung == null || !(level.getEntity(slung.horse()) instanceof Entity horse)) {
				continue;
			}
			body.setPos(horse.getX(), horse.getY(), horse.getZ());
			body.setOldPosAndRot(new Vec3(horse.xo, horse.yo, horse.zo), body.getYRot(), body.getXRot());
			body.setDeltaMovement(Vec3.ZERO);
			body.resetFallDistance();
			if (body instanceof LivingEntity living && horse instanceof LivingEntity mount) {
				living.yBodyRot = mount.yBodyRot;
				living.yBodyRotO = mount.yBodyRotO;
			}
		}
	}

	// ---- render state

	/** From the living and avatar renderers' extractRenderState, after the carry's (so the sling wins). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(LYING_KEY, null);
		state.setData(HEAVE_KEY, null);
		long now = entity.level().getGameTime();
		Long heaved = entity.getAttached(Sling.HEAVED);
		if (heaved != null && entity instanceof net.minecraft.world.entity.player.Player) {
			float share = (now - heaved + partialTicks) / Sling.HEAVE_TICKS;
			if (share >= 0.0F && share < 1.0F) {
				state.setData(HEAVE_KEY, share);
			}
		}
		Sling.Slung slung = entity.getAttached(Sling.SLUNG);
		if (slung == null || !(entity.level().getEntity(slung.horse()) instanceof LivingEntity horse)) {
			return;
		}
		float share = Mth.clamp((now - slung.at() + partialTicks) / Sling.HEAVE_TICKS, 0.0F, 1.0F);
		state.setData(LYING_KEY, new Lying(share, slung.side(), horse.getBbHeight(), entity.tickCount + partialTicks,
			horse.walkAnimation.position(partialTicks), horse.walkAnimation.speed(partialTicks)));
		// turned to the horse's facing, limp: no walking, crouching, swimming or anything else's pose
		state.bodyRot = Mth.rotLerp(partialTicks, horse.yBodyRotO, horse.yBodyRot);
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		state.pose = Pose.STANDING;
		state.isAutoSpinAttack = false;
		state.shadowRadius = 0.0F;
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = false;
			humanoid.swimAmount = 0.0F;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.isPassenger = false;
		}
		state.setData(FreefallPose.KEY, null);
		state.setData(WebRagdoll.KEY, null);
	}

	// ---- the whole body

	/** After the body faces its way (the horse's): across the horse's back, or arcing up onto it. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Lying lying = state.getData(LYING_KEY);
		if (lying == null) {
			return;
		}
		// laid out in world blocks: undo the body's own scale (a baby's, say) around it
		float scale = Math.max(entityScale, 0.01F);
		poseStack.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
		float t = lying.share();
		float eased = t * t * (3.0F - 2.0F * t);
		float x = (1.0F - eased) * lying.side() * START_SIDE;
		float y = Mth.lerp(eased, START_UP, lying.height() * Sling.BACK_HEIGHT + LIE_LIFT) + HEAVE_ARC * Mth.sin(t * Mth.PI);
		float z = eased * Sling.BACK_BEHIND;
		poseStack.translate(x, y, z);
		// across the back, face down, the head toward the side the carrier stood (+X its right, -Z its front)
		poseStack.rotateDegrees(Axis.YP, -lying.side() * 90.0F * eased);
		poseStack.rotateDegrees(Axis.XP, -90.0F * eased);
		poseStack.translate(0.0F, -HIP, 0.0F);
		poseStack.scale(scale, scale, scale);
	}

	// ---- limbs

	/** After the model animates: the slung body's loose limbs, the heaving carrier's clip. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof LivingEntityRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Lying lying = state.getData(LYING_KEY);
		if (lying != null) {
			hang(h, lying);
			return;
		}
		Float share = state.getData(HEAVE_KEY);
		KeyframeClip clip = share == null ? null : heaveClip();
		if (clip != null) {
			pose(h.body, clip, "body", share);
			pose(h.rightArm, clip, "right_arm", share);
			pose(h.leftArm, clip, "left_arm", share);
			pose(h.rightLeg, clip, "right_leg", share);
			pose(h.leftLeg, clip, "left_leg", share);
		}
	}

	/** Arms and legs hanging loose off both sides of the back, drifting a little and swinging with the horse's stride. */
	private static void hang(HumanoidModel<?> h, Lying lying) {
		float t = lying.time();
		float eased = lying.share() * lying.share() * (3.0F - 2.0F * lying.share());
		float swing = Mth.sin(lying.stride() * 0.6662F) * lying.pace() * STRIDE_SWING;
		float d0 = Mth.sin(t * 0.13F) * LIMB_DRIFT, d1 = Mth.sin(t * 0.11F + 1.7F) * LIMB_DRIFT;
		float d2 = Mth.sin(t * 0.09F + 3.1F) * LIMB_DRIFT, d3 = Mth.sin(t * 0.12F + 4.4F) * LIMB_DRIFT;
		set(h.rightArm, ARMS_HANG * eased + d0 + swing, 0.0F, 0.15F * eased + d2);
		set(h.leftArm, ARMS_HANG * eased + d1 - swing, 0.0F, -0.15F * eased - d3);
		set(h.rightLeg, LEGS_HANG * eased + d2 - swing, 0.0F, 0.1F * eased);
		set(h.leftLeg, LEGS_HANG * eased + d3 + swing, 0.0F, -0.1F * eased);
		set(h.head, HEAD_LOLL * eased + d0 * 0.5F, 0.0F, 0.0F);
		h.hat.xRot = h.head.xRot;
		h.hat.yRot = h.head.yRot;
		h.hat.zRot = h.head.zRot;
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	private static void pose(ModelPart part, KeyframeClip clip, String bone, float share) {
		if (clip.has(bone)) {
			part.xRot = clip.partX(bone, share);
			part.yRot = clip.partY(bone, share);
			part.zRot = clip.partZ(bone, share);
		}
	}

	private static @Nullable KeyframeClip heaveClip() {
		if (!heaveLoaded) {
			heaveLoaded = true;
			heave = KeyframeClip.load("jaymospowers", "player_mount_heave", "heave");
		}
		return heave;
	}
}
