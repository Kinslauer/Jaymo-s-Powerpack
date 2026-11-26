package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterWaveBeam;
import com.jaymospowers.client.PoseBlend;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * Both arms thrust out along the beam, heels of the hands together, on everyone whose fired wave is still out. (0.15.42)
 * Eased in and out ({@link PoseBlend}), braced against it: leaned in, the feet planted wide one before the other, the
 * arms shaking with the beam's push.
 */
public final class ZFighterFiringPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_wave_firing");
	public static final RenderStateDataKey<Integer> STAGE = RenderStateDataKey.create(() -> "jaymospowers:zfighter_wave_stage");
	/** Arms straight forward (x, following the look up and down), turned in (y) so the hands meet. */
	static final float THRUST = -Mth.HALF_PI;
	static final float AIM_SHARE = 0.9F;
	static final float CLOSE = 0.3F;
	/** Braced: the lean in at the hips, the drop (pixels), the feet (front to back, apart; faded out while walking). */
	static final float LEAN = 0.14F, DROP = 0.8F, STANCE = 0.35F, STANCE_SPREAD = 0.1F, STANCE_WALK = 0.6F;
	/** The arms shaking with the beam (radians) at this beat (per tick). */
	static final float SHAKE = 0.03F, SHAKE_BEAT = 2.1F;
	/** How quick it comes on (the weight's spring, times its usual): the thrust is sharp. */
	static final float SPEED = 1.6F;
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("zfighter_firing");

	private ZFighterFiringPose() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		boolean out = entity instanceof Player player && ZFighterWaveBeam.out(player);
		state.setData(KEY, out);
		state.setData(STAGE, out ? ZFighterWaveBeam.stage((Player)entity) : ZFighterWaveBeam.STAGE_NONE);
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		boolean on = Boolean.TRUE.equals(avatar.getData(KEY)) && avatar.deathTime <= 0.0F;
		Integer stageBox = avatar.getData(STAGE);
		int stage = stageBox == null ? ZFighterWaveBeam.STAGE_NONE : stageBox;
		PoseBlend.Track track = BLEND.track(avatar.id, on, 0.0F, stage == ZFighterWaveBeam.STAGE_END ? SPEED * 0.7F : SPEED);
		if (track == null || avatar.deathTime > 0.0F) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		float pitch = THRUST + Mth.clamp(avatar.xRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) * AIM_SHARE;
		float stance = 1.0F - Mth.clamp(avatar.walkAnimationSpeed / STANCE_WALK, 0.0F, 1.0F);
		float lean = stage == ZFighterWaveBeam.STAGE_END ? LEAN * 0.45F : stage == ZFighterWaveBeam.STAGE_SUSTAIN ? LEAN * 1.15F : LEAN;
		float drop = stage == ZFighterWaveBeam.STAGE_END ? DROP * 0.4F : stage == ZFighterWaveBeam.STAGE_SUSTAIN ? DROP * 1.15F : DROP;
		float armPitch = stage == ZFighterWaveBeam.STAGE_END ? pitch + 0.32F : pitch;
		float close = stage == ZFighterWaveBeam.STAGE_END ? CLOSE * 0.45F : CLOSE;
		// the arms aim from the shoulders; the lean is taken back out of them so they stay on the beam
		f.set(PoseBlend.RIGHT_ARM, armPitch - lean, -close, 0.0F);
		f.set(PoseBlend.LEFT_ARM, armPitch - lean, close, 0.0F);
		float shakeScale = stage == ZFighterWaveBeam.STAGE_FIRE ? 1.4F : stage == ZFighterWaveBeam.STAGE_SUSTAIN ? 1.0F : 0.25F;
		float shake = Mth.sin(avatar.ageInTicks * SHAKE_BEAT) * SHAKE * shakeScale;
		f.shake(PoseBlend.RIGHT_ARM, shake);
		f.shake(PoseBlend.LEFT_ARM, -shake * 0.8F);
		f.set(PoseBlend.HIP_PITCH, lean);
		f.set(PoseBlend.DROP, drop * stance);
		f.add(PoseBlend.HEAD, -lean);
		f.add(PoseBlend.RIGHT_LEG, STANCE * stance);
		f.add(PoseBlend.RIGHT_LEG + 2, STANCE_SPREAD * stance);
		f.add(PoseBlend.LEFT_LEG, -STANCE * stance);
		f.add(PoseBlend.LEFT_LEG + 2, -STANCE_SPREAD * stance);
		track.apply(h, f);
	}
}
