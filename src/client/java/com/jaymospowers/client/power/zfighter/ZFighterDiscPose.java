package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterDisc;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Ki Disc's throw ({@link ZFighterDisc#THROW}), on everyone: the throwing arm goes straight up while the disc forms
 * over the palm, the other arm out for balance and the body drawn back a little; then the overhand throw, the arm
 * coming over and down ({@link ZFighterDisc#sweepAngle}, the same curve the server follows to let it go), the body
 * leaning into it and turning through, held a moment and eased out.
 */
public final class ZFighterDiscPose {
	/** Which stage, how far into it (ticks), how much of the pose shows (0-1), and whether it's his own first-person view. */
	public record Pose(int stage, float ticks, float amount, boolean ownView) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_disc");
	/** How fast the pose eases in and out (a share a tick); the arm goes up over this many ticks. */
	static final float EASE = 0.34F;
	static final float RAISE_TICKS = 4.0F;
	/** The throwing arm overhead: turned out from the body a touch (radians). */
	static final float ARM_OUT = 0.12F;
	/** The other arm while it forms (pitch, out) and flung back on the throw. */
	static final float OFF_WIND_X = -0.35F, OFF_WIND_OUT = 0.35F;
	static final float OFF_THROW_X = 0.7F, OFF_THROW_OUT = 0.25F;
	/** The upper body, tipped at the hip (+ forward) and turned (radians, + the throwing shoulder back): drawn back, then into it. */
	static final float LEAN_WIND = -0.12F, TWIST_WIND = 0.3F;
	static final float LEAN_THROW = 0.42F, TWIST_THROW = -0.35F;
	/** The head tips back a little to the disc, then follows through. */
	static final float HEAD_WIND = -0.15F, HEAD_THROW = 0.1F;
	/** A tremble in the raised arm while the disc gathers (radians). */
	static final float SHAKE = 0.02F;

	private static final Vector3f HIP = new Vector3f(0.0F, 12.0F, 0.0F);

	/** entity id → {amount, amount before, stage, ticks in it, ticks before}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterDiscPose() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Integer stage = player.getAttached(ZFighterDisc.THROW);
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (stage == null) {
					continue;
				}
				s = new float[5];
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			s[4] = s[3];
			if (stage != null) {
				s[0] = Math.min(1.0F, s[0] + EASE);
				if ((int)s[2] != stage) {
					s[2] = stage;
					s[3] = 0.0F;
					s[4] = 0.0F;
				} else {
					s[3]++;
				}
			} else {
				// let go: the follow-through carries on while it eases out
				s[0] = Math.max(0.0F, s[0] - EASE);
				s[3]++;
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		if (s == null || !(entity instanceof Player)) {
			state.setData(KEY, null);
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		state.setData(KEY, new Pose((int)s[2], Mth.lerp(partialTicks, s[4], s[3]), Mth.lerp(partialTicks, s[1], s[0]), ownView));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F) {
			return;
		}
		float a = ZFighterFlightPose.smooth(pose.amount());
		boolean throwing = pose.stage() == ZFighterDisc.THROWING;
		ModelPart lead = avatar.mainArm == HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		ModelPart other = lead == h.rightArm ? h.leftArm : h.rightArm;
		float t = pose.ticks();
		float armX, offX, offOut, lean, twist, head, shake;
		if (throwing) {
			// over and down, the body following the arm through
			float k = ZFighterFlightPose.smooth(t / (ZFighterDisc.SWEEP_TICKS + 1.0F));
			armX = ZFighterDisc.sweepAngle(t);
			offX = Mth.lerp(k, OFF_WIND_X, OFF_THROW_X);
			offOut = Mth.lerp(k, OFF_WIND_OUT, OFF_THROW_OUT);
			lean = Mth.lerp(k, LEAN_WIND, LEAN_THROW);
			twist = Mth.lerp(k, TWIST_WIND, TWIST_THROW);
			head = Mth.lerp(k, HEAD_WIND, HEAD_THROW);
			shake = 0.0F;
		} else {
			// up from wherever the arm was, then held there while the disc gathers
			float up = ZFighterFlightPose.smooth(t / RAISE_TICKS);
			armX = Mth.lerp(up, lead.xRot, ZFighterDisc.ARM_UP);
			offX = OFF_WIND_X * up;
			offOut = OFF_WIND_OUT * up;
			lean = LEAN_WIND * up;
			twist = TWIST_WIND * up;
			head = HEAD_WIND * up;
			shake = SHAKE * up * Mth.sin(t * 2.9F);
		}
		// out from the body: positive zRot for the right arm, negative for the left
		float outSign = lead == h.rightArm ? 1.0F : -1.0F;
		set(lead, armX + shake, 0.0F, ARM_OUT * outSign + shake, a);
		set(other, offX, 0.0F, -offOut * outSign, a);
		h.head.xRot = Mth.lerp(a, h.head.xRot, h.head.xRot + head);
		if (pose.ownView()) {
			return;
		}
		// the shoulders turn with the torso, the throwing one drawn back and then whipped through
		float turn = twist * outSign * a;
		turn(new ModelPart[] {h.body, h.rightArm, h.leftArm}, new Quaternionf().rotationZYX(0.0F, turn, 0.0F));
		// and the upper body tips at the hip, the legs planted
		turn(new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}, new Quaternionf().rotationZYX(0.0F, 0.0F, lean * a));
	}

	/** Turns parts about the hips: where they hang from and how they lie. */
	private static void turn(ModelPart[] parts, Quaternionf q) {
		Vector3f at = new Vector3f();
		Vector3f angles = new Vector3f();
		for (ModelPart part : parts) {
			q.transform(at.set(part.x, part.y, part.z).sub(HIP)).add(HIP);
			part.x = at.x;
			part.y = at.y;
			part.z = at.z;
			new Quaternionf(q).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(angles);
			part.xRot = angles.x;
			part.yRot = angles.y;
			part.zRot = angles.z;
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
