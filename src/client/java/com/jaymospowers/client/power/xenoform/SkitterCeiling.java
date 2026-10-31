package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.113, Scan E) Skitter clinging to a ceiling ({@link XenoformState#CEILING}): every client turns its body over
 * (feet to the ceiling, head down) and the clinging player's own camera rolls over with it, its eyes moved down to
 * where the upside-down head is. Both ease in and out over {@link #TURN_TICKS} ticks.
 */
public final class SkitterCeiling {
	/** Ticks to turn right over. */
	static final float TURN_TICKS = 6.0F;

	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:skitter_ceiling");

	/** entity id → {amount, amount before}. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private SkitterCeiling() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean on = Boolean.TRUE.equals(player.getAttached(XenoformState.CEILING));
			float[] a = AMOUNTS.get(player.getId());
			if (a == null) {
				if (!on) {
					continue;
				}
				a = new float[2];
				AMOUNTS.put(player.getId(), a);
			}
			a[1] = a[0];
			a[0] = Mth.clamp(a[0] + (on ? 1.0F : -1.0F) / TURN_TICKS, 0.0F, 1.0F);
		}
		AMOUNTS.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** How far over {@code entity} is turned (0 upright, 1 upside down), smoothed. */
	public static float amount(@Nullable Entity entity, float partialTicks) {
		float[] a = entity == null ? null : AMOUNTS.get(entity.getId());
		return a == null ? 0.0F : XenoformFlightPose.smooth(Mth.lerp(partialTicks, a[1], a[0]));
	}

	/** More than half turned over: the mouse and strafe keys go by the rolled view. */
	public static boolean overturned(@Nullable Entity entity) {
		return amount(entity, 1.0F) > 0.5F;
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		float flip = amount(entity, partialTicks);
		state.setData(KEY, flip > 0.001F ? flip : null);
	}

	/** After the body's turned to face its way: rolled over about its middle. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float entityScale) {
		Float flip = state.getData(KEY);
		if (flip == null || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, 180.0F * flip);
		poseStack.translate(0.0F, -middle, 0.0F);
	}
}
