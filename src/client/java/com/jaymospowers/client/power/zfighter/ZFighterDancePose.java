package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterFusion;
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

/**
 * The fusion dance: arms swept out to one side, then over the head to the other, then both pointed at the partner, a
 * knee up. The two dancers sweep opposite ways, so they mirror each other.
 */
public final class ZFighterDancePose {
	/** How far through the dance (0-1), which way he sweeps, and how much of the pose shows (eased in and out). */
	public record Pose(float progress, int side, float amount) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_dance");
	static final float EASE = 0.25F;

	/**
	 * Key poses for a dancer sweeping right: right arm x y z, left arm x y z, right leg x z, left leg x z, body lean (z).
	 * The left-sweeping one is the mirror.
	 */
	private static final float[][] KEYS = {
		// arms flung out to the right, the left one across the chest; stepping right
		{0.0F, 0.0F, 1.5F, -1.4F, 0.7F, 0.0F, 0.0F, 0.35F, 0.0F, 0.0F, 0.15F},
		// swept over the top to the left
		{-1.4F, -0.7F, 0.0F, 0.0F, 0.0F, -1.5F, 0.0F, 0.0F, 0.0F, -0.35F, -0.15F},
		// fingertips pointed at the partner, the outer knee raised
		{-1.5F, 0.35F, 0.0F, -2.6F, 0.0F, -0.5F, -1.2F, 0.2F, 0.0F, 0.0F, 0.1F},
	};
	/** Where each key pose is reached (share of the dance). */
	private static final float[] AT = {0.3F, 0.6F, 0.85F};

	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterDancePose() {
	}

	/** Every client tick: [0] amount now, [1] amount before, [2] ticks into the dance, [3] side. */
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
			Integer side = player.getAttached(ZFighterFusion.DANCE);
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (side == null) {
					continue;
				}
				s = new float[4];
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			if (side != null) {
				s[0] = Math.min(1.0F, s[0] + EASE);
				s[2]++;
				s[3] = side;
			} else {
				s[0] = Math.max(0.0F, s[0] - EASE);
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		if (s == null) {
			state.setData(KEY, null);
			return;
		}
		float progress = Mth.clamp((s[2] + partialTicks) / ZFighterFusion.DANCE_TICKS, 0.0F, 1.0F);
		state.setData(KEY, new Pose(progress, (int)s[3], Mth.lerp(partialTicks, s[1], s[0])));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null || pose.amount() <= 0.001F) {
			return;
		}
		float[] k = frame(pose.progress());
		float a = ZFighterFlightPose.smooth(pose.amount());
		boolean right = pose.side() >= 0;
		ModelPart sweepArm = right ? h.rightArm : h.leftArm;
		ModelPart otherArm = right ? h.leftArm : h.rightArm;
		ModelPart sweepLeg = right ? h.rightLeg : h.leftLeg;
		ModelPart otherLeg = right ? h.leftLeg : h.rightLeg;
		float m = right ? 1.0F : -1.0F;
		pose(sweepArm, k[0], k[1] * m, k[2] * m, a);
		pose(otherArm, k[3], k[4] * m, k[5] * m, a);
		pose(sweepLeg, k[6], 0.0F, k[7] * m, a);
		pose(otherLeg, k[8], 0.0F, k[9] * m, a);
		h.body.zRot = Mth.lerp(a, h.body.zRot, k[10] * m);
	}

	/** The pose at this point of the dance, between the key poses. */
	private static float[] frame(float t) {
		if (t <= AT[0]) {
			return lerp(KEYS[0], KEYS[0], 1.0F);
		}
		for (int i = 1; i < KEYS.length; i++) {
			if (t <= AT[i]) {
				return lerp(KEYS[i - 1], KEYS[i], ZFighterFlightPose.smooth((t - AT[i - 1]) / (AT[i] - AT[i - 1])));
			}
		}
		return KEYS[KEYS.length - 1];
	}

	private static float[] lerp(float[] from, float[] to, float t) {
		float[] out = new float[from.length];
		for (int i = 0; i < from.length; i++) {
			out[i] = Mth.lerp(t, from[i], to[i]);
		}
		return out;
	}

	private static void pose(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
