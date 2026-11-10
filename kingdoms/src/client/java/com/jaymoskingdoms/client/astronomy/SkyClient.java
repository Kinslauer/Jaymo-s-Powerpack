package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The real night sky over the overworld: the HYG catalog's stars down to magnitude 6.5, turning with the world's time
 * and the observer's position, plus the 88 constellation figures whose lines connect as their stars are discovered
 * ({@link Discovery}). Stars dim near settlements (light pollution) and in rain.
 * <p>
 * Drawn as camera-centred geometry during the world render, so the vanilla sky renderer is never replaced and shader
 * packs keep their own sky. The dome is only {@link #DOME} blocks out, so terrain farther than that would otherwise sit
 * behind the stars: a horizon profile worked out from the heightmap ({@link #horizon}) hides every star below the
 * skyline in its direction.
 */
public final class SkyClient {
	/** Radius of the camera-centred dome the stars are drawn on (blocks). */
	static final float DOME = 60.0F;
	/** Constellation line thickness (blocks at dome distance). */
	static final float LINE_WIDTH = 0.05F;
	/** Brightness a settlement's lights can take away at most. */
	static final float POLLUTION_MAX = 0.85F;
	/** Settlement light reach (blocks). */
	static final float SETTLEMENT_RADIUS = 96.0F;
	/** Angular radius (degrees) within which looking at a star discovers it. */
	static final double LOOK_DEG = 2.5;
	/** The horizon profile: directions round the compass, first and last sample distance, step (blocks), how often (ticks). */
	static final int HORIZON_BINS = 64, HORIZON_FROM = 16, HORIZON_STEP = 8, HORIZON_EVERY = 10;
	/** Light pollution is looked for this often (ticks). */
	static final int POLLUTION_EVERY = 20;

	private static final Identifier WHITE = JaymosKingdoms.id("textures/astronomy/white.png");
	/** By compass bin: the sine of the skyline's elevation there, as seen from the eye (-1: open to the horizon). */
	private static final float[] HORIZON = new float[HORIZON_BINS];
	private static long clock;
	private static int ticks;
	private static float pollution;

	private SkyClient() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(SkyClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(SkyClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Discovery.clear();
			pollution = 0.0F;
			java.util.Arrays.fill(HORIZON, -1.0F);
		});
		java.util.Arrays.fill(HORIZON, -1.0F);
	}

	/** Above the skyline in its direction (and so drawn and discoverable)? {@code dir} is a unit vector, y up. */
	static boolean clear(Vec3 dir) {
		if (dir.y <= 0.0) {
			return false;
		}
		double az = Math.atan2(-dir.x, dir.z);
		int bin = Math.floorMod((int) Math.round(az / (Math.PI * 2.0) * HORIZON_BINS), HORIZON_BINS);
		return dir.y > HORIZON[bin];
	}

	/**
	 * The skyline round the player: for each compass bin, walk out along the heightmap to the edge of the loaded world
	 * and keep the highest elevation seen. Cheap (a few hundred heightmap reads), and done every {@link #HORIZON_EVERY} ticks.
	 */
	private static void horizon(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		double reach = Math.max(HORIZON_FROM, (minecraft.options.getEffectiveRenderDistance() - 1) * 16.0);
		for (int bin = 0; bin < HORIZON_BINS; bin++) {
			double az = bin * Math.PI * 2.0 / HORIZON_BINS;
			double dx = -Math.sin(az), dz = Math.cos(az);
			float best = -1.0F;
			for (double d = HORIZON_FROM; d <= reach; d += HORIZON_STEP) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(eye.x + dx * d), Mth.floor(eye.z + dz * d));
				if (top <= level.getMinY()) {
					continue; // not loaded
				}
				double rise = top - eye.y;
				float sin = (float) (rise / Math.sqrt(rise * rise + d * d));
				best = Math.max(best, sin);
			}
			HORIZON[bin] = best;
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || level.dimension() != Level.OVERWORLD) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float night = SkyMath.night(level, partial);
		if (night <= 0.0F) {
			return;
		}
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		if (width <= 0 || height <= 0) {
			return;
		}
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
		int days = (int) (level.getGameTime() / 24000L);
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 forward = vec(camera.forwardVector());
		Vec3 left = vec(camera.leftVector());
		Vec3 up = vec(camera.upVector());
		float scale = 1.0F - POLLUTION_MAX * pollution;
		double aspect = width / (double) height;
		double fovTan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		List<Star> stars = StarCatalog.stars();
		Discovery discovery = Discovery.getOrCreate(minecraft);
		clock++;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(WHITE), (pose, buffer) -> {
			for (Star star : stars) {
				Vec3 dir = SkyMath.direction(star, time, partial, lonDeg, days);
				double depth = dir.dot(forward);
				if (depth <= 0.05 || !clear(dir)) {
					continue; // behind the camera, below the horizon or behind the land
				}
				double nx = -dir.dot(left) / depth / (fovTan * aspect);
				double ny = dir.dot(up) / depth / fovTan;
				if (Math.abs(nx) > 1.05 || Math.abs(ny) > 1.05) {
					continue; // outside the field of view
				}
				float brightness = starBrightness(star.mag()) * night * scale * twinkle(star.hip());
				int alpha = Math.round(255.0F * brightness);
				if (alpha < 6) {
					continue;
				}
				billboard(buffer, pose, (float) (dir.x * DOME), (float) (dir.y * DOME), (float) (dir.z * DOME), starSize(star.mag()),
					alpha << 24 | 0xFFFFFF);
			}
			if (discovery == null) {
				return;
			}
			int lineAlpha = Math.round(255.0F * night * 0.45F * scale);
			if (lineAlpha < 6) {
				return;
			}
			int colour = lineAlpha << 24 | 0xD0D8FF;
			for (Constellation figure : Constellations.all()) {
				for (int[] segment : figure.segments()) {
					Star a = StarCatalog.byHip(segment[0]);
					Star b = StarCatalog.byHip(segment[1]);
					if (a == null || b == null || !discovery.isDiscovered(a.hip()) || !discovery.isDiscovered(b.hip())) {
						continue;
					}
					Vec3 da = SkyMath.direction(a, time, partial, lonDeg, days);
					Vec3 db = SkyMath.direction(b, time, partial, lonDeg, days);
					if (!clear(da) || !clear(db) || da.dot(forward) <= 0.05 || db.dot(forward) <= 0.05) {
						continue;
					}
					line(buffer, pose, da.scale(DOME), db.scale(DOME), colour);
				}
			}
		});
	}

	/**
	 * Each tick: count how long the player keeps each star in view (a star watched long enough is reported to the server
	 * by {@link Discovery}), and now and then redo the skyline and the light pollution so the render stays cheap.
	 */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || level.dimension() != Level.OVERWORLD) {
			return;
		}
		ticks++;
		if (ticks % HORIZON_EVERY == 0) {
			horizon(minecraft, level, player);
		}
		if (ticks % POLLUTION_EVERY == 0) {
			pollution = pollution(level, player);
		}
		if (SkyMath.night(level, 0.0F) <= 0.0F || minecraft.gui.screen() != null) {
			return;
		}
		Discovery discovery = Discovery.getOrCreate(minecraft);
		if (discovery == null) {
			return;
		}
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
		int days = (int) (level.getGameTime() / 24000L);
		Vec3 forward = vec(minecraft.gameRenderer.mainCamera().forwardVector());
		double lookCos = Math.cos(Math.toRadians(LOOK_DEG));
		for (Star star : StarCatalog.stars()) {
			if (discovery.isDiscovered(star.hip())) {
				continue;
			}
			Vec3 dir = SkyMath.direction(star, time, 0.0F, lonDeg, days);
			if (dir.dot(forward) <= lookCos || !clear(dir)) {
				discovery.stopLook(star.hip());
				continue;
			}
			discovery.noteLook(star.hip());
		}
	}

	// ---------------------------------------------------------------- drawing

	private static float starBrightness(float mag) {
		return Mth.clamp((6.5F - mag) / 6.0F, 0.2F, 1.0F);
	}

	private static float starSize(float mag) {
		return 0.12F + 0.30F * Mth.clamp((6.5F - mag) / 5.0F, 0.0F, 1.0F);
	}

	private static float twinkle(int hip) {
		return 1.0F - 0.15F * (0.5F + 0.5F * Mth.sin((clock + hip * 1.7F) * 0.07F));
	}

	/** Light pollution: nearby villagers mark a settlement, and its lights dim the sky. A place, not a number. */
	private static float pollution(ClientLevel level, LocalPlayer player) {
		double best = Double.MAX_VALUE;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SETTLEMENT_RADIUS))) {
			best = Math.min(best, player.distanceToSqr(villager));
		}
		if (best == Double.MAX_VALUE) {
			return 0.0F;
		}
		return Mth.clamp(1.0F - (float) (Math.sqrt(best) / SETTLEMENT_RADIUS), 0.0F, 1.0F);
	}

	/** A point of light at dome distance, facing the camera, full bright. */
	private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float size, int colour) {
		Vec3 n = new Vec3(x, y, z).normalize();
		Vec3 side = Math.abs(n.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 up = side.cross(n).normalize();
		float h = size * 0.5F;
		float sx = (float) side.x * h, sy = (float) side.y * h, sz = (float) side.z * h;
		float ux = (float) up.x * h, uy = (float) up.y * h, uz = (float) up.z * h;
		quad(buffer, pose,
			new float[] {x - sx + ux, y - sy + uy, z - sz + uz}, colour, 0.0F, 0.0F,
			new float[] {x + sx + ux, y + sy + uy, z + sz + uz}, colour, 1.0F, 0.0F,
			new float[] {x + sx - ux, y + sy - uy, z + sz - uz}, colour, 1.0F, 1.0F,
			new float[] {x - sx - ux, y - sy - uy, z - sz - uz}, colour, 0.0F, 1.0F);
	}

	/** A constellation line between two dome points: a thin quad across the line of sight. */
	private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, int colour) {
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 dir = b.subtract(a).normalize();
		Vec3 side = dir.cross(mid.normalize());
		if (side.lengthSqr() < 1.0E-6) {
			side = new Vec3(0.0, 0.0, 1.0);
		}
		side = side.normalize();
		float mx = (float) mid.x, my = (float) mid.y, mz = (float) mid.z;
		float half = (float) a.distanceTo(b) * 0.5F;
		float sx = (float) side.x * LINE_WIDTH, sy = (float) side.y * LINE_WIDTH, sz = (float) side.z * LINE_WIDTH;
		float dx = (float) dir.x * half, dy = (float) dir.y * half, dz = (float) dir.z * half;
		quad(buffer, pose,
			new float[] {mx + dx + sx, my + dy + sy, mz + dz + sz}, colour, 0.0F, 0.0F,
			new float[] {mx - dx + sx, my - dy + sy, mz - dz + sz}, colour, 1.0F, 0.0F,
			new float[] {mx - dx - sx, my - dy - sy, mz - dz - sz}, colour, 1.0F, 1.0F,
			new float[] {mx + dx - sx, my + dy - sy, mz + dz - sz}, colour, 0.0F, 1.0F);
	}

	/** One quad, drawn both ways round so it shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float[] p0, int k0, float u0, float v0,
		float[] p1, int k1, float u1, float v1, float[] p2, int k2, float u2, float v2, float[] p3, int k3, float u3, float v3) {
		vertex(buffer, pose, p0, k0, u0, v0);
		vertex(buffer, pose, p1, k1, u1, v1);
		vertex(buffer, pose, p2, k2, u2, v2);
		vertex(buffer, pose, p3, k3, u3, v3);
		vertex(buffer, pose, p3, k3, u3, v3);
		vertex(buffer, pose, p2, k2, u2, v2);
		vertex(buffer, pose, p1, k1, u1, v1);
		vertex(buffer, pose, p0, k0, u0, v0);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] p, int colour, float u, float v) {
		buffer.addVertex(pose, p[0], p[1], p[2]).setColor(colour).setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static Vec3 vec(org.joml.Vector3fc v) {
		return new Vec3(v.x(), v.y(), v.z());
	}
}
