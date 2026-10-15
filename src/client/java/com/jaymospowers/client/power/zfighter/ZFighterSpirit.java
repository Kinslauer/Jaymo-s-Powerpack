package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Spirit Bomb drawn as what it is: a great ball of pale blue light, see-through at its rim, with energy streaming
 * round it (the beacon beam's texture, scrolling) and a white-hot heart. Embedding itself before the burst, it glows
 * brighter and brighter: the heart swells, everything whitens and a halo of light grows round it. The server sends where it is every tick
 * ({@link ZFighterNet.SpiritFx}); it's drawn between the last two places, and goes when they stop coming.
 */
public final class ZFighterSpirit {
	/** The heart's share of the radius, and the colours: the shell's rim, its glow, the heart. */
	static final float CORE = 0.62F;
	static final int SHELL = 0x9070C8FF;
	static final int INNER = 0x60B8E8FF;
	static final int HEART = 0xFFF0FAFF;
	static final int LON = 20;
	static final int LAT = 14;
	static final float SPIN = 0.02F;
	static final float FLOW = 0.04F;
	/** Ticks without word before it's dropped. */
	static final int STALE = 3;

	private static final class Live {
		Vec3 prev;
		Vec3 at;
		float prevSize;
		float size;
		float prevGlow;
		float glow;
		int quiet;
		int age;
	}

	private static final Map<Integer, Live> SPHERES = new HashMap<>();

	private ZFighterSpirit() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.SpiritFx.TYPE, (fx, context) -> {
			Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
			Live live = SPHERES.get(fx.id());
			if (live == null) {
				live = new Live();
				live.at = at;
				live.size = fx.size();
				SPHERES.put(fx.id(), live);
			}
			// one word a tick: what was the latest becomes the one before
			live.prev = live.at;
			live.prevSize = live.size;
			live.at = at;
			live.size = fx.size();
			live.prevGlow = live.glow;
			live.glow = fx.glow();
			live.quiet = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				SPHERES.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			SPHERES.values().removeIf(live -> ++live.quiet > STALE);
			for (Live live : SPHERES.values()) {
				live.age++;
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ZFighterSpirit::render);
	}

	private static void render(LevelRenderContext context) {
		if (SPHERES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Live live : SPHERES.values()) {
			Vec3 at = live.prev.lerp(live.at, partial).subtract(camera);
			float size = Mth.lerp(partial, live.prevSize, live.size);
			float time = live.age + partial;
			float glow = Mth.lerp(partial, live.prevGlow, live.glow);
			// it breathes a little, quicker and harder as it glows towards the burst
			float pulse = 1.0F + (0.03F + 0.05F * glow) * Mth.sin(time * (0.25F + glow * 0.9F));
			float r = size * pulse;
			int heart = HEART;
			int inner = brighten(INNER, glow);
			int shell = brighten(SHELL, glow);
			float core = CORE + (0.92F - CORE) * glow;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> sphere(buffer, pose, at, r * core, heart, time * SPIN * 1.7F, time * FLOW * 1.5F));
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
				(pose, buffer) -> sphere(buffer, pose, at, r * Math.max(0.85F, core + 0.03F), inner, -time * SPIN, time * FLOW));
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
				(pose, buffer) -> sphere(buffer, pose, at, r, shell, time * SPIN, -time * FLOW * 0.7F));
			if (glow > 0.02F) {
				// a halo of light round it, growing as it glows
				int halo = ((int)(glow * 0x70) << 24) | 0xE8F6FF;
				float hr = r * (1.0F + 0.35F * glow);
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
					(pose, buffer) -> sphere(buffer, pose, at, hr, halo, -time * SPIN * 0.5F, time * FLOW * 2.0F));
			}
		}
	}

	/** {@code argb} towards white and opaque, by {@code glow} (0-1). */
	private static int brighten(int argb, float glow) {
		int a = (int)Mth.lerp(glow, argb >>> 24, 255);
		int r = (int)Mth.lerp(glow, argb >> 16 & 0xFF, 255);
		int g = (int)Mth.lerp(glow, argb >> 8 & 0xFF, 255);
		int b = (int)Mth.lerp(glow, argb & 0xFF, 255);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** A ball of {@link #LON} by {@link #LAT} faces, the texture wrapped round it and turning; both sides drawn. */
	static void sphere(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, float radius, int color, float uShift, float vShift) {
		for (int i = 0; i < LAT; i++) {
			float t0 = Mth.PI * i / LAT;
			float t1 = Mth.PI * (i + 1) / LAT;
			for (int j = 0; j < LON; j++) {
				float p0 = Mth.TWO_PI * j / LON;
				float p1 = Mth.TWO_PI * (j + 1) / LON;
				float u0 = j / (float)LON * 2.0F + uShift;
				float u1 = (j + 1) / (float)LON * 2.0F + uShift;
				float v0 = i / (float)LAT + vShift;
				float v1 = (i + 1) / (float)LAT + vShift;
				Vec3 a = point(c, radius, t0, p0);
				Vec3 b = point(c, radius, t1, p0);
				Vec3 d = point(c, radius, t1, p1);
				Vec3 e = point(c, radius, t0, p1);
				vertex(buffer, pose, a, color, u0, v0);
				vertex(buffer, pose, b, color, u0, v1);
				vertex(buffer, pose, d, color, u1, v1);
				vertex(buffer, pose, e, color, u1, v0);
				vertex(buffer, pose, e, color, u1, v0);
				vertex(buffer, pose, d, color, u1, v1);
				vertex(buffer, pose, b, color, u0, v1);
				vertex(buffer, pose, a, color, u0, v0);
			}
		}
	}

	private static Vec3 point(Vec3 c, float radius, float theta, float phi) {
		float s = Mth.sin(theta);
		return c.add(radius * s * Mth.cos(phi), radius * Mth.cos(theta), radius * s * Mth.sin(phi));
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, int color, float u, float v) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
