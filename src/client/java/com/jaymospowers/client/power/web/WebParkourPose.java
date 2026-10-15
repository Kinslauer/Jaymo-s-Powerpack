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
 * <li><b>Cling</b> (webbed fists): a crouched spider perch, feet splayed wide on the wall, one hand high and the other
 * flat beside you; shimmying or climbing, it crawls hand over hand, the legs stepping in turn. In the suit the
 * tendrils hold the wall instead ({@code WebTendrilWalk}): the body hangs off them, arms and legs loose and ready.</li>
 * <li><b>Wall jump:</b> arched back off the wall, arms flung up and out, legs driving off it, then tucking.</li>
 * <li><b>Slide</b>, one of four at random: a <b>baseball</b> slide (feet first, lead leg out, the other cocked aside, a hand trailing on the
 * ground, the other up), a <b>knee</b> slide (upright on the knees, arms flung wide, head back), a <b>hip</b> slide
 * (rolled onto one side on a planted forearm, the legs stacked ahead) or a <b>head-first</b> dive (flat on the chest,
 * arms reaching ahead).</li>
 * </ul>
 * Your own moves don't show in first person.
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
	/** How far the body leans into the wall while clinging (degrees). */
	static final float CLING_LEAN = 10.0F;
	/** In the suit, the tendrils hold the body this far out from the wall (degrees, tipping the top back off it). */
	static final float TENDRIL_HANG = 8.0F;
	/** Crawling on the wall: how fast the limbs cycle (radians a tick), and the speed (blocks/tick) that's a full crawl. */
	static final float CRAWL_RATE = 0.55F, CRAWL_FULL = 0.06F;
	/** Ticks a held move (cling, slide) takes to settle in. */
	static final float EASE_IN = 3.0F;
	// ================================================================================================================

	/** {@code kind}: a {@link WebParkour} move; {@code p}: how far through it (0-1, timed moves); {@code in}: settled into it (0-1). */
	public record Pose(int kind, int variant, float p, float in, float time, boolean tendrils, float crawl) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_parkour");

	/** Per entity id: the move it's in and the game time it began. */
	private static final java.util.Map<Integer, float[]> STARTED = new java.util.HashMap<>();

	private WebParkourPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer move = entity.getAttached(WebParkour.MOVE);
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		if (move == null || ownView || entity.isPassenger()) {
			if (move == null) {
				STARTED.remove(entity.getId());
			}
			state.setData(KEY, null);
			return;
		}
		int kind = WebParkour.kind(move);
		float time = entity.level().getGameTime() + partialTicks;
		float[] started = STARTED.get(entity.getId());
		if (started == null || started[0] != move) {
			started = new float[] {move, time, 0.0F};
			STARTED.put(entity.getId(), started);
		}
		float age = time - started[1];
		float length = kind == WebParkour.VAULT ? WebParkour.VAULT_TICKS : kind == WebParkour.WALL_JUMP ? WebParkour.WALL_JUMP_TICKS : 0.0F;
		float p = length > 0.0F ? Mth.clamp(age / length, 0.0F, 1.0F) : 0.0F;
		float in = length > 0.0F ? Mth.sin(Mth.PI * p) : smooth(Mth.clamp(age / EASE_IN, 0.0F, 1.0F));
		boolean tendrils = kind == WebParkour.CLING && entity instanceof net.minecraft.world.entity.player.Player player
			&& com.jaymospowers.power.web.WebSymbiote.suitShown(player);
		// moving on the wall (shimmy or climb): how much it's crawling, eased so the limbs settle when it stops
		float speed = (float)Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getY() - entity.yo) + Mth.square(entity.getZ() - entity.zo));
		float crawlWant = kind == WebParkour.CLING ? Mth.clamp(speed / CRAWL_FULL, 0.0F, 1.0F) : 0.0F;
		started[2] = Mth.lerp(0.15F, started[2], crawlWant);
		state.setData(KEY, new Pose(kind, WebParkour.variant(move), p, Math.min(1.0F, in * (length > 0.0F ? 1.6F : 1.0F)), time, tendrils, started[2]));
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
			case WebParkour.CLING -> poseStack.rotateDegrees(Axis.XP, (pose.tendrils() ? TENDRIL_HANG : -CLING_LEAN) * pose.in());
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
				// the perch: one hand high, the other flat on the wall beside you, feet splayed wide on it. Crawling, the
				// hands go over each other and the legs step in turn (opposite arm and leg together)
				float c = pose.crawl();
				float step = Mth.sin(pose.time() * CRAWL_RATE);
				float reachR = Mth.lerp(c, 1.0F, 0.5F + 0.5F * step), reachL = Mth.lerp(c, 0.0F, 0.5F - 0.5F * step);
				set(h.rightArm, Mth.lerp(reachR, -1.7F, -2.8F) + sway, -0.1F, Mth.lerp(reachR, 0.65F, 0.2F), e);
				set(h.leftArm, Mth.lerp(reachL, -1.7F, -2.8F) - sway, 0.1F, Mth.lerp(reachL, -0.65F, -0.2F), e);
				set(h.rightLeg, -0.85F - 0.35F * c * step, 0.0F, 0.55F, e);
				set(h.leftLeg, -0.85F + 0.35F * c * step + sway, 0.0F, -0.55F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, Math.min(h.head.xRot, -0.25F));
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

	private static void set(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
