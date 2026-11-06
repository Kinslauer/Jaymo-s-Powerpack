package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebParkour;
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
 * The parkour moves as everyone sees them ({@link WebParkour#MOVE}):
 * <ul>
 * <li><b>Vault</b>, one of four at random: a <b>kong</b> (dives over flat on both hands, knees tucked under), a
 * <b>lazy</b> vault (side-on over one hand, the legs scissoring over one after the other), a <b>monkey flip</b> (a tucked
 * front flip over it) or a <b>thief</b> vault (leaning back, the lead leg kicked high over it, a hand brushing the
 * top).</li>
 * <li><b>Cling</b> (webbed fists): (0.13.17) climbed like a vine or a ladder: upright against the wall, both hands up on
 * it going hand over hand and the feet stepping up in turn as you rise (the hands keep their place while you hang).
 * In the suit the tendrils hold the wall instead ({@code WebTendrilWalk}): the body hangs off them, arms and legs
 * loose and ready.</li>
 * <li><b>Wall jump:</b> arched back off the wall, arms flung up and out, legs driving off it, then tucking.</li>
 * <li><b>Slide</b>, one of four at random: a <b>baseball</b> slide (feet first, lead leg out, the other cocked aside, a hand trailing on the
 * ground, the other up), a <b>knee</b> slide (upright on the knees, arms flung wide, head back), a <b>hip</b> slide
 * (rolled onto one side on a planted forearm, the legs stacked ahead) or a <b>head-first</b> dive (flat on the chest,
 * arms reaching ahead).</li>
 * </ul>
 * (0.21.38) Andy: "a better climbing animation, moving hands and feet interchangeably". The cling (webbed fists and a
 * freerunner's climb) is our own again, Not Enough Animations' ladder pose no longer borrowed: each hand reaches up,
 * grips and stays put on the wall while the body rises past it, the elbow folding as it comes down to the shoulder, then
 * lets go and reaches up again, the hands half a cycle apart; each foot steps up with the opposite hand, knee out, and
 * pushes down straight. All of it is keyed to how far you've climbed (up, down or across), so it holds still while you
 * hang and runs backward as you climb down. Elbows and knees bend ({@link #bendLimbs}).
 * <p>(0.21.40) Andy: "There should be a fluid transition to an animation while sliding after you lose grip". Once the
 * grip gives out and you slide down the wall, the climb blends over into a slide ({@link Pose#slip()}): both arms up,
 * fingers clawing and dragging, toes scraping and kicking for a hold, pressed in against the wall, looking up.
 * <p>Your own moves don't show in first person. (0.14.3) A freerunner's vault, cling and wall kick ({@code Freerun#MOVE})
 * are drawn the same way.
 */
public final class WebParkourPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Kong vault: how far the body dives forward (degrees) and lifts (blocks) over the hands. */
	static final float KONG_DIVE = 65.0F, KONG_LIFT = 0.45F;
	/** Lazy vault: how far the body turns side-on (degrees) and leans over the planted hand. */
	static final float LAZY_TURN = 60.0F, LAZY_TILT = 30.0F;
	/** Monkey flip: its height at the top (blocks), and where in the body it turns (blocks above the feet). */
	static final float FLIP_LIFT = 0.5F, FLIP_PIVOT = 0.9F;
	/** Thief vault: how far it leans back (degrees). */
	static final float THIEF_LEAN = 28.0F;
	/** Slides: the baseball slide's lean back, the hip slide's lean back and roll onto its side, the dive's pitch onto the chest (degrees). */
	static final float BASEBALL_LEAN = 62.0F, HIP_LEAN = 70.0F, HIP_ROLL = 55.0F, DIVE_PITCH = 78.0F;
	/** How far each slide drops the body toward the ground (blocks): baseball, knee, hip, dive. */
	static final float[] SLIDE_DROP = {0.12F, 0.5F, 0.2F, 0.05F};
	/** How far the body leans into the wall while clinging (degrees): (0.13.17) upright, as on a ladder. */
	static final float CLING_LEAN = 0.0F;
	/** (0.21.38) Blocks climbed per full cycle (each hand and each foot moves once). */
	static final float CLIMB_CYCLE = 1.2F;
	/** (0.21.38) Share of the cycle a hand, and a foot, stays gripped on the wall; the rest it's moving up to its next hold. */
	static final float HAND_HOLD = 0.6F, FOOT_HOLD = 0.45F;
	/**
	 * (0.21.38) Where a hand grips (pixels above the shoulder, and out toward the wall), how far off the wall it comes
	 * moving up; the same for a foot (pixels above the hip).
	 */
	static final float HAND_TOP = 9.0F, HAND_WALL = 4.5F, HAND_LIFT = 1.5F, FOOT_TOP = -4.0F, FOOT_WALL = 3.0F, FOOT_LIFT = 1.5F;
	/** (0.21.38) The hands out wider than the shoulders, the knees turned out as they come up (radians). */
	static final float HAND_OUT = 0.32F, KNEE_OUT = 0.4F;
	/** (0.21.38) The upper arm and forearm (to the hand), thigh and shin (pixels). */
	static final float UPPER_ARM = 4.0F, FOREARM = 6.0F, ELBOW = 4.0F, THIGH = 6.0F, SHIN = 6.0F;
	/** (0.21.38) The body's roll and twist toward the pushing leg, and the head turned up toward the reaching hand (radians). */
	static final float CLIMB_ROLL = 0.04F, CLIMB_TWIST = 0.05F, CLIMB_LOOK_UP = -0.35F, CLIMB_LOOK_TURN = 0.15F;
	/**
	 * (0.21.40) Sliding down once the grip is gone: falling faster than this (blocks/tick) on a freerun cling is the slide,
	 * eased in by this share a frame. Both arms up and clawing at the wall, the fingers dragging, the legs straight with
	 * the toes scraping and kicking for a hold, the body pressed in ({@link #SLIDE_LEAN} degrees), the head back to look up.
	 */
	static final float SLIP_DETECT = 0.03F, SLIP_EASE = 0.12F;
	static final float SLIDE_ARM = -2.85F, SLIDE_ARM_OUT = 0.3F, SLIDE_CLAW = 0.14F, SLIDE_CLAW_RATE = 0.9F, SLIDE_ELBOW = 0.35F;
	static final float SLIDE_LEG = -0.12F, SLIDE_KICK = 0.25F, SLIDE_KNEE = 0.45F, SLIDE_LEAN = 6.0F, SLIDE_LOOK = -0.6F;
	/** In the suit, the tendrils hold the body this far out from the wall (degrees, tipping the top back off it). */
	static final float TENDRIL_HANG = 8.0F;
	/** Crawling on the wall: how fast the limbs cycle (radians a tick), and the speed (blocks/tick) that's a full crawl. */
	static final float CRAWL_RATE = 0.55F, CRAWL_FULL = 0.06F;
	/** Ticks a held move (cling, slide) takes to settle in. */
	static final float EASE_IN = 3.0F;
	// ================================================================================================================

	/** {@code kind}: a {@link WebParkour} move; {@code p}: how far through it (0-1, timed moves); {@code in}: settled into it (0-1). */
	public record Pose(int kind, int variant, float p, float in, float time, boolean tendrils, float crawl, float height, float travel,
		float slip) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_parkour");

	/** Per entity id: the move it's in and the game time it began. */
	private static final java.util.Map<Integer, float[]> STARTED = new java.util.HashMap<>();
	/** (0.21.38) Per entity id, on the wall: where it was last frame (x, z) and how far it has gone across the wall. */
	private static final java.util.Map<Integer, double[]> ACROSS = new java.util.HashMap<>();

	private WebParkourPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer move = entity.getAttached(WebParkour.MOVE);
		boolean web = move != null;
		if (move == null) {
			// (0.14.3) a freerunner's vault, cling or wall kick (Freerun) looks the same
			move = entity.getAttached(com.jaymospowers.freerun.Freerun.MOVE);
			// (0.15.67) the vertical wall run has its own pose (UpRunPose)
			if (move != null && move / 8 == com.jaymospowers.freerun.Freerun.UP_RUN) {
				move = null;
			}
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		if (move == null || ownView || entity.isPassenger()) {
			if (move == null) {
				STARTED.remove(entity.getId());
				ACROSS.remove(entity.getId());
			}
			state.setData(KEY, null);
			return;
		}
		int kind = WebParkour.kind(move);
		float time = entity.level().getGameTime() + partialTicks;
		float[] started = STARTED.get(entity.getId());
		if (started == null || started[0] != move) {
			started = new float[] {move, time, 0.0F, 0.0F};
			STARTED.put(entity.getId(), started);
		}
		float age = time - started[1];
		float length = kind == WebParkour.VAULT ? WebParkour.VAULT_TICKS : kind == WebParkour.WALL_JUMP ? WebParkour.WALL_JUMP_TICKS : 0.0F;
		float p = length > 0.0F ? Mth.clamp(age / length, 0.0F, 1.0F) : 0.0F;
		float in = length > 0.0F ? Mth.sin(Mth.PI * p) : smooth(Mth.clamp(age / EASE_IN, 0.0F, 1.0F));
		boolean tendrils = web && kind == WebParkour.CLING && entity instanceof net.minecraft.world.entity.player.Player player
			&& com.jaymospowers.power.web.WebSymbiote.suitShown(player);
		// moving on the wall (shimmy or climb): how much it's crawling, eased so the limbs settle when it stops
		float speed = (float)Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getY() - entity.yo) + Mth.square(entity.getZ() - entity.zo));
		float crawlWant = kind == WebParkour.CLING ? Mth.clamp(speed / CRAWL_FULL, 0.0F, 1.0F) : 0.0F;
		started[2] = Mth.lerp(0.15F, started[2], crawlWant);
		// (0.21.40) a freerunner's grip gone: sliding down the wall (the only way a cling moves down), eased in
		boolean sliding = !web && kind == WebParkour.CLING && entity.getY() - entity.yo < -SLIP_DETECT;
		started[3] = Mth.lerp(SLIP_EASE, started[3], sliding ? 1.0F : 0.0F);
		// (0.21.38) the climb's limbs follow the distance climbed: up and down, plus across the wall
		double x = Mth.lerp(partialTicks, entity.xo, entity.getX()), z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
		double[] across = ACROSS.computeIfAbsent(entity.getId(), id -> new double[] {x, z, 0.0, y, 0.0});
		double step = Math.sqrt(Mth.square(x - across[0]) + Mth.square(z - across[1]));
		across[0] = x;
		across[1] = z;
		if (step < 0.5) {
			across[2] = (across[2] + step) % 4096.0;
		}
		// (0.21.40) the drop while sliding isn't climbing: the limbs hold where they were instead of running backward
		double fall = y - across[3];
		across[3] = y;
		if (sliding && fall < 0.0 && fall > -0.5) {
			across[4] += fall;
		}
		state.setData(KEY, new Pose(kind, WebParkour.variant(move), p, Math.min(1.0F, in * (length > 0.0F ? 1.6F : 1.0F)), time, tendrils, started[2],
			(float) y, (float) ((y - across[4] + across[2]) % 4096.0), smooth(Mth.clamp(started[3], 0.0F, 1.0F))));
	}

	/** After the body faces its way: tip over in a vault, lie back in a slide, lean into the wall in a cling. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F) {
			return;
		}
		// (here +X is the body's right, +Y up, +Z behind it)
		switch (pose.kind()) {
			case WebParkour.VAULT -> {
				float in = pose.in();
				switch (pose.variant()) {
					case 0 -> {
						// kong: dived forward flat over both hands
						poseStack.translate(0.0F, KONG_LIFT * in, 0.0F);
						poseStack.rotateDegrees(Axis.XP, -KONG_DIVE * in);
					}
					case 1 -> {
						// lazy: turned side-on, leaning over the left hand
						poseStack.translate(0.0F, 0.3F * in, 0.0F);
						poseStack.rotateDegrees(Axis.YP, LAZY_TURN * in);
						poseStack.rotateDegrees(Axis.ZP, LAZY_TILT * in);
					}
					case 2 -> {
						// monkey flip: a full tucked front flip round the middle of the body
						poseStack.translate(0.0F, FLIP_LIFT * in + FLIP_PIVOT, 0.0F);
						poseStack.rotateDegrees(Axis.XP, -360.0F * smooth(pose.p()));
						poseStack.translate(0.0F, -FLIP_PIVOT, 0.0F);
					}
					default -> {
						// thief: leaning back as the lead leg goes over
						poseStack.translate(0.0F, 0.25F * in, 0.0F);
						poseStack.rotateDegrees(Axis.XP, THIEF_LEAN * in);
					}
				}
			}
			case WebParkour.SLIDE -> {
				float in = pose.in();
				poseStack.translate(0.0F, -SLIDE_DROP[pose.variant() % SLIDE_DROP.length] * in, 0.0F);
				switch (pose.variant()) {
					case 0 -> poseStack.rotateDegrees(Axis.XP, BASEBALL_LEAN * in);
					// the knee slide stays upright, leaning back a touch
					case 1 -> poseStack.rotateDegrees(Axis.XP, 14.0F * in);
					case 2 -> {
						poseStack.rotateDegrees(Axis.ZP, -HIP_ROLL * in);
						poseStack.rotateDegrees(Axis.XP, HIP_LEAN * in);
					}
					// head first: tipped forward onto the chest
					default -> poseStack.rotateDegrees(Axis.XP, -DIVE_PITCH * in);
				}
			}
			case WebParkour.CLING -> poseStack.rotateDegrees(Axis.XP, (pose.tendrils() ? TENDRIL_HANG : -CLING_LEAN - SLIDE_LEAN * pose.slip()) * pose.in());
			case WebParkour.WALL_JUMP -> poseStack.rotateDegrees(Axis.XP, 18.0F * pose.in());
			default -> {
			}
		}
	}

	/** After the model animates: the limbs. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float e = pose.in();
		switch (pose.kind()) {
			case WebParkour.VAULT -> {
				float swing = Mth.sin(Mth.PI * pose.p());
				switch (pose.variant()) {
					case 0 -> {
						// kong: both arms reaching down to the hands planted ahead, knees tucked up under the chest
						set(h.rightArm, -1.75F, 0.0F, 0.2F, e);
						set(h.leftArm, -1.75F, 0.0F, -0.2F, e);
						set(h.rightLeg, -1.9F * swing, 0.0F, 0.15F, e);
						set(h.leftLeg, -1.9F * swing, 0.0F, -0.15F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, -0.7F);
					}
					case 1 -> {
						// lazy: left hand planted down by the hip, right arm out; the legs scissor over, right leg first
						float lead = Mth.sin(Mth.PI * Mth.clamp(pose.p() * 1.4F, 0.0F, 1.0F));
						float trail = Mth.sin(Mth.PI * Mth.clamp(pose.p() * 1.4F - 0.4F, 0.0F, 1.0F));
						set(h.leftArm, -0.15F, 0.0F, -0.25F, e);
						set(h.rightArm, -1.0F, 0.0F, 1.4F, e);
						set(h.rightLeg, -1.5F * lead, 0.0F, 0.5F * lead, e);
						set(h.leftLeg, -1.3F * trail, 0.0F, 0.3F * trail, e);
						h.head.zRot = Mth.lerp(e, h.head.zRot, -LAZY_TILT * 0.5F * Mth.DEG_TO_RAD);
					}
					case 2 -> {
						// monkey flip: balled up, hands to the shins, knees to the chest
						set(h.rightArm, -1.3F, 0.0F, 0.15F, e);
						set(h.leftArm, -1.3F, 0.0F, -0.15F, e);
						set(h.rightLeg, -1.95F, 0.0F, 0.1F, e);
						set(h.leftLeg, -1.95F, 0.0F, -0.1F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, 0.5F);
					}
					default -> {
						// thief: lead leg kicked high over it, the other tucked; right hand brushing the top, left swept up behind
						set(h.rightLeg, -1.7F, 0.0F, 0.1F, e);
						set(h.leftLeg, -0.7F * swing, 0.0F, -0.15F, e);
						set(h.rightArm, -0.35F, 0.0F, 0.25F, e);
						set(h.leftArm, 1.3F, 0.0F, -0.5F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - THIEF_LEAN * 0.8F * Mth.DEG_TO_RAD);
					}
				}
			}
			case WebParkour.CLING -> {
				float sway = Mth.sin(pose.time() * 0.12F) * 0.05F;
				if (pose.tendrils()) {
					// held off the wall by the tendrils: arms loose and a little out, ready; legs drawn up a touch, swaying
					set(h.rightArm, -0.35F + sway, 0.0F, 0.35F, e);
					set(h.leftArm, -0.35F - sway, 0.0F, -0.35F, e);
					set(h.rightLeg, -0.45F - sway, 0.0F, 0.12F, e);
					set(h.leftLeg, -0.2F + sway, 0.0F, -0.12F, e);
					break;
				}
				// (0.21.38) hand over hand: each hand grips, the body rises past it, it lets go and reaches up again; each
				// foot steps up with the opposite hand and pushes down (see the class notes). Elbows and knees: bendLimbs.
				float u = pose.travel() / CLIMB_CYCLE;
				float[] limb = new float[2];
				climbLimb(u, 0.0F, true, pose.crawl(), limb);
				set(h.rightArm, limb[0], 0.0F, HAND_OUT, e);
				climbLimb(u, 0.5F, true, pose.crawl(), limb);
				set(h.leftArm, limb[0], 0.0F, -HAND_OUT, e);
				climbLimb(u, 0.6F, false, pose.crawl(), limb);
				set(h.rightLeg, limb[0], KNEE_OUT * Math.min(1.0F, limb[1]), 0.08F, e);
				climbLimb(u, 0.1F, false, pose.crawl(), limb);
				set(h.leftLeg, limb[0], -KNEE_OUT * Math.min(1.0F, limb[1]), -0.08F, e);
				// the body leans into the pushing leg, the head looks up toward the reaching hand
				float s = Mth.sin(u * Mth.TWO_PI);
				h.body.yRot = Mth.lerp(e, h.body.yRot, CLIMB_TWIST * s);
				h.body.zRot = Mth.lerp(e, h.body.zRot, CLIMB_ROLL * s);
				h.head.xRot = Mth.lerp(e, h.head.xRot, Math.min(h.head.xRot, CLIMB_LOOK_UP));
				h.head.yRot = Mth.lerp(e, h.head.yRot, h.head.yRot + CLIMB_LOOK_TURN * s);
				// (0.21.40) the grip gone: blended over into the slide, so it flows out of wherever the climb was
				float sl = pose.slip() * e;
				if (sl > 0.0F) {
					float claw = Mth.sin(pose.time() * SLIDE_CLAW_RATE);
					set(h.rightArm, SLIDE_ARM + SLIDE_CLAW * claw, 0.0F, SLIDE_ARM_OUT, sl);
					set(h.leftArm, SLIDE_ARM - SLIDE_CLAW * claw, 0.0F, -SLIDE_ARM_OUT, sl);
					set(h.rightLeg, SLIDE_LEG - SLIDE_KICK * Math.max(0.0F, claw), 0.0F, 0.06F, sl);
					set(h.leftLeg, SLIDE_LEG - SLIDE_KICK * Math.max(0.0F, -claw), 0.0F, -0.06F, sl);
					h.body.yRot = Mth.lerp(sl, h.body.yRot, 0.0F);
					h.body.zRot = Mth.lerp(sl, h.body.zRot, 0.0F);
					h.head.xRot = Mth.lerp(sl, h.head.xRot, SLIDE_LOOK);
				}
			}
			case WebParkour.WALL_JUMP -> {
				// pushed off: arms up and out, legs driving down off the wall, tucking as it flies
				float tuck = pose.p();
				set(h.rightArm, -2.4F, 0.0F, 0.6F, e);
				set(h.leftArm, -2.4F, 0.0F, -0.6F, e);
				set(h.rightLeg, Mth.lerp(tuck, 0.5F, -1.0F), 0.0F, 0.1F, e);
				set(h.leftLeg, Mth.lerp(tuck, 0.3F, -0.7F), 0.0F, -0.1F, e);
			}
			case WebParkour.SLIDE -> {
				switch (pose.variant()) {
					case 0 -> {
						// baseball: lead leg out flat ahead, the other cocked out to the side; right hand trailing on the
						// ground, left arm up for balance
						set(h.rightLeg, -(90.0F - BASEBALL_LEAN) * Mth.DEG_TO_RAD, 0.0F, 0.08F, e);
						set(h.leftLeg, -0.3F, 0.0F, -0.5F, e);
						set(h.rightArm, 0.6F, 0.0F, 0.7F, e);
						set(h.leftArm, -2.3F, 0.0F, -0.45F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - BASEBALL_LEAN * 0.85F * Mth.DEG_TO_RAD);
					}
					case 1 -> {
						// knees: shins flat behind on the ground, arms flung wide, head thrown back
						set(h.rightLeg, 1.45F, 0.0F, 0.12F, e);
						set(h.leftLeg, 1.45F, 0.0F, -0.12F, e);
						set(h.rightArm, -0.4F, 0.0F, 1.5F, e);
						set(h.leftArm, -0.4F, 0.0F, -1.5F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, -0.55F);
					}
					case 2 -> {
						// hip: on the left forearm, legs stacked out ahead, the right arm forward over them
						float flat = -(90.0F - HIP_LEAN) * Mth.DEG_TO_RAD;
						set(h.rightLeg, flat, 0.0F, 0.0F, e);
						set(h.leftLeg, flat - 0.15F, 0.0F, 0.05F, e);
						set(h.leftArm, -0.9F, 0.0F, -1.1F, e);
						set(h.rightArm, -1.2F, 0.0F, 0.2F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - HIP_LEAN * 0.7F * Mth.DEG_TO_RAD);
						h.head.zRot = Mth.lerp(e, h.head.zRot, HIP_ROLL * 0.7F * Mth.DEG_TO_RAD);
					}
					default -> {
						// head first: arms reaching out ahead, legs straight back, head up to see where it's going
						set(h.rightArm, -2.95F, 0.0F, 0.15F, e);
						set(h.leftArm, -2.95F, 0.0F, -0.15F, e);
						set(h.rightLeg, 0.08F, 0.0F, 0.1F, e);
						set(h.leftLeg, 0.08F, 0.0F, -0.1F, e);
						h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - DIVE_PITCH * 0.85F * Mth.DEG_TO_RAD);
					}
				}
			}
			default -> {
			}
		}
	}

	/**
	 * (0.21.38) Right before the model is drawn (after {@link #applyModel}): a climber's elbows and knees bent for this one
	 * draw ({@link com.jaymospowers.client.KneeBend}), as far as reaching each hold needs.
	 */
	public static void bendLimbs(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h) || living.deathTime > 0.0F) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null || pose.kind() != WebParkour.CLING || pose.tendrils()) {
			return;
		}
		float u = pose.travel() / CLIMB_CYCLE, e = pose.in();
		// (0.21.40) sliding: the elbows a little bent as the fingers drag, a knee coming up with each kick
		float sl = pose.slip(), claw = Mth.sin(pose.time() * SLIDE_CLAW_RATE);
		float[] limb = new float[2];
		climbLimb(u, 0.0F, true, pose.crawl(), limb);
		com.jaymospowers.client.KneeBend.bend(h.rightArm, Mth.lerp(sl, limb[1], SLIDE_ELBOW) * e, ELBOW, false);
		climbLimb(u, 0.5F, true, pose.crawl(), limb);
		com.jaymospowers.client.KneeBend.bend(h.leftArm, Mth.lerp(sl, limb[1], SLIDE_ELBOW) * e, ELBOW, false);
		climbLimb(u, 0.6F, false, pose.crawl(), limb);
		com.jaymospowers.client.KneeBend.bend(h.rightLeg, Mth.lerp(sl, limb[1], SLIDE_KNEE * Math.max(0.0F, claw)) * e);
		climbLimb(u, 0.1F, false, pose.crawl(), limb);
		com.jaymospowers.client.KneeBend.bend(h.leftLeg, Mth.lerp(sl, limb[1], SLIDE_KNEE * Math.max(0.0F, -claw)) * e);
	}

	/**
	 * (0.21.38) One limb of the climb, {@code offset} of a cycle along from the right hand, {@code u} cycles climbed: its
	 * swing (xRot) and its elbow or knee bend into {@code out}. Gripped, the hand (or foot) stays on its spot on the wall
	 * while the body rises past; then it comes off a little and moves up to the next hold. {@code moving}: 0 hanging still
	 * (a hand caught between holds rests back on the wall) to 1 climbing.
	 */
	static void climbLimb(float u, float offset, boolean hand, float moving, float[] out) {
		float v = u + offset;
		v -= Mth.floor(v);
		float hold = hand ? HAND_HOLD : FOOT_HOLD, top = hand ? HAND_TOP : FOOT_TOP, wall = hand ? HAND_WALL : FOOT_WALL;
		float lift = hand ? HAND_LIFT : FOOT_LIFT;
		float drop = hold * CLIMB_CYCLE * 16.0F, up, out2;
		if (v < hold) {
			up = top - v * CLIMB_CYCLE * 16.0F;
			out2 = wall;
		} else {
			float t = (v - hold) / (1.0F - hold);
			up = top - drop + drop * smooth(t);
			out2 = wall - lift * Mth.sin(Mth.PI * t) * moving;
		}
		reach(up, out2, hand ? UPPER_ARM : THIGH, hand ? FOREARM : SHIN, out);
	}

	/**
	 * (0.21.38) Two-bone reach in the body's side plane: the swing (xRot) and the bend (+ folds the lower half back, as
	 * {@link com.jaymospowers.client.KneeBend} bends) that put the end of a limb {@code up} pixels above its pivot and
	 * {@code ahead} pixels in front. The joint goes out behind the line to the target (elbows back, knees forward).
	 */
	static void reach(float up, float ahead, float upper, float lower, float[] out) {
		float ym = -up, zm = -ahead;
		float d = (float) Math.sqrt(ym * ym + zm * zm);
		float at = (float) Math.atan2(zm, ym);
		if (d >= upper + lower - 0.001F) {
			out[0] = at;
			out[1] = 0.0F;
			return;
		}
		d = Math.max(d, Math.abs(upper - lower) + 0.5F);
		float bend = Mth.PI - (float) Math.acos(Mth.clamp((upper * upper + lower * lower - d * d) / (2.0F * upper * lower), -1.0F, 1.0F));
		float alpha = (float) Math.acos(Mth.clamp((upper * upper + d * d - lower * lower) / (2.0F * upper * d), -1.0F, 1.0F));
		out[0] = at - alpha;
		out[1] = bend;
	}

	private static void set(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
