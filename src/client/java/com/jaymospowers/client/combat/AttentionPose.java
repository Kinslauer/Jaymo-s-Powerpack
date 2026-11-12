package com.jaymospowers.client.combat;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.combat.Armed;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Guards at attention (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its palace guard stands with its pike
 * at rest and brings it to bear when a fight starts). A humanoid mob with a polearm in hand ({@link Armed.Kind#POLE})
 * that is standing still with nobody to fight holds it upright at its side, the hand out before the shoulder so the
 * shaft stands; one that has someone to fight ({@link Mob#isAggressive}) levels it at them in both hands. Walking
 * with nothing to fight, and every swing, are left as they were. Players have their own stance ({@link ArmedPose}).
 */
public final class AttentionPose {
	// ===== the knobs ================================================================================================
	/** The model's walking speed (0-1) under which a mob counts as standing. */
	static final float STILL = 0.08F;
	/** At attention: the weapon arm (x raised ahead when negative, y turned in, z lifted out), and the other arm at the side. */
	static final float[] UPRIGHT = {-1.42F, 0.0F, 0.12F};
	/** Levelled: the weapon arm low with the shaft pointing ahead, the other hand come across to it, and the shoulders turned. */
	static final float[] LEVEL = {-0.4F, 0.3F, 0.0F, -0.95F, 0.75F, 0.0F};
	static final float LEVEL_YAW = 0.3F;
	/** How quick it comes and goes (the weight's spring, times its usual). */
	static final float SPEED = 0.8F;
	// ================================================================================================================

	/** What's on a render state: the mob, which arm has the polearm, and whether it is levelled at someone. */
	public record Posted(int id, boolean right, boolean levelled) {
	}

	public static final RenderStateDataKey<Posted> KEY = RenderStateDataKey.create(() -> "jaymospowers:attention");
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("attention");

	private AttentionPose() {
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (state instanceof AvatarRenderState || !(entity instanceof Mob mob) || !mob.isAlive() || mob.isSleeping() || mob.isPassenger()
			|| mob.isUsingItem() || mob.isSwinging() || Armed.kind(mob.getMainHandItem()) != Armed.Kind.POLE) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Posted(mob.getId(), mob.getMainArm() == HumanoidArm.RIGHT, mob.isAggressive()));
	}

	/** Whether the pose is on this state (for {@link PoseTakeover}: Entity Model Features pauses so it holds). */
	public static boolean on(LivingEntityRenderState state) {
		Posted posted = state.getData(KEY);
		return posted != null && state.deathTime <= 0.0F && (posted.levelled() || state.walkAnimationSpeed < STILL) && state.getData(ParryPose.KEY) == null;
	}

	/** Right after a model is animated. */
	public static void applyModel(Model<?> m, Object s) {
		if (!(m instanceof HumanoidModel<?> h) || !(s instanceof LivingEntityRenderState state) || s instanceof AvatarRenderState) {
			return;
		}
		Posted posted = state.getData(KEY);
		Integer mob = state.getData(PoseTakeover.MOB_ID);
		int id = posted != null ? posted.id() : mob != null ? mob : -1;
		if (id < 0) {
			return;
		}
		boolean on = on(state);
		PoseBlend.Track track = BLEND.track(id, on, 0.0F, SPEED);
		if (track == null) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		float side = posted.right() ? 1.0F : -1.0F;
		int mainArm = posted.right() ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM, otherArm = posted.right() ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		if (posted.levelled()) {
			f.set(mainArm, LEVEL[0], -side * LEVEL[1], side * LEVEL[2]);
			f.set(otherArm, LEVEL[3], side * LEVEL[4], -side * LEVEL[5]);
			f.set(PoseBlend.HIP_YAW, -side * LEVEL_YAW);
			f.add(PoseBlend.HEAD + 1, side * LEVEL_YAW);
		} else {
			f.set(mainArm, UPRIGHT[0], -side * UPRIGHT[1], side * UPRIGHT[2]);
			f.set(otherArm, 0.0F, 0.0F, 0.0F);
		}
		track.apply(h, f);
	}
}
