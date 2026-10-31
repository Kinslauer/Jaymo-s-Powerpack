package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A web slinger's whole-body moves ({@link WebPower#POSE}), drawn for everyone: Spider-Sense's dodges (a spinning
 * sidestep, a backflip into a crouch, a duck and roll, a weave), the limp sprawl after a slingshot's floor gives
 * way, and the swing tricks (front flip, corkscrew, cartwheel, twirl, giant swing, superhero landing, landing roll,
 * the wings' flare and spin). Your own tricks don't show in first person: the camera would turn with them. The body turns about its middle ({@link #rotate}); the limbs tuck and reach to match ({@link #applyModel}).
 */
public final class WebBodyPose {
	/** The move, which way it goes, and how far through it (0-1). */
	public record Pose(int kind, int side, float p, float time) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_body_pose");

	private WebBodyPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		WebPose pose = entity.getAttached(WebPower.POSE);
		if (pose == null) {
			state.setData(KEY, null);
			return;
		}
		float time = entity.level().getGameTime() + partialTicks;
		float p = pose.progress(time);
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		boolean show = p >= 0.0F && p < 1.0F && !(ownView && WebPose.trick(pose.kind()));
		state.setData(KEY, show ? new Pose(pose.kind(), pose.side(), p, time) : null);
	}

	/** Eased 0-1-0 over the move: in fast, out slower. */
	private static float swell(float p) {
		float e = p < 0.3F ? p / 0.3F : 1.0F - (p - 0.3F) / 0.7F;
		return e * e * (3.0F - 2.0F * e);
	}

	/** The superhero landing eases down into its pose over this share of it (it used to snap down in a tenth). */
	static final float HERO_IN = 0.3F;

	/** Smoothstep of {@code x} clamped to 0-1. */
	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** 0 → 1 over {@code in}, held, then 1 → 0 from {@code out} to the end (eased). */
	private static float hold(float p, float in, float out) {
		return p < in ? ease(p / in) : p > out ? ease(1.0F - (p - out) / (1.0F - out)) : 1.0F;
	}

	/** After the body faces its way: the whole body spins, flips, rolls or sways about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		float p = pose.p();
		float turn = p * p * (3.0F - 2.0F * p);
		switch (pose.kind()) {
			case WebPose.SPIN -> {
				// a full pirouette, leaning out of it
				poseStack.rotateDegrees(Axis.YP, pose.side() * 360.0F * turn);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 15.0F * swell(p));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.BACKFLIP -> {
				// over backwards, landing low
				float flip = Math.min(1.0F, p / 0.8F);
				flip = flip * flip * (3.0F - 2.0F * flip);
				poseStack.translate(0.0F, middle + 0.6F * swell(p), 0.0F);
				poseStack.rotateDegrees(Axis.XP, 360.0F * flip);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.ROLL -> {
				// ducked, then tumbling over a shoulder along the ground
				float roll = Mth.clamp((p - 0.15F) / 0.7F, 0.0F, 1.0F);
				roll = roll * roll * (3.0F - 2.0F * roll);
				float low = swell(p);
				poseStack.translate(0.0F, middle * (1.0F - 0.55F * low), 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -pose.side() * 360.0F * roll);
				poseStack.rotateDegrees(Axis.XP, -35.0F * low);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.WEAVE -> {
				// a quick bob and weave: down and to one side, back through to the other
				float s = Mth.sin(p * Mth.TWO_PI);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 30.0F * s);
				poseStack.rotateDegrees(Axis.XP, -20.0F * swell(p));
				poseStack.translate(0.0F, -middle - 0.25F * swell(p), 0.0F);
			}
			case WebPose.FRONT_FLIP -> {
				// tucked, over forwards, popping up a little off the release
				poseStack.translate(0.0F, middle + 0.3F * swell(p), 0.0F);
				poseStack.rotateDegrees(Axis.XP, -360.0F * ease(p / 0.85F));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.BACK_FLIP -> {
				// tucked, over backwards in the air (Motion patch 0's air flip)
				poseStack.translate(0.0F, middle + 0.3F * swell(p), 0.0F);
				poseStack.rotateDegrees(Axis.XP, 360.0F * ease(p / 0.85F));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.CORKSCREW -> {
				// laid out flat ahead like a dive, twisting a full turn along the body
				float lay = Mth.sin(p * Mth.PI);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -70.0F * lay);
				poseStack.rotateDegrees(Axis.YP, pose.side() * 360.0F * turn);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.CARTWHEEL -> {
				// an aerial cartwheel: over sideways, arms and legs spread like a star
				poseStack.translate(0.0F, middle + 0.2F * swell(p), 0.0F);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 360.0F * ease(p / 0.9F));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.TWIRL -> {
				// still on the web: a full turn around the line, hanging from the hand
				float hand = middle * 1.9F;
				poseStack.translate(0.0F, hand, 0.0F);
				poseStack.rotateDegrees(Axis.YP, pose.side() * 360.0F * turn);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 12.0F * swell(p));
				poseStack.translate(0.0F, -hand, 0.0F);
			}
			case WebPose.GIANT -> {
				// a gymnast's giant swing: the body goes right round over the hand on the web
				float hand = middle * 2.2F;
				poseStack.translate(0.0F, hand, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -360.0F * turn);
				poseStack.translate(0.0F, -hand, 0.0F);
			}
			case WebPose.HERO_LAND -> {
				// slammed down onto one knee and a fist, head bowed, then up
				float low = hold(p, HERO_IN, 0.72F);
				float hips = middle * 0.85F;
				poseStack.translate(0.0F, -0.55F * low, 0.0F);
				poseStack.translate(0.0F, hips, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -28.0F * low);
				poseStack.translate(0.0F, -hips, 0.0F);
			}
			case WebPose.LAND_ROLL -> {
				// carried straight on into a forward roll along the ground, up at the end of it
				float low = swell(p);
				poseStack.translate(0.0F, middle * (1.0F - 0.55F * low), 0.0F);
				poseStack.rotateDegrees(Axis.XP, -360.0F * ease((p - 0.1F) / 0.75F));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.WINGS_LAND -> {
				// the wings flare (leaning back to brake), then a spin down into a crouch as they fold
				float flare = p < 0.3F ? ease(p / 0.15F) : ease(1.0F - (p - 0.3F) / 0.2F);
				float crouch = hold(p, 0.45F, 0.8F) * ease((p - 0.25F) / 0.2F);
				poseStack.translate(0.0F, -0.35F * crouch, 0.0F);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.YP, pose.side() * 360.0F * ease((p - 0.2F) / 0.5F));
				poseStack.rotateDegrees(Axis.XP, 30.0F * flare - 15.0F * crouch);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.ROAR -> {
				// hunched over and shuddering while it builds, then reared back as it roars, then upright again
				float p2 = pose.p();
				float hunch = roarHunch(p2), rear = roarRear(p2);
				float shudder = Mth.sin(pose.time() * 2.9F) * 2.5F * hunch;
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -22.0F * hunch + 18.0F * rear);
				poseStack.rotateDegrees(Axis.ZP, shudder);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.SPIKES -> {
				// the surge: dropped into a crouch, curled over, trembling as it gathers; then thrown open, chest out, leaning back
				float gather = spikeGather(p), burst = spikeBurst(p);
				float shudder = Mth.sin(pose.time() * 3.7F) * 2.0F * gather;
				poseStack.translate(0.0F, -0.3F * gather + 0.06F * burst, 0.0F);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -26.0F * gather + 14.0F * burst);
				poseStack.rotateDegrees(Axis.ZP, shudder);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.RAGDOLL -> {
				// flat on the back, a little twitch to it
				float fall = Math.min(1.0F, p * 6.0F);
				float twitch = Mth.sin(pose.time() * 0.8F) * 4.0F * (1.0F - p);
				poseStack.translate(0.0F, 0.2F * fall, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -90.0F * fall);
				poseStack.rotateDegrees(Axis.ZP, twitch);
			}
			default -> {
			}
		}
	}

	/** After the model animates: tucked for a flip or roll, arms out for the spin, limp for the ragdoll. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float e = swell(pose.p());
		switch (pose.kind()) {
			case WebPose.SPIN -> {
				set(h.rightArm, -0.3F, 0.0F, 1.3F, e);
				set(h.leftArm, -0.3F, 0.0F, -1.3F, e);
			}
			case WebPose.BACKFLIP, WebPose.ROLL -> {
				// knees to chest, arms hugging them
				set(h.rightLeg, -1.9F, 0.0F, 0.1F, e);
				set(h.leftLeg, -1.9F, 0.0F, -0.1F, e);
				set(h.rightArm, -1.6F, -0.3F, 0.2F, e);
				set(h.leftArm, -1.6F, 0.3F, -0.2F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, 0.6F);
			}
			case WebPose.WEAVE -> {
				// guard up
				set(h.rightArm, -1.4F, -0.4F, 0.2F, e);
				set(h.leftArm, -1.4F, 0.4F, -0.2F, e);
				set(h.rightLeg, -0.4F, 0.0F, 0.1F, e);
			}
			case WebPose.FRONT_FLIP, WebPose.BACK_FLIP, WebPose.LAND_ROLL -> {
				set(h.rightLeg, -1.9F, 0.0F, 0.1F, e);
				set(h.leftLeg, -1.9F, 0.0F, -0.1F, e);
				set(h.rightArm, -1.6F, -0.3F, 0.2F, e);
				set(h.leftArm, -1.6F, 0.3F, -0.2F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, 0.6F);
			}
			case WebPose.CORKSCREW -> {
				// arms crossed tight on the chest, legs straight together: the twist goes faster for it
				set(h.rightArm, -1.3F, -0.9F, 0.0F, e);
				set(h.leftArm, -1.3F, 0.9F, 0.0F, e);
				set(h.rightLeg, 0.1F, 0.0F, 0.02F, e);
				set(h.leftLeg, 0.1F, 0.0F, -0.02F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, -0.4F);
			}
			case WebPose.CARTWHEEL -> {
				// a star: arms up and out, legs split
				set(h.rightArm, 0.0F, 0.0F, 2.5F, e);
				set(h.leftArm, 0.0F, 0.0F, -2.5F, e);
				set(h.rightLeg, 0.0F, 0.0F, 0.7F, e);
				set(h.leftLeg, 0.0F, 0.0F, -0.7F, e);
			}
			case WebPose.TWIRL -> {
				// one knee drawn up, the other leg pointed (the arms stay on the web)
				set(h.rightLeg, -1.2F, 0.0F, 0.15F, e);
				set(h.leftLeg, 0.2F, 0.0F, -0.05F, e);
			}
			case WebPose.GIANT -> {
				// straight through the bottom, tucked over the top
				float tuck = Mth.sin(pose.p() * Mth.PI);
				set(h.rightLeg, -1.6F * tuck, 0.0F, 0.05F, e);
				set(h.leftLeg, -1.6F * tuck, 0.0F, -0.05F, e);
			}
			case WebPose.HERO_LAND -> {
				float low = hold(pose.p(), HERO_IN, 0.72F);
				// right knee down behind, left foot planted ahead
				set(h.rightLeg, 1.35F, 0.0F, 0.1F, low);
				set(h.leftLeg, -1.35F, 0.0F, -0.15F, low);
				// right fist on the ground, left arm out behind for balance
				set(h.rightArm, -0.25F, 0.0F, 0.15F, low);
				set(h.leftArm, 0.7F, 0.0F, -0.6F, low);
				// head bowed at the impact, looking up before rising
				h.head.xRot = Mth.lerp(low, h.head.xRot, pose.p() < 0.45F ? 0.7F : -0.2F);
			}
			case WebPose.WINGS_LAND -> {
				float p = pose.p();
				float flare = p < 0.3F ? ease(p / 0.15F) : ease(1.0F - (p - 0.3F) / 0.2F);
				float crouch = hold(p, 0.45F, 0.8F) * ease((p - 0.25F) / 0.2F);
				// wings thrown up and wide to catch the air, then wrapped down and in as you spin
				set(h.rightArm, 0.2F, 0.0F, 2.3F, flare);
				set(h.leftArm, 0.2F, 0.0F, -2.3F, flare);
				set(h.rightArm, 0.5F, 0.0F, 0.9F, crouch * (1.0F - flare));
				set(h.leftArm, 0.5F, 0.0F, -0.9F, crouch * (1.0F - flare));
				set(h.rightLeg, -1.1F, 0.0F, 0.2F, crouch);
				set(h.leftLeg, 0.6F, 0.0F, -0.2F, crouch);
			}
			case WebPose.ROAR -> {
				float p = pose.p();
				float hunch = roarHunch(p), rear = roarRear(p);
				float t = pose.time();
				// clutching in, trembling, as the suit crawls over you
				float tremble = Mth.sin(t * 3.1F) * 0.12F;
				set(h.rightArm, -0.9F + tremble, -0.5F, 0.35F, hunch);
				set(h.leftArm, -0.9F - tremble, 0.5F, -0.35F, hunch);
				h.head.xRot = Mth.lerp(hunch, h.head.xRot, 0.55F);
				// then flung wide and back, head thrown back, legs braced
				set(h.rightArm, 0.45F, 0.0F, 1.7F, rear);
				set(h.leftArm, 0.45F, 0.0F, -1.7F, rear);
				set(h.rightLeg, 0.0F, 0.0F, 0.32F, Math.max(hunch * 0.5F, rear));
				set(h.leftLeg, 0.0F, 0.0F, -0.32F, Math.max(hunch * 0.5F, rear));
				h.head.xRot = Mth.lerp(rear, h.head.xRot, -0.75F);
			}
			case WebPose.SPIKES -> {
				// gathering: arms crossed tight over the chest, knees bent, head down, trembling;
				// the burst: arms flung out wide and up, legs braced apart, head thrown back
				float gather = spikeGather(pose.p()), burst = spikeBurst(pose.p());
				float tremble = Mth.sin(pose.time() * 3.3F) * 0.1F * gather;
				set(h.rightArm, -1.5F + tremble, -0.9F, 0.2F, gather);
				set(h.leftArm, -1.5F - tremble, 0.9F, -0.2F, gather);
				set(h.rightLeg, -0.7F, 0.0F, 0.15F, gather);
				set(h.leftLeg, -0.7F, 0.0F, -0.15F, gather);
				h.head.xRot = Mth.lerp(gather, h.head.xRot, 0.6F);
				set(h.rightArm, -0.5F, 0.0F, 1.9F, burst);
				set(h.leftArm, -0.5F, 0.0F, -1.9F, burst);
				set(h.rightLeg, 0.0F, 0.0F, 0.35F, burst);
				set(h.leftLeg, 0.0F, 0.0F, -0.35F, burst);
				h.head.xRot = Mth.lerp(burst, h.head.xRot, -0.6F);
			}
			case WebPose.RAGDOLL -> {
				float t = pose.time();
				float give = 1.0F - pose.p() * 0.7F;
				h.rightArm.xRot = -0.4F + Mth.sin(t * 0.9F) * 0.5F * give;
				h.rightArm.zRot = 1.0F;
				h.leftArm.xRot = -0.3F + Mth.sin(t * 0.9F + 2.0F) * 0.5F * give;
				h.leftArm.zRot = -1.0F;
				h.rightLeg.xRot = 0.1F + Mth.sin(t * 0.7F + 1.0F) * 0.3F * give;
				h.rightLeg.zRot = 0.3F;
				h.leftLeg.xRot = 0.1F + Mth.sin(t * 0.7F + 3.0F) * 0.3F * give;
				h.leftLeg.zRot = -0.3F;
				h.head.xRot = 0.3F;
			}
			default -> {
			}
		}
	}

	/** Spike Burst's gather (curled in until it bursts) and burst (thrown open, easing back by the end). */
	private static float spikeGather(float p) {
		float g = com.jaymospowers.power.web.WebSymbiote.SPIKE_GATHER_TICKS / (float)com.jaymospowers.power.web.WebSymbiote.SPIKE_POSE_TICKS;
		return p < g ? ease(p / g) : 1.0F - ease((p - g) / 0.05F);
	}

	private static float spikeBurst(float p) {
		float g = com.jaymospowers.power.web.WebSymbiote.SPIKE_GATHER_TICKS / (float)com.jaymospowers.power.web.WebSymbiote.SPIKE_POSE_TICKS;
		return p < g ? 0.0F : hold((p - g) / (1.0F - g), 0.06F, 0.6F);
	}

	/** A roar's two halves: hunched while it builds (to just before it roars), and reared back through the roar. */
	private static float roarHunch(float p) {
		float blast = com.jaymospowers.power.web.WebSymbiote.ROAR_BLAST_AT / (float)com.jaymospowers.power.web.WebSymbiote.ROAR_TICKS;
		return p < blast - 0.06F ? ease(p / 0.15F) : ease(1.0F - (p - blast + 0.06F) / 0.06F);
	}

	private static float roarRear(float p) {
		float blast = com.jaymospowers.power.web.WebSymbiote.ROAR_BLAST_AT / (float)com.jaymospowers.power.web.WebSymbiote.ROAR_TICKS;
		return p < blast - 0.06F ? 0.0F : p < 0.85F ? ease((p - blast + 0.06F) / 0.06F) : ease((1.0F - p) / 0.15F);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
