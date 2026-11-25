package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Shader packs often don't draw the outlines the black strain's scent ({@link WebScent}) and the red strain's heartbeat
 * sense ({@link WebRedSense}) rely on. With Iris running a shader pack, the same things get a small diamond over them
 * on the screen instead, in the same colour the outline would have had. Without shaders nothing is drawn here.
 */
final class WebShaderMarks {
	/** A diamond's radius (gui pixels) and how far over the entity's head it floats (blocks). */
	private static final int RADIUS = 4;
	private static final double LIFT = 0.35;
	private static final String API = "net.irisshaders.iris.api.v0.IrisApi";

	private static Method instance, inUse;
	private static boolean looked;

	private WebShaderMarks() {
	}

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("web_shader_marks"), WebShaderMarks::render);
	}

	/** Iris is loaded and a shader pack is on (its public API is looked up by name, so the game runs without Iris). */
	static boolean shadersOn() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					Class<?> api = Class.forName(API);
					instance = api.getMethod("getInstance");
					inUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException | LinkageError e) {
					instance = null;
				}
			}
		}
		if (instance == null || inUse == null) {
			return false;
		}
		try {
			return (Boolean)inUse.invoke(instance.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || minecraft.level == null || self.isSpectator() || !shadersOn()) {
			return;
		}
		boolean scent = WebScent.has(self);
		boolean red = com.jaymospowers.power.web.WebSymbioteBody.senses(self);
		if (!scent && !red) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		double range = Math.max(scent ? WebScent.RANGE : 0.0, red ? WebRedSense.RANGE : 0.0);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity == self || entity.distanceToSqr(self) > range * range) {
				continue;
			}
			int color = red ? WebRedSense.outline(entity) : 0;
			if (color == 0 && scent) {
				color = WebScent.outline(entity);
			}
			if (color == 0) {
				continue;
			}
			double[] p = project(graphics, minecraft, entity.getPosition(partial).add(0.0, entity.getBbHeight() + LIFT, 0.0));
			if (p != null) {
				diamond(graphics, (int)p[0], (int)p[1], color);
			}
		}
	}

	/** Where a point in the world falls on the screen: {x, y}, or null if it is behind the camera or off the screen. */
	private static double[] project(GuiGraphicsExtractor graphics, Minecraft minecraft, Vec3 world) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 rel = world.subtract(camera.position());
		Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
		double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		if (z < 0.2) {
			return null;
		}
		double lx = rel.x * left.x() + rel.y * left.y() + rel.z * left.z();
		double uy = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
		float fov = camera.getFov() > 1.0F ? camera.getFov() : minecraft.options.fov().get();
		double scale = graphics.guiHeight() * 0.5 / Math.tan(Math.toRadians(fov) * 0.5);
		double sx = graphics.guiWidth() * 0.5 - lx / z * scale;
		double sy = graphics.guiHeight() * 0.5 - uy / z * scale;
		if (sx < 0 || sy < 0 || sx > graphics.guiWidth() || sy > graphics.guiHeight()) {
			return null;
		}
		return new double[] {sx, sy};
	}

	private static void diamond(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		int shade = 0xA0000000;
		for (int dy = -RADIUS - 1; dy <= RADIUS + 1; dy++) {
			int half = RADIUS + 1 - Math.abs(dy);
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, shade);
		}
		for (int dy = -RADIUS; dy <= RADIUS; dy++) {
			int half = RADIUS - Math.abs(dy);
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}
}
