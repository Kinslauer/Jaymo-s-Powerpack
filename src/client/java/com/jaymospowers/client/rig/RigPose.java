package com.jaymospowers.client.rig;

import com.jaymospowers.client.power.wind.WindPose;
import static com.jaymospowers.client.rig.RigBeats.BACK;
import static com.jaymospowers.client.rig.RigBeats.IN;
import static com.jaymospowers.client.rig.RigBeats.LINEAR;
import static com.jaymospowers.client.rig.RigBeats.OUT;
import static com.jaymospowers.client.rig.RigBeats.SINE;

import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigFrame;
import com.jaymospowers.rig.RigMoves;
import com.jaymospowers.rig.RigUnibeam;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * (0.11.107) How a Rig pilot moves, on everyone's screen (from the synced {@link RigMoves}). Soul Industries (the Fisk's
 * Superheroes Iron Man pack, All Rights Reserved) was the reference; these are our own:
 * <ul>
 * <li><b>Hovering</b> (or held still in full flight)<b>:</b> the arms held down and out, palms to the ground, steadying the hover on their repulsors (the
 * server's wash under each palm), legs together and hanging.</li>
 * <li><b>Full flight:</b> Wind's cruise leans the body out along the flight ({@link #cruising}); the arms lie back along
 * the sides, palms to the rear, instead of a fist out ahead.</li>
 * <li><b>Palm blast:</b> the arm thrust out straight along the look, a kick as it fires, then down.</li>
 * <li><b>Launcher:</b> braced, arms back and out, while the pods rise off the shoulders ({@link #pods}).</li>
 * <li><b>Unibeam:</b> both arms drawn back and wide, chest out, the head back a little; it shakes as the beam pours.</li>
 * </ul>
 * Every move is keyed in three beats ({@link RigBeats}): a wind-up you can read, a release that runs past its mark and
 * comes back, and a settle. The whole body joins in (the hips drop, the legs brace, the head follows), and a shot's kick
 * travels: the arm first, the chest a tick later, the head after that, harder on the Siege frame. The take-off, the scan,
 * the suit call and the wrist table have poses of their own.
 */
public final class RigPose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:rig_pose");

	/**
	 * {@code kind}: a {@link RigMoves} kind or 0; {@code t}: ticks into it; {@code hover}: 0-1; {@code flight}: 0-1;
	 * {@code recoil}: how hard this frame's shots rock the wearer (1: the standard frame); {@code still}: 1 standing, 0 at a
	 * walk (a move's stance only takes the legs of someone standing).
	 */
	public record Pose(int kind, float t, float hover, float flight, float time, float recoil, float still) {
	}

	/** Moving at least this fast (blocks/tick) in full flight, Wind's cruise lean takes the body. */
	static final double CRUISE_SPEED = 0.25;
	/** How fast the hover pose eases in and out (share a tick). */
	static final float HOVER_EASE = 0.2F;
	/** The hover: arms out from the sides (radians), a little forward, and their slow sway. */
	static final float HOVER_ARM_OUT = 0.42F, HOVER_ARM_FORWARD = -0.1F, HOVER_SWAY = 0.05F;
	/** Full flight: the arms back along the sides, and out from them. */
	static final float FLIGHT_ARM_BACK = 0.3F, FLIGHT_ARM_OUT = 0.14F;
	/** Palm blast: ticks to raise, the kick (radians) and the ticks it lowers over (from {@code PALM_LOWER_AT}). */
	static final float PALM_KICK = 0.4F, PALM_LOWER_AT = 9.0F;
	/** Palm blast, the rest of the body: the hips' drop (model pixels), the lean into it, the step of the legs, the off arm pulled back and out, and how far the kick rocks the chest and the head. */
	static final float PALM_DROP = 1.2F, PALM_LEAN = 0.1F, PALM_STEP = 0.35F, PALM_OFF_BACK = 0.45F, PALM_OFF_OUT = 0.25F, PALM_CHEST = 0.14F, PALM_HEAD = 0.1F;
	/** Recoil travels: each part up the body takes the blow this many ticks after the one before. */
	static final float RECOIL_LAG = 1.0F;
	/** How much harder the Siege frame's shots rock the wearer. */
	static final float SIEGE_RECOIL = 1.5F;
	/** A leg's length (model pixels), for carrying the legs under leaning hips. */
	static final float LEG_LENGTH = 12.0F;
	/** Launcher: the brace, and the pods' rise, hold and lowering (ticks). */
	static final float LAUNCH_ARM_BACK = 0.35F, LAUNCH_ARM_OUT = 0.3F, POD_UP = 3.0F, POD_DOWN_AT = 12.0F;
	/** Launcher, the rest of the body: the brace's drop and lean, the feet apart, the head tucked; the thump's extra drop and how far it rocks the chest back. */
	static final float LAUNCH_DROP = 1.6F, LAUNCH_LEAN = 0.2F, LAUNCH_SPREAD = 0.14F, LAUNCH_TUCK = 0.15F, LAUNCH_THUMP = 1.4F, LAUNCH_ROCK = 0.16F;
	/** Unibeam: the arms drawn back and wide, the head back, and the shake while the beam pours. */
	static final float UNI_ARM_BACK = 0.75F, UNI_ARM_OUT = 0.95F, UNI_HEAD_BACK = 0.2F, UNI_SHAKE = 0.05F;
	/** Unibeam, the rest of the body: the planted drop and spread, the chest pushed out, thrown back as it fires, then leaned into the beam. */
	static final float UNI_DROP = 1.0F, UNI_SPREAD = 0.2F, UNI_CHEST_OUT = 0.12F, UNI_THROWN = 0.18F, UNI_INTO = 0.16F;

	/** The one-knee landing: ticks to drop, when it rises, how far the body drops (model pixels), the lean, the head, the legs, the planted fist and the flung arm. */
	static final float KNEE_DOWN = 3.0F, KNEE_UP_AT = 17.0F, KNEE_DROP = 6.0F, KNEE_LEAN = 0.55F, KNEE_HEAD = 0.25F;
	static final float KNEE_FRONT = -1.2F, KNEE_BACK = 1.1F, KNEE_FIST = -0.5F, KNEE_FLUNG = 0.7F, KNEE_FLUNG_OUT = 0.4F;
	/** A Rig leg's kick: ticks to raise, when it lowers, and the leg's swing. */
	static final float KICK_RAISE = 2.0F, KICK_LOWER_AT = 7.0F, KICK_LEG = -1.35F;
	/** The kick's wind-up (the leg drawn back) and the chest leaning away from it; the landing's nod as the head catches up. */
	static final float KICK_CHAMBER = 0.45F, KICK_LEAN = 0.18F, KNEE_NOD = 0.2F;
	/** Take-off: the crouch's drop and lean, the knees, the arms swept back; then the arch as the thrusters fire, the arms thrown out, and the head. */
	static final float TAKEOFF_DROP = 2.6F, TAKEOFF_LEAN = 0.32F, TAKEOFF_KNEES = 0.55F, TAKEOFF_ARMS_BACK = 0.7F, TAKEOFF_ARCH = 0.14F, TAKEOFF_ARMS_OUT = 0.55F,
		TAKEOFF_HEAD = 0.2F;
	/** Scan: the off arm up to the helm (up, in across the body, out from the side), the head dipped and tilted to it. */
	static final float SCAN_ARM_UP = -2.35F, SCAN_ARM_IN = 0.35F, SCAN_ARM_OUT = 0.45F, SCAN_HEAD = 0.12F, SCAN_TILT = 0.08F;
	/** Suit call: the arms out and a touch forward, the feet apart, the chin up. */
	static final float CALL_ARM_FORWARD = -0.12F, CALL_ARM_OUT = 1.0F, CALL_SPREAD = 0.12F, CALL_CHIN = 0.14F;
	/** Wrist table: the off forearm up and across the chest, the head down and turned to it. */
	static final float TABLE_ARM_UP = -1.15F, TABLE_ARM_ACROSS = 0.7F, TABLE_HEAD = 0.35F, TABLE_TURN = 0.2F;
	/** Thruster dash: the coil's drop and lean, then the lunge's lean, the arms swept back and out, the trailing leg. */
	static final float DASH_DROP = 1.8F, DASH_COIL = 0.25F, DASH_LEAN = 0.5F, DASH_ARMS_BACK = 0.9F, DASH_ARMS_OUT = 0.25F, DASH_TRAIL = 0.5F;
	/** Powered punch: the fist driven along the look and past it, the shoulder turned in behind it, the off arm pulled back, the step. */
	static final float PUNCH_THROUGH = 0.3F, PUNCH_TURN = 0.3F, PUNCH_OFF_BACK = 0.5F, PUNCH_STEP = 0.3F, PUNCH_LEAN = 0.16F;
	/** Barrage: the deep brace's drop and lean, the fists planted low and wide, the feet apart, the head up to watch them go; each missile's thump. */
	static final float BARRAGE_DROP = 2.4F, BARRAGE_LEAN = 0.3F, BARRAGE_ARM_BACK = 0.2F, BARRAGE_ARM_OUT = 0.5F, BARRAGE_SPREAD = 0.2F, BARRAGE_CHIN = 0.3F,
		BARRAGE_THUMP = 0.5F, BARRAGE_POD_DOWN_AT = 28.0F;
	/** Walking at this share of full pace or more, a move leaves the legs to the walk. */
	static final float STILL_PACE = 0.35F;

	/** Per entity: {hover amount, game time it was last stepped}. */
	private static final Map<Integer, float[]> HOVER = new HashMap<>();
	private static final int MAX_TRACKS = 256;

	private RigPose() {
	}

	/** Whether this player is in the Rig's full flight at speed: Wind's cruise lean is theirs. */
	public static boolean cruising(Player player) {
		if (RigMoves.thrust(player) != RigMoves.THRUST_FLIGHT || player.onGround()) {
			return false;
		}
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		return dx * dx + dy * dy + dz * dz >= CRUISE_SPEED * CRUISE_SPEED;
	}

	/** Whether the Rig's flight pose has the arms (Wind's cruise leaves them be). */
	public static boolean ownsArms(AvatarRenderState state) {
		Pose pose = state.getData(KEY);
		return pose != null && pose.flight() > 0.001F;
	}

	/** How far the shoulder launcher's pods are raised (0-1), for the suit drawn over this player. */
	static float pods(Player player, float partialTicks) {
		RigMoves.Move move = RigMoves.get(player);
		Minecraft minecraft = Minecraft.getInstance();
		if (move == null || move.kind() != RigMoves.LAUNCHER && move.kind() != RigMoves.BARRAGE || minecraft.level == null) {
			return 0.0F;
		}
		float t = minecraft.level.getGameTime() - move.start() + partialTicks;
		return move.kind() == RigMoves.LAUNCHER ? pods(t)
			: RigBeats.at(t, 0.0F, 0.0F, LINEAR, POD_UP, 1.0F, BACK, BARRAGE_POD_DOWN_AT, 1.0F, LINEAR, RigMoves.BARRAGE_TICKS, 0.0F, SINE);
	}

	/** From the avatar renderer's extractRenderState (RigSuitAvatarRendererMixin). */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(entity instanceof Player player) || minecraft.level == null || player.isSleeping() || player.isPassenger()
			|| !Rig.wearsAny(player) && RigMoves.get(player) == null) {
			state.setData(KEY, null);
			if (entity != null) {
				HOVER.remove(entity.getId());
			}
			return;
		}
		float now = minecraft.level.getGameTime() + partialTicks;
		int kind = 0;
		float t = 0.0F;
		RigMoves.Move move = RigMoves.get(player);
		if (move != null) {
			t = now - move.start();
			if (t >= 0.0F && t < RigMoves.length(move.kind())) {
				kind = move.kind();
			}
		}
		int thrust = RigMoves.thrust(player);
		// hovering, or held still in full flight: the steadying pose; cruising, the flight's
		boolean steady = thrust == RigMoves.THRUST_HOVER || thrust == RigMoves.THRUST_FLIGHT && !cruising(player);
		float hover = hover(player, steady && !player.onGround(), now);
		float flight = thrust == RigMoves.THRUST_FLIGHT ? WindPose.amount(player, partialTicks) : 0.0F;
		float recoil = Rig.frame(player) == RigFrame.SIEGE ? SIEGE_RECOIL : 1.0F;
		float still = player.onGround() ? 1.0F - Mth.clamp(state.walkAnimationSpeed / STILL_PACE, 0.0F, 1.0F) : 1.0F;
		state.setData(KEY, kind == 0 && hover <= 0.001F && flight <= 0.001F ? null : new Pose(kind, t, hover, flight, now, recoil, still));
	}

	/** The hover pose's amount, easing toward {@code on} as time passes. */
	private static float hover(Player player, boolean on, float now) {
		float[] track = HOVER.get(player.getId());
		if (track == null) {
			if (!on) {
				return 0.0F;
			}
			if (HOVER.size() > MAX_TRACKS) {
				HOVER.clear();
			}
			track = new float[] {0.0F, now};
			HOVER.put(player.getId(), track);
		}
		float dt = Mth.clamp(now - track[1], 0.0F, 5.0F);
		track[1] = now;
		track[0] = Mth.clamp(track[0] + (on ? HOVER_EASE : -HOVER_EASE) * dt, 0.0F, 1.0F);
		if (!on && track[0] <= 0.0F) {
			HOVER.remove(player.getId());
		}
		return track[0];
	}

	/** After a humanoid model animates (the suit drawn over the body takes it too). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		float time = pose.time();
		if (pose.hover() > 0.001F) {
			float sway = Mth.sin(time * 0.12F) * HOVER_SWAY;
			set(h.rightArm, HOVER_ARM_FORWARD + sway, 0.0F, HOVER_ARM_OUT, pose.hover());
			set(h.leftArm, HOVER_ARM_FORWARD - sway, 0.0F, -HOVER_ARM_OUT, pose.hover());
			set(h.rightLeg, 0.08F, 0.0F, 0.05F, pose.hover());
			set(h.leftLeg, -0.03F, 0.0F, -0.05F, pose.hover());
		}
		if (pose.flight() > 0.001F) {
			set(h.rightArm, FLIGHT_ARM_BACK, 0.0F, FLIGHT_ARM_OUT, pose.flight());
			set(h.leftArm, FLIGHT_ARM_BACK, 0.0F, -FLIGHT_ARM_OUT, pose.flight());
		}
		boolean leftMain = avatar.mainArm == HumanoidArm.LEFT;
		ModelPart main = leftMain ? h.leftArm : h.rightArm, off = leftMain ? h.rightArm : h.leftArm;
		ModelPart front = leftMain ? h.rightLeg : h.leftLeg, rear = leftMain ? h.leftLeg : h.rightLeg;
		float side = leftMain ? -1.0F : 1.0F;
		float t = pose.t(), recoil = pose.recoil(), still = pose.still();
		switch (pose.kind()) {
			case RigMoves.PALM -> {
				// wind-up: the arm snaps out along the look (past it and back) as the hips drop and the back leg braces;
				// release: the kick runs up the arm, into the chest a tick later, the head after that; then it all settles
				float fire = RigMoves.PALM_FIRE;
				float raise = RigBeats.at(t, 0.0F, 0.0F, LINEAR, fire, 1.0F, BACK, PALM_LOWER_AT, 1.0F, LINEAR, RigMoves.PALM_TICKS, 0.0F, SINE);
				float stance = RigBeats.at(t, 0.0F, 0.0F, LINEAR, fire, 1.0F, OUT, PALM_LOWER_AT, 1.0F, LINEAR, RigMoves.PALM_TICKS, 0.0F, SINE);
				float kick = PALM_KICK * recoil * pulse(t - fire, 1.0F, 4.5F);
				float chest = PALM_CHEST * recoil * pulse(t - fire - RECOIL_LAG, 1.5F, 5.0F);
				float nod = PALM_HEAD * recoil * pulse(t - fire - 2.0F * RECOIL_LAG, 1.5F, 5.0F);
				crouch(h, PALM_DROP * stance, PALM_LEAN * stance - chest);
				brace(front, rear, side, PALM_STEP * stance * still);
				set(main, -Mth.HALF_PI + h.head.xRot - kick, h.head.yRot - side * 0.1F, 0.0F, raise);
				set(off, PALM_OFF_BACK, 0.0F, -side * PALM_OFF_OUT, stance);
				h.head.xRot -= nod;
			}
			case RigMoves.LAUNCHER -> {
				// wind-up: down into a brace as the pods rise; release: the thump drives the hips down and the chest back
				float fire = RigMoves.LAUNCHER_FIRE;
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, fire, 1.0F, OUT, POD_DOWN_AT, 1.0F, LINEAR, RigMoves.LAUNCHER_TICKS, 0.0F, SINE);
				float thump = recoil * pulse(t - fire, 1.0F, 6.0F);
				float nod = recoil * pulse(t - fire - RECOIL_LAG, 1.5F, 6.0F);
				crouch(h, LAUNCH_DROP * amount + LAUNCH_THUMP * thump, LAUNCH_LEAN * amount - LAUNCH_ROCK * thump);
				set(h.rightArm, LAUNCH_ARM_BACK, 0.0F, LAUNCH_ARM_OUT, amount);
				set(h.leftArm, LAUNCH_ARM_BACK, 0.0F, -LAUNCH_ARM_OUT, amount);
				spread(h, LAUNCH_SPREAD * amount * still);
				h.head.xRot += LAUNCH_TUCK * amount - LAUNCH_ROCK * nod;
			}
			case RigMoves.UNIBEAM -> {
				// wind-up: arms drawn back and wide, chest out, feet planted wide while the reactor whines; release: the
				// chest is thrown back by the beam, then leans into it and trembles; settle: forward past rest and back
				float charge = RigUnibeam.CHARGE_TICKS, end = charge + RigUnibeam.BEAM_TICKS;
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, charge, 1.0F, SINE, end, 1.0F, LINEAR, end + RigMoves.UNIBEAM_SETTLE, 0.0F, BACK);
				float thrown = recoil * pulse(t - charge, 1.5F, 6.0F);
				float lean = RigBeats.at(t, charge, 0.0F, LINEAR, charge + 5.0F, 1.0F, SINE, end, 1.0F, LINEAR, end + 3.0F, 0.0F, OUT);
				float shake = t >= charge && t < end ? Mth.sin(time * 2.7F) * UNI_SHAKE : 0.0F;
				crouch(h, UNI_DROP * amount, -UNI_CHEST_OUT * amount - UNI_THROWN * thrown + UNI_INTO * lean + shake * 0.5F);
				set(h.rightArm, UNI_ARM_BACK + shake, 0.0F, UNI_ARM_OUT - shake, amount);
				set(h.leftArm, UNI_ARM_BACK - shake, 0.0F, -UNI_ARM_OUT + shake, amount);
				spread(h, UNI_SPREAD * amount * still);
				h.head.xRot = Mth.lerp(amount, h.head.xRot, h.head.xRot - UNI_HEAD_BACK + UNI_INTO * lean);
			}
			case RigMoves.KNEE -> {
				// the one-knee landing: down fast and a little too far, held with a fist on the ground, then up
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, KNEE_DOWN, 1.0F, BACK, KNEE_UP_AT, 1.0F, LINEAR, RigMoves.KNEE_TICKS, 0.0F, SINE);
				float nod = pulse(t - KNEE_DOWN, 2.0F, 7.0F);
				for (ModelPart part : new ModelPart[] {h.head, h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg}) {
					part.y += KNEE_DROP * amount;
				}
				h.body.xRot = Mth.lerp(amount, h.body.xRot, KNEE_LEAN);
				h.head.xRot = Mth.lerp(amount, h.head.xRot, KNEE_HEAD) + KNEE_NOD * nod;
				set(h.rightLeg, KNEE_FRONT, 0.0F, 0.08F, amount);
				set(h.leftLeg, KNEE_BACK, 0.0F, -0.1F, amount);
				set(h.rightArm, KNEE_FIST, 0.0F, 0.1F, amount);
				set(h.leftArm, KNEE_FLUNG, 0.0F, -KNEE_FLUNG_OUT, amount);
			}
			case RigMoves.KICK -> {
				// wind-up: the leg chambers back and the chest leans away; release: it snaps out past the line; settle
				float chamber = RigBeats.at(t, 0.0F, 0.0F, LINEAR, KICK_RAISE, 1.0F, OUT, KICK_RAISE + 1.0F, 0.0F, IN);
				float out = RigBeats.at(t, KICK_RAISE, 0.0F, LINEAR, KICK_RAISE + 2.0F, 1.0F, BACK, KICK_LOWER_AT, 1.0F, LINEAR, RigMoves.KICK_TICKS, 0.0F, SINE);
				float amount = Math.max(chamber, Math.min(out, 1.0F));
				crouch(h, 0.0F, -KICK_LEAN * amount);
				set(h.rightLeg, Mth.lerp(chamber, 0.0F, KICK_CHAMBER) + KICK_LEG * out, 0.0F, 0.0F, amount);
				set(h.leftLeg, 0.25F, 0.0F, 0.0F, amount);
				set(h.rightArm, 0.5F, 0.0F, 0.5F, amount);
				set(h.leftArm, 0.5F, 0.0F, -0.5F, amount);
			}
			case RigMoves.TAKEOFF -> {
				// wind-up: a deep crouch, arms swept back; release: the body snaps straight as the thrusters fire, arms thrown
				// down and out; settle into the hover or the flight
				float crouch = RigBeats.at(t, 0.0F, 0.0F, LINEAR, RigMoves.TAKEOFF_CROUCH, 1.0F, OUT, RigMoves.TAKEOFF_CROUCH + 2.0F, 0.0F, BACK);
				float thrown = RigBeats.at(t, RigMoves.TAKEOFF_CROUCH, 0.0F, LINEAR, RigMoves.TAKEOFF_CROUCH + 2.0F, 1.0F, BACK, RigMoves.TAKEOFF_TICKS, 0.0F, SINE);
				crouch(h, TAKEOFF_DROP * crouch, TAKEOFF_LEAN * crouch - TAKEOFF_ARCH * thrown);
				set(h.rightLeg, -TAKEOFF_KNEES, 0.0F, 0.06F, crouch);
				set(h.leftLeg, -TAKEOFF_KNEES * 0.7F, 0.0F, -0.06F, crouch);
				set(h.rightArm, TAKEOFF_ARMS_BACK, 0.0F, 0.3F, crouch);
				set(h.leftArm, TAKEOFF_ARMS_BACK, 0.0F, -0.3F, crouch);
				set(h.rightArm, 0.15F, 0.0F, TAKEOFF_ARMS_OUT, thrown);
				set(h.leftArm, 0.15F, 0.0F, -TAKEOFF_ARMS_OUT, thrown);
				h.head.xRot += TAKEOFF_HEAD * crouch - TAKEOFF_HEAD * thrown;
			}
			case RigMoves.SCAN -> {
				// two fingers to the side of the helm, the head dipped to listen; held while the sweep goes out
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, 3.0F, 1.0F, BACK, RigMoves.SCAN_TICKS - 5.0F, 1.0F, LINEAR, RigMoves.SCAN_TICKS, 0.0F, SINE);
				set(off, SCAN_ARM_UP, side * SCAN_ARM_IN, -side * SCAN_ARM_OUT, amount);
				h.head.xRot += SCAN_HEAD * amount;
				h.head.zRot += -side * SCAN_TILT * amount;
			}
			case RigMoves.CALL -> {
				// arms out from the sides and still, chin up, feet planted, while the plates come in and lock on
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, 5.0F, 1.0F, BACK, RigMoves.CALL_TICKS - 8.0F, 1.0F, LINEAR, RigMoves.CALL_TICKS, 0.0F, SINE);
				set(h.rightArm, CALL_ARM_FORWARD, 0.0F, CALL_ARM_OUT, amount);
				set(h.leftArm, CALL_ARM_FORWARD, 0.0F, -CALL_ARM_OUT, amount);
				spread(h, CALL_SPREAD * amount * still);
				h.head.xRot -= CALL_CHIN * amount;
			}
			case RigMoves.TABLE -> {
				// the forearm brought up across the chest and a look down at it
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, 4.0F, 1.0F, BACK, RigMoves.TABLE_TICKS - 6.0F, 1.0F, LINEAR, RigMoves.TABLE_TICKS, 0.0F, SINE);
				set(off, TABLE_ARM_UP, side * TABLE_ARM_ACROSS, 0.0F, amount);
				h.head.xRot += TABLE_HEAD * amount;
				h.head.yRot += -side * TABLE_TURN * amount;
			}
			case RigMoves.DASH -> {
				// wind-up: a coil, hips down and arms swept back; release: thrown forward flat behind the head, a leg trailing; settle
				float fire = RigMoves.DASH_FIRE;
				float coil = RigBeats.at(t, 0.0F, 0.0F, LINEAR, fire, 1.0F, OUT, fire + 2.0F, 0.0F, IN);
				float lunge = RigBeats.at(t, fire, 0.0F, LINEAR, fire + 2.0F, 1.0F, BACK, RigMoves.DASH_TICKS - 4.0F, 1.0F, LINEAR, RigMoves.DASH_TICKS, 0.0F, SINE);
				float amount = Math.max(coil, Math.min(lunge, 1.0F));
				crouch(h, DASH_DROP * coil, DASH_COIL * coil + DASH_LEAN * lunge);
				set(h.rightArm, DASH_ARMS_BACK, 0.0F, DASH_ARMS_OUT, amount);
				set(h.leftArm, DASH_ARMS_BACK, 0.0F, -DASH_ARMS_OUT, amount);
				brace(front, rear, side, DASH_TRAIL * lunge);
				h.head.xRot -= DASH_LEAN * 0.8F * lunge;
			}
			case RigMoves.PUNCH -> {
				// no wind-up (the blow has landed): the fist driven through past the line, the shoulder behind it, then back
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, 1.0F, 1.0F, BACK, 3.0F, 1.0F, LINEAR, RigMoves.PUNCH_TICKS, 0.0F, SINE);
				crouch(h, 0.0F, PUNCH_LEAN * amount);
				h.body.yRot += -side * PUNCH_TURN * amount;
				set(main, -Mth.HALF_PI + h.head.xRot - PUNCH_THROUGH * 0.3F, h.head.yRot - side * PUNCH_THROUGH, 0.0F, Math.min(amount, 1.0F));
				set(off, PUNCH_OFF_BACK, 0.0F, -side * 0.2F, Math.min(amount, 1.0F));
				brace(front, rear, side, PUNCH_STEP * amount * still);
			}
			case RigMoves.BARRAGE -> {
				// wind-up: down into a deep brace, fists low and wide, as the pods rise; release: each missile thumps the hips
				// down while the head comes up to watch them go; settle as the pods lower
				float fire = RigMoves.BARRAGE_FIRE;
				float amount = RigBeats.at(t, 0.0F, 0.0F, LINEAR, fire, 1.0F, OUT, BARRAGE_POD_DOWN_AT, 1.0F, LINEAR, RigMoves.BARRAGE_TICKS, 0.0F, SINE);
				float firing = t >= fire && t < BARRAGE_POD_DOWN_AT - 6.0F ? 1.0F : 0.0F;
				float thump = firing * recoil * pulse((t - fire) % 2.0F, 0.5F, 2.0F);
				float watch = RigBeats.at(t, fire, 0.0F, LINEAR, fire + 4.0F, 1.0F, SINE, BARRAGE_POD_DOWN_AT, 1.0F, LINEAR, RigMoves.BARRAGE_TICKS, 0.0F, SINE);
				crouch(h, BARRAGE_DROP * amount + BARRAGE_THUMP * thump, BARRAGE_LEAN * amount);
				set(h.rightArm, BARRAGE_ARM_BACK, 0.0F, BARRAGE_ARM_OUT, amount);
				set(h.leftArm, BARRAGE_ARM_BACK, 0.0F, -BARRAGE_ARM_OUT, amount);
				spread(h, BARRAGE_SPREAD * amount * still);
				h.head.xRot -= (BARRAGE_LEAN + BARRAGE_CHIN) * watch;
			}
			default -> {
			}
		}
	}

	/** A blow's shape: nothing before 0, up to 1 by {@code rise} (fast, slowing), back to nothing by {@code end}. */
	private static float pulse(float t, float rise, float end) {
		return RigBeats.at(t, 0.0F, 0.0F, LINEAR, rise, 1.0F, OUT, end, 0.0F, SINE);
	}

	/**
	 * The hips drop by {@code drop} (model pixels) and the chest leans by {@code lean} (radians, forward positive) about
	 * the neck, the legs carried back under the hips the way a sneak's are.
	 */
	private static void crouch(HumanoidModel<?> h, float drop, float lean) {
		h.body.xRot += lean;
		h.head.y += drop;
		h.body.y += drop;
		h.rightArm.y += drop;
		h.leftArm.y += drop;
		float back = LEG_LENGTH * Mth.sin(lean), up = LEG_LENGTH * (1.0F - Mth.cos(lean));
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			leg.z += back;
			leg.y += drop - up;
		}
	}

	/** A fighter's stance: the front leg forward, the rear leg back and turned out. */
	private static void brace(ModelPart front, ModelPart rear, float side, float amount) {
		front.xRot -= amount;
		rear.xRot += amount * 1.15F;
		rear.zRot += side * amount * 0.3F;
	}

	/** Feet planted apart. */
	private static void spread(HumanoidModel<?> h, float amount) {
		h.rightLeg.zRot += amount;
		h.leftLeg.zRot -= amount;
	}

	private static float pods(float t) {
		return RigBeats.at(t, 0.0F, 0.0F, LINEAR, POD_UP, 1.0F, BACK, POD_DOWN_AT, 1.0F, LINEAR, RigMoves.LAUNCHER_TICKS, 0.0F, SINE);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}

}
