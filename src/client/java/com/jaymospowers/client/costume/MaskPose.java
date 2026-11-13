package com.jaymospowers.client.costume;

import com.jaymospowers.costume.MaskMotion;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * Both hands up to the face and away again while a mask comes off or goes on ({@link MaskMotion}): they rise, take hold
 * as it comes off (or settles on), and come down with it.
 */
public final class MaskPose {
	static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:mask_motion");
	/** The arms at the face: raised this far (radians, from hanging), turned in this far. */
	static final float RAISE = 2.55F, IN = 0.55F;

	private MaskPose() {
	}

	/** From the avatar renderer's extractRenderState: how far through the motion (0-1), or nothing. */
	public static void extract(Avatar entity, AvatarRenderState state, float partial) {
		Long at = entity.getAttached(MaskMotion.MASKING);
		@Nullable Float t = null;
		if (at != null) {
			float f = (entity.level().getGameTime() - at + partial) / MaskMotion.TICKS;
			if (f >= 0.0F && f < 1.0F) {
				t = f;
			}
		}
		state.setData(KEY, t);
	}

	/** Right after a humanoid model animates: the hands lifted toward the face, by how far through it is. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Float t = avatar.getData(KEY);
		if (t == null) {
			return;
		}
		// up quick, a hold at the face, down slower: peaks just as the mask moves (MaskMotion.REACH)
		float reach = (float)MaskMotion.REACH / MaskMotion.TICKS;
		float lift = t < reach ? Mth.sin(t / reach * Mth.HALF_PI) : Mth.cos((t - reach) / (1.0F - reach) * Mth.HALF_PI);
		float look = h.head.xRot * 0.5F;
		h.rightArm.xRot = Mth.lerp(lift, h.rightArm.xRot, -RAISE + look);
		h.rightArm.yRot = Mth.lerp(lift, h.rightArm.yRot, h.head.yRot - IN);
		h.rightArm.zRot = Mth.lerp(lift, h.rightArm.zRot, 0.0F);
		h.leftArm.xRot = Mth.lerp(lift, h.leftArm.xRot, -RAISE + look);
		h.leftArm.yRot = Mth.lerp(lift, h.leftArm.yRot, h.head.yRot + IN);
		h.leftArm.zRot = Mth.lerp(lift, h.leftArm.zRot, 0.0F);
	}
}
