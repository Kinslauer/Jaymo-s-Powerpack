package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebCounter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The body's side of every Spider-Sense counter ({@link WebCounter}), drawn for everyone from the synced
 * {@link WebCounter#ACTIVE}: the web slinger's spinning heel kick, uppercut, overhead web slam, jabs and cross, flip kick,
 * cocoon heave, flying zip kick, low sweep and haymaker; and in the symbiote's suit the lunges, whips, heaves and
 * squeezes that go with its tendrils, and the head thrust forward (or thrown back roaring) for the maw. Each one's
 * beats line up with the server's ({@link WebCounter#ticks}, the hit ticks in {@code WebCounter.step}). Your own
 * whole-body turns don't show in first person (the limbs still move).
 *
 * <p>The symbiote's tendril slams (PS5 style, {@link WebCounter#bundle}) sell the bundle with the whole body: the lead
 * arm cocked back and thrown at it as the bundle whips out, both arms heaving through the haul (up and over for Impale
 * and Hurl, wrenched round for Whiplash), the body driven down into the slam, then easing back as the bundle recoils.
 */
public final class WebCounterPose {
	/** The counter, which way it goes, how many ticks in, and whether it's your own seen from your own eyes. */
	public record Pose(int kind, int side, float t, boolean ownView) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_counter_pose");

	private WebCounterPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		WebCounter.Active active = entity.getAttached(WebCounter.ACTIVE);
		if (active == null) {
			state.setData(KEY, null);
			return;
		}
		float t = entity.level().getGameTime() - active.start() + partialTicks;
		if (t < 0.0F || t >= WebCounter.ticks(active.kind())) {
			state.setData(KEY, null);
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		state.setData(KEY, new Pose(active.kind(), (active.start() & 1L) == 0L ? 1 : -1, t, ownView));
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** 0 → 1 over {@code in} ticks up to {@code at}, then 1 → 0 over {@code out} ticks after it. */
	private static float bump(float t, float at, float in, float out) {
		return t < at ? ease(1.0F - (at - t) / in) : ease(1.0F - (t - at) / out);
	}

	/** 0 → 1 over the ticks from {@code from} to {@code to}. */
	private static float ramp(float t, float from, float to) {
		return ease((t - from) / (to - from));
	}

	/** In over the first {@code in} ticks, out over the last {@code out}: how much of the counter shows. */
	private static float envelope(Pose pose, float in, float out) {
		float end = WebCounter.ticks(pose.kind());
		return Math.min(ease(pose.t() / in), ease((end - pose.t()) / out));
	}

	/** After the body faces its way: whole-body spins, flips, leans and drops. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F || pose.ownView()) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		float hips = middle * 0.9F;
		float t = pose.t();
		int side = pose.side();
		float env = envelope(pose, 2.0F, 3.0F);
		switch (pose.kind()) {
			case WebCounter.HEEL_KICK -> {
				// a full spin on the planted foot, leaning away from the kicking leg
				poseStack.rotateDegrees(Axis.YP, side * 360.0F * ramp(t, 0.0F, 7.0F));
				lean(poseStack, hips, 0.0F, side * 18.0F * bump(t, 4.0F, 3.0F, 4.0F));
			}
			case WebCounter.YANK_UPPERCUT -> {
				// down low while it comes in, then up and leaning back off the uppercut, off the ground
				float low = bump(t, 4.0F, 3.0F, 3.0F), up = bump(t, 7.0F, 2.0F, 6.0F);
				poseStack.translate(0.0F, -0.25F * low + 0.35F * up, 0.0F);
				lean(poseStack, hips, -20.0F * low + 22.0F * up, side * 10.0F * up);
			}
			case WebCounter.WEB_SLAM -> {
				// leaning back to heave it up, then bent forward as it comes down
				float heave = bump(t, 9.0F, 7.0F, 3.0F), slam = bump(t, 12.0F, 3.0F, 4.0F);
				lean(poseStack, hips, 18.0F * heave - 35.0F * slam, 0.0F);
				poseStack.translate(0.0F, -0.2F * slam, 0.0F);
			}
			case WebCounter.BLIND_JABS -> {
				// bouncing on the toes, shoulders turning with each jab, the cross thrown from the hip
				float bob = Mth.sin(t * 1.05F) * 0.04F * env;
				poseStack.translate(0.0F, bob, 0.0F);
				float twist = jabTwist(t) + side * 25.0F * bump(t, 12.0F, 2.0F, 3.0F);
				poseStack.rotateDegrees(Axis.YP, twist * env);
				lean(poseStack, hips, -10.0F * env, 0.0F);
			}
			case WebCounter.FLIP_KICK -> {
				// up and over forwards, the legs whipping down onto it at the top
				poseStack.translate(0.0F, middle + 0.45F * Mth.sin(Mth.clamp(t / 13.0F, 0.0F, 1.0F) * Mth.PI), 0.0F);
				poseStack.rotateDegrees(Axis.XP, -360.0F * ease(t / 12.0F));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebCounter.COCOON_THROW -> {
				// turning as it spins the cocoon, then a full-body heave across
				float wind = ramp(t, 0.0F, 7.0F) * (1.0F - ramp(t, 8.0F, 10.0F));
				float heave = bump(t, 9.0F, 2.0F, 6.0F);
				poseStack.rotateDegrees(Axis.YP, side * (40.0F * wind - 55.0F * heave));
				lean(poseStack, hips, -15.0F * heave + 6.0F * Mth.sin(t * 1.4F) * wind, 0.0F);
			}
			case WebCounter.ZIP_KICK -> {
				// flying in feet first: laid back along the line, the kick out ahead
				float fly = Math.min(ramp(t, 0.0F, 3.0F), 1.0F - ramp(t, 11.0F, 14.0F));
				lean(poseStack, middle, 50.0F * fly, side * 8.0F * fly);
			}
			case WebCounter.TRIP_SWEEP -> {
				// dropped low onto one hand, spinning round with a leg out along the ground
				float low = Math.min(ramp(t, 0.0F, 2.0F), 1.0F - ramp(t, 7.0F, 10.0F));
				poseStack.translate(0.0F, -0.6F * low, 0.0F);
				poseStack.rotateDegrees(Axis.YP, side * 360.0F * ramp(t, 0.0F, 8.0F));
				lean(poseStack, hips, -25.0F * low, side * 20.0F * low);
			}
			case WebCounter.PINNED_HAYMAKER -> {
				// coiled right back, then the whole body turns through the punch
				float coil = ramp(t, 2.0F, 7.0F) * (1.0F - ramp(t, 7.0F, 9.0F));
				float swing = bump(t, 8.5F, 1.5F, 6.0F);
				poseStack.rotateDegrees(Axis.YP, side * (40.0F * coil - 35.0F * swing));
				lean(poseStack, hips, 8.0F * coil - 22.0F * swing, 0.0F);
			}
			case WebCounter.IMPALE -> {
				// twisted back as the arm cocks, thrown at it, reared up tall heaving it into the air, then driven down into the slam
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float heave = bump(t, 10.0F, 3.0F, 2.0F), slam = bump(t, 12.5F, 1.5F, 6.0F);
				poseStack.rotateDegrees(Axis.YP, side * (30.0F * wind - 20.0F * cast));
				lean(poseStack, hips, 6.0F * wind - 12.0F * cast + 20.0F * heave - 38.0F * slam, side * 6.0F * slam);
				poseStack.translate(0.0F, 0.08F * heave - 0.25F * slam, 0.0F);
			}
			case WebCounter.SPEAR_FLURRY -> {
				// coiled back, then driving down and forward behind each stab
				float wind = bump(t, 4.0F, 4.0F, 3.0F);
				float drive = stab(t);
				poseStack.rotateDegrees(Axis.YP, side * 25.0F * wind);
				lean(poseStack, hips, 8.0F * wind - 32.0F * drive, side * 6.0F * drive);
				poseStack.translate(0.0F, -0.15F * drive, 0.0F);
			}
			case WebCounter.WHIPLASH -> {
				// twisted back, the arm thrown at it, then wrenched round the way it swings it, and leaning into the slam
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float swing = ramp(t, 7.0F, 12.0F) * (1.0F - ramp(t, 13.0F, 19.0F));
				float slam = bump(t, 12.5F, 1.5F, 6.0F);
				poseStack.rotateDegrees(Axis.YP, side * (30.0F * wind - 15.0F * cast - 70.0F * swing));
				lean(poseStack, hips, -10.0F * cast - 25.0F * slam, -side * 18.0F * slam);
				poseStack.translate(0.0F, -0.15F * slam, 0.0F);
			}
			case WebCounter.HURL -> {
				// the arm thrown at it, hunched to heave, arching further and further back as it goes up over your head,
				// then whipped right back over as it slams down behind you
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float heave = ramp(t, 8.0F, 15.0F) * (1.0F - ramp(t, 18.0F, 26.0F));
				float slam = bump(t, 17.5F, 2.0F, 7.0F);
				poseStack.rotateDegrees(Axis.YP, side * (25.0F * wind - 15.0F * cast + 20.0F * heave));
				lean(poseStack, hips, -12.0F * cast - 10.0F * bump(t, 8.0F, 2.0F, 3.0F) + 22.0F * heave + 18.0F * slam, -side * 8.0F * slam);
				poseStack.translate(0.0F, -0.15F * slam, 0.0F);
			}
			case WebCounter.CONSTRICT -> {
				// hunched over what it holds, shuddering with every squeeze, then heaved up and driven down into the slam
				float hold = envelope(pose, 6.0F, 5.0F);
				float squeeze = squeezes(t);
				float heave = bump(t, 25.0F, 2.0F, 2.0F), slam = bump(t, 27.5F, 1.5F, 5.0F);
				lean(poseStack, hips, -25.0F * hold * (1.0F - heave) - 8.0F * squeeze + 12.0F * heave - 20.0F * slam,
					Mth.sin(t * 2.7F) * 3.0F * squeeze);
				poseStack.translate(0.0F, -0.12F * hold - 0.15F * slam, 0.0F);
			}
			case WebCounter.JAW_SNAP -> {
				// the head lunges and the body follows it
				float lunge = bump(t, 8.0F, 5.0F, 8.0F);
				lean(poseStack, hips, -30.0F * lunge, 0.0F);
			}
			case WebCounter.MAUL -> {
				// on top of it: hunched in, wrenching side to side, then twisting to toss it
				float on = envelope(pose, 5.0F, 5.0F);
				float worry = t > 8.0F && t < 22.0F ? Mth.sin(t * 0.9F) * 10.0F : 0.0F;
				float toss = bump(t, 22.0F, 3.0F, 5.0F);
				lean(poseStack, hips, -28.0F * on, worry * on);
				poseStack.rotateDegrees(Axis.YP, side * 35.0F * toss);
			}
			case WebCounter.MAW_ROAR -> {
				// thrown back roaring, then lunging in to bite
				float roar = Math.min(ramp(t, 1.0F, 4.0F), 1.0F - ramp(t, 9.0F, 12.0F));
				float bite = bump(t, 16.0F, 4.0F, 6.0F);
				float shake = Mth.sin(t * 3.3F) * 2.0F * roar;
				lean(poseStack, hips, 22.0F * roar - 30.0F * bite, shake);
			}
			case WebCounter.DRAG_TO_JAWS -> {
				// reaching out, leaning back to haul it in, then onto it with the jaws
				float reach = bump(t, 6.0F, 5.0F, 4.0F), haul = bump(t, 13.0F, 4.0F, 4.0F), bite = bump(t, 17.0F, 2.0F, 6.0F);
				lean(poseStack, hips, -18.0F * reach + 18.0F * haul - 32.0F * bite, 0.0F);
			}
			default -> {
			}
		}
	}

	/** Turns the body about a pivot {@code height} up: {@code pitch} forward (−) or back (+), {@code roll} to the side. */
	private static void lean(PoseStack poseStack, float height, float pitch, float roll) {
		poseStack.translate(0.0F, height, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -pitch);
		poseStack.rotateDegrees(Axis.ZP, roll);
		poseStack.translate(0.0F, -height, 0.0F);
	}

	/** Blinding Jabs: the shoulders turn into each of the three jabs, right, left, right. */
	private static float jabTwist(float t) {
		return -18.0F * bump(t, 3.0F, 1.5F, 1.5F) + 18.0F * bump(t, 6.0F, 1.5F, 1.5F) - 18.0F * bump(t, 9.0F, 1.5F, 1.5F);
	}

	/** Spear Flurry: driving in behind each of its three stabs. */
	private static float stab(float t) {
		return Math.max(bump(t, 9.0F, 3.0F, 3.0F), Math.max(bump(t, 16.0F, 3.0F, 3.0F), bump(t, 23.0F, 3.0F, 5.0F)));
	}

	/** Constrict: each squeeze (every 8 ticks from the coils closing). */
	private static float squeezes(float t) {
		if (t < 9.0F) {
			return 0.0F;
		}
		float k = (t - 9.0F) % 8.0F;
		return k < 2.0F ? ease(k / 2.0F) : ease(1.0F - (k - 2.0F) / 5.0F);
	}

	/** After the model animates: arms, legs and head through each move. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float t = pose.t();
		int side = pose.side();
		float e = envelope(pose, 2.0F, 3.0F);
		// the lead arm and leg (the side the move goes), and the others
		ModelPart arm = side > 0 ? h.rightArm : h.leftArm, other = side > 0 ? h.leftArm : h.rightArm;
		ModelPart leg = side > 0 ? h.rightLeg : h.leftLeg, plant = side > 0 ? h.leftLeg : h.rightLeg;
		float out = side > 0 ? 1.0F : -1.0F;
		switch (pose.kind()) {
			case WebCounter.HEEL_KICK -> {
				// arms flung out for the spin, the kicking leg swung up and out at head height
				float kick = bump(t, 4.0F, 3.0F, 4.0F);
				set(h.rightArm, -0.4F, 0.0F, 1.2F, e);
				set(h.leftArm, -0.4F, 0.0F, -1.2F, e);
				set(leg, -1.5F, 0.0F, out * 1.1F, kick);
				set(plant, 0.15F, 0.0F, -out * 0.1F, e);
			}
			case WebCounter.YANK_UPPERCUT -> {
				// the web arm out, then yanked back; the other fist low, then driven up past the chin
				float yank = bump(t, 2.0F, 2.0F, 4.0F);
				float cut = bump(t, 6.5F, 3.0F, 6.0F);
				float load = bump(t, 4.0F, 2.0F, 2.0F);
				set(other, -1.5F + 1.0F * ramp(t, 2.0F, 5.0F), 0.0F, 0.0F, yank);
				set(arm, Mth.lerp(cut, 0.5F, -2.9F), 0.0F, out * 0.1F, Math.max(load, cut));
				set(h.rightLeg, -0.6F, 0.0F, 0.1F, load);
				set(h.leftLeg, -0.6F, 0.0F, -0.1F, load);
				h.head.xRot = Mth.lerp(cut, h.head.xRot, -0.4F);
			}
			case WebCounter.WEB_SLAM -> {
				// both arms out at it, heaved up overhead, then hauled down
				float shoot = Math.min(ramp(t, 0.0F, 2.0F), 1.0F - ramp(t, 4.0F, 7.0F));
				float heave = bump(t, 9.0F, 5.0F, 2.0F), slam = bump(t, 12.0F, 2.5F, 4.0F);
				for (ModelPart a : new ModelPart[] {h.rightArm, h.leftArm}) {
					float s = a == h.rightArm ? 1.0F : -1.0F;
					set(a, -1.5F, 0.0F, s * 0.1F, shoot);
					set(a, -3.0F, 0.0F, s * 0.25F, heave);
					set(a, -0.6F, 0.0F, s * 0.2F, slam);
				}
				set(h.rightLeg, -0.4F, 0.0F, 0.2F, slam);
				set(h.leftLeg, 0.3F, 0.0F, -0.2F, slam);
			}
			case WebCounter.BLIND_JABS -> {
				// guard up; the web shot, three jabs and the cross straight out from it
				float shot = bump(t, 1.0F, 1.0F, 2.0F);
				set(h.rightArm, -1.4F, -0.5F, 0.0F, e);
				set(h.leftArm, -1.4F, 0.5F, 0.0F, e);
				set(h.leftArm, -1.6F, 0.0F, 0.0F, shot);
				set(h.rightArm, -1.6F, 0.05F, 0.0F, Math.max(bump(t, 3.0F, 1.5F, 1.5F), bump(t, 9.0F, 1.5F, 1.5F)));
				set(h.leftArm, -1.6F, -0.05F, 0.0F, bump(t, 6.0F, 1.5F, 1.5F));
				set(arm, -1.65F, out * -0.3F, 0.0F, bump(t, 12.0F, 2.0F, 3.0F));
				set(h.rightLeg, -0.35F, 0.0F, 0.08F, e);
				set(h.leftLeg, 0.25F, 0.0F, -0.08F, e);
			}
			case WebCounter.FLIP_KICK -> {
				// tucked through the first half of the flip, then both legs shoot out at it
				float tuck = Math.min(ramp(t, 0.0F, 2.0F), 1.0F - ramp(t, 5.0F, 7.0F));
				float kick = bump(t, 7.0F, 2.0F, 5.0F);
				set(h.rightLeg, -1.9F, 0.0F, 0.1F, tuck);
				set(h.leftLeg, -1.9F, 0.0F, -0.1F, tuck);
				set(h.rightArm, -1.6F, -0.3F, 0.2F, tuck);
				set(h.leftArm, -1.6F, 0.3F, -0.2F, tuck);
				set(h.rightLeg, -0.3F, 0.0F, 0.05F, kick);
				set(h.leftLeg, -0.3F, 0.0F, -0.05F, kick);
				set(h.rightArm, 0.2F, 0.0F, 1.6F, kick);
				set(h.leftArm, 0.2F, 0.0F, -1.6F, kick);
			}
			case WebCounter.COCOON_THROW -> {
				// both hands turning over each other spinning the web, then swung across together to throw
				float spin = ramp(t, 0.0F, 2.0F) * (1.0F - ramp(t, 7.0F, 9.0F));
				float heave = bump(t, 9.0F, 2.0F, 6.0F);
				set(h.rightArm, -1.4F + Mth.sin(t * 1.6F) * 0.45F, -0.2F, 0.0F, spin);
				set(h.leftArm, -1.4F - Mth.sin(t * 1.6F) * 0.45F, 0.2F, 0.0F, spin);
				set(h.rightArm, -2.2F, out * -0.8F, 0.0F, heave);
				set(h.leftArm, -2.2F, out * -0.8F, 0.0F, heave);
				set(leg, -0.5F, 0.0F, 0.0F, heave);
			}
			case WebCounter.ZIP_KICK -> {
				// the zip hand out ahead on the line, the kicking leg straight out, the other knee drawn up
				float fly = Math.min(ramp(t, 0.0F, 3.0F), 1.0F - ramp(t, 11.0F, 14.0F));
				set(other, -2.6F, 0.0F, -out * 0.15F, fly);
				set(arm, 0.4F, 0.0F, out * 0.6F, fly);
				set(leg, -1.45F, 0.0F, out * 0.05F, fly);
				set(plant, -1.9F, 0.0F, -out * 0.1F, fly * 0.8F);
				h.head.xRot = Mth.lerp(fly, h.head.xRot, -0.5F);
			}
			case WebCounter.TRIP_SWEEP -> {
				// one hand down on the ground, the sweeping leg straight out to the side, the other knee deep
				float low = Math.min(ramp(t, 0.0F, 2.0F), 1.0F - ramp(t, 7.0F, 10.0F));
				set(other, -0.4F, 0.0F, -out * 0.5F, low);
				set(arm, -0.3F, 0.0F, out * 1.2F, low);
				set(leg, -0.2F, 0.0F, out * 1.45F, low);
				set(plant, -1.6F, 0.0F, -out * 0.2F, low);
			}
			case WebCounter.PINNED_HAYMAKER -> {
				// the web hand pointing it down, the punching arm drawn far back, then round through it
				float shot = bump(t, 1.0F, 1.0F, 3.0F);
				float coil = ramp(t, 2.0F, 7.0F) * (1.0F - ramp(t, 7.0F, 8.5F));
				float swing = bump(t, 8.5F, 1.5F, 6.0F);
				set(other, -1.0F, 0.0F, 0.0F, shot);
				set(other, -1.2F, out * 0.6F, 0.0F, Math.max(coil, swing) * 0.8F);
				set(arm, 0.6F, out * 0.4F, out * 0.6F, coil);
				set(arm, -1.75F, -out * 0.55F, 0.0F, swing);
				set(leg, 0.35F, 0.0F, 0.0F, coil);
				set(plant, -0.45F, 0.0F, 0.0F, Math.max(coil, swing));
			}
			case WebCounter.IMPALE -> {
				// the lead arm cocked back and thrown at it as the bundle whips out; both arms heaving it up overhead;
				// then hauled down hard into the slam, knees bent, and easing back as the bundle recoils
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float heave = bump(t, 10.0F, 3.0F, 2.0F), slam = bump(t, 12.5F, 1.5F, 6.0F);
				castArm(arm, other, out, wind, cast);
				set(h.rightArm, -2.9F, 0.0F, 0.25F, heave);
				set(h.leftArm, -2.9F, 0.0F, -0.25F, heave);
				set(arm, -0.7F, -out * 0.2F, out * 0.15F, slam);
				set(other, -0.4F, 0.0F, -out * 0.6F, slam);
				set(leg, -0.7F, 0.0F, out * 0.12F, slam);
				set(plant, 0.45F, 0.0F, -out * 0.12F, slam);
				h.head.xRot = Mth.lerp(heave, h.head.xRot, -0.5F);
				h.head.xRot = Mth.lerp(slam, h.head.xRot, 0.35F);
			}
			case WebCounter.WHIPLASH -> {
				// thrown at it, then both arms wrenched round after it the way it swings, and down with it into the slam
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float swing = ramp(t, 7.0F, 12.0F) * (1.0F - ramp(t, 13.0F, 19.0F));
				float slam = bump(t, 12.5F, 1.5F, 6.0F);
				castArm(arm, other, out, wind, cast);
				set(arm, -1.6F, out * 1.1F, out * 0.7F, swing);
				set(other, -1.4F, out * 1.0F, 0.0F, swing);
				set(arm, -0.6F, out * 0.6F, out * 0.9F, slam);
				set(plant, -0.3F, 0.0F, -out * 0.2F, e);
				set(leg, 0.2F, 0.0F, out * 0.3F, slam);
			}
			case WebCounter.HURL -> {
				// thrown at it, then both arms heaving it up and over your head, further and further back,
				// whipped right back over the shoulder as it slams down behind you
				float wind = bump(t, 2.5F, 2.5F, 2.5F), cast = bump(t, 5.0F, 2.0F, 2.5F);
				float heave = ramp(t, 8.0F, 15.0F) * (1.0F - ramp(t, 18.0F, 26.0F));
				float slam = bump(t, 17.5F, 2.0F, 7.0F);
				castArm(arm, other, out, wind, cast);
				set(h.rightArm, -2.4F - 0.7F * heave, 0.0F, 0.3F, heave);
				set(h.leftArm, -2.4F - 0.7F * heave, 0.0F, -0.3F, heave);
				set(h.rightArm, -3.3F, 0.2F, 0.5F, slam);
				set(h.leftArm, -3.3F, -0.2F, -0.5F, slam);
				set(h.rightLeg, -0.4F, 0.0F, 0.25F, Math.max(heave, slam));
				set(h.leftLeg, 0.25F, 0.0F, -0.25F, Math.max(heave, slam));
				h.head.xRot = Mth.lerp(Math.max(heave, slam), h.head.xRot, -0.7F);
			}
			case WebCounter.CONSTRICT -> {
				// thrown at it, arms locked round it and pulled in tight with every squeeze, then up and down into the slam
				float cast = bump(t, 5.0F, 3.0F, 3.0F);
				float hold = envelope(pose, 6.0F, 5.0F) * (1.0F - cast);
				float squeeze = squeezes(t);
				float heave = bump(t, 25.0F, 2.0F, 2.0F), slam = bump(t, 27.5F, 1.5F, 5.0F);
				float tremble = Mth.sin(t * 3.1F) * 0.06F * squeeze;
				castArm(arm, other, out, 0.0F, cast);
				set(h.rightArm, -1.3F + tremble, -0.4F - 0.35F * squeeze, 0.0F, hold);
				set(h.leftArm, -1.3F - tremble, 0.4F + 0.35F * squeeze, 0.0F, hold);
				set(h.rightArm, -2.8F, 0.0F, 0.2F, heave);
				set(h.leftArm, -2.8F, 0.0F, -0.2F, heave);
				set(h.rightArm, -0.6F, 0.0F, 0.2F, slam);
				set(h.leftArm, -0.6F, 0.0F, -0.2F, slam);
				set(h.rightLeg, -0.25F, 0.0F, 0.25F, Math.max(hold, slam));
				set(h.leftLeg, -0.25F, 0.0F, -0.25F, Math.max(hold, slam));
				h.head.xRot = Mth.lerp(hold, h.head.xRot, 0.35F);
			}
			case WebCounter.JAW_SNAP -> {
				// arms flung back as the head shoots forward, then the claws come round
				float lunge = bump(t, 8.0F, 5.0F, 8.0F);
				set(h.rightArm, 0.8F, 0.0F, 0.7F, lunge);
				set(h.leftArm, 0.8F, 0.0F, -0.7F, lunge);
				// the bundle thrown out of the lead arm first, to reel it in to the jaws
				castArm(arm, other, out, 0.0F, bump(t, 2.5F, 2.5F, 3.0F));
				set(h.rightLeg, -0.5F, 0.0F, 0.1F, lunge);
				set(h.leftLeg, 0.4F, 0.0F, -0.1F, lunge);
				h.head.xRot = Mth.lerp(lunge, h.head.xRot, -0.6F);
			}
			case WebCounter.MAUL -> {
				// both clawed arms pinning it, the head worrying it side to side, then a twist to toss it
				float on = envelope(pose, 5.0F, 5.0F);
				float toss = bump(t, 22.0F, 3.0F, 5.0F);
				set(h.rightArm, -1.2F, -0.3F, 0.3F, on);
				set(h.leftArm, -1.2F, 0.3F, -0.3F, on);
				set(arm, -2.0F, out * 0.8F, 0.0F, toss);
				castArm(arm, other, out, 0.0F, bump(t, 2.5F, 2.5F, 3.0F));
				h.head.yRot += (t > 8.0F && t < 22.0F ? Mth.sin(t * 0.9F) * 0.5F : 0.0F) * on;
				set(h.rightLeg, -0.35F, 0.0F, 0.2F, on);
				set(h.leftLeg, -0.35F, 0.0F, -0.2F, on);
			}
			case WebCounter.MAW_ROAR -> {
				// arms thrown back and wide, head back, as it roars; then the bite
				float roar = Math.min(ramp(t, 1.0F, 4.0F), 1.0F - ramp(t, 9.0F, 12.0F));
				float bite = bump(t, 16.0F, 4.0F, 6.0F);
				float shake = Mth.sin(t * 3.3F) * 0.08F * roar;
				set(h.rightArm, 0.5F + shake, 0.0F, 1.5F, roar);
				set(h.leftArm, 0.5F - shake, 0.0F, -1.5F, roar);
				set(h.rightLeg, 0.0F, 0.0F, 0.3F, roar);
				set(h.leftLeg, 0.0F, 0.0F, -0.3F, roar);
				h.head.xRot = Mth.lerp(roar, h.head.xRot, -0.8F);
				claws(h, bite, 0.8F);
				h.head.xRot = Mth.lerp(bite, h.head.xRot, -0.5F);
				// after the roar, the bundle thrown out to snatch it in for the bite
				castArm(arm, other, out, 0.0F, bump(t, 11.0F, 2.0F, 3.0F));
			}
			case WebCounter.SPEAR_FLURRY -> {
				// both arms pumping in turn behind each stab
				float on = envelope(pose, 4.0F, 4.0F);
				float a1 = Math.max(bump(t, 9.0F, 3.0F, 3.0F), bump(t, 23.0F, 3.0F, 5.0F)), a2 = bump(t, 16.0F, 3.0F, 3.0F);
				set(h.rightArm, -0.5F, 0.0F, 0.2F, on);
				set(h.leftArm, -0.5F, 0.0F, -0.2F, on);
				// the arm drawn back as the braid forms, before the stabs
				set(arm, 0.7F, out * 0.3F, out * 0.4F, bump(t, 4.0F, 4.0F, 3.0F));
				set(arm, -1.6F, -out * 0.1F, 0.0F, a1);
				set(other, -1.6F, out * 0.1F, 0.0F, a2);
				set(leg, -0.5F, 0.0F, 0.1F, on);
				set(plant, 0.35F, 0.0F, -0.1F, on);
			}
			case WebCounter.DRAG_TO_JAWS -> {
				// the lead arm out after it, hauled back in to the chest, then both clawed hands as the jaws close
				float reach = bump(t, 6.0F, 5.0F, 4.0F), haul = bump(t, 13.0F, 4.0F, 4.0F), bite = bump(t, 17.0F, 2.0F, 6.0F);
				set(arm, -1.7F, 0.0F, 0.0F, reach);
				set(arm, -0.9F, -out * 0.6F, 0.0F, haul);
				set(plant, 0.4F, 0.0F, 0.0F, haul);
				set(leg, -0.4F, 0.0F, 0.0F, haul);
				claws(h, bite, 1.0F);
				h.head.xRot = Mth.lerp(bite, h.head.xRot, -0.5F);
			}
			default -> {
			}
		}
	}

	/**
	 * The tendril slam's throw: the lead arm cocked back and out ({@code wind}), then flung straight at it as the bundle
	 * whips out ({@code cast}), the other arm swung back for balance.
	 */
	private static void castArm(ModelPart arm, ModelPart other, float out, float wind, float cast) {
		set(arm, 0.6F, out * 0.5F, out * 0.9F, wind);
		set(other, -0.6F, -out * 0.3F, -out * 0.3F, wind);
		set(arm, -1.75F, -out * 0.15F, out * 0.05F, cast);
		set(other, 0.5F, 0.0F, -out * 0.5F, cast);
	}

	/** Both arms up and out, clawed, framing the head (a beast rearing). */
	private static void claws(HumanoidModel<?> h, float amount, float height) {
		set(h.rightArm, -2.2F * height, 0.3F, 0.6F, amount);
		set(h.leftArm, -2.2F * height, -0.3F, -0.6F, amount);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
