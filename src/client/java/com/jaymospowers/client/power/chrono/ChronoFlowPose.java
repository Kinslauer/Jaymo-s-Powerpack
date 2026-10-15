package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Time Flow's blows, drawn: a jab, a hook, an uppercut, a lunging punch, a flying kick, the counters (a crowd's quick
 * guard and strike; against one attacker the circle, the slip, one of five deflects, the vault, the leg sweep or the
 * shoulder throw, each then the answer) and
 * Chrono Punch's quick jabs and its Time Flow barrage. Everything is set outright from the blow's progress, so applying it twice
 * changes nothing.
 */
public final class ChronoFlowPose {
	/** The blow on this player and how far through it they are (0-1). */
	public record Pose(ChronoFlow.Move move, float p) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:chrono_flow_pose");

	private ChronoFlowPose() {
	}

	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		ChronoFlow.Move move = entity.getAttached(ChronoFlow.MOVE);
		ChronoFlow.Move mine = ChronoFlowClient.local;
		if (entity == Minecraft.getInstance().player && mine != null && (move == null || mine.start() >= move.start())) {
			move = mine;
		}
		if (move == null) {
			state.setData(KEY, null);
			return;
		}
		float p = (entity.level().getGameTime() - move.start() + partialTicks) / move.duration();
		state.setData(KEY, p >= 0.0F && p < 1.0F ? new Pose(move, p) : null);
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		float p = pose.p();
		// out fast, back slower
		float e = p < 0.35F ? p / 0.35F : 1.0F - (p - 0.35F) / 0.65F;
		e = e * e * (3.0F - 2.0F * e);
		int side = pose.move().side();
		ModelPart lead = side > 0 ? h.rightArm : h.leftArm;
		ModelPart guard = side > 0 ? h.leftArm : h.rightArm;
		ModelPart frontLeg = side > 0 ? h.leftLeg : h.rightLeg;
		ModelPart backLeg = side > 0 ? h.rightLeg : h.leftLeg;
		switch (pose.move().style()) {
			case ChronoFlow.JAB, ChronoFlow.RAPID -> {
				set(lead, -Mth.HALF_PI, 0.0F, 0.0F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, Math.max(e, 0.6F));
				h.body.yRot = side * -0.35F * e;
			}
			case ChronoFlow.BARRAGE -> {
				// a punch every few ticks, fists alternating: each driven out fast and drawn back, the body twisting into it
				float beat = p * ChronoFlow.BARRAGE_PUNCHES;
				int n = Math.min(ChronoFlow.BARRAGE_PUNCHES - 1, (int)beat);
				float q = beat - n;
				float out = q < 0.4F ? q / 0.4F : 1.0F - (q - 0.4F) / 0.6F;
				out = out * out * (3.0F - 2.0F * out);
				int s = n % 2 == 0 ? 1 : -1;
				ModelPart punching = s > 0 ? h.rightArm : h.leftArm;
				ModelPart drawn = s > 0 ? h.leftArm : h.rightArm;
				set(punching, -1.25F, s * -0.3F, s * -0.25F, 1.0F);
				set(punching, -Mth.HALF_PI - 0.05F, s * 0.1F, 0.0F, out);
				set(drawn, -1.25F, s * 0.3F, s * 0.25F, 1.0F);
				set(h.leftLeg, -0.25F, 0.0F, 0.0F, 1.0F);
				set(h.rightLeg, 0.3F, 0.0F, 0.0F, 1.0F);
				h.body.yRot = s * -0.35F * out;
				h.body.xRot = 0.15F;
			}
			case ChronoFlow.HOOK -> {
				// swung round from the side
				set(lead, -Mth.HALF_PI, Mth.lerp(p, side * 1.0F, side * -0.5F), 0.0F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, 0.7F);
				h.body.yRot = Mth.lerp(p, side * 0.3F, side * -0.5F) * e;
			}
			case ChronoFlow.UPPERCUT -> {
				// from low, driven up
				set(lead, Mth.lerp(Math.min(1.0F, p * 2.0F), 0.4F, -2.7F), 0.0F, side * -0.15F, e);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, 0.7F);
				h.body.xRot = -0.15F * e;
			}
			case ChronoFlow.LUNGE -> {
				set(lead, -Mth.HALF_PI - 0.1F, 0.0F, 0.0F, e);
				set(guard, 0.6F, 0.0F, side * 0.2F, e);
				set(frontLeg, -0.7F, 0.0F, 0.0F, e);
				set(backLeg, 0.6F, 0.0F, 0.0F, e);
				h.body.xRot = 0.3F * e;
				h.body.yRot = side * -0.4F * e;
			}
			case ChronoFlow.SPIN -> {
				// (0.11.40) the Space counter: a spinning kick, the leg swung out level, arms flung wide for the turn
				float out = seg(p, 0.0F, 0.25F) * (1.0F - seg(p, 0.75F, 1.0F));
				set(h.rightLeg, -0.2F, 0.0F, 1.35F, out);
				set(h.leftLeg, 0.15F, 0.0F, -0.1F, out);
				set(h.rightArm, -0.2F, 0.0F, 1.4F, out);
				set(h.leftArm, -0.2F, 0.0F, -1.4F, out);
				h.body.zRot = -0.3F * out;
				h.head.zRot = 0.2F * out;
			}
			case ChronoFlow.PUSH -> {
				// (0.11.40) the time stop: both palms drawn in to the chest, then driven straight out, braced
				float hit = ChronoFlow.HIT_AT[ChronoFlow.PUSH] / (float)ChronoFlow.DURATION[ChronoFlow.PUSH];
				float draw = seg(p, 0.0F, hit * 0.6F) * (1.0F - seg(p, hit * 0.6F, hit));
				float push = seg(p, hit * 0.6F, hit) * (1.0F - seg(p, 0.8F, 1.0F));
				set(h.rightArm, -0.9F, 0.5F, -0.3F, draw);
				set(h.leftArm, -0.9F, -0.5F, 0.3F, draw);
				h.body.xRot = -0.1F * draw;
				set(h.rightArm, -Mth.HALF_PI - 0.05F, -0.12F, 0.0F, push);
				set(h.leftArm, -Mth.HALF_PI - 0.05F, 0.12F, 0.0F, push);
				set(h.leftLeg, -0.45F, 0.0F, 0.0F, push);
				set(h.rightLeg, 0.4F, 0.0F, 0.0F, push);
				h.body.xRot = Mth.lerp(push, h.body.xRot, 0.2F);
			}
			case ChronoFlow.KICK -> {
				// the kicking leg straight out ahead, the body thrown back, arms flung out
				set(h.rightLeg, -Mth.HALF_PI - 0.2F, 0.0F, 0.0F, e);
				set(h.leftLeg, 0.45F, 0.0F, 0.0F, e);
				set(h.rightArm, -0.4F, 0.0F, 1.2F, e);
				set(h.leftArm, -0.4F, 0.0F, -1.2F, e);
				h.body.xRot = -0.3F * e;
			}
			case ChronoFlow.COUNTER -> {
				if (pose.move().variant() != ChronoFlow.CROWD) {
					counter(h, pose.move(), p, side, lead, guard, frontLeg, backLeg);
				} else if (p < 0.4F) {
					// guard up: the forearm across the face, catching the blow
					float g = p / 0.4F;
					set(lead, -2.0F, side * -0.7F, 0.0F, Math.min(1.0F, g * 2.0F));
					set(guard, -1.4F, side * 0.4F, 0.0F, Math.min(1.0F, g * 2.0F));
				} else {
					// and the answer: a palm strike straight back
					float s = 1.0F - (p - 0.4F) / 0.6F;
					set(lead, -Mth.HALF_PI, 0.0F, 0.0F, s);
					set(guard, -1.25F, side * 0.3F, side * 0.25F, s);
					h.body.yRot = side * -0.45F * s;
				}
			}
			default -> {
			}
		}
	}

	/** The whole body turned: the Space counter's spinning kick goes once round (fast into the kick, easing out). */
	public static void rotate(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		Pose pose = state.getData(KEY);
		if (pose == null || pose.move().style() != ChronoFlow.SPIN) {
			return;
		}
		float t = 1.0F - pose.p();
		float turn = 1.0F - t * t * t;
		poseStack.rotateDegrees(com.mojang.math.Axis.YP, -360.0F * turn);
	}

	/** A counter against one attacker: the evasion or the parry, then the answering blow when it lands. */
	private static void counter(HumanoidModel<?> h, ChronoFlow.Move move, float p, int side, ModelPart lead, ModelPart guard,
		ModelPart frontLeg, ModelPart backLeg) {
		float hit = move.hitAt() / (float)move.duration();
		// the answer: drawn back just before the blow, driven out on it, then eased off
		float windUp = seg(p, hit - 0.16F, hit - 0.05F) * (1.0F - seg(p, hit - 0.05F, hit));
		float strike = seg(p, hit - 0.05F, hit) * (1.0F - seg(p, hit + 0.1F, 1.0F));
		switch (move.variant()) {
			case ChronoFlow.CIRCLE -> {
				// a crouch with the guard up, then running round them, leaning in
				float g = seg(p, 0.0F, 0.08F) * (1.0F - seg(p, 0.5F, 0.58F));
				set(lead, -1.3F, -side * 0.3F, 0.0F, g);
				set(guard, -1.3F, side * 0.3F, 0.0F, g);
				float run = seg(p, 0.06F, 0.14F) * (1.0F - seg(p, 0.46F, 0.56F));
				float stride = Mth.sin(p * Mth.PI * 10.0F) * 0.9F;
				set(h.rightLeg, stride, 0.0F, 0.0F, run);
				set(h.leftLeg, -stride, 0.0F, 0.0F, run);
				h.body.xRot = 0.3F * Math.max(run, g * 0.5F);
				h.body.zRot = side * 0.2F * run;
				h.head.zRot = side * 0.15F * run;
				// from behind: the lead fist cocked back, then driven in
				set(lead, 0.7F, 0.0F, side * 0.2F, windUp);
				set(lead, -Mth.HALF_PI - 0.15F, 0.0F, 0.0F, strike);
				set(guard, -1.2F, side * 0.4F, 0.0F, strike);
				set(frontLeg, -0.45F, 0.0F, 0.0F, strike);
				set(backLeg, 0.35F, 0.0F, 0.0F, strike);
				h.body.yRot = side * -0.45F * strike;
			}
			case ChronoFlow.SLIP -> {
				// leaning out of the blow, the step's way, guard high
				float lean = seg(p, 0.0F, 0.12F) * (1.0F - seg(p, 0.45F, 0.58F));
				set(lead, -1.45F, -side * 0.35F, -side * 0.3F, lean);
				set(guard, -1.45F, side * 0.35F, -side * 0.3F, lean);
				h.body.zRot = -side * 0.38F * lean;
				h.head.zRot = -side * 0.3F * lean;
				set(side > 0 ? h.rightLeg : h.leftLeg, 0.0F, 0.0F, side * 0.35F, lean);
				// and a short hook to the body: low, turned through the hips
				set(lead, 0.4F, 0.0F, side * 0.3F, windUp);
				set(lead, -1.15F, -side * 0.25F, 0.0F, strike);
				set(guard, -1.3F, side * 0.35F, 0.0F, strike);
				h.body.xRot = 0.2F * strike;
				h.body.yRot = side * -0.55F * strike;
			}
			case ChronoFlow.VAULT -> {
				// a crouch, arms swung back to spring
				float crouch = seg(p, 0.0F, 0.08F) * (1.0F - seg(p, 0.12F, 0.18F));
				set(h.rightArm, 0.8F, 0.0F, 0.15F, crouch);
				set(h.leftArm, 0.8F, 0.0F, -0.15F, crouch);
				h.body.xRot = 0.45F * crouch;
				// over the top: knees tucked up, arms flung high
				float air = seg(p, 0.14F, 0.22F) * (1.0F - seg(p, hit - 0.2F, hit - 0.1F));
				set(h.rightLeg, -1.3F, 0.0F, 0.1F, air);
				set(h.leftLeg, -1.3F, 0.0F, -0.1F, air);
				set(h.rightArm, -2.6F, 0.0F, 0.35F, air);
				set(h.leftArm, -2.6F, 0.0F, -0.35F, air);
				h.body.xRot = Mth.lerp(air, h.body.xRot, -0.25F);
				// coming down behind it: both fists raised overhead, then a hammer fist driven down
				float raise = seg(p, hit - 0.2F, hit - 0.06F) * (1.0F - seg(p, hit - 0.04F, hit));
				set(lead, -2.9F, -side * 0.2F, 0.0F, raise);
				set(guard, -2.9F, side * 0.2F, 0.0F, raise);
				float hammer = seg(p, hit - 0.04F, hit) * (1.0F - seg(p, hit + 0.12F, 1.0F));
				set(lead, -0.8F, -side * 0.15F, 0.0F, hammer);
				set(guard, -0.9F, side * 0.15F, 0.0F, hammer);
				set(frontLeg, -0.5F, 0.0F, 0.0F, hammer);
				set(backLeg, 0.4F, 0.0F, 0.0F, hammer);
				h.body.xRot = Mth.lerp(hammer, h.body.xRot, 0.45F);
			}
			case ChronoFlow.SWEEP -> {
				// dropped low, the sweeping leg thrown out wide and swung round, arms out for balance
				float low = seg(p, 0.0F, 0.1F) * (1.0F - seg(p, hit - 0.18F, hit - 0.06F));
				float round = seg(p, 0.08F, 0.42F);
				h.body.xRot = 0.5F * low;
				h.body.yRot = side * Mth.lerp(round, 0.6F, -0.9F) * low;
				set(frontLeg, -0.25F, 0.0F, side * Mth.lerp(round, -0.4F, -1.4F), low);
				set(backLeg, 0.9F, 0.0F, 0.0F, low);
				set(lead, -0.35F, 0.0F, side * 1.3F, low);
				set(guard, 0.2F, 0.0F, -side * 0.6F, low);
				h.head.xRot = Mth.lerp(low, h.head.xRot, -0.3F);
				// up from the crouch: the palm drawn low, then driven up under the chin
				set(lead, 0.6F, 0.0F, side * 0.2F, windUp);
				h.body.xRot = Mth.lerp(windUp, h.body.xRot, 0.3F);
				set(lead, -2.75F, -side * 0.1F, side * -0.1F, strike);
				set(guard, -1.2F, side * 0.35F, 0.0F, strike);
				set(frontLeg, -0.3F, 0.0F, 0.0F, strike);
				h.body.xRot = Mth.lerp(strike, h.body.xRot, -0.2F);
			}
			case ChronoFlow.THROW -> {
				// both hands catch the arm out front
				float grab = seg(p, 0.0F, 0.1F) * (1.0F - seg(p, hit - 0.06F, hit));
				set(h.rightArm, -1.4F, -0.25F, 0.0F, grab);
				set(h.leftArm, -1.4F, 0.25F, 0.0F, grab);
				// turned in, back to its chest, the arm hauled up over the shoulder
				float load = seg(p, 0.18F, hit - 0.12F) * (1.0F - seg(p, hit - 0.05F, hit));
				set(lead, -2.75F, side * 0.3F, 0.0F, load);
				set(guard, -2.5F, side * 0.5F, 0.0F, load);
				h.body.yRot = side * 0.9F * load;
				h.body.xRot = 0.15F * load;
				set(frontLeg, -0.3F, 0.0F, 0.0F, load);
				set(backLeg, 0.3F, 0.0F, 0.0F, load);
				// and over: bent double, hands driven down in front where it lands
				float over = seg(p, hit - 0.05F, hit) * (1.0F - seg(p, hit + 0.15F, 1.0F));
				set(h.rightArm, -0.5F, -0.2F, 0.0F, over);
				set(h.leftArm, -0.5F, 0.2F, 0.0F, over);
				h.body.xRot = Mth.lerp(over, h.body.xRot, 0.65F);
				h.body.yRot = Mth.lerp(over, h.body.yRot, side * -0.3F);
				h.head.xRot = Mth.lerp(over, h.head.xRot, 0.4F);
			}
			default -> {
				deflect(h, move.deflect(), p, side, lead, guard);
				// the riposte: a palm strike straight back
				set(lead, 0.5F, 0.0F, 0.0F, windUp);
				set(lead, -Mth.HALF_PI, 0.0F, 0.0F, strike);
				set(guard, -1.25F, side * 0.3F, side * 0.25F, strike);
				set(frontLeg, -0.35F, 0.0F, 0.0F, strike);
				h.body.yRot = side * -0.45F * strike;
			}
		}
	}

	/**
	 * The five parries: each arm goes to its catch, then on to where it turns the blow away, and lets go before the riposte.
	 * For the lead arm "in" (toward the centre line) is a yaw of {@code -side}, out to the side a roll of {@code side}.
	 */
	private static void deflect(HumanoidModel<?> h, int which, float p, int side, ModelPart lead, ModelPart guard) {
		float amount = seg(p, 0.0F, 0.1F) * (1.0F - seg(p, 0.42F, 0.55F));
		float out = seg(p, 0.16F, 0.36F);
		switch (which) {
			case ChronoFlow.HIGH_BLOCK -> {
				// the lead forearm snaps up across the face, then pushes up and out
				arm(lead, -2.2F, -side * 0.9F, 0.0F, -1.85F, -side * 0.15F, side * 0.3F, out, amount);
				set(guard, -1.0F, side * 0.3F, 0.0F, amount);
			}
			case ChronoFlow.OUTSIDE_SWEEP -> {
				// from the centre line, swept outward, the body turning with it
				arm(lead, -1.5F, -side * 0.6F, 0.0F, -1.3F, side * 1.0F, side * 0.2F, out, amount);
				set(guard, -1.2F, side * 0.35F, 0.0F, amount);
				h.body.yRot = side * 0.25F * out * amount;
			}
			case ChronoFlow.SLAP_DOWN -> {
				// an open hand high in front, driving the blow down and across the body
				arm(lead, -1.9F, -side * 0.2F, 0.0F, -0.5F, -side * 0.9F, -side * 0.3F, out, amount);
				set(guard, -1.25F, side * 0.3F, 0.0F, amount);
				h.body.xRot = 0.2F * out * amount;
				h.body.yRot = side * -0.2F * out * amount;
			}
			case ChronoFlow.CROSS_BLOCK -> {
				// both forearms crossed in an X before the chest, then thrown apart
				arm(lead, -1.45F, -side * 0.75F, 0.0F, -1.2F, side * 0.55F, side * 0.35F, out, amount);
				arm(guard, -1.45F, side * 0.75F, 0.0F, -1.2F, -side * 0.55F, -side * 0.35F, out, amount);
				h.body.xRot = 0.12F * amount * (1.0F - out);
			}
			default -> {
				// the elbow raised to the side to take it, then the forearm swung out front
				arm(lead, -0.4F, 0.0F, side * 1.7F, -1.5F, side * 0.8F, side * 0.4F, out, amount);
				set(guard, -1.2F, side * 0.35F, 0.0F, amount);
				h.body.zRot = -side * 0.12F * amount * (1.0F - out);
			}
		}
	}

	/** An arm from one pose to another ({@code t}), applied by {@code amount}. */
	private static void arm(ModelPart part, float x0, float y0, float z0, float x1, float y1, float z1, float t, float amount) {
		set(part, Mth.lerp(t, x0, x1), Mth.lerp(t, y0, y1), Mth.lerp(t, z0, z1), amount);
	}

	/** 0 before {@code a}, 1 after {@code b}, eased between. */
	private static float seg(float p, float a, float b) {
		float t = Mth.clamp((p - a) / (b - a), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
