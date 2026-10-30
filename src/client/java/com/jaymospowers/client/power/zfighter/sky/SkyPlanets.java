package com.jaymospowers.client.power.zfighter.sky;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.sky.Charts;
import com.jaymospowers.power.zfighter.sky.Sky;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.52, Andy's SevTech picks: "Planets in the Sky") A point of light in the night sky for each world whose star chart is
 * read, at a fixed place on the sky (right ascension and declination in {@link #SPOTS}) that turns with the stars through the
 * night. Client only: nothing is sent or kept but the charts the player has read. It is drawn over the world as the hud's
 * first layer, in the screen position of the star's direction (behind a ceiling or a hill it fades out: {@link #SEE_TICKS}),
 * and shines brighter and bigger for {@link #GLOW_TICKS} after a pod flight to the world, or home from it
 * ({@link #glow}). The book page of a chart is opened here too.
 */
public final class SkyPlanets {
	// ---- tuning ----
	/** Per world (by destination index): right ascension and declination (degrees), colour (rgb), and size (gui pixels). */
	private static final float[][] SPOTS = {
		{128.0F, 38.0F, 0xE6EBFF, 1.0F}, // the Pale Moon: a cold white
		{205.0F, 24.0F, 0xFF7A4A, 1.0F}, // Rustfall: rust red
		{245.0F, 46.0F, 0x9FE0FF, 1.0F}, // Glacia: ice blue
		{170.0F, 14.0F, 0xFFB050, 1.0F}, // Cinder: ember orange
		{190.0F, 62.0F, 0xB070FF, 0.8F}, // the Maw: a dim violet
		{105.0F, 22.0F, 0x7CFF8A, 1.0F}, // the Verdant World: green
	};
	/** How long a world shines brighter after a flight to it or from it (ticks), and by how much. */
	static final int GLOW_TICKS = 6000;
	static final float GLOW_GAIN = 1.6F, BASE_LIGHT = 0.8F;
	/** How far (blocks) a star's line must run clear of blocks to be seen, how often it is checked (ticks), how fast it fades. */
	static final double SEE_RANGE = 160.0;
	static final int SEE_TICKS = 5;
	static final float FADE = 0.12F;
	/** The sky is dark enough to show a light when the sun is this far below the horizon (share of a quarter turn), over this span. */
	static final float DUSK_AT = 0.05F, DUSK_SPAN = 0.25F;
	/** The twinkle: how deep and how quickly (ticks). */
	static final float TWINKLE = 0.12F, TWINKLE_RATE = 0.07F;

	private static final float[] SEEN = new float[Charts.WORLDS];
	private static final boolean[] OPEN = new boolean[Charts.WORLDS];
	private static final long[] GLOWING_UNTIL = new long[Charts.WORLDS];
	private static long clock;

	private SkyPlanets() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Sky.Page.TYPE,
			(payload, ctx) -> ctx.client().gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(List.of(Charts.page(payload.world()))))));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, JaymosPowers.id("sky_planets"), SkyPlanets::draw);
		ClientTickEvents.END_CLIENT_TICK.register(SkyPlanets::tick);
	}

	/** A pod flight to (or home from) {@code world} has begun: it shines brighter for a while. */
	public static void glow(int world) {
		if (world >= 0 && world < Charts.WORLDS) {
			GLOWING_UNTIL[world] = clock + GLOW_TICKS;
		}
	}

	// ---------------------------------------------------------------- where in the sky

	/** The turn of the sky from noon (radians): the sun is overhead at 0 and under the world at pi. */
	private static double turn(ClientLevel level, float partial) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return Math.PI * 2.0 * ((time - 6000L + partial) / 24000.0);
	}

	/** How dark enough the sky is for a light to show (0: day, 1: night). */
	private static float night(ClientLevel level, float partial) {
		double below = -Math.cos(turn(level, partial));
		return Mth.clamp((float)((below - DUSK_AT) / DUSK_SPAN), 0.0F, 1.0F) * (1.0F - level.getRainLevel(partial));
	}

	/** The direction to {@code world}'s light now: east is +x, the sun's path across the sky is the x-y plane. */
	private static Vec3 direction(int world, double turn) {
		double ra = Math.toRadians(SPOTS[world][0]), dec = Math.toRadians(SPOTS[world][1]);
		double x0 = Math.cos(dec) * Math.sin(ra), y0 = Math.cos(dec) * Math.cos(ra), z0 = Math.sin(dec);
		double c = Math.cos(turn), s = Math.sin(turn);
		return new Vec3(x0 * c - y0 * s, x0 * s + y0 * c, z0);
	}

	private static boolean active(Minecraft minecraft) {
		return minecraft.level != null && minecraft.player != null && minecraft.level.dimension() == Level.OVERWORLD;
	}

	// ---------------------------------------------------------------- every tick: is each light in the clear?

	private static void tick(Minecraft minecraft) {
		clock++;
		if (!active(minecraft)) {
			return;
		}
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		int read = Charts.readMask(player);
		boolean look = clock % SEE_TICKS == 0 && night(level, 0.0F) > 0.0F;
		double turn = turn(level, 0.0F);
		Vec3 eye = player.getEyePosition();
		for (int world = 0; world < Charts.WORLDS; world++) {
			if ((read & 1 << world) == 0) {
				SEEN[world] = 0.0F;
				continue;
			}
			if (look) {
				Vec3 dir = direction(world, turn);
				OPEN[world] = dir.y > 0.0 && level.clip(new ClipContext(eye, eye.add(dir.scale(SEE_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
					.getType() == HitResult.Type.MISS;
			}
			SEEN[world] = Mth.clamp(SEEN[world] + (OPEN[world] ? FADE : -FADE), 0.0F, 1.0F);
		}
	}

	// ---------------------------------------------------------------- drawing

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active(minecraft) || minecraft.gui.screen() != null) {
			// the hud is also drawn under a screen: the sky is for the open view only
			return;
		}
		ClientLevel level = minecraft.level;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float night = night(level, partial);
		if (night <= 0.0F) {
			return;
		}
		int read = Charts.readMask(minecraft.player);
		if (read == 0) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		double turn = turn(level, partial);
		float fov = camera.getFov();
		double tan = Math.tan(Math.toRadians(fov) / 2.0);
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		if (tan <= 0.0 || width <= 0 || height <= 0) {
			return;
		}
		double aspect = width / (double)height;
		Vec3 forward = vec(camera.forwardVector()), left = vec(camera.leftVector()), up = vec(camera.upVector());
		for (int world = 0; world < Charts.WORLDS; world++) {
			if ((read & 1 << world) == 0 || SEEN[world] <= 0.0F) {
				continue;
			}
			Vec3 dir = direction(world, turn);
			double depth = dir.dot(forward);
			if (depth < 0.05) {
				continue;
			}
			double nx = -dir.dot(left) / depth / (tan * aspect), ny = dir.dot(up) / depth / tan;
			if (Math.abs(nx) > 1.0 || Math.abs(ny) > 1.0) {
				continue;
			}
			int x = Math.round((float)(width / 2.0 * (1.0 + nx))), y = Math.round((float)(height / 2.0 * (1.0 - ny)));
			boolean glowing = GLOWING_UNTIL[world] > clock;
			float twinkle = 1.0F - TWINKLE * (0.5F + 0.5F * Mth.sin((clock + partial) * TWINKLE_RATE + world * 1.7F));
			float light = night * SEEN[world] * twinkle * (glowing ? GLOW_GAIN : BASE_LIGHT);
			point(graphics, x, y, (int)SPOTS[world][2], Mth.clamp(light, 0.0F, 1.0F), glowing ? 2 : 1, SPOTS[world][3]);
		}
	}

	private static Vec3 vec(org.joml.Vector3fc v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	/** A point of light: a bright core, with a fainter cross round it (longer when shining). */
	private static void point(GuiGraphicsExtractor graphics, int x, int y, int rgb, float light, int reach, float size) {
		int core = Math.max(1, Math.round(size));
		graphics.fill(x, y, x + core, y + core, colour(rgb, light));
		int halo = colour(rgb, light * 0.4F);
		for (int i = 1; i <= reach; i++) {
			int a = i == 1 ? halo : colour(rgb, light * 0.2F);
			graphics.fill(x - i, y, x - i + 1, y + core, a);
			graphics.fill(x + core + i - 1, y, x + core + i, y + core, a);
			graphics.fill(x, y - i, x + core, y - i + 1, a);
			graphics.fill(x, y + core + i - 1, x + core, y + core + i, a);
		}
	}

	private static int colour(int rgb, float alpha) {
		return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 0xFFFFFF;
	}
}
