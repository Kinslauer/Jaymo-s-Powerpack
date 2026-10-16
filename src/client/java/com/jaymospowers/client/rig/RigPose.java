package com.jaymospowers.client.rig;

import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.rig.Rig;
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
 */
public final class RigPose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:rig_pose");

	/** {@code kind}: a {@link RigMoves} kind or 0; {@code t}: ticks into it; {@code hover}: 0-1; {@code flight}: 0-1. */
	public record Pose(int kind, float t, float hover, float flight, float time) {
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
	static final float PALM_RAISE = 2.0F, PALM_KICK = 0.35F, PALM_LOWER_AT = 6.0F;
	/** Launcher: the brace, and the pods' rise, hold and lowering (ticks). */
	static final float LAUNCH_ARM_BACK = 0.35F, LAUNCH_ARM_OUT = 0.3F, POD_UP = 2.0F, POD_DOWN_AT = 9.0F;
	/** Unibeam: the arms drawn back and wide, the head back, and the shake while the beam pours. */
	static final float UNI_ARM_BACK = 0.75F, UNI_ARM_OUT = 0.95F, UNI_HEAD_BACK = 0.2F, UNI_SHAKE = 0.05F;

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
		if (move == null || move.kind() != RigMoves.LAUNCHER || minecraft.level == null) {
			return 0.0F;
		}
		return pods(minecraft.level.getGameTime() - move.start() + partialTicks);
	}

	/** From the avatar renderer's extractRenderState (RigSuitAvatarRendererMixin). */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(entity instanceof Player player) || minecraft.level == null || player.isSleeping() || player.isPassenger() || !Rig.wearsAny(player)) {
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
		state.setData(KEY, kind == 0 && hover <= 0.001F && flight <= 0.001F ? null : new Pose(kind, t, hover, flight, now));
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
		ModelPart main = leftMain ? h.leftArm : h.rightArm;
		float t = pose.t();
		switch (pose.kind()) {
			case RigMoves.PALM -> {
				// straight out along the look, as a bow is drawn; a kick up as it fires
				float amount = smooth(t / PALM_RAISE) * (1.0F - smooth((t - PALM_LOWER_AT) / (RigMoves.PALM_TICKS - PALM_LOWER_AT)));
				float kick = t > PALM_RAISE ? PALM_KICK * Math.max(0.0F, 1.0F - (t - PALM_RAISE) / 3.0F) : 0.0F;
				set(main, -Mth.HALF_PI + h.head.xRot - kick, h.head.yRot + (leftMain ? 0.1F : -0.1F), 0.0F, amount);
			}
			case RigMoves.LAUNCHER -> {
				float amount = pods(t);
				set(h.rightArm, LAUNCH_ARM_BACK, 0.0F, LAUNCH_ARM_OUT, amount);
				set(h.leftArm, LAUNCH_ARM_BACK, 0.0F, -LAUNCH_ARM_OUT, amount);
			}
			case RigMoves.UNIBEAM -> {
				float end = RigUnibeam.CHARGE_TICKS + RigUnibeam.BEAM_TICKS;
				float amount = smooth(t / RigUnibeam.CHARGE_TICKS) * (1.0F - smooth((t - end) / RigMoves.UNIBEAM_SETTLE));
				float shake = t >= RigUnibeam.CHARGE_TICKS && t < end ? Mth.sin(time * 2.7F) * UNI_SHAKE : 0.0F;
				set(h.rightArm, UNI_ARM_BACK + shake, 0.0F, UNI_ARM_OUT - shake, amount);
				set(h.leftArm, UNI_ARM_BACK - shake, 0.0F, -UNI_ARM_OUT + shake, amount);
				h.head.xRot = Mth.lerp(amount, h.head.xRot, h.head.xRot - UNI_HEAD_BACK);
			}
			default -> {
			}
		}
	}

	private static float pods(float t) {
		return smooth(t / POD_UP) * (1.0F - smooth((t - POD_DOWN_AT) / (RigMoves.LAUNCHER_TICKS - POD_DOWN_AT)));
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}

	private static float smooth(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
