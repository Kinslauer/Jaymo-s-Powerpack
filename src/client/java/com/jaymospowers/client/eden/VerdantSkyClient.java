package com.jaymospowers.client.eden;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Verdant World's sky (Andy's Eden Ring pick, "a gas giant and its ring in the sky"): a blue giant hangs always in the
 * same place overhead, a ring round it, faint by day and bright at night. It is drawn round the camera at the edge of what
 * is loaded, so the land in front of it hides it as it would hide anything far off. Art: Eden Ring's planet and rings
 * (paulevs, MIT), {@code textures/environment/eden_planet.png} and {@code eden_rings.png}.
 */
public final class VerdantSkyClient {
	// ---- tuning ----
	/** Where the giant hangs: degrees above the horizon, and round from east toward south. */
	static final float ELEVATION = 52.0F, AROUND = 205.0F;
	/** Its width and its ring's, as shares of the distance it is drawn at, and how flat the ring lies to the eye (1 a circle). */
	static final float PLANET_SIZE = 0.34F, RING_SIZE = 0.82F, RING_TILT = 0.3F;
	/** How far round the ring is turned in the sky, in degrees. */
	static final float RING_ROLL = -24.0F;
	/** How strongly it shows at noon and at midnight (0-1). */
	static final float DAY_ALPHA = 0.5F, NIGHT_ALPHA = 1.0F;
	/** It is drawn this share of the loaded distance away, and never nearer than this many blocks. */
	static final float DISTANCE_SHARE = 0.95F, LEAST_DISTANCE = 96.0F;

	static final Identifier PLANET = JaymosPowers.id("textures/environment/eden_planet.png");
	static final Identifier RINGS = JaymosPowers.id("textures/environment/eden_rings.png");
	static final ResourceKey<Level> VERDANT_WORLD = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("verdant_world"));

	private VerdantSkyClient() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(VerdantSkyClient::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || level.dimension() != VERDANT_WORLD) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		// 1 at noon, -1 at midnight
		float sun = (float) Math.cos(Math.PI * 2.0 * ((time - 6000L + partial) / 24000.0));
		int alpha = Math.round(255.0F * Mth.lerp(Mth.clamp(sun * 2.0F + 0.5F, 0.0F, 1.0F), NIGHT_ALPHA, DAY_ALPHA));
		int colour = alpha << 24 | 0xFFFFFF;
		float distance = Math.max(LEAST_DISTANCE, minecraft.options.getEffectiveRenderDistance() * 16.0F * DISTANCE_SHARE);
		double e = Math.toRadians(ELEVATION), a = Math.toRadians(AROUND);
		Vec3 to = new Vec3(Math.cos(e) * Math.cos(a), Math.sin(e), Math.cos(e) * Math.sin(a));
		Vec3 side = to.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 up = side.cross(to).normalize();
		// the ring's long and short ways across the sky, rolled a little off level
		double roll = Math.toRadians(RING_ROLL);
		Vec3 along = side.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
		Vec3 across = up.scale(Math.cos(roll)).subtract(side.scale(Math.sin(roll)));
		Vec3 middle = to.scale(distance);
		float ring = distance * RING_SIZE * 0.5F, planet = distance * PLANET_SIZE * 0.5F;
		Vec3 ringAlong = along.scale(ring), ringAcross = across.scale(ring * RING_TILT);
		PoseStack poseStack = context.poseStack();
		// the far half of the ring, the giant in front of it, then the near half in front of the giant
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RINGS), (pose, buffer) ->
			band(buffer, pose, middle, ringAlong, ringAcross, 0.0F, 0.5F, colour));
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(PLANET), (pose, buffer) ->
			band(buffer, pose, middle.scale(0.999), side.scale(planet), up.scale(planet), 0.0F, 1.0F, colour));
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RINGS), (pose, buffer) ->
			band(buffer, pose, middle.scale(0.998), ringAlong, ringAcross, 0.5F, 1.0F, colour));
	}

	/**
	 * The part of a picture between {@code v0} and {@code v1} of its height (0 its top), on the flat sheet through
	 * {@code middle} whose half-width is {@code right} and half-height {@code up}.
	 */
	private static void band(VertexConsumer buffer, PoseStack.Pose pose, Vec3 middle, Vec3 right, Vec3 up, float v0, float v1, int colour) {
		Vec3 top = middle.add(up.scale(1.0 - v0 * 2.0)), bottom = middle.add(up.scale(1.0 - v1 * 2.0));
		Vec3[] corners = {top.subtract(right), top.add(right), bottom.add(right), bottom.subtract(right)};
		float[][] uv = {{0.0F, v0}, {1.0F, v0}, {1.0F, v1}, {0.0F, v1}};
		// drawn both ways round, so it shows whichever way the sheet faces
		for (int i = 0; i < 8; i++) {
			int k = i < 4 ? i : 7 - i;
			buffer.addVertex(pose, (float) corners[k].x, (float) corners[k].y, (float) corners[k].z).setColor(colour).setUv(uv[k][0], uv[k][1])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
		}
	}
}
