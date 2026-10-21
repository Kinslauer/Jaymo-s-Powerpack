package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterGuard;
import com.jaymospowers.client.PoseBlend;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * The guard's crossed forearms, raised before the face, on everyone who holds it up. (0.15.42) It eases up and down
 * ({@link PoseBlend}), with a fighter's stance under it: chin tucked, shoulders a little forward, knees set, feet apart.
 */
public final class ZFighterGuardPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_guard");
	/** Arms raised forward (x), turned inwards (y) so the forearms cross. */
	static final float RAISE = -1.75F;
	static final float CROSS = 0.65F;
	/** The stance: leaned in at the hips, lowered (pixels), the chin tucked, the feet apart (faded out while walking). */
	static final float LEAN = 0.12F, DROP = 0.6F, TUCK = 0.15F, STANCE = 0.22F, STANCE_SPREAD = 0.08F, STANCE_WALK = 0.6F;
	/** How quick the guard comes up (the weight's spring, times its usual). */
	static final float SPEED = 1.5F;
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("zfighter_guard");

	private ZFighterGuardPose() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player && ZFighterGuard.guarding(player));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		boolean on = Boolean.TRUE.equals(avatar.getData(KEY)) && avatar.deathTime <= 0.0F;
		PoseBlend.Track track = BLEND.track(avatar.id, on, 0.0F, SPEED);
		if (track == null || avatar.deathTime > 0.0F) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		float pitch = RAISE + Mth.clamp(avatar.xRot * Mth.DEG_TO_RAD, -0.5F, 0.5F) * 0.5F;
		float stance = 1.0F - Mth.clamp(avatar.walkAnimationSpeed / STANCE_WALK, 0.0F, 1.0F);
		float breath = Mth.sin(avatar.ageInTicks * 0.12F) * 0.015F;
		f.set(PoseBlend.RIGHT_ARM, pitch + breath, -CROSS, 0.0F);
		f.set(PoseBlend.LEFT_ARM, pitch + 0.1F + breath, CROSS, 0.0F);
		f.set(PoseBlend.HIP_PITCH, LEAN);
		f.set(PoseBlend.DROP, DROP * stance);
		f.add(PoseBlend.HEAD, TUCK - LEAN);
		f.add(PoseBlend.RIGHT_LEG, STANCE * stance);
		f.add(PoseBlend.RIGHT_LEG + 2, STANCE_SPREAD * stance);
		f.add(PoseBlend.LEFT_LEG, -STANCE * stance);
		f.add(PoseBlend.LEFT_LEG + 2, -STANCE_SPREAD * stance);
		track.apply(h, f);
	}
}
