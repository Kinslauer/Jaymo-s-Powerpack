package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterGuard;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/** The guard's crossed forearms, raised before the face, on everyone who holds it up. */
public final class ZFighterGuardPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_guard");
	/** Arms raised forward (x), turned inwards (y) so the forearms cross. */
	static final float RAISE = -1.75F;
	static final float CROSS = 0.65F;

	private ZFighterGuardPose() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player && ZFighterGuard.guarding(player));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || !Boolean.TRUE.equals(avatar.getData(KEY))
			|| avatar.deathTime > 0.0F) {
			return;
		}
		float pitch = RAISE + Mth.clamp(avatar.xRot * Mth.DEG_TO_RAD, -0.5F, 0.5F) * 0.5F;
		h.rightArm.xRot = pitch;
		h.rightArm.yRot = -CROSS;
		h.rightArm.zRot = 0.0F;
		h.leftArm.xRot = pitch + 0.1F;
		h.leftArm.yRot = CROSS;
		h.leftArm.zRot = 0.0F;
	}
}
