package com.jaymospowers.client.power.zfighter.sky;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.zfighter.ZFighterHighSky;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.36, Andy: "see space almost, and planets ... fighting above the planet, still technically within its atmosphere, but
 * barely") The high sky as you see it from inside ({@link ZFighterHighSky}). The game's own sky is shut out behind a dark dome
 * round the camera ({@link #DOME}), and on it: stars by day and night, the sun, the Pale Moon, Rustfall and Glacia hanging close
 * and big, all turning with the day; and below you the planet itself, its curve across the lower part of the view
 * ({@link #PLANET_R}, {@link #PLANET_DOWN}) with its seas, lands and clouds sliding slowly past as you fly, lit by the sun, and
 * the thin blue of its air glowing along the curve of its edge.
 * <p>
 * All drawn camera-centred, so it never comes nearer (a sky, not a place); everything within the dome (the fight, the pod, ki
 * blasts) is drawn in front of it. Near the bottom of the high sky it all fades ({@link #FADE_FROM}, {@link #FADE_OVER}) so you
 * see the Aether's islands coming up as you sink toward them. Textures: {@code textures/environment/high_sky.png}
 * ({@code tools/gen_high_sky.py}).
 */
public final class HighSkyClient {
	// ---- tuning ----
	/** The dome's radius (blocks), and the colour of space overhead and of the air just over the planet's edge. */
	static final float DOME = 62.0F;
	static final int ZENITH = 0x020309, HORIZON = 0x16336E;
	/** How high over the planet's edge (degrees) the air's blue has gone to black. */
	static final float AIR_DEGREES = 28.0F;
	/**
	 * The planet below, at its own scale (blocks): its radius and how far under you its face is, which set how far below level
	 * its edge lies (about 15 degrees: the curve across the lower part of the view). It is drawn on the sky round the camera
	 * ({@link #PLANET_AT}), so it hides nothing nearer than that: a foe below you is drawn over it.
	 */
	static final float PLANET_R = 1000.0F, PLANET_DOWN = 35.0F, PLANET_AT = 54.0F;
	/** One tile of its surface (blocks at its scale), the cells it is laid in, and how far it slides past for each block you fly. */
	static final float TILE = 240.0F, CELL = 30.0F, SLIDE = 0.5F;
	/** The air at its edge: colour, and how tall the glow stands (blocks at its scale). */
	static final int HAZE = 0x7FB8FF;
	static final float HAZE_HIGH = 9.0F;
	/** Stars: how many, how far, how big. */
	static final int STARS = 420;
	static final float STAR_R = 60.5F, STAR_MIN = 0.07F, STAR_MAX = 0.2F;
	/** The sun and the worlds: how far, and each one's size (blocks across). Behind the planet (farther than it), so they set behind it. */
	static final float BODY_R = 58.0F, SUN_SIZE = 3.2F;
	/** Per world: right ascension, declination (degrees), size (blocks across) and atlas cell (x, y, in 16-pixel cells). */
	static final float[][] WORLDS = {
		{128.0F, 38.0F, 7.0F, 2, 0}, // the Pale Moon, near and big
		{205.0F, 24.0F, 2.6F, 3, 0}, // Rustfall
		{245.0F, 46.0F, 3.4F, 2, 1}, // Glacia
	};
	/** It fades out this many blocks above the bottom of the high sky, over this many more. */
	static final float FADE_FROM = 8.0F, FADE_OVER = 40.0F;

	static final Identifier ATLAS = JaymosPowers.id("textures/environment/high_sky.png");
	/** The atlas's plain white cell (u, v of its middle). */
	private static final float WHITE_U = 4.0F / 64.0F, WHITE_V = 36.0F / 64.0F;
	private static final float[][] STAR_DIRS = new float[STARS][4];

	static {
		Random random = new Random(42L);
		for (int i = 0; i < STARS; i++) {
			double y = -0.4 + random.nextDouble() * 1.4, a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
			STAR_DIRS[i][0] = (float) (Math.cos(a) * r);
			STAR_DIRS[i][1] = (float) y;
			STAR_DIRS[i][2] = (float) (Math.sin(a) * r);
			// a few big bright ones, most faint and small
			float pick = random.nextFloat();
			STAR_DIRS[i][3] = pick * pick;
		}
	}

	private HighSkyClient() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(HighSkyClient::render);
	}

	/** How far into the high sky the camera is (0 not in it, 1 well up in it). */
	static float depth(ClientLevel level, Vec3 camera) {
		if (Stacked.realm(level, camera.y) != ZFighterHighSky.HIGH_SKY) {
			return 0.0F;
		}
		float above = FADE_FROM + FADE_OVER;
		for (int down = 4; down <= FADE_FROM + FADE_OVER; down += 4) {
			if (Stacked.realm(level, camera.y - down) != ZFighterHighSky.HIGH_SKY) {
				above = down;
				break;
			}
		}
		return Mth.clamp((above - FADE_FROM) / FADE_OVER, 0.0F, 1.0F);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float fade = depth(level, camera);
		if (fade <= 0.0F) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		double turn = Math.PI * 2.0 * ((time - 6000L + partial) / 24000.0);
		float c = (float) Math.cos(turn), s = (float) Math.sin(turn);
		// the sun's way: overhead at noon, under the world at midnight
		float sunX = -s, sunY = c;
		int alpha = Math.round(255.0F * fade);
		double slideX = camera.x * SLIDE + (level.getGameTime() + partial) * 0.05, slideZ = camera.z * SLIDE;
		RenderType type = RenderTypes.entityTranslucent(ATLAS);
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			dome(buffer, pose, alpha);
			stars(buffer, pose, c, s, fade);
			sun(buffer, pose, sunX, sunY, alpha);
			for (float[] world : WORLDS) {
				world(buffer, pose, world, c, s, alpha);
			}
			planet(buffer, pose, slideX, slideZ, sunY, alpha);
			haze(buffer, pose, sunY, fade);
		});
	}

	// ---------------------------------------------------------------- the dome and the stars

	private static void dome(VertexConsumer buffer, PoseStack.Pose pose, int alpha) {
		int rings = 12, segments = 24;
		for (int r = 0; r < rings; r++) {
			// from straight down to straight up
			double e0 = -Math.PI / 2.0 + Math.PI * r / rings, e1 = -Math.PI / 2.0 + Math.PI * (r + 1) / rings;
			int k0 = alpha << 24 | domeColour(e0), k1 = alpha << 24 | domeColour(e1);
			for (int g = 0; g < segments; g++) {
				double a0 = Math.PI * 2.0 * g / segments, a1 = Math.PI * 2.0 * (g + 1) / segments;
				quad(buffer, pose,
					at(e0, a0, DOME), k0, WHITE_U, WHITE_V,
					at(e0, a1, DOME), k0, WHITE_U, WHITE_V,
					at(e1, a1, DOME), k1, WHITE_U, WHITE_V,
					at(e1, a0, DOME), k1, WHITE_U, WHITE_V);
			}
		}
	}

	/** The dome's colour at elevation {@code e}: the thin blue of the air along the planet's edge, black higher up. */
	private static int domeColour(double e) {
		double over = e + LIMB_DIP;
		if (over <= 0.0) {
			return HORIZON;
		}
		return mix(HORIZON, ZENITH, (float) Math.sqrt(Math.min(1.0, over / Math.toRadians(AIR_DEGREES))));
	}

	private static float[] at(double elevation, double around, float radius) {
		double r = Math.cos(elevation) * radius;
		return new float[] {(float) (Math.cos(around) * r), (float) (Math.sin(elevation) * radius), (float) (Math.sin(around) * r)};
	}

	/** The stars, turned with the sky (round the north-south line, as the sun goes). */
	private static void stars(VertexConsumer buffer, PoseStack.Pose pose, float c, float s, float fade) {
		for (float[] star : STAR_DIRS) {
			float x = star[0] * c - star[1] * s, y = star[0] * s + star[1] * c, z = star[2];
			float size = STAR_MIN + (STAR_MAX - STAR_MIN) * star[3];
			int light = Math.round(255.0F * fade * (0.45F + 0.55F * star[3]));
			billboard(buffer, pose, x * STAR_R, y * STAR_R, z * STAR_R, size, light << 24 | 0xFFFFFF, WHITE_U, WHITE_V, WHITE_U, WHITE_V);
		}
	}

	// ---------------------------------------------------------------- the sun and the worlds

	private static void sun(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int alpha) {
		float u0 = 48.0F / 64.0F, v0 = 16.0F / 64.0F, u1 = 64.0F / 64.0F, v1 = 32.0F / 64.0F;
		// a soft glow round it, then the disc
		billboard(buffer, pose, x * (BODY_R + 1.0F), y * (BODY_R + 1.0F), 0.0F, SUN_SIZE * 2.2F, (alpha / 5) << 24 | 0xFFE9B0, WHITE_U, WHITE_V,
			WHITE_U, WHITE_V);
		billboard(buffer, pose, x * BODY_R, y * BODY_R, 0.0F, SUN_SIZE, alpha << 24 | 0xFFFFFF, u0, v0, u1, v1);
	}

	private static void world(VertexConsumer buffer, PoseStack.Pose pose, float[] world, float c, float s, int alpha) {
		double ra = Math.toRadians(world[0]), dec = Math.toRadians(world[1]);
		float x0 = (float) (Math.cos(dec) * Math.sin(ra)), y0 = (float) (Math.cos(dec) * Math.cos(ra)), z0 = (float) Math.sin(dec);
		float x = x0 * c - y0 * s, y = x0 * s + y0 * c;
		float u0 = world[3] * 16.0F / 64.0F, v0 = world[4] * 16.0F / 64.0F;
		billboard(buffer, pose, x * BODY_R, y * BODY_R, z0 * BODY_R, world[2] * 0.5F, alpha << 24 | 0xFFFFFF, u0, v0, u0 + 0.25F, v0 + 0.25F);
	}

	// ---------------------------------------------------------------- the planet below

	/** The horizontal reach of the planet's edge as seen from the camera, its height there, and how far below level it lies (radians). */
	private static final float LIMB_REACH, LIMB_Y, LIMB_DIP;

	static {
		float out = PLANET_R + PLANET_DOWN;
		float cos = PLANET_R / out;
		LIMB_REACH = PLANET_R * (float) Math.sqrt(1.0 - cos * cos);
		LIMB_Y = -out + PLANET_R * cos;
		LIMB_DIP = (float) Math.atan2(-LIMB_Y, LIMB_REACH);
	}

	/** The point of the planet's face under the camera at (x, z) (the reach clamped to its edge), put on the sky at {@code at}. */
	private static float[] ground(float x, float z, float at) {
		float reach = (float) Math.sqrt(x * x + z * z);
		if (reach > LIMB_REACH) {
			x *= LIMB_REACH / reach;
			z *= LIMB_REACH / reach;
			reach = LIMB_REACH;
		}
		float y = -(PLANET_R + PLANET_DOWN) + (float) Math.sqrt(PLANET_R * PLANET_R - reach * reach);
		return onSky(x, y, z, at);
	}

	/** The direction of (x, y, z) from the camera, {@code at} blocks out. */
	private static float[] onSky(float x, float y, float z, float at) {
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		return length < 1.0E-4F ? new float[] {0.0F, -at, 0.0F} : new float[] {x / length * at, y / length * at, z / length * at};
	}

	private static void planet(VertexConsumer buffer, PoseStack.Pose pose, double slideX, double slideZ, float sunY, int alpha) {
		// lit on the day side: dim at night, never black (the moonlight, the cities)
		float light = 0.3F + 0.7F * Mth.clamp(sunY * 1.6F + 0.4F, 0.0F, 1.0F);
		int grey = Math.round(255.0F * light);
		int colour = alpha << 24 | grey << 16 | grey << 8 | grey;
		// the surface is laid in cells of a fixed grid that slides under you: whole cells move the texture, the rest moves the grid
		double cellsX = slideX / CELL, cellsZ = slideZ / CELL;
		int whole = (int) Math.floor(cellsX), wholeZ = (int) Math.floor(cellsZ);
		float fracX = (float) (cellsX - whole) * CELL, fracZ = (float) (cellsZ - wholeZ) * CELL;
		int per = Math.round(TILE / CELL);
		int span = (int) Math.ceil(LIMB_REACH / CELL) + 1;
		float cellUv = 32.0F / 64.0F / per;
		for (int i = -span; i < span; i++) {
			for (int j = -span; j < span; j++) {
				float x0 = i * CELL - fracX, x1 = x0 + CELL, z0 = j * CELL - fracZ, z1 = z0 + CELL;
				if (Math.min(Math.abs(x0), Math.abs(x1)) > LIMB_REACH || Math.min(Math.abs(z0), Math.abs(z1)) > LIMB_REACH) {
					continue;
				}
				int ti = Math.floorMod(i + whole, per), tj = Math.floorMod(j + wholeZ, per);
				float u0 = ti * cellUv, u1 = u0 + cellUv, v0 = tj * cellUv, v1 = v0 + cellUv;
				quad(buffer, pose,
					ground(x0, z0, PLANET_AT), colour, u0, v0,
					ground(x1, z0, PLANET_AT), colour, u1, v0,
					ground(x1, z1, PLANET_AT), colour, u1, v1,
					ground(x0, z1, PLANET_AT), colour, u0, v1);
			}
		}
	}

	/** The air along the planet's edge: a thin blue glow standing up from the curve, brighter on the day side. */
	private static void haze(VertexConsumer buffer, PoseStack.Pose pose, float sunY, float fade) {
		int segments = 48;
		float day = 0.45F + 0.55F * Mth.clamp(sunY + 0.5F, 0.0F, 1.0F);
		int inner = Math.round(200.0F * fade * day) << 24 | HAZE, outer = HAZE;
		float in = LIMB_REACH * 0.985F, out = LIMB_REACH * 1.01F;
		for (int g = 0; g < segments; g++) {
			double a0 = Math.PI * 2.0 * g / segments, a1 = Math.PI * 2.0 * (g + 1) / segments;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			// just nearer than the planet, so it lies over its edge
			float at = PLANET_AT - 0.5F;
			quad(buffer, pose,
				onSky(c0 * in, LIMB_Y, s0 * in, at), inner, WHITE_U, WHITE_V,
				onSky(c1 * in, LIMB_Y, s1 * in, at), inner, WHITE_U, WHITE_V,
				onSky(c1 * out, LIMB_Y + HAZE_HIGH, s1 * out, at), outer, WHITE_U, WHITE_V,
				onSky(c0 * out, LIMB_Y + HAZE_HIGH, s0 * out, at), outer, WHITE_U, WHITE_V);
		}
	}

	// ---------------------------------------------------------------- drawing

	/** A square {@code size} across at (x, y, z), turned to face the camera (at the origin). */
	private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float size, int colour, float u0, float v0,
		float u1, float v1) {
		Vec3 n = new Vec3(x, y, z).normalize();
		Vec3 side = Math.abs(n.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 up = side.cross(n).normalize();
		float h = size * 0.5F;
		float sx = (float) side.x * h, sy = (float) side.y * h, sz = (float) side.z * h;
		float ux = (float) up.x * h, uy = (float) up.y * h, uz = (float) up.z * h;
		quad(buffer, pose,
			new float[] {x - sx + ux, y - sy + uy, z - sz + uz}, colour, u0, v0,
			new float[] {x + sx + ux, y + sy + uy, z + sz + uz}, colour, u1, v0,
			new float[] {x + sx - ux, y + sy - uy, z + sz - uz}, colour, u1, v1,
			new float[] {x - sx - ux, y - sy - uy, z - sz - uz}, colour, u0, v1);
	}

	/** One quad, drawn both ways round so it shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float[] p0, int k0, float u0, float v0, float[] p1, int k1, float u1, float v1,
		float[] p2, int k2, float u2, float v2, float[] p3, int k3, float u3, float v3) {
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
		buffer.addVertex(pose, p[0], p[1], p[2]).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int mix(int from, int to, float t) {
		int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
		return r << 16 | g << 8 | b;
	}
}
