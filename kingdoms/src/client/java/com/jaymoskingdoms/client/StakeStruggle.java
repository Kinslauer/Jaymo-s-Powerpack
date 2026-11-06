package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Stakes;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.641) Working a stake out ({@link Stakes}). Input: Sneak + scroll up, one tug a notch ({@link Stakes.Tug}), ahead of
 * the stance wheel's Sneak + scroll (scroll down still steps the stance). The meter holds where it is between tugs.
 * Look: a staked vampire struggling with it, both hands clawing at the chest and wrenching outward in jerks, bent over it,
 * head down: a mob the whole time it's staked, a player for a moment after each tug.
 */
public final class StakeStruggle {
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:stake_struggle");
	/** Arms: raised forward this far (radians), turned in toward the chest, and the wrench's size and pace (per tick). */
	static final float ARM_UP = -1.15F, ARM_IN = 0.6F, WRENCH = 0.32F, WRENCH_SPEED = 1.1F;
	/** Bent over the stake, head down, and a shake side to side. */
	static final float BEND = 0.22F, HEAD_DOWN = 0.45F, SHAKE = 0.08F;

	private StakeStruggle() {
	}

	/** From the scroll mixin: whether this scroll was a tug (and so not the stance wheel's or the hotbar's). */
	public static boolean scroll(double y) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (y <= 0.0 || player == null || minecraft.gui.screen() != null || !minecraft.options.keyShift.isDown()) {
			return false;
		}
		List<Stakes.Stake> stakes = player.getAttached(Stakes.STAKE);
		if (stakes == null || stakes.isEmpty()) {
			return false;
		}
		if (ClientPlayNetworking.canSend(Stakes.Tug.TYPE)) {
			ClientPlayNetworking.send(new Stakes.Tug());
		}
		return true;
	}

	/** Render-state extraction: whether {@code entity} is struggling with a stake, and its clock. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		List<Stakes.Stake> stakes = entity.getAttached(Stakes.STAKE);
		if (stakes == null || stakes.isEmpty() || !entity.isAlive()) {
			state.setData(KEY, null);
			return;
		}
		float weight = 1.0F;
		if (entity instanceof Player) {
			Long last = entity.getAttached(Stakes.STRUGGLE);
			long since = last == null ? Long.MAX_VALUE : entity.level().getGameTime() - last;
			if (since < 0 || since >= Stakes.STRUGGLE_TICKS) {
				state.setData(KEY, null);
				return;
			}
			// eases off over the last few ticks
			weight = Math.min(1.0F, (Stakes.STRUGGLE_TICKS - since - partialTicks) / 4.0F);
		}
		state.setData(KEY, new float[] {Math.max(0.0F, weight), entity.tickCount + partialTicks});
	}

	/** After the model's own animation: the struggle on top. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] s = living.getData(KEY);
		if (s == null || s[0] <= 0.0F) {
			return;
		}
		float w = s[0], t = s[1] * WRENCH_SPEED;
		// a jerk, not a smooth swing: sharp outward, slower back
		float wrench = Mth.sin(t) * WRENCH;
		wrench = wrench > 0 ? wrench : wrench * 0.5F;
		float shake = Mth.sin(t * 2.3F) * SHAKE;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, ARM_UP - wrench);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, ARM_UP - wrench * 0.9F);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -ARM_IN);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, ARM_IN);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.1F);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.1F);
		m.body.xRot += w * (BEND + wrench * 0.25F);
		m.body.zRot += w * shake;
		m.head.xRot += w * (HEAD_DOWN + wrench * 0.3F);
		m.head.zRot += w * shake * 1.5F;
	}
}
