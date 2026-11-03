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
 * <p>
 * (0.19.60) Also Skitter's crawl: prone, it drops from its upright shape into the spider ({@link #CRAWL_KEY}), and
 * crawling up a wall ({@link XenoformState#CLIMB}) its body tips head-up against the wall ({@link #CLIMB_KEY}).
 */
public final class SkitterCeiling {
	/** Ticks to turn right over. */
	static final float TURN_TICKS = 6.0F;

	/** (0.19.60) Ticks to drop into the spider (or rise back to the upright shape), and to tip up onto a wall. */
	static final float CRAWL_TICKS = 5.0F, CLIMB_TICKS = 5.0F;
	/** (0.19.60) How far in front of the feet the wall is (blocks): the spider tips up about this high, its legs on the wall. */
	static final float WALL_PIVOT = 0.3F;

	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:skitter_ceiling");
	/** (0.19.60) Skitter in its spider shape, 0 (upright) to 1 (spider). */
	public static final RenderStateDataKey<Float> CRAWL_KEY = RenderStateDataKey.create(() -> "jaymospowers:skitter_crawl");
	/** (0.19.60) Skitter's spider tipped up a wall, 0 to 1. */
	public static final RenderStateDataKey<Float> CLIMB_KEY = RenderStateDataKey.create(() -> "jaymospowers:skitter_climb");

	/** entity id → {amount, amount before}. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();
	/** (0.19.60) The same for the crawl and the wall. */
	private static final Map<Integer, float[]> CRAWLS = new HashMap<>(), CLIMBS = new HashMap<>();

	private SkitterCeiling() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			CRAWLS.clear();
			CLIMBS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			step(AMOUNTS, player, Boolean.TRUE.equals(player.getAttached(XenoformState.CEILING)), TURN_TICKS);
			boolean crawl = "skitter".equals(player.getAttached(XenoformState.FORM)) && com.jaymospowers.power.xenoform.forms.Skitter.crawling(player)
				&& !com.jaymospowers.core.wounds.Downed.isDowned(player);
			step(CRAWLS, player, crawl, CRAWL_TICKS);
			step(CLIMBS, player, crawl && Boolean.TRUE.equals(player.getAttached(XenoformState.CLIMB)), CLIMB_TICKS);
		}
		prune(AMOUNTS, level);
		prune(CRAWLS, level);
		prune(CLIMBS, level);
	}

	private static void step(Map<Integer, float[]> amounts, Entity entity, boolean on, float ticks) {
		float[] a = amounts.get(entity.getId());
		if (a == null) {
			if (!on) {
				return;
			}
			a = new float[2];
			amounts.put(entity.getId(), a);
		}
		a[1] = a[0];
		a[0] = Mth.clamp(a[0] + (on ? 1.0F : -1.0F) / ticks, 0.0F, 1.0F);
	}

	private static void prune(Map<Integer, float[]> amounts, ClientLevel level) {
		amounts.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	private static float eased(Map<Integer, float[]> amounts, @Nullable Entity entity, float partialTicks) {
		float[] a = entity == null ? null : amounts.get(entity.getId());
		return a == null ? 0.0F : XenoformFlightPose.smooth(Mth.lerp(partialTicks, a[1], a[0]));
	}

	/** (0.19.60) How far {@code entity} (Skitter) has dropped into its spider shape, smoothed. */
	public static float crawl(@Nullable Entity entity, float partialTicks) {
		return eased(CRAWLS, entity, partialTicks);
	}

	/** How far over {@code entity} is turned (0 upright, 1 upside down), smoothed. */
	public static float amount(@Nullable Entity entity, float partialTicks) {
		return eased(AMOUNTS, entity, partialTicks);
	}

	/** More than half turned over: the mouse and strafe keys go by the rolled view. */
	public static boolean overturned(@Nullable Entity entity) {
		return amount(entity, 1.0F) > 0.5F;
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		float flip = amount(entity, partialTicks);
		state.setData(KEY, flip > 0.001F ? flip : null);
		float crawl = crawl(entity, partialTicks);
		state.setData(CRAWL_KEY, crawl > 0.001F ? crawl : null);
		float climb = eased(CLIMBS, entity, partialTicks);
		state.setData(CLIMB_KEY, climb > 0.001F ? climb : null);
	}

	/** After the body's turned to face its way: rolled over about its middle. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float entityScale) {
		Float climb = state.getData(CLIMB_KEY);
		if (climb != null && state.deathTime <= 0.0F) {
			// (0.19.60) up a wall: tipped head-up, the belly (and legs) to the wall in front
			float pivot = WALL_PIVOT / Math.max(entityScale, 0.01F);
			poseStack.translate(0.0F, pivot, 0.0F);
			poseStack.rotateDegrees(Axis.XP, 90.0F * climb);
			poseStack.translate(0.0F, -pivot, 0.0F);
		}
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
