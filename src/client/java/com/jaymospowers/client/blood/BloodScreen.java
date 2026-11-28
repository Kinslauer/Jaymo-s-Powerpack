package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Blood on your own screen, in first person. Andy (2026-11-28, an orc fight): "also improve the blood splatter on
 * screen". It was drawn as rows of flat fills (stepped circles, square runs down to the hotbar), nearly opaque, anywhere
 * on the screen (over the crosshair too), and the spray's own drops passing right in front of the eye showed as their
 * 16-pixel pictures blown up to a hand's width: red blocks across the bottom of the view. Now every spatter is one of a
 * few smooth splat pictures (soft edges, droplets and flung runs, never pixels), tinted the blood's own colour, darkened.
 * <ul>
 * <li>A big blow close by and in view ({@code BladeBlood}, {@link #hit}): a few splats on the side of the screen it
 * happened on, more and bigger the heavier it was.</li>
 * <li>A blow that hurts you ({@code HurtCauseHud}, {@link #blow}): one to a few of your own on the side it came from,
 * by how hard it was.</li>
 * <li>A drop flying at your eyes from within {@link #NEAR} ({@link BloodDrop}) isn't drawn in the world; it lands
 * here, where it would have hit the view.</li>
 * <li>Each pops in, runs a little way down, then fades: gone in a few seconds. None ever lies over the crosshair (or
 * the line under it) or the hotbar, and only a handful are up at once. Toned down: a dark red, never opaque.</li>
 * </ul>
 * The blood settings ({@link BloodSettings}) rule it: Off draws none, the gore level sets how many and how big, and
 * streamer safe draws grey. Nothing in creative, spectator or third person, and all of it is wiped when you die or go
 * down (no red on the death screen).
 */
public final class BloodScreen {
	/** The splat pictures (white, shaded, soft alpha: tinted when drawn). */
	static final int PICTURES = 5;
	/** How long a splat holds before it fades, give or take this many (ticks), and how long its fade takes. */
	static final int HOLD_TICKS = 35, HOLD_SPREAD = 20, FADE_TICKS = 30;
	/** It pops in over this many ticks, from this share of its size. */
	static final int POP_TICKS = 3;
	static final float POP_FROM = 0.6F;
	/** How far it runs down while it holds and fades (a share of its size), and how much longer it grows as it runs. */
	static final float RUN = 0.35F, RUN_STRETCH = 0.25F;
	/** Its strength at most (0-1): toned down, the view behind it always shows through. */
	static final float MAX_ALPHA = 0.7F;
	/** A splat picture's size: a share of the screen's height, at the smallest blow and the hardest. */
	static final float SIZE_SMALL = 0.16F, SIZE_BIG = 0.34F;
	/** The share of a picture its blood fills (the rest is clear round it): what's kept off the crosshair and hotbar. */
	static final float FILLED = 0.55F;
	/** At most this many up at once (times the gore level), the oldest going first. */
	static final int MAX_SPLATS = 9;
	/** A drop lands on the screen at most once in this many ticks (a spray at your face is one or two splats, not a wall). */
	static final int DROP_GAP_TICKS = 4;
	/** A big blow close by: within this many blocks of your eye, and no wider of where you look than this (a cosine). */
	static final double RANGE = 2.5, IN_VIEW = 0.35;
	/** A big blow close by throws this many, plus this many per unit of its weight, at most this many (times the gore level). */
	static final int HIT_SPLATS = 1, HIT_MOST = 4;
	static final float HIT_PER_WEIGHT = 1.2F;
	/** How far round the blow's place on the screen its splats scatter (a share of the screen). */
	static final float HIT_SPREAD = 0.3F;
	/** A drop must be this near where you look (a cosine) to land on the screen. */
	static final double DROP_IN_VIEW = 0.3;
	/** A blow on you throws 1, up to this many more at its hardest; hardest = this share of your health in one blow. */
	static final int BLOW_EXTRA = 3;
	static final float BLOW_HARDEST = 0.3F;
	/** Kept clear: an oval round the crosshair (a share of the screen's width and height). */
	static final float CLEAR_X = 0.2F, CLEAR_Y = 0.24F;
	/** Kept clear: the hotbar, this wide either side of the middle and this tall from the bottom (gui pixels). */
	static final int HOTBAR_HALF = 100, HOTBAR_TALL = 44;
	/** A blow lands round this oval (a share of the screen's half width and half height), on the side it came from. */
	static final float BLOW_RING_X = 0.78F, BLOW_RING_Y = 0.72F;
	/** A drop at your eyes: closer than this (blocks) isn't drawn in the world, and lands here if it's coming at you. */
	public static final double NEAR = 1.1;
	/** A drop lands on the screen only if the line it flies on passes this near the eye (blocks): one flying past doesn't. */
	public static final double EYE_MISS = 0.4;
	/** The colour of your own blood on a blow ({@code BladeBlood.RED}, a person's; darkened when drawn, {@link #SHADE}). */
	static final int OWN_BLOOD = 0x80000D;
	/** The blood's own colour is darkened to this share, so a bright red reads as blood, not paint. */
	static final float SHADE = 0.85F;

	private static final Identifier[] TEXTURES = new Identifier[PICTURES];

	static {
		for (int i = 0; i < PICTURES; i++) {
			TEXTURES[i] = JaymosPowers.id("textures/gui/blood/screen_splat_" + (i + 1) + ".png");
		}
	}

	/** One splat: where its middle is (a share of the screen), how big (a share of its height), and its look. */
	private record Splat(float x, float y, float size, int picture, float turn, int color, long born, int hold) {
	}

	private static final List<Splat> SPLATS = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long now;
	private static long lastFromDrop = Long.MIN_VALUE / 2;

	private BloodScreen() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BloodScreen::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SPLATS.clear());
		// (under the crosshair, the hotbar and the rest of the HUD: they draw over it)
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("blood_screen"), BloodScreen::draw);
	}

	/** Whether the local player's screen takes blood now. */
	private static boolean shown(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator() && minecraft.options.getCameraType().isFirstPerson()
			&& !BloodSettings.off() && !Downed.isDowned(player);
	}

	/** The most up at once, by the gore level. */
	private static int most() {
		return Math.max(1, Math.round(MAX_SPLATS * BloodSettings.gore().share));
	}

	/** A size multiplier by the gore level (low a little smaller, high a little bigger). */
	private static float goreSize() {
		return 0.8F + 0.2F * BloodSettings.gore().share;
	}

	/** How many of {@code count} splats the gore level allows (at least one). */
	private static int byGore(float count) {
		return Math.max(1, Math.round(count * BloodSettings.gore().share));
	}

	/**
	 * A blow of {@code size} weight (1 = a heavy blunt blow, at most 2), spilling {@code color}, landed at {@code at}:
	 * splats on that side of the screen if it is near and in view.
	 */
	static void hit(Vec3 at, int color, float size) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!shown(minecraft)) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 to = at.subtract(camera.position());
		double distance = to.length();
		if (distance > RANGE) {
			return;
		}
		float[] seen = distance < 0.3 ? new float[] {0.5F, 0.5F} : onScreen(camera, to.x, to.y, to.z, distance, IN_VIEW);
		if (seen == null) {
			return;
		}
		float heavy = Mth.clamp(size / 2.0F, 0.0F, 1.0F);
		int count = Math.min(HIT_MOST, byGore(HIT_SPLATS + size * HIT_PER_WEIGHT));
		for (int i = 0; i < count; i++) {
			float x = seen[0] + (RANDOM.nextFloat() - 0.5F) * HIT_SPREAD, y = seen[1] + (RANDOM.nextFloat() - 0.5F) * HIT_SPREAD;
			float splat = Mth.lerp(heavy, SIZE_SMALL, SIZE_BIG) * (0.65F + RANDOM.nextFloat() * 0.45F) * (i == 0 ? 1.0F : 0.7F);
			add(x, y, splat, BloodSettings.tint(color));
		}
	}

	/**
	 * Where a point {@code (dx, dy, dz)} from the camera ({@code distance} away) shows on the screen, as shares of its
	 * width and height (may lie off it), or null when it's further than {@code inView} (a cosine) from where it looks.
	 */
	static float[] onScreen(Camera camera, double dx, double dy, double dz, double distance, double inView) {
		Vector3fc forward = camera.forwardVector(), left = camera.leftVector(), up = camera.upVector();
		double ahead = (forward.x() * dx + forward.y() * dy + forward.z() * dz) / distance;
		if (ahead < inView) {
			return null;
		}
		double reach = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		double across = -(left.x() * dx + left.y() * dy + left.z() * dz) / distance / ahead / reach;
		double high = (up.x() * dx + up.y() * dy + up.z() * dz) / distance / ahead / reach;
		var window = Minecraft.getInstance().getWindow();
		float aspect = window.getGuiScaledHeight() / (float) Math.max(1, window.getGuiScaledWidth());
		return new float[] {0.5F + (float) across * 0.5F * aspect, 0.5F - (float) high * 0.5F};
	}

	/**
	 * A blow hurt you: {@code amount} health of your {@code maxHealth}, from {@code at} (where it came from, or null).
	 * A few splats on that side. Called from {@code HurtCauseHud} for every blow, not only the one it tells you of.
	 */
	public static void blow(float amount, float maxHealth, Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!shown(minecraft) || amount <= 0.0F) {
			return;
		}
		float hard = Mth.clamp(amount / Math.max(1.0F, maxHealth * BLOW_HARDEST), 0.0F, 1.0F);
		int count = Math.min(1 + BLOW_EXTRA, byGore(1 + hard * BLOW_EXTRA));
		float bearing;
		if (at != null) {
			LocalPlayer player = minecraft.player;
			Vec3 to = at.subtract(player.getEyePosition());
			bearing = to.horizontalDistanceSqr() < 1.0E-4 ? RANDOM.nextFloat() * Mth.TWO_PI
				: (float) Math.toRadians(Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F - player.getYRot());
		} else {
			bearing = RANDOM.nextFloat() * Mth.TWO_PI;
		}
		for (int i = 0; i < count; i++) {
			float angle = bearing + (RANDOM.nextFloat() - 0.5F) * 1.1F;
			float ring = 0.85F + RANDOM.nextFloat() * 0.3F;
			float x = 0.5F + Mth.sin(angle) * 0.5F * BLOW_RING_X * ring;
			float y = 0.5F - Mth.cos(angle) * 0.5F * BLOW_RING_Y * ring;
			float size = Mth.lerp(hard, SIZE_SMALL, SIZE_BIG) * (0.7F + RANDOM.nextFloat() * 0.5F) * (i == 0 ? 1.0F : 0.75F);
			add(x, y, size, BloodSettings.tint(OWN_BLOOD));
		}
	}

	/**
	 * A drop of {@code color} (world size {@code quadSize}) flying at a first-person eye came within {@link #NEAR}
	 * of it, {@code (dx, dy, dz)} from {@code camera}: it lands where it would have hit the view, if that's in view.
	 */
	static void drop(Camera camera, double dx, double dy, double dz, float quadSize, int color) {
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (now - lastFromDrop < DROP_GAP_TICKS || distance < 1.0E-3 || !shown(Minecraft.getInstance())) {
			return;
		}
		float[] seen = onScreen(camera, dx, dy, dz, distance, DROP_IN_VIEW);
		if (seen == null) {
			return;
		}
		lastFromDrop = now;
		float size = Mth.clamp(quadSize / 0.5F, 0.5F, 1.4F) * Mth.lerp(RANDOM.nextFloat(), SIZE_SMALL, (SIZE_SMALL + SIZE_BIG) / 2.0F);
		add(seen[0], seen[1], size, BloodSettings.tint(color));
	}

	private static void add(float x, float y, float size, int color) {
		Minecraft minecraft = Minecraft.getInstance();
		int w = minecraft.getWindow().getGuiScaledWidth(), h = minecraft.getWindow().getGuiScaledHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		size *= goreSize();
		float[] at = clear(x, y, size, w, h);
		while (SPLATS.size() >= most()) {
			SPLATS.remove(0);
		}
		SPLATS.add(new Splat(at[0], at[1], size, RANDOM.nextInt(PICTURES), RANDOM.nextFloat() * Mth.TWO_PI, color, now,
			HOLD_TICKS + RANDOM.nextInt(HOLD_SPREAD + 1)));
	}

	/**
	 * Where a splat of {@code size} meant for ({@code x}, {@code y}) goes: pushed out of the oval round the crosshair (with
	 * its own size added) along the line from the middle, then up off the hotbar, then kept on the screen.
	 */
	static float[] clear(float x, float y, float size, int w, int h) {
		float half = size * h / 2.0F * FILLED;
		float px = (x - 0.5F) * w, py = (y - 0.5F) * h;
		float rx = CLEAR_X * w + half, ry = CLEAR_Y * h + half;
		float d = (px * px) / (rx * rx) + (py * py) / (ry * ry);
		if (d < 1.0F) {
			if (d < 1.0E-4F) {
				px = RANDOM.nextBoolean() ? 1.0F : -1.0F;
				py = 0.0F;
				d = px * px / (rx * rx);
			}
			float push = 1.0F / (float) Math.sqrt(d);
			px *= push;
			py *= push;
		}
		float sx = w / 2.0F + px, sy = h / 2.0F + py;
		// (the run down the screen counts too: it mustn't slide onto the hotbar)
		float bottom = sy + half * (1.0F + RUN_STRETCH) + RUN * size * h;
		if (Math.abs(sx - w / 2.0F) < HOTBAR_HALF + half && bottom > h - HOTBAR_TALL) {
			sy -= bottom - (h - HOTBAR_TALL);
		}
		sx = Mth.clamp(sx, half * 0.3F, w - half * 0.3F);
		sy = Mth.clamp(sy, half * 0.3F, h - half * 0.3F);
		return new float[] {sx / w, sy / h};
	}

	private static void tick(Minecraft minecraft) {
		now++;
		if (!SPLATS.isEmpty() && !shown(minecraft)) {
			SPLATS.clear();
		}
		SPLATS.removeIf(splat -> now - splat.born() > splat.hold() + FADE_TICKS);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (SPLATS.isEmpty() || HudZones.hidden()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		for (Splat splat : SPLATS) {
			float age = now - splat.born() + partial;
			float pop = age >= POP_TICKS ? 1.0F : Mth.lerp(age / POP_TICKS, POP_FROM, 1.0F);
			float life = Mth.clamp(age / (splat.hold() + FADE_TICKS), 0.0F, 1.0F);
			float fade = Mth.clamp((splat.hold() + FADE_TICKS - age) / FADE_TICKS, 0.0F, 1.0F);
			// (it runs fastest at first, then slows: smooth, never stepping)
			float ran = 1.0F - (1.0F - life) * (1.0F - life);
			float size = splat.size() * h * pop;
			int side = Math.max(2, Math.round(size));
			float cx = splat.x() * w, cy = splat.y() * h + ran * RUN * size;
			float alpha = MAX_ALPHA * fade * fade * Mth.clamp(age / 1.5F, 0.0F, 1.0F);
			if (alpha < 0.01F) {
				continue;
			}
			int c = splat.color();
			int tint = ARGB.colorFromFloat(alpha, (c >> 16 & 0xFF) / 255.0F * SHADE, (c >> 8 & 0xFF) / 255.0F * SHADE, (c & 0xFF) / 255.0F * SHADE);
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, cy);
			// (it runs down: a little longer, and a little narrower, as it goes)
			graphics.pose().scale(1.0F - ran * RUN_STRETCH * 0.3F, 1.0F + ran * RUN_STRETCH);
			graphics.pose().rotate(splat.turn());
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[splat.picture()], -side / 2, -side / 2, 0.0F, 0.0F, side, side, side, side, tint);
			graphics.pose().popMatrix();
		}
	}
}
