package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterWaveBeam;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/** Both arms thrust out along the beam, heels of the hands together, on everyone whose fired wave is still out. */
public final class ZFighterFiringPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_wave_firing");
	/** Arms straight forward (x, following the look up and down), turned in (y) so the hands meet. */
	static final float THRUST = -Mth.HALF_PI;
	static final float AIM_SHARE = 0.9F;
	static final float CLOSE = 0.3F;

	private ZFighterFiringPose() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player && ZFighterWaveBeam.out(player));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || !Boolean.TRUE.equals(avatar.getData(KEY))
			|| avatar.deathTime > 0.0F) {
			return;
		}
		float pitch = THRUST + Mth.clamp(avatar.xRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) * AIM_SHARE;
		h.rightArm.xRot = pitch;
		h.rightArm.yRot = -CLOSE;
		h.rightArm.zRot = 0.0F;
		h.leftArm.xRot = pitch;
		h.leftArm.yRot = CLOSE;
		h.leftArm.zRot = 0.0F;
	}
}
