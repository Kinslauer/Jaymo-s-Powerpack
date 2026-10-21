package com.jaymospowers.client.power.cryo;

import com.jaymospowers.power.cryo.CryoBlizzard;
import com.jaymospowers.power.cryo.CryoMoves;
import com.jaymospowers.power.cryo.CryoSlide;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.15.32) Cryokinesis' body poses, on everyone's screen (third person; from the synced {@link CryoSlide#SLIDING},
 * {@link CryoBlizzard#STORM} and {@link CryoMoves#MOVE}). The held ones (the slide, the breath, the Blizzard) come and go
 * on small springs ticked here per player, so they ease in, overshoot a hair and settle, and ease out after the move
 * ends; the one-shots run on eased curves with a wind-up and a follow-through. The torso turns at the waist and carries
 * the head and shoulders with it (as {@code WebArms}' heave).
 * <ul>
 * <li>R Ice Slide: a skater's crouch, torso low and forward, legs apart pushing off in turn as you speed up, arms out for
 * balance and swinging against the turn, the whole body leaning into it.</li>
 * <li>G Frost Breath: braced on the legs, head and chest pushed forward; the hand goes to the mouth, then is thrown
 * forward along the breath and held there, trembling.</li>
 * <li>V Ice Shards: a short wind-up, then a fanned sweep of the throwing arm across the body with a step and a twist of
 * the torso, carried through past the release.</li>
 * <li>B Flash Freeze: both hands drawn in to the chest, then pushed out at the target.</li>
 * <li>X Ice Wall: a dip down low, then both palms driven up from the ground as the wall rises.</li>
 * <li>C Blizzard: both arms rise overhead and spread, held while it rages, swaying with the wind and shivering.</li>
 * </ul>
 */
public final class CryoPose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:cryo_pose");

	// ---- tuning ----
	/** The springs every held pose comes and goes on: pull per tick, and velocity kept per tick (a small overshoot). */
	static final float SPRING = 0.22F;
	static final float DAMPING = 0.68F;
	/** The lean into a turn: radians per (degree a tick of turning × block a tick of speed), the most, and its springs. */
	static final float LEAN_GAIN = 0.12F;
	static final float LEAN_MAX = 0.4F;
	static final float LEAN_SPRING = 0.15F;
	static final float LEAN_DAMPING = 0.75F;
	/** Ice Slide: the sprint top speed (blocks a tick, as {@code CryoSlideClient}), the crouch, the stride and the arms. */
	static final float SLIDE_TOP = 0.85F;
	static final float SLIDE_CROUCH = 0.42F;
	static final float SLIDE_STRIDE = 0.55F;
	static final float SLIDE_ARMS_OUT = 1.05F;
	static final float SLIDE_ARM_SWING = 0.9F;
	/** Frost Breath: ticks to the mouth and then thrown out, the chest pushed forward, and the tremble. */
	static final float BREATH_TO_MOUTH = 5.0F;
	static final float BREATH_THROW = 4.0F;
	static final float BREATH_LEAN = 0.22F;
	static final float BREATH_TREMBLE = 0.03F;
	/** Blizzard: ticks for the arms to rise, then to spread; the sway and the shiver. */
	static final float BLIZZARD_RISE = 10.0F;
	static final float BLIZZARD_SPREAD = 8.0F;
	static final float BLIZZARD_SWAY = 0.1F;
	static final float BLIZZARD_SHIVER = 0.02F;
	/** The torso turns at the waist, this far (model units) under the neck. */
	static final float WAIST = 12.0F;

	/**
	 * This frame: {@code slide}/{@code breath}/{@code blizzard} each 0-1 (a little over in the overshoot), {@code lean} the
	 * lean into a turn (+ to the right), {@code speed} 0-1 of top speed, {@code push} 0-1 how hard the legs push,
	 * {@code stride} the push cycle's phase, the breath's and the Blizzard's ages (ticks), the one-shot ({@code kind}, a
	 * {@link CryoMoves} kind or 0, {@code t} ticks into it) and the clock (the player's own ticks, for the trembles).
	 */
	public record Pose(float slide, float lean, float speed, float push, float stride, float breath, float breathAge, float blizzard,
		float blizzardAge, int kind, float t, float time) {
	}

	/** A damped spring, one step a tick. */
	private static final class Spring {
		float value, velocity, prev;

		void step(float target, float stiffness, float damping) {
			this.prev = this.value;
			this.velocity = (this.velocity + (target - this.value) * stiffness) * damping;
			this.value += this.velocity;
		}

		float at(float partialTicks) {
			return Mth.lerp(partialTicks, this.prev, this.value);
		}
	}

	/** A player's springy state. */
	private static final class Body {
		final Spring slide = new Spring(), lean = new Spring(), push = new Spring(), breath = new Spring(), blizzard = new Spring();
		float stride, prevStride;
		float speed, prevSpeed;
		float breathAge, blizzardAge;
		float heading = Float.NaN;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private CryoPose() {
	}

	/** Every client tick (registered in {@link CryoPowerClient#init}). */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (AbstractClientPlayer player : level.players()) {
			boolean sliding = CryoSlide.active(player) && !player.isPassenger();
			CryoMoves.Move move = CryoMoves.get(player);
			boolean breathing = move != null && move.kind() == CryoMoves.BREATH;
			Long storm = player.getAttached(CryoBlizzard.STORM);
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (!sliding && !breathing && storm == null) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			// how it's moving: the local skater's own velocity, anyone else's from their position
			Vec3 v = player == minecraft.player && CryoSlideClient.skating() ? CryoSlideClient.velocity()
				: new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
			float speed = (float)Math.sqrt(v.x * v.x + v.z * v.z);
			// turning: how far the heading swung this tick (degrees, + to the right; MC yaw grows turning right)
			float turn = 0.0F;
			if (speed > 0.05F) {
				float heading = (float)(Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG);
				if (!Float.isNaN(body.heading)) {
					turn = Mth.wrapDegrees(heading - body.heading);
				}
				body.heading = heading;
			} else {
				body.heading = Float.NaN;
			}
			body.prevSpeed = body.speed;
			float accel = speed - body.speed;
			body.speed = speed;
			body.prevStride = body.stride;
			if (sliding && player.onGround()) {
				body.stride += 0.25F + speed * 0.6F;
			}
			body.slide.step(sliding ? 1.0F : 0.0F, SPRING, DAMPING);
			float lean = sliding ? Mth.clamp(turn * speed * LEAN_GAIN, -LEAN_MAX, LEAN_MAX) : 0.0F;
			body.lean.step(lean, LEAN_SPRING, LEAN_DAMPING);
			// the legs push while it picks up speed (and a little always, gliding)
			float pushing = sliding && player.onGround() ? Mth.clamp(0.25F + accel * 25.0F, 0.0F, 1.0F) : 0.0F;
			body.push.step(pushing, SPRING * 0.6F, DAMPING);
			body.breath.step(breathing ? 1.0F : 0.0F, SPRING, DAMPING);
			if (breathing) {
				body.breathAge = now - move.start();
			}
			body.blizzard.step(storm != null ? 1.0F : 0.0F, SPRING * 0.5F, DAMPING);
			if (storm != null) {
				body.blizzardAge = now - storm;
			}
		}
		BODIES.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null || idle(entry.getValue()));
	}

	private static boolean idle(Body body) {
		for (Spring s : new Spring[] {body.slide, body.breath, body.blizzard}) {
			if (Math.abs(s.value) > 0.002F || Math.abs(s.prev) > 0.002F || Math.abs(s.velocity) > 0.002F) {
				return false;
			}
		}
		return true;
	}

	/** From the avatar renderer's extractRenderState (via {@code CryoAvatarRendererMixin}). */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(entity instanceof Player player) || minecraft.level == null || player.isSleeping() || player.isPassenger()) {
			state.setData(KEY, null);
			return;
		}
		// ages from whole ticks first, so a long-running world's game time doesn't lose the fraction to float rounding
		long gameTime = minecraft.level.getGameTime();
		float now = player.tickCount + partialTicks;
		int kind = 0;
		float t = 0.0F;
		CryoMoves.Move move = CryoMoves.get(player);
		if (move != null) {
			int length = CryoMoves.length(move.kind());
			float age = (gameTime - move.start()) + partialTicks;
			if (length > 0 && age >= 0.0F && age < length) {
				kind = move.kind();
				t = age;
			}
		}
		Body body = BODIES.get(player.getId());
		if (body == null && kind == 0) {
			state.setData(KEY, null);
			return;
		}
		if (body == null) {
			state.setData(KEY, new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, kind, t, now));
			return;
		}
		float speed = Mth.clamp(Mth.lerp(partialTicks, body.prevSpeed, body.speed) / SLIDE_TOP, 0.0F, 1.0F);
		// the held moves' ages run on between ticks while they last (and stop where they were once they end)
		float breathAge = body.breathAge + (move != null && move.kind() == CryoMoves.BREATH ? partialTicks : 0.0F);
		float blizzardAge = body.blizzardAge + (CryoBlizzard.raging(player) ? partialTicks : 0.0F);
		state.setData(KEY, new Pose(body.slide.at(partialTicks), body.lean.at(partialTicks), speed, body.push.at(partialTicks),
			Mth.lerp(partialTicks, body.prevStride, body.stride), body.breath.at(partialTicks), breathAge, body.blizzard.at(partialTicks),
			blizzardAge, kind, t, now));
	}

	/** After a humanoid model animates (via {@code CryoModelFeatureRendererMixin} and {@code ModelPoses}). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean leftMain = avatar.mainArm == HumanoidArm.LEFT;
		ModelPart main = leftMain ? h.leftArm : h.rightArm;
		ModelPart off = leftMain ? h.rightArm : h.leftArm;
		ModelPart mainLeg = leftMain ? h.leftLeg : h.rightLeg;
		ModelPart offLeg = leftMain ? h.rightLeg : h.leftLeg;
		// +1 for the right arm: zRot times this is out from the body, yRot times minus this in across it
		float mainOut = leftMain ? -1.0F : 1.0F;
		// the torso at the waist (pitch + forward, yaw + turns the main shoulder back for a right-hander, roll + to the left)
		float[] torso = new float[3];
		if (pose.slide() > 0.001F) {
			slide(h, pose, torso);
		}
		if (pose.blizzard() > 0.001F) {
			blizzard(h, pose, torso);
		}
		if (pose.breath() > 0.001F) {
			breath(h, main, off, mainLeg, offLeg, mainOut, pose, torso);
		}
		switch (pose.kind()) {
			case CryoMoves.SHARDS -> shards(h, main, off, mainLeg, offLeg, mainOut, pose.t(), torso);
			case CryoMoves.FREEZE -> freeze(h, mainLeg, offLeg, pose.t(), torso);
			case CryoMoves.WALL -> wall(h, pose.t(), torso);
			default -> {
			}
		}
		// (crouching, the walk's own crouch has moved the parts already: the torso stays as it is)
		if (!avatar.isCrouching) {
			turnTorso(h, torso[0], torso[1], torso[2]);
		}
	}

	// ---------------------------------------------------------------- the poses

	/** R Ice Slide: the skater's crouch, the push and glide, arms out for balance, leaning into the turn. */
	private static void slide(HumanoidModel<?> h, Pose pose, float[] torso) {
		float a = Math.min(pose.slide(), 1.1F);
		float lean = pose.lean();
		float push = pose.push();
		float speed = pose.speed();
		float s = Mth.sin(pose.stride() * 0.5F);
		// legs apart, each pushing out and back in turn; bent a little forward under the low torso
		float rightPush = Math.max(0.0F, s) * push;
		float leftPush = Math.max(0.0F, -s) * push;
		set(h.rightLeg, -0.3F + rightPush * SLIDE_STRIDE, 0.1F, 0.14F + rightPush * 0.35F + lean * 0.3F, a);
		set(h.leftLeg, -0.3F + leftPush * SLIDE_STRIDE, -0.1F, -0.14F - leftPush * 0.35F + lean * 0.3F, a);
		// arms out for balance; the pair swings against the turn, and each counter-swings its own side's leg
		float swing = -lean * SLIDE_ARM_SWING;
		float out = SLIDE_ARMS_OUT * (0.75F + 0.25F * speed);
		set(h.rightArm, -0.35F - s * 0.3F * push, -0.2F, out + swing, a);
		set(h.leftArm, -0.35F + s * 0.3F * push, 0.2F, -out + swing, a);
		// low and forward, faster is lower; lean into the turn (roll - leans right), the shoulders twist a little with the push
		torso[0] += (SLIDE_CROUCH + speed * 0.12F) * a;
		torso[2] += -lean * a;
		torso[1] += s * 0.12F * push * a;
		// the head looks ahead, up against the crouch
		h.head.xRot -= SLIDE_CROUCH * 0.7F * a;
	}

	/** G Frost Breath: hand to the mouth, then thrown forward; braced, chest and head pushed out, trembling. */
	private static void breath(HumanoidModel<?> h, ModelPart main, ModelPart off, ModelPart mainLeg, ModelPart offLeg, float mainOut, Pose pose,
		float[] torso) {
		float a = Math.min(pose.breath(), 1.1F);
		float t = pose.breathAge();
		float tremble = Mth.sin(pose.time() * 2.9F) * BREATH_TREMBLE + Mth.sin(pose.time() * 4.3F + 1.0F) * BREATH_TREMBLE * 0.5F;
		float toMouth = smooth(t / BREATH_TO_MOUTH);
		float thrown = smooth((t - BREATH_TO_MOUTH) / BREATH_THROW);
		// at the mouth: raised high and turned in across the face; thrown: along the look
		float x = Mth.lerp(thrown, -2.25F, h.head.xRot - Mth.HALF_PI + 0.1F) + tremble;
		float y = Mth.lerp(thrown, -mainOut * 0.75F, h.head.yRot - mainOut * 0.05F);
		float z = Mth.lerp(thrown, -mainOut * 0.15F, mainOut * 0.05F);
		set(main, Mth.lerp(toMouth, main.xRot, x), Mth.lerp(toMouth, main.yRot, y), Mth.lerp(toMouth, main.zRot, z), a);
		// the other arm braced back and out
		set(off, 0.35F - tremble, 0.0F, -mainOut * 0.3F, a);
		// legs set, one forward one back
		set(offLeg, -0.35F, 0.0F, -mainOut * 0.06F, a);
		set(mainLeg, 0.3F, 0.0F, mainOut * 0.06F, a);
		// chest pushed into it (more once the hand's thrown), the head forward along it
		torso[0] += (BREATH_LEAN * (0.5F + 0.5F * thrown) + tremble * 0.5F) * a;
		h.head.xRot -= BREATH_LEAN * 0.6F * a;
	}

	/** V Ice Shards: wind-up, a fanned sweep across the body with a step, follow-through. */
	private static void shards(HumanoidModel<?> h, ModelPart main, ModelPart off, ModelPart mainLeg, ModelPart offLeg, float mainOut, float t,
		float[] torso) {
		float wind = smooth(t / 3.0F);
		float sweep = easeOut((t - 2.0F) / 4.0F);
		float settle = smooth((t - 8.0F) / 6.0F);
		float a = wind * (1.0F - settle);
		// out to the side and back, then swept across and down in front, carried on past
		float x = Mth.lerp(sweep, -1.35F, -0.85F);
		float y = Mth.lerp(sweep, mainOut * 0.95F, -mainOut * 0.95F);
		float z = Mth.lerp(sweep, mainOut * 0.55F, -mainOut * 0.15F);
		set(main, x, y, z, a);
		// the other arm forward for aim, pulled back against the sweep
		set(off, Mth.lerp(sweep, -1.2F, -0.3F), Mth.lerp(sweep, mainOut * 0.4F, -mainOut * 0.2F), -mainOut * 0.2F, a);
		// step: the off-side foot goes forward on the sweep, the main side's pushes back
		set(offLeg, -0.45F * sweep, 0.0F, -mainOut * 0.05F, a);
		set(mainLeg, 0.35F * sweep, 0.0F, mainOut * 0.05F, a);
		// the torso coils (main shoulder back), then unwinds through and past
		torso[1] += mainOut * Mth.lerp(sweep, 0.45F, -0.5F) * a;
		torso[0] += 0.15F * sweep * a;
		// the head follows the throw
		h.head.yRot += -mainOut * 0.25F * sweep * a;
	}

	/** B Flash Freeze: both hands drawn in to the chest, then pushed out at the target. */
	private static void freeze(HumanoidModel<?> h, ModelPart mainLeg, ModelPart offLeg, float t, float[] torso) {
		float draw = smooth(t / 2.5F);
		float push = easeOut((t - 2.0F) / 3.0F);
		float settle = smooth((t - 8.0F) / 6.0F);
		float a = draw * (1.0F - settle);
		float aim = h.head.xRot - Mth.HALF_PI;
		float x = Mth.lerp(push, -1.0F, aim);
		set(h.rightArm, x, Mth.lerp(push, -0.65F, h.head.yRot - 0.18F), 0.0F, a);
		set(h.leftArm, x, Mth.lerp(push, 0.65F, h.head.yRot + 0.18F), 0.0F, a);
		set(offLeg, -0.3F * push, 0.0F, 0.0F, a);
		set(mainLeg, 0.25F * push, 0.0F, 0.0F, a);
		torso[0] += Mth.lerp(push, -0.08F, 0.2F) * a;
	}

	/** X Ice Wall: dip down low, then both palms driven up from the ground. */
	private static void wall(HumanoidModel<?> h, float t, float[] torso) {
		float dip = smooth(t / 3.0F);
		float rise = easeOut((t - 3.0F) / 4.0F);
		float settle = smooth((t - 10.0F) / 6.0F);
		float a = dip * (1.0F - settle);
		// low and forward, palms down near the ground; then up overhead, a little forward
		float x = Mth.lerp(rise, -0.45F, -2.65F);
		set(h.rightArm, x, -0.15F, Mth.lerp(rise, 0.1F, 0.3F), a);
		set(h.leftArm, x, 0.15F, Mth.lerp(rise, -0.1F, -0.3F), a);
		set(h.rightLeg, Mth.lerp(rise, -0.5F, 0.0F), 0.0F, 0.12F, a);
		set(h.leftLeg, Mth.lerp(rise, -0.5F, 0.0F), 0.0F, -0.12F, a);
		torso[0] += Mth.lerp(rise, 0.45F, -0.12F) * a;
		h.head.xRot += Mth.lerp(rise, 0.2F, -0.35F) * a;
	}

	/** C Blizzard: arms rise overhead and spread, held, swaying with the storm and shivering. */
	private static void blizzard(HumanoidModel<?> h, Pose pose, float[] torso) {
		float a = Math.min(pose.blizzard(), 1.1F);
		float age = pose.blizzardAge();
		float time = pose.time();
		float rise = smooth(age / BLIZZARD_RISE);
		float spread = smooth((age - BLIZZARD_RISE * 0.6F) / BLIZZARD_SPREAD);
		float sway = Mth.sin(time * 0.11F) * BLIZZARD_SWAY + Mth.sin(time * 0.27F + 2.0F) * BLIZZARD_SWAY * 0.4F;
		float shiver = Mth.sin(time * 3.7F) * BLIZZARD_SHIVER;
		float x = Mth.lerp(rise, 0.0F, -2.85F);
		float z = Mth.lerp(spread, 0.15F, 0.85F);
		set(h.rightArm, x + sway * 0.5F + shiver, 0.0F, z + sway + shiver, a);
		set(h.leftArm, x - sway * 0.5F - shiver, 0.0F, -z + sway - shiver, a);
		set(h.rightLeg, 0.0F, 0.0F, 0.12F, a * 0.8F);
		set(h.leftLeg, 0.0F, 0.0F, -0.12F, a * 0.8F);
		// arched back a little into it, swaying side to side, the head thrown back
		torso[0] += (-0.12F * rise) * a;
		torso[2] += sway * 0.5F * a;
		h.head.xRot -= 0.35F * rise * a;
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Turns the torso at the waist: the body, and the head and shoulders riding on it, swing round a point {@link #WAIST}
	 * under the neck (as {@code WebArms.heave}). Set from the parts' rest pose, so a second pass in a frame is the same.
	 */
	private static void turnTorso(HumanoidModel<?> h, float pitch, float yaw, float roll) {
		if (Math.abs(pitch) < 1.0E-4F && Math.abs(yaw) < 1.0E-4F && Math.abs(roll) < 1.0E-4F) {
			return;
		}
		Quaternionf turn = new Quaternionf().rotationZYX(roll, yaw, pitch);
		PartPose rest = h.body.getInitialPose();
		float neckY = rest.y();
		Vector3f neck = turned(turn, neckY, rest.x(), rest.y(), rest.z());
		h.body.x = neck.x;
		h.body.y = neck.y;
		h.body.z = neck.z;
		h.body.xRot += pitch;
		h.body.yRot += yaw;
		h.body.zRot += roll;
		PartPose headRest = h.head.getInitialPose();
		Vector3f head = turned(turn, neckY, headRest.x(), headRest.y(), headRest.z());
		h.head.x = head.x;
		h.head.y = head.y;
		h.head.z = head.z;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			PartPose armRest = arm.getInitialPose();
			Vector3f shoulder = turned(turn, neckY, armRest.x(), armRest.y(), armRest.z());
			arm.x = shoulder.x;
			arm.y = shoulder.y;
			arm.z = shoulder.z;
			// the arm goes round with the chest
			arm.yRot += yaw;
			arm.zRot += roll;
		}
	}

	private static Vector3f turned(Quaternionf turn, float neckY, float x, float y, float z) {
		Vector3f p = new Vector3f(x, y - neckY - WAIST, z);
		turn.transform(p);
		return p.add(0.0F, neckY + WAIST, 0.0F);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}

	/** Ease in and out. */
	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Fast out, soft landing (the strike's snap). */
	private static float easeOut(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		float u = 1.0F - x;
		return 1.0F - u * u * u;
	}
}
