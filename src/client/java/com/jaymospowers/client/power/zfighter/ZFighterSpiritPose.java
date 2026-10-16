package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

/**
 * Hands raised to a Spirit Bomb ({@link ZFighterPower#SPIRIT_HANDS}): the one raising it and everyone lending to it hold
 * both arms straight up, palms to the sky, with a faint tremble. Eased in and out.
 */
public final class ZFighterSpiritPose {
	/** How far into the pose (0-1) this frame, and the time (for the tremble). */
	public record Pose(float amount, float time) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_spirit_hands");
	static final float EASE = 0.15F;
	/** Arms up (radians: negative raises them), spread out from the body, the head tipped back to look at it. */
	static final float ARMS_UP = -2.95F, ARMS_OUT = 0.28F, HEAD_BACK = -0.45F, SHAKE = 0.02F;

	/** entity id → {amount, amount before}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	private ZFighterSpiritPose() {
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
			boolean up = Boolean.TRUE.equals(player.getAttached(ZFighterPower.SPIRIT_HANDS));
			float[] s = STATE.get(player.getId());
			if (s == null) {
				if (!up) {
					continue;
				}
				s = new float[2];
				STATE.put(player.getId(), s);
			}
			s[1] = s[0];
			s[0] = up ? Math.min(1.0F, s[0] + EASE) : Math.max(0.0F, s[0] - EASE);
		}
		STATE.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] s = STATE.get(entity.getId());
		state.setData(KEY, s == null ? null : new Pose(Mth.lerp(partialTicks, s[1], s[0]), entity.tickCount + partialTicks));
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
		float shake = SHAKE * Mth.sin(pose.time() * 2.3F);
		h.rightArm.xRot = Mth.lerp(a, h.rightArm.xRot, ARMS_UP + shake);
		h.rightArm.yRot = Mth.lerp(a, h.rightArm.yRot, 0.0F);
		h.rightArm.zRot = Mth.lerp(a, h.rightArm.zRot, ARMS_OUT + shake);
		h.leftArm.xRot = Mth.lerp(a, h.leftArm.xRot, ARMS_UP - shake);
		h.leftArm.yRot = Mth.lerp(a, h.leftArm.yRot, 0.0F);
		h.leftArm.zRot = Mth.lerp(a, h.leftArm.zRot, -ARMS_OUT - shake);
		h.head.xRot = Mth.lerp(a, h.head.xRot, HEAD_BACK);
	}
}
