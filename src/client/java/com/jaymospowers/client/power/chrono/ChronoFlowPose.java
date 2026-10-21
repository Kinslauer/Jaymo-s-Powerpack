package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.PoseBlend;
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
 * <p>
 * (0.15.40) The plain blows (jab, quick jab, hook, uppercut, lunge, the barrage's punches) are whole-body now: each coils
 * first (the fist drawn back, the body turned away), then the hips and shoulders turn through it with a step in, and it
 * recovers into a guard. Every blow, these and the rest, is laid on through {@link PoseBlend}: eased in and out, on springs
 * that carry the fist a little past the strike, the guard held a moment between blows.
 */
public final class ChronoFlowPose {
	/** Share of the time to the hit spent coiling; share of what's after it spent holding the strike before the recovery. */
	public static final float COIL_SHARE = 0.55F, HOLD_SHARE = 0.25F;
	/** The guard held after a blow before it eases out (seconds), and the weight's speed (times its usual). */
	public static final float LINGER = 0.3F, WEIGHT_SPEED = 1.8F;
	/** The blows are quick: the springs this many times as stiff. */
	static final float STIFFNESS = 2.5F;
	/** While walking the stance's legs give way to the walk by this much at the most. */
	static final float WALK_LEGS = 0.6F, STANCE_WALK = 0.6F;

	/**
	 * One key of a blow, for a right-handed lead (mirrored for the left): the lead arm's and the guard arm's pitch,
	 * inward turn and elbow in (radians); the hips' turn ({@code yaw}, + the lead shoulder back) and lean ({@code pitch},
	 * + forward); the front and back legs' swing; the step forward and the drop (model pixels).
	 */
	record Key(float lx, float ly, float lz, float gx, float gy, float gz, float yaw, float pitch, float front, float back, float fwd,
		float drop) {
		Key mix(Key o, float t) {
			return new Key(Mth.lerp(t, lx, o.lx), Mth.lerp(t, ly, o.ly), Mth.lerp(t, lz, o.lz), Mth.lerp(t, gx, o.gx), Mth.lerp(t, gy, o.gy),
				Mth.lerp(t, gz, o.gz), Mth.lerp(t, yaw, o.yaw), Mth.lerp(t, pitch, o.pitch), Mth.lerp(t, front, o.front),
				Mth.lerp(t, back, o.back), Mth.lerp(t, fwd, o.fwd), Mth.lerp(t, drop, o.drop));
		}
	}

	/** Fists up, elbows in, a little sunk, the feet staggered. */
	static final Key GUARD = new Key(-1.15F, 0.3F, 0.2F, -1.25F, 0.3F, 0.25F, 0.0F, 0.05F, -0.15F, 0.15F, 0.0F, 0.3F);
	/** Jab: drawn back, turned away; then straight out, the shoulder through, a step in. */
	static final Key JAB_COIL = new Key(0.45F, 0.0F, 0.05F, -1.3F, 0.3F, 0.25F, 0.4F, -0.03F, -0.15F, 0.15F, -0.4F, 0.3F);
	static final Key JAB_STRIKE = new Key(-1.68F, 0.05F, 0.0F, -1.2F, 0.35F, 0.25F, -0.55F, 0.1F, -0.45F, 0.35F, 1.6F, 0.2F);
	/** The quick jab: the same, shorter. */
	static final Key RAPID_COIL = new Key(0.1F, 0.0F, 0.05F, -1.3F, 0.3F, 0.25F, 0.2F, 0.0F, -0.15F, 0.15F, 0.0F, 0.3F);
	static final Key RAPID_STRIKE = new Key(-1.62F, 0.05F, 0.0F, -1.2F, 0.35F, 0.25F, -0.35F, 0.06F, -0.3F, 0.25F, 1.0F, 0.2F);
	/** Hook: the arm cocked out to the side, the body wound away; then swung round across, the hips turning hard. */
	static final Key HOOK_COIL = new Key(-1.1F, -0.9F, -0.3F, -1.3F, 0.3F, 0.25F, 0.45F, 0.0F, -0.2F, 0.2F, 0.0F, 0.5F);
	static final Key HOOK_STRIKE = new Key(-1.45F, 0.55F, 0.1F, -1.2F, 0.4F, 0.25F, -0.75F, 0.08F, -0.35F, 0.3F, 0.8F, 0.3F);
	/** Uppercut: sunk low, the fist dropped; then driven up from the legs, the body rising and leaning back. */
	static final Key UPPER_COIL = new Key(0.4F, 0.0F, 0.15F, -1.3F, 0.3F, 0.25F, 0.3F, 0.2F, -0.3F, 0.3F, 0.0F, 1.6F);
	static final Key UPPER_STRIKE = new Key(-2.7F, 0.1F, 0.0F, -1.2F, 0.35F, 0.25F, -0.4F, -0.15F, -0.35F, 0.3F, 0.8F, -0.2F);
	/** Lunge: gathered and drawn back; then a long step through, the fist out, the other arm thrown back. */
	static final Key LUNGE_COIL = new Key(0.5F, 0.0F, 0.05F, -1.2F, 0.3F, 0.25F, 0.45F, 0.0F, -0.2F, 0.2F, -0.6F, 0.9F);
	static final Key LUNGE_STRIKE = new Key(-1.7F, 0.05F, 0.0F, 0.6F, 0.0F, -0.2F, -0.5F, 0.3F, -0.7F, 0.6F, 3.0F, 1.0F);
	/** The barrage's punch out (each from the guard and back, the fists alternating). */
	static final Key BARRAGE_STRIKE = new Key(-1.62F, 0.1F, 0.0F, -1.25F, 0.35F, 0.25F, -0.35F, 0.15F, -0.25F, 0.3F, 0.8F, 0.4F);

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("chrono_flow");
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
		PoseBlend.Track track = BLEND.track(avatar.id, pose != null, LINGER, WEIGHT_SPEED);
		if (track == null) {
			return;
		}
		if (pose == null) {
			// between blows or after the last: the guard (or whatever the blow ended on) held, then eased out
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame frame = new PoseBlend.Frame(h);
		if (!blow(frame, pose, avatar)) {
			// the rest keep their own keys, laid on eased
			legacy(h, pose);
			frame.take(h);
		}
		track.apply(h, frame, STIFFNESS);
	}

	/** The plain blows, whole-body: false for the others ({@link #legacy}). */
	private static boolean blow(PoseBlend.Frame f, Pose pose, AvatarRenderState avatar) {
		ChronoFlow.Move move = pose.move();
		float p = pose.p();
		int side = move.side() > 0 ? 1 : -1;
		Key coil, strike;
		switch (move.style()) {
			case ChronoFlow.JAB -> {
				coil = JAB_COIL;
				strike = JAB_STRIKE;
			}
			case ChronoFlow.RAPID -> {
				coil = RAPID_COIL;
				strike = RAPID_STRIKE;
			}
			case ChronoFlow.HOOK -> {
				coil = HOOK_COIL;
				strike = HOOK_STRIKE;
			}
			case ChronoFlow.UPPERCUT -> {
				coil = UPPER_COIL;
				strike = UPPER_STRIKE;
			}
			case ChronoFlow.LUNGE -> {
				coil = LUNGE_COIL;
				strike = LUNGE_STRIKE;
			}
			case ChronoFlow.BARRAGE -> {
				// a punch every few ticks, fists alternating: each driven out fast from the guard and drawn back
				float beat = p * ChronoFlow.BARRAGE_PUNCHES;
				int n = Math.min(ChronoFlow.BARRAGE_PUNCHES - 1, (int)beat);
				float q = beat - n;
				float out = q < 0.4F ? q / 0.4F : 1.0F - (q - 0.4F) / 0.6F;
				lay(f, GUARD.mix(BARRAGE_STRIKE, PoseBlend.smooth(out)), n % 2 == 0 ? 1 : -1, avatar);
				return true;
			}
			default -> {
				return false;
			}
		}
		float hit = Mth.clamp(move.hitAt() / (float)move.duration(), 0.05F, 0.95F);
		float c = hit * COIL_SHARE;
		Key key;
		if (p < c) {
			key = GUARD.mix(coil, PoseBlend.seg(p, 0.0F, c));
		} else if (p < hit) {
			key = coil.mix(strike, PoseBlend.seg(p, c, hit));
		} else {
			key = strike.mix(GUARD, PoseBlend.seg(p, hit + (1.0F - hit) * HOLD_SHARE, 1.0F));
		}
		lay(f, key, side, avatar);
		return true;
	}

	/** One key on the frame, for a blow led by {@code side}'s arm (1 the right). */
	private static void lay(PoseBlend.Frame f, Key k, int side, AvatarRenderState avatar) {
		float yaw = k.yaw() * side;
		int lead = side > 0 ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM;
		int guard = side > 0 ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		// the arms aimed from the front: the hips' turn and lean taken back out of them (all of it from the lead, half
		// from the guard, which goes round with the chest)
		f.set(lead, k.lx() - k.pitch(), -side * k.ly() - yaw, -side * k.lz());
		f.set(guard, k.gx() - k.pitch(), side * k.gy() - yaw * 0.5F, side * k.gz());
		f.set(PoseBlend.HIP_YAW, yaw);
		f.set(PoseBlend.HIP_PITCH, k.pitch());
		f.set(PoseBlend.FORWARD, k.fwd());
		f.set(PoseBlend.DROP, k.drop());
		// the eyes stay on the foe
		f.add(PoseBlend.HEAD, -k.pitch());
		f.add(PoseBlend.HEAD + 1, -yaw);
		// the step: the front foot (the guard's side) forward, the back one behind; walking takes over some of it
		float stance = 1.0F - WALK_LEGS * Mth.clamp(avatar.walkAnimationSpeed / STANCE_WALK, 0.0F, 1.0F);
		int front = side > 0 ? PoseBlend.LEFT_LEG : PoseBlend.RIGHT_LEG;
		int back = side > 0 ? PoseBlend.RIGHT_LEG : PoseBlend.LEFT_LEG;
		f.mix(front, k.front(), 0.0F, -side * 0.05F, stance);
		f.mix(back, k.back(), 0.0F, side * 0.05F, stance);
	}

	/** The other blows' own keys, set straight on the model (taken from it into the frame after). */
	private static void legacy(HumanoidModel<?> h, Pose pose) {
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
			// (0.15.40) the jab, quick jab, hook, uppercut, lunge and barrage are blow()'s now
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
