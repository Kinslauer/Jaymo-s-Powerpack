package com.jaymospowers.client;

import com.jaymospowers.client.power.chrono.ChronoFlowPose;
import com.jaymospowers.client.power.cryo.CryoPose;
import com.jaymospowers.client.power.ember.EmberKindleClient;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.core.Carry;
import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.cryo.CryoMoves;
import com.jaymospowers.power.ember.EmberKindle;
import com.jaymospowers.power.gravity.GravityPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.42) The big moves in your own view: vanilla's first-person bare arm, posed. Driven from the same state the
 * third-person poses read (the render state's keys, the synced attachments) and eased the same way ({@link PoseBlend}
 * weights), so first and third person agree. Drawn from {@code PowerArmsFirstPersonMixin} (each hand, before vanilla's
 * {@code renderPlayerArm}) and {@code CarryFirstPersonMixin}.
 * <ul>
 * <li>Ember's Kindle: the main arm draws back, then comes up into the middle pushing out, palm forward, trembling more
 * as the heat builds; let go at full heat it whips up and forward with the throw.</li>
 * <li>Carrying: both arms rise in under the load (the held items sink out as they come), bob with your steps, and sink
 * away again when you let go.</li>
 * <li>Time Flow: the guard's fists come up, the lead fist coils back, punches into the middle and recoils, on the
 * third-person timing (jab, quick jab, hook, uppercut, lunge, the barrage's alternating fists, the time-stop push and
 * the counters' block and palm strike).</li>
 * <li>Gravity's Lift: the main arm reaches out at the held creature, following it, straining.</li>
 * <li>Cryokinesis: Frost Breath's hand to the mouth, then thrown out; Ice Shards' sweep across (the off hand aiming);
 * Flash Freeze's two hands to the chest and pushed out; the Ice Wall's palms dipped low and driven up out of view; the
 * Blizzard's arms raised to the top of the view and spread, swaying.</li>
 * </ul>
 * Only a hand holding nothing is posed (a held item, Better Combat's swings and vanilla's bob are left alone). The main
 * hand eases out of and back into vanilla's own bare arm; the off hand (which vanilla doesn't draw empty) rises in from
 * below. Nothing here with First-person Model drawing your body: its arms take the third-person poses. Render thread only.
 */
public final class FirstPersonArms {
	// ---- tuning: view space, blocks and degrees. "In" is toward the middle of the view; "forward" away from you.

	/** The shoulder each arm turns about, for the right arm (x right, y up, z back toward you; mirrored for the left). */
	static final float SHOULDER_X = 0.75F, SHOULDER_Y = -0.85F, SHOULDER_Z = 0.0F;
	/** How far below the view the empty off hand starts (and goes back to). */
	static final float ENTRY_DROP = 0.6F;
	/** The arms' springs (per second squared) and damping ratio (under 1 overshoots a little), as PoseBlend's arms. */
	static final float ARM_STIFFNESS = 260.0F, ARM_DAMPING = 0.62F;
	/** Integration step and the longest gap taken in one go (seconds). */
	static final float SUBSTEP = 0.008F, MAX_DT = 0.1F;
	/** Under this weight a move is off; the most the moves together may weigh (PoseBlend's overshoot). */
	static final float ACTIVE = 0.002F, WEIGHT_CAP = 1.12F;

	// a pose: in, up, forward (blocks); raised, turned in, tipped in (degrees, about the shoulder)
	static final int X = 0, Y = 1, Z = 2, PITCH = 3, YAW = 4, ROLL = 5, SLOTS = 6;

	/** Kindle: the coil, pushed out (palm forward), the throw's wind over the shoulder and the drive through. */
	static final float[] KINDLE_COIL = {-0.05F, -0.08F, -0.15F, -6.0F, 0.0F, 0.0F};
	static final float[] KINDLE_PUSH = {0.26F, 0.12F, 0.2F, 26.0F, 8.0F, 14.0F};
	static final float[] KINDLE_WIND = {0.12F, 0.3F, -0.08F, 52.0F, 4.0F, 6.0F};
	static final float[] KINDLE_THROUGH = {0.32F, 0.04F, 0.48F, -6.0F, 12.0F, 10.0F};
	/** Kindle's timing (seconds; as EmberKindleClient's), the heat that throws, and the tremble (blocks, degrees, beats). */
	static final float KINDLE_COIL_TIME = 0.18F, KINDLE_WHIP_UP_TIME = 0.08F, KINDLE_WHIP_TIME = 0.42F, KINDLE_THROW_HEAT = 0.99F;
	static final float KINDLE_TREMBLE = 0.008F, KINDLE_TREMBLE_TURN = 1.6F, KINDLE_BEAT = 47.0F, KINDLE_BEAT_SIDE = 39.0F;

	/** Carrying: how far below the arms start; the step's bob (blocks) and rock (degrees); the walk's beat (vanilla's). */
	static final float CARRY_DROP = 0.5F, CARRY_BOB = 0.035F, CARRY_ROCK = 2.5F, WALK_BEAT = 0.6662F;
	/** Carrying: the held items stop being drawn once the arms are this far in. */
	public static final float CARRY_HANDOFF = 0.95F;

	/** Time Flow: the guard; each blow's coil and strike for the lead fist; the lunge's back arm; the recoil. */
	static final float[] CHRONO_GUARD = {0.1F, 0.1F, 0.0F, 14.0F, 6.0F, 6.0F};
	static final float[] JAB_COIL = {0.0F, 0.0F, -0.18F, 4.0F, 0.0F, 0.0F};
	static final float[] JAB_STRIKE = {0.3F, 0.18F, 0.42F, 22.0F, 10.0F, 8.0F};
	static final float[] RAPID_COIL = {0.06F, 0.06F, -0.06F, 10.0F, 2.0F, 2.0F};
	static final float[] RAPID_STRIKE = {0.28F, 0.16F, 0.34F, 20.0F, 8.0F, 6.0F};
	static final float[] HOOK_COIL = {-0.2F, 0.12F, -0.05F, 16.0F, -25.0F, -10.0F};
	static final float[] HOOK_STRIKE = {0.5F, 0.16F, 0.25F, 18.0F, 35.0F, 20.0F};
	static final float[] UPPER_COIL = {0.08F, -0.25F, -0.05F, -10.0F, 0.0F, 0.0F};
	static final float[] UPPER_STRIKE = {0.26F, 0.36F, 0.25F, 55.0F, 6.0F, 6.0F};
	static final float[] LUNGE_COIL = {0.0F, 0.02F, -0.22F, 4.0F, 0.0F, 0.0F};
	static final float[] LUNGE_STRIKE = {0.3F, 0.16F, 0.55F, 20.0F, 10.0F, 8.0F};
	static final float[] LUNGE_BACK = {-0.1F, -0.45F, -0.2F, -20.0F, 0.0F, 0.0F};
	static final float RECOIL_BACK = 0.07F, RECOIL_DROP = 6.0F;
	/** Time Flow's time-stop push (palms to the chest, then out) and the counters' high block and palm strike. */
	static final float[] PUSH_DRAW = {0.3F, 0.0F, -0.1F, 10.0F, 20.0F, 30.0F};
	static final float[] PUSH_OUT = {0.28F, 0.14F, 0.4F, 26.0F, 12.0F, 18.0F};
	static final float[] COUNTER_BLOCK = {0.35F, 0.28F, 0.05F, 38.0F, 20.0F, 45.0F};
	static final float[] COUNTER_PALM = {0.28F, 0.16F, 0.45F, 24.0F, 10.0F, 20.0F};

	/** Lift: the reach, how much of the target's offset from the look the arm follows, the most it turns, the strain. */
	static final float[] GRIP_REACH = {0.18F, 0.1F, 0.3F, 18.0F, 4.0F, 8.0F};
	static final float GRIP_FOLLOW = 0.6F, GRIP_AIM_MAX = 40.0F;
	static final float GRIP_STRAIN = 1.2F, GRIP_STRAIN_BEAT = 1.7F, GRIP_TREMOR = 0.004F;

	/** Frost Breath: the hand at the mouth, thrown out; ticks for each (as CryoPose's); the tremble (degrees, blocks). */
	static final float[] BREATH_MOUTH = {0.5F, 0.05F, -0.2F, 25.0F, 25.0F, 40.0F};
	static final float[] BREATH_OUT = {0.3F, 0.16F, 0.35F, 24.0F, 10.0F, 10.0F};
	static final float BREATH_TO_MOUTH = 5.0F, BREATH_THROW = 4.0F, BREATH_TREMBLE = 1.5F, BREATH_SHAKE = 0.004F;
	/** Ice Shards: the throwing arm out to the side, swept across; the off hand aiming, pulled back. */
	static final float[] SHARDS_OUT = {-0.25F, 0.12F, -0.1F, 20.0F, -30.0F, -15.0F};
	static final float[] SHARDS_ACROSS = {0.65F, 0.0F, 0.25F, 8.0F, 45.0F, 10.0F};
	static final float[] SHARDS_AIM = {0.2F, 0.12F, 0.25F, 20.0F, 8.0F, 0.0F};
	static final float[] SHARDS_BACK = {-0.1F, -0.4F, -0.2F, -15.0F, 0.0F, 0.0F};
	/** Flash Freeze: both hands at the chest, then pushed out. */
	static final float[] FREEZE_CHEST = {0.38F, 0.02F, -0.12F, 12.0F, 25.0F, 35.0F};
	static final float[] FREEZE_PUSH = {0.3F, 0.15F, 0.42F, 24.0F, 12.0F, 20.0F};
	/** Ice Wall: palms low, then driven up out of the top of the view. */
	static final float[] WALL_LOW = {0.1F, -0.3F, 0.1F, -15.0F, 4.0F, 0.0F};
	static final float[] WALL_UP = {0.15F, 0.55F, 0.2F, 65.0F, 4.0F, -5.0F};
	/** Blizzard: arms raised to the top of the view, then spread; ticks for each (as CryoPose's); the sway and shiver. */
	static final float[] BLIZZARD_RAISED = {0.0F, 0.45F, 0.1F, 60.0F, 0.0F, 0.0F};
	static final float[] BLIZZARD_SPREAD = {-0.25F, 0.45F, 0.1F, 60.0F, -6.0F, -20.0F};
	static final float BLIZZARD_RISE = 10.0F, BLIZZARD_SPREAD_TICKS = 8.0F, BLIZZARD_SWAY = 4.0F, BLIZZARD_SHIVER = 1.0F;

	private static final PoseBlend.Channel KINDLE = PoseBlend.channel("first_person_kindle");
	private static final PoseBlend.Channel CHRONO = PoseBlend.channel("first_person_chrono_flow");
	private static final PoseBlend.Channel GRIP = PoseBlend.channel("first_person_gravity_grip");
	private static final PoseBlend.Channel CARRY = PoseBlend.channel("first_person_carry");

	/** Each hand's springs (0 the main hand, 1 the off hand). */
	private static final Arm[] ARMS = {new Arm(), new Arm()};
	/** How far the held items are sunk for the carry arms coming in or going (0-1, vanilla's equip height). */
	private static float carrySink;

	private FirstPersonArms() {
	}

	// ---------------------------------------------------------------- the hands

	/**
	 * Poses one first-person arm ({@code main}: the main hand's; {@code right}: it's the right arm) for whatever move is
	 * on, before vanilla's renderPlayerArm draws it. {@code empty}: the hand holds nothing. Returns the moves' weight (0-1:
	 * how much of vanilla's own swing to take away), or -1 to leave the hand to vanilla.
	 */
	public static float pose(PoseStack poseStack, AvatarRenderState avatar, boolean main, boolean right, boolean empty, float partialTicks) {
		Arm arm = ARMS[main ? 0 : 1];
		LocalPlayer self = Minecraft.getInstance().player;
		if (self == null || avatar.id != self.getId() || !empty || carrySink > 0.0F || self.getAttached(Carry.CARRYING) != null
			|| FirstPersonCompat.bodyShown()) {
			arm.live = false;
			return -1.0F;
		}
		Mix mix = new Mix();
		kindle(mix, self, avatar, main);
		chrono(mix, avatar, right);
		grip(mix, self, avatar, main, right, partialTicks);
		cryo(mix, avatar, main);
		float w = mix.weight;
		if (w > WEIGHT_CAP) {
			float scale = WEIGHT_CAP / w;
			for (int i = 0; i < SLOTS; i++) {
				mix.off[i] *= scale;
				mix.raw[i] *= scale;
			}
			w = WEIGHT_CAP;
		}
		float[] target = mix.off;
		if (!main) {
			// the empty off hand isn't drawn by vanilla: it rises in from below
			target[Y] -= (1.0F - Mth.clamp(w, 0.0F, 1.0F)) * ENTRY_DROP;
		}
		boolean active = w > ACTIVE || arm.live && (main ? arm.moving() : arm.value[Y] > -ENTRY_DROP * 0.9F);
		if (!active) {
			arm.live = false;
			return -1.0F;
		}
		arm.step(target, main);
		float side = right ? 1.0F : -1.0F;
		float[] v = arm.value;
		float[] r = mix.raw;
		poseStack.translate(-side * (v[X] + r[X]), v[Y] + r[Y], -(v[Z] + r[Z]));
		poseStack.translate(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z);
		poseStack.rotateDegrees(Axis.XP, v[PITCH] + r[PITCH]);
		poseStack.rotateDegrees(Axis.YP, side * (v[YAW] + r[YAW]));
		poseStack.rotateDegrees(Axis.ZP, side * (v[ROLL] + r[ROLL]));
		poseStack.translate(-side * SHOULDER_X, -SHOULDER_Y, -SHOULDER_Z);
		return Mth.clamp(w, 0.0F, 1.0F);
	}

	/** Ember's Kindle, on the main hand (the flame needs it empty). */
	private static void kindle(Mix mix, LocalPlayer self, AvatarRenderState avatar, boolean main) {
		if (!main) {
			return;
		}
		boolean on = Boolean.TRUE.equals(avatar.getData(EmberKindleClient.KEY)) && avatar.deathTime <= 0.0F;
		float heat = Mth.clamp(EmberKindle.heat(self), 0.0F, 1.0F);
		PoseBlend.Track before = KINDLE.peek(avatar.id);
		if (before != null && before.on() && !on && before.memoTime >= KINDLE_THROW_HEAT) {
			// let go at full heat: the throw (as EmberKindleClient.applyModel)
			before.memo = Boolean.TRUE;
		}
		boolean throwing = before != null && Boolean.TRUE.equals(before.memo) && !on;
		PoseBlend.Track track = KINDLE.track(avatar.id, on, throwing ? KINDLE_WHIP_TIME : 0.0F, 1.0F);
		if (track == null) {
			return;
		}
		if (on) {
			track.memo = null;
			track.memoTime = heat;
		}
		float t = track.age();
		float w = track.weight();
		if (throwing) {
			float up = PoseBlend.seg(t, 0.0F, KINDLE_WHIP_UP_TIME);
			float through = PoseBlend.seg(t, KINDLE_WHIP_UP_TIME, KINDLE_WHIP_TIME * 0.7F);
			mix.add(w, lerp(through, lerp(up, KINDLE_PUSH, KINDLE_WIND), KINDLE_THROUGH));
			return;
		}
		if (on && t < KINDLE_COIL_TIME) {
			mix.add(w, KINDLE_COIL);
			return;
		}
		mix.add(w, KINDLE_PUSH);
		if (on) {
			// trembling, more as the heat builds
			float time = avatar.ageInTicks / 20.0F;
			float shake = heat * (0.4F + 0.6F * PoseBlend.smooth((t - KINDLE_COIL_TIME) / 1.5F));
			mix.shake(w, X, Mth.sin(time * KINDLE_BEAT) * KINDLE_TREMBLE * shake);
			mix.shake(w, Y, Mth.sin(time * KINDLE_BEAT_SIDE) * KINDLE_TREMBLE * shake);
			mix.shake(w, PITCH, Mth.sin(time * (KINDLE_BEAT + KINDLE_BEAT_SIDE) * 0.5F) * KINDLE_TREMBLE_TURN * shake);
		}
	}

	/** Time Flow's blows, both fists, on the third-person timing (ChronoFlowPose). */
	private static void chrono(Mix mix, AvatarRenderState avatar, boolean right) {
		ChronoFlowPose.Pose pose = avatar.getData(ChronoFlowPose.KEY);
		int style = pose == null ? -1 : pose.move().style();
		// (the kicks fling the arms out wide, out of view)
		boolean on = pose != null && style != ChronoFlow.KICK && style != ChronoFlow.SPIN && avatar.deathTime <= 0.0F;
		PoseBlend.Track track = CHRONO.track(avatar.id, on, ChronoFlowPose.LINGER, ChronoFlowPose.WEIGHT_SPEED);
		if (track == null) {
			return;
		}
		if (!on) {
			// between blows and after the last: the guard held, then eased out
			mix.add(track.weight(), CHRONO_GUARD);
			return;
		}
		mix.add(track.weight(), chronoArm(pose.move(), pose.p(), right));
	}

	private static float[] chronoArm(ChronoFlow.Move move, float p, boolean right) {
		boolean lead = (move.side() > 0) == right;
		float hit = Mth.clamp(move.hitAt() / (float)move.duration(), 0.05F, 0.95F);
		float[] coil, strike;
		switch (move.style()) {
			case ChronoFlow.BARRAGE -> {
				// a punch every few ticks, the fists alternating (the right on the even ones), out fast and drawn back
				float beat = p * ChronoFlow.BARRAGE_PUNCHES;
				int n = Math.min(ChronoFlow.BARRAGE_PUNCHES - 1, (int)beat);
				float q = beat - n;
				float out = q < 0.4F ? q / 0.4F : 1.0F - (q - 0.4F) / 0.6F;
				return (n % 2 == 0) == right ? lerp(PoseBlend.smooth(out), CHRONO_GUARD, RAPID_STRIKE) : CHRONO_GUARD;
			}
			case ChronoFlow.PUSH -> {
				// both palms drawn in to the chest, then driven straight out
				float draw = PoseBlend.seg(p, 0.0F, hit * 0.6F) * (1.0F - PoseBlend.seg(p, hit * 0.6F, hit));
				float push = PoseBlend.seg(p, hit * 0.6F, hit) * (1.0F - PoseBlend.seg(p, 0.8F, 1.0F));
				return lerp(push, lerp(draw, CHRONO_GUARD, PUSH_DRAW), PUSH_OUT);
			}
			case ChronoFlow.COUNTER -> {
				if (!lead) {
					return CHRONO_GUARD;
				}
				// the lead forearm up to take it, then drawn back and a palm strike on the hit (as the counters' answer)
				float block = PoseBlend.bump(p, 0.0F, 0.1F, hit - 0.18F, hit - 0.06F);
				float windUp = PoseBlend.seg(p, hit - 0.16F, hit - 0.05F) * (1.0F - PoseBlend.seg(p, hit - 0.05F, hit));
				float palm = PoseBlend.seg(p, hit - 0.05F, hit) * (1.0F - PoseBlend.seg(p, hit + 0.1F, 1.0F));
				return lerp(palm, lerp(windUp, lerp(block, CHRONO_GUARD, COUNTER_BLOCK), JAB_COIL), COUNTER_PALM);
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
			default -> {
				coil = JAB_COIL;
				strike = JAB_STRIKE;
			}
		}
		// as ChronoFlowPose.blow: guard to coil, coil to the strike on the hit, held, then back to the guard
		float c = hit * ChronoFlowPose.COIL_SHARE;
		float struck;
		float[] key;
		if (p < c) {
			struck = 0.0F;
			key = lerp(PoseBlend.seg(p, 0.0F, c), CHRONO_GUARD, coil);
		} else if (p < hit) {
			struck = PoseBlend.seg(p, c, hit);
			key = lerp(struck, coil, strike);
		} else {
			struck = 1.0F - PoseBlend.seg(p, hit + (1.0F - hit) * ChronoFlowPose.HOLD_SHARE, 1.0F);
			key = lerp(1.0F - struck, strike, CHRONO_GUARD);
		}
		if (!lead) {
			// the guard stays up (thrown back behind a lunge)
			return move.style() == ChronoFlow.LUNGE ? lerp(struck, CHRONO_GUARD, LUNGE_BACK) : CHRONO_GUARD;
		}
		// the recoil: the fist snaps back a touch off the hit
		float recoil = PoseBlend.bump(p, hit, hit + 0.04F, hit + 0.06F, hit + 0.22F);
		key[Z] -= RECOIL_BACK * recoil;
		key[PITCH] -= RECOIL_DROP * recoil;
		return key;
	}

	/** Gravity's Lift grip, the main arm reaching at what it holds. */
	private static void grip(Mix mix, LocalPlayer self, AvatarRenderState avatar, boolean main, boolean right, float partial) {
		if (!main) {
			return;
		}
		Integer grip = self.getAttached(GravityPower.GRIP);
		Entity held = grip == null ? null : self.level().getEntity(grip);
		boolean on = held != null && avatar.deathTime <= 0.0F;
		PoseBlend.Track track = GRIP.track(avatar.id, on);
		if (track == null) {
			return;
		}
		float[] aim = track.memo instanceof float[] kept ? kept : new float[2];
		if (on) {
			// where it is from the look: degrees up from it, and to the right
			Vec3 to = held.getEyePosition(partial).subtract(self.getEyePosition(partial));
			float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float)(-(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
			aim = new float[] {self.getXRot(partial) - pitch, Mth.wrapDegrees(yaw - self.getYRot(partial))};
			track.memo = aim;
		}
		float side = right ? 1.0F : -1.0F;
		float[] pose = GRIP_REACH.clone();
		pose[PITCH] += Mth.clamp(aim[0] * GRIP_FOLLOW, -GRIP_AIM_MAX, GRIP_AIM_MAX);
		pose[YAW] += Mth.clamp(-side * aim[1] * GRIP_FOLLOW, -GRIP_AIM_MAX, GRIP_AIM_MAX);
		float w = track.weight();
		mix.add(w, pose);
		// straining against it
		float time = avatar.ageInTicks;
		mix.shake(w, PITCH, Mth.sin(time * GRIP_STRAIN_BEAT) * GRIP_STRAIN);
		mix.shake(w, X, Mth.sin(time * 9.0F) * GRIP_TREMOR);
		mix.shake(w, Y, Mth.sin(time * 7.3F + 1.0F) * GRIP_TREMOR);
	}

	/** Cryokinesis' moves (CryoPose's state: its springs for the held ones, its curves for the one-shots). */
	private static void cryo(Mix mix, AvatarRenderState avatar, boolean main) {
		CryoPose.Pose pose = avatar.getData(CryoPose.KEY);
		if (pose == null || avatar.deathTime > 0.0F) {
			return;
		}
		float time = pose.time();
		float breath = Mth.clamp(pose.breath(), 0.0F, 1.1F);
		if (main && breath > ACTIVE) {
			// hand to the mouth, then thrown out along the breath, trembling
			float toMouth = PoseBlend.smooth(pose.breathAge() / BREATH_TO_MOUTH);
			float thrown = PoseBlend.smooth((pose.breathAge() - BREATH_TO_MOUTH) / BREATH_THROW);
			mix.add(breath, lerp(thrown, lerp(toMouth, new float[SLOTS], BREATH_MOUTH), BREATH_OUT));
			float tremble = Mth.sin(time * 2.9F) + Mth.sin(time * 4.3F + 1.0F) * 0.5F;
			mix.shake(breath, PITCH, tremble * BREATH_TREMBLE);
			mix.shake(breath, Y, tremble * BREATH_SHAKE);
		}
		float t = pose.t();
		switch (pose.kind()) {
			case CryoMoves.SHARDS -> {
				float wind = PoseBlend.smooth(t / 3.0F);
				float sweep = easeOut((t - 2.0F) / 4.0F);
				float a = wind * (1.0F - PoseBlend.smooth((t - 8.0F) / 6.0F));
				mix.add(a, main ? lerp(sweep, SHARDS_OUT, SHARDS_ACROSS) : lerp(sweep, SHARDS_AIM, SHARDS_BACK));
			}
			case CryoMoves.FREEZE -> {
				float draw = PoseBlend.smooth(t / 2.5F);
				float push = easeOut((t - 2.0F) / 3.0F);
				float a = draw * (1.0F - PoseBlend.smooth((t - 8.0F) / 6.0F));
				mix.add(a, lerp(push, FREEZE_CHEST, FREEZE_PUSH));
			}
			case CryoMoves.WALL -> {
				float dip = PoseBlend.smooth(t / 3.0F);
				float rise = easeOut((t - 3.0F) / 4.0F);
				float a = dip * (1.0F - PoseBlend.smooth((t - 10.0F) / 6.0F));
				mix.add(a, lerp(rise, WALL_LOW, WALL_UP));
			}
			default -> {
			}
		}
		float blizzard = Mth.clamp(pose.blizzard(), 0.0F, 1.1F);
		if (blizzard > ACTIVE) {
			float age = pose.blizzardAge();
			float rise = PoseBlend.smooth(age / BLIZZARD_RISE);
			float spread = PoseBlend.smooth((age - BLIZZARD_RISE * 0.6F) / BLIZZARD_SPREAD_TICKS);
			// up from the arm's rest, then spread to the sides
			mix.add(blizzard, lerp(rise, new float[SLOTS], lerp(spread, BLIZZARD_RAISED, BLIZZARD_SPREAD)));
			float sway = Mth.sin(time * 0.11F) + Mth.sin(time * 0.27F + 2.0F) * 0.4F;
			mix.shake(blizzard, ROLL, (main ? sway : -sway) * BLIZZARD_SWAY + Mth.sin(time * 3.7F) * BLIZZARD_SHIVER);
		}
	}

	// ---------------------------------------------------------------- carrying (CarryFirstPersonMixin)

	/** The last carry hold, kept while the arms ease out. */
	public record CarryHold(int hold, boolean animal) {
	}

	/**
	 * Every frame from CarryFirstPersonMixin: steps the carry arms' easing ({@code carrying}: what's carried now, or null).
	 * Null when there are no carry arms to draw (nothing is sunk then either).
	 */
	public static PoseBlend.@Nullable Track carry(LocalPlayer self, Carry.@Nullable Carrying carrying) {
		PoseBlend.Track track = CARRY.track(self.getId(), carrying != null);
		if (track != null && carrying != null) {
			track.memo = new CarryHold(carrying.hold(), !(self.level().getEntity(carrying.target()) instanceof Player));
		}
		if (track == null || !(track.memo instanceof CarryHold)) {
			carrySink = 0.0F;
			return null;
		}
		carrySink = track.amount();
		return track;
	}

	/** Nothing carried is drawn this frame (scoping, or the body drawn by First-person Model). */
	public static void noCarry() {
		carrySink = 0.0F;
	}

	/** How far the held items are sunk out of view for the carry arms (vanilla's equip height, 0-1). */
	public static float carrySink() {
		return carrySink;
	}

	/** One carry arm, before CarryPose.firstPersonArm places it: risen in by {@code weight}, bobbing with the steps. */
	public static void carryArm(PoseStack poseStack, AvatarRenderState avatar, float weight, boolean right) {
		float stride = Mth.clamp(avatar.walkAnimationSpeed, 0.0F, 1.0F);
		float step = avatar.walkAnimationPos * WALK_BEAT;
		// down on each footfall (twice a stride), and the load rocking from side to side with it
		float bob = (0.5F - Math.abs(Mth.cos(step))) * 2.0F * CARRY_BOB * stride;
		poseStack.translate(0.0F, bob - (1.0F - Mth.clamp(weight, 0.0F, 1.0F)) * CARRY_DROP, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(step) * CARRY_ROCK * stride);
	}

	// ---------------------------------------------------------------- helpers

	/** One hand's springs (offsets from vanilla's arm), on real time as PoseBlend's. */
	private static final class Arm {
		final float[] value = new float[SLOTS], speed = new float[SLOTS];
		long last;
		boolean live;

		void step(float[] target, boolean main) {
			long now = System.nanoTime();
			if (!this.live) {
				// from vanilla's arm (the main hand) or from below the view (the off hand)
				this.live = true;
				Arrays.fill(this.value, 0.0F);
				Arrays.fill(this.speed, 0.0F);
				if (!main) {
					this.value[Y] = -ENTRY_DROP;
				}
				this.last = now;
			}
			float dt = Mth.clamp((now - this.last) / 1.0E9F, 0.0F, MAX_DT);
			this.last = now;
			int steps = Math.max(1, Mth.ceil(dt / SUBSTEP));
			float h = dt / steps;
			float c = 2.0F * ARM_DAMPING * Mth.sqrt(ARM_STIFFNESS);
			for (int i = 0; i < SLOTS; i++) {
				float v = this.speed[i], x = this.value[i];
				for (int n = 0; n < steps; n++) {
					v += (ARM_STIFFNESS * (target[i] - x) - c * v) * h;
					x += v * h;
				}
				this.speed[i] = v;
				this.value[i] = x;
			}
		}

		/** Still away from vanilla's arm (or moving). */
		boolean moving() {
			for (int i = 0; i < SLOTS; i++) {
				float scale = i < PITCH ? 1.0F : 0.02F;
				if (Math.abs(this.value[i]) * scale > 0.001F || Math.abs(this.speed[i]) * scale > 0.01F) {
					return true;
				}
			}
			return false;
		}
	}

	/** The moves on one arm this frame, each laid on by its weight. */
	private static final class Mix {
		final float[] off = new float[SLOTS], raw = new float[SLOTS];
		float weight;

		void add(float w, float[] pose) {
			if (w <= 0.0F) {
				return;
			}
			for (int i = 0; i < SLOTS; i++) {
				this.off[i] += w * pose[i];
			}
			this.weight += w;
		}

		/** Past the springs (a tremble too quick for them). */
		void shake(float w, int slot, float delta) {
			if (w > 0.0F) {
				this.raw[slot] += w * delta;
			}
		}
	}

	private static float[] lerp(float t, float[] from, float[] to) {
		float[] out = new float[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			out[i] = Mth.lerp(t, from[i], to[i]);
		}
		return out;
	}

	/** Fast out, soft landing (as CryoPose's). */
	private static float easeOut(float x) {
		float u = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}
}
