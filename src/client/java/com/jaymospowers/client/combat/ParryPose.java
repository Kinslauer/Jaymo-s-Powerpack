package com.jaymospowers.client.combat;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.jaymospowers.combat.Armed;
import com.jaymospowers.combat.Guard;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A weapon's guard as a body holds it (Andy's picks from Star Wars: Galaxy in a Cube, ideas only: see {@link Armed}).
 * The guard ({@link Guard}) had its timing bar and its sparks, but the body only lifted an arm. Now:
 * <ul>
 * <li>Held up, the blade is carried across the body, the weapon shoulder forward.</li>
 * <li>When a blow meets it ({@link Armed#MET}, synced to everyone) the guard goes to the line it came on for
 * {@link #HOLD_TICKS}: <b>high</b> (the blade over the head, for a blow from above), <b>low</b> (dropped across the
 * legs, the body bent to it, for one from below) or <b>hanging</b> (point down across the far side); blows at your own
 * height take the high and hanging guards turn about.</li>
 * <li>The blow rocks the body back ({@link #RECOIL_TICKS}): hard on a plain block, barely on a parry.</li>
 * </ul>
 * On anything drawn with a humanoid model, so fighters who parry you (Kingdoms' MobParry, through
 * {@link com.jaymospowers.combat.ClashFx#met}) snap to the line too, guard raised or not. A shield is left to vanilla.
 * The powers' own guards (Darktide's, a Z Fighter's) keep their own poses.
 */
public final class ParryPose {
	// ===== the knobs ================================================================================================
	/** After a blow is met: how long the guard stays on its line, and how long the body rocks from it (ticks). */
	static final float HOLD_TICKS = 9.0F, RECOIL_TICKS = 7.0F;
	/** Arm angles (x raised ahead when negative, y turned in toward the chest, z lifted out), weapon arm then other arm, as a right-hander's: the plain guard, then each line. */
	static final float[] GUARD = {-1.15F, 0.45F, 0.15F, -0.35F, 0.2F, 0.1F};
	static final float[] HIGH = {-2.35F, 0.55F, 0.45F, -0.2F, 0.0F, 0.3F};
	static final float[] LOW = {-0.6F, 0.8F, -0.25F, 0.3F, 0.0F, 0.35F};
	static final float[] HANGING = {-1.85F, -0.55F, 0.85F, -0.6F, 0.3F, 0.0F};
	/** The hips with each: bent forward, the weapon shoulder turned forward, tipped (radians), and lowered (pixels). */
	static final float[] GUARD_HIPS = {0.05F, 0.2F, 0.0F, 0.3F};
	static final float[] HIGH_HIPS = {-0.05F, 0.1F, 0.0F, 0.6F};
	static final float[] LOW_HIPS = {0.28F, 0.3F, 0.0F, 1.4F};
	static final float[] HANGING_HIPS = {0.0F, -0.35F, -0.08F, 0.3F};
	/** The rock back from a blow: the hips (radians), the weapon arm driven back (radians), the body shoved back (pixels); and how much of it a parry takes. */
	static final float RECOIL_HIPS = 0.14F, RECOIL_ARM = 0.3F, RECOIL_BACK = 0.8F, PARRY_RECOIL = 0.4F;
	/** How quick the guard comes up (the weight's spring, times its usual), and how much snappier its springs are than a pose's. */
	static final float SPEED = 1.6F, STIFFNESS = 1.5F;
	// ================================================================================================================

	/**
	 * What's on a render state: whose it is (entity id), which arm guards, the line met ({@link Armed#HIGH}...; -1: only
	 * held up), how long ago (ticks; -1: nothing lately) and whether that was a parry.
	 */
	public record Parry(int id, boolean right, int line, float since, boolean parried, boolean dual) {
	}

	/** Two blades guard crossed: how much lower the second arm is held than the first, and how much further across the body. */
	static final float CROSS_LOWER = 0.2F, CROSS_OVER = 0.25F;

	public static final RenderStateDataKey<Parry> KEY = RenderStateDataKey.create(() -> "jaymospowers:parry_pose");
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("parry");

	private ParryPose() {
	}

	/** From the living renderer's extractRenderState (players and mobs alike). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(KEY, parry(entity, partialTicks));
	}

	private static @Nullable Parry parry(LivingEntity entity, float partialTicks) {
		if (!entity.isAlive()) {
			return null;
		}
		boolean up = entity.isUsingItem() && Guard.weaponGuard(entity.getUseItem());
		Armed.Met met = entity.getAttached(Armed.MET);
		float since = met == null ? -1.0F : entity.level().getGameTime() - met.time() + partialTicks;
		boolean lately = since >= 0.0F && since < HOLD_TICKS;
		if (!up && !lately) {
			return null;
		}
		boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
		if (up && entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
			right = !right;
		}
		return new Parry(entity.getId(), right, lately ? met.line() : -1, lately ? since : -1.0F, lately && met.parried(), Armed.dual(entity));
	}

	/** Right after a model is animated (for the layers, and as each model is drawn). */
	public static void applyModel(Model<?> m, Object s) {
		if (!(m instanceof HumanoidModel<?> h) || !(s instanceof LivingEntityRenderState state)) {
			return;
		}
		Parry parry = state.getData(KEY);
		boolean on = parry != null && state.deathTime <= 0.0F && !CombatAnimCompat.firstPersonPass(state)
			&& (state instanceof AvatarRenderState player ? !PoseTakeover.posedOtherwise(player) : !PoseTakeover.mobPosed(state));
		Integer mob = state.getData(PoseTakeover.MOB_ID);
		int id = parry != null ? parry.id() : state instanceof AvatarRenderState avatar ? avatar.id : mob != null ? mob : -1;
		if (id < 0) {
			return;
		}
		PoseBlend.Track track = BLEND.track(id, on, 0.0F, SPEED);
		if (track == null) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		float[] arms, hips;
		switch (parry.line()) {
			case Armed.HIGH -> {
				arms = HIGH;
				hips = HIGH_HIPS;
			}
			case Armed.LOW -> {
				arms = LOW;
				hips = LOW_HIPS;
			}
			case Armed.HANGING -> {
				arms = HANGING;
				hips = HANGING_HIPS;
			}
			default -> {
				arms = GUARD;
				hips = GUARD_HIPS;
			}
		}
		// the rock back: sharp at the blow, gone by RECOIL_TICKS
		float k = parry.since() >= 0.0F && parry.since() < RECOIL_TICKS ? 1.0F - parry.since() / RECOIL_TICKS : 0.0F;
		k = k * k * (parry.parried() ? PARRY_RECOIL : 1.0F);
		float side = parry.right() ? 1.0F : -1.0F;
		int mainArm = parry.right() ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM, otherArm = parry.right() ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		f.set(mainArm, arms[0] + RECOIL_ARM * k, -side * arms[1], side * arms[2]);
		if (parry.dual()) {
			// a blade in each hand: the other comes up behind the first, the two crossed before the body
			f.set(otherArm, arms[0] + RECOIL_ARM * k + CROSS_LOWER, side * (arms[1] + CROSS_OVER), -side * arms[2]);
		} else {
			f.set(otherArm, arms[3], side * arms[4], -side * arms[5]);
		}
		float pitch = hips[0] - RECOIL_HIPS * k, yaw = -side * hips[1];
		f.set(PoseBlend.HIP_PITCH, pitch);
		f.set(PoseBlend.HIP_YAW, yaw);
		f.set(PoseBlend.HIP_ROLL, side * hips[2]);
		f.set(PoseBlend.DROP, hips[3]);
		f.set(PoseBlend.FORWARD, -RECOIL_BACK * k);
		// the head stays on the foe
		f.add(PoseBlend.HEAD, -pitch);
		f.add(PoseBlend.HEAD + 1, -yaw);
		track.apply(h, f, STIFFNESS);
	}
}
