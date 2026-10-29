package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FeralCurse;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MoonPull;
import com.jaymoskingdoms.MorningAfter;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

/**
 * (0.7.332) What a werewolf player's own screen shows of the curse (nobody else's):
 *
 * <ul>
 * <li><b>Mood:</b> amber edges that darken with the stage of the curse ({@link FeralCurse}): none at 0, faint at 1, strong
 * at 4. From {@link #GROWL_FROM} the wolf growls under their breath now and then.</li>
 * <li><b>The moon's pull:</b> on the nights before the full moon, a mortal player in their sight
 * ({@link MoonPull#HEARTBEAT}) beats for them: a slow, deep heartbeat in their ears and golden edges pulsing with it (the
 * vampire's red one, {@link BloodVision}, made deeper and golden).</li>
 * <li><b>The morning after:</b> bloody hands at the lower corners of the screen ({@link MorningAfter.Morning}), drawn
 * procedurally into a texture of our own, fading after {@link MorningAfter#BLOODY_TICKS}.</li>
 * </ul>
 */
public final class WolfScreen {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The mood's edges by stage of the curse (0-1). */
	static final float[] MOOD = {0.0F, 0.12F, 0.25F, 0.4F, 0.55F, 0.65F};
	/** The mood's slow breathing: how much the edges swell, and its period (ticks). */
	static final float MOOD_SWELL = 0.2F, MOOD_BEAT = 80.0F;
	/** From this stage the wolf growls under their breath: how long between growls (ticks, at least, and up to this much more, shorter at higher stages), and how loud (per stage). */
	static final int GROWL_FROM = 3, GROWL_EVERY = 240, GROWL_SPREAD = 320;
	static final float GROWL_VOLUME = 0.07F, GROWL_PITCH = 0.55F;
	/** The edges ease in and out this much a tick. */
	static final float FADE = 0.03F;
	/** A mortal in sight: the beat's period face to face and at the range's edge (ticks), the edges at rest and at the top of a beat, and the fade (per tick). */
	static final float SEEN_BEAT = 16.0F, SEEN_BEAT_FAR = 30.0F, SEEN_EDGE = 0.2F, SEEN_EDGE_BEAT = 0.5F, SEEN_FADE = 0.05F;
	/** The heartbeat's volume at the range's edge and face to face (capped at 80%), and its pitch (deeper than a vampire's). */
	static final float HEART_QUIET = 0.2F, HEART_LOUD = 0.8F, HEART_PITCH = 0.55F;
	/** The hands: how tall (share of the screen), how far down they sink (share of their height), and the ticks they take to come and go. */
	static final float HAND_SHARE = 0.38F, HAND_SINK = 0.12F;
	static final int HAND_FADE_IN = 10, HAND_FADE_OUT = 60;
	/** The hands' texture: each print's size in pixels. */
	static final int PRINT_W = 80, PRINT_H = 96;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	private static final Identifier HANDS = JaymosKingdoms.id("textures/gui/bloody_hands");

	/** The mood's edges now (eased) and last tick. */
	private static float mood, lastMood;
	private static int stage, growlIn;
	/** A mortal in sight (0-1), eased; the beat's phase (0-1) and period (ticks). */
	private static float seen, lastSeen, phase, period = SEEN_BEAT_FAR;
	/** The bloody hands: ticks left, and how many there were. */
	private static int blood, bloodAll;
	private static boolean handsMade;

	private WolfScreen() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MorningAfter.Morning.TYPE, (payload, context) -> {
			blood = bloodAll = Math.max(1, payload.ticks());
			ensureHands(context.client());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(WolfScreen::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("wolf_screen"), WolfScreen::hud);
	}

	private static void reset() {
		mood = lastMood = seen = lastSeen = 0.0F;
		stage = 0;
		blood = bloodAll = 0;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		lastMood = mood;
		lastSeen = seen;
		if (player == null || minecraft.level == null) {
			reset();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		boolean cursed = player.hasAttached(FeralCurse.FERAL) && !player.isSpectator();
		stage = cursed ? FeralCurse.stage(player) : 0;
		float want = MOOD[Mth.clamp(stage, 0, MOOD.length - 1)];
		mood = want > mood ? Math.min(want, mood + FADE) : Math.max(want, mood - FADE);
		if (cursed && stage >= GROWL_FROM && --growlIn <= 0) {
			growlIn = Math.max(60, (GROWL_EVERY + player.getRandom().nextInt(GROWL_SPREAD)) / (stage - GROWL_FROM + 1));
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(), GROWL_PITCH + player.getRandom().nextFloat() * 0.15F,
				GROWL_VOLUME * stage));
		}
		float target = cursed ? MoonPull.heartbeat(player) : 0.0F;
		seen = target > seen ? Math.min(target, seen + SEEN_FADE) : Math.max(target, seen - SEEN_FADE);
		period = Mth.lerp(seen, SEEN_BEAT_FAR, SEEN_BEAT);
		phase += 1.0F / period;
		if (phase >= 1.0F) {
			phase -= 1.0F;
			if (seen > 0.05F) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, HEART_PITCH, Mth.lerp(seen, HEART_QUIET, HEART_LOUD)));
			}
		}
		if (blood > 0) {
			blood--;
		}
	}

	/** The beat now: 0 at rest, 1 at the top of the first thump, a smaller second thump after it. */
	private static float beat(float partialTicks) {
		float t = (phase + partialTicks / period) % 1.0F;
		return Math.max(thump(t, 0.0F), 0.6F * thump(t, 0.24F));
	}

	private static float thump(float t, float at) {
		float d = (t - at) / 0.1F;
		return d < 0.0F || d > 1.0F ? 0.0F : Mth.sin(d * Mth.PI);
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float m = Mth.lerp(partialTicks, lastMood, mood);
		if (m > 0.0F) {
			float t = ((minecraft.level.getGameTime() % 1000L) + partialTicks) % MOOD_BEAT / MOOD_BEAT;
			float k = Math.min(1.0F, m * (1.0F + MOOD_SWELL * Mth.sin(t * Mth.TWO_PI)));
			// (the vignette pipeline takes away as much of each colour as given: some red and green for amber)
			edges(graphics, ARGB.colorFromFloat(1.0F, 0.45F * k, 0.7F * k, k));
		}
		float p = Mth.lerp(partialTicks, lastSeen, seen);
		if (p > 0.0F) {
			float k = Math.min(1.0F, p * (SEEN_EDGE + SEEN_EDGE_BEAT * beat(partialTicks)));
			// (less red and green taken than the mood's: a brighter, golden pulse)
			edges(graphics, ARGB.colorFromFloat(1.0F, 0.05F * k, 0.4F * k, k));
		}
		if (blood > 0 && handsMade) {
			hands(graphics, partialTicks);
		}
	}

	private static void edges(GuiGraphicsExtractor graphics, int color) {
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), color);
	}

	/** Two bloody hands at the lower corners, fading in fast and out slowly. */
	private static void hands(GuiGraphicsExtractor graphics, float partialTicks) {
		float left = blood - partialTicks;
		float in = Mth.clamp((bloodAll - left) / HAND_FADE_IN, 0.0F, 1.0F);
		float out = Mth.clamp(left / HAND_FADE_OUT, 0.0F, 1.0F);
		float alpha = Math.min(in, out);
		if (alpha <= 0.0F) {
			return;
		}
		int h = Math.round(graphics.guiHeight() * HAND_SHARE);
		int w = Math.round(h * (float)PRINT_W / PRINT_H);
		int y = graphics.guiHeight() - h + Math.round(h * HAND_SINK);
		int color = ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F);
		// (the texture holds the left hand, then the right)
		graphics.blit(RenderPipelines.GUI_TEXTURED, HANDS, 0, y, 0.0F, 0.0F, w, h, PRINT_W, PRINT_H, PRINT_W * 2, PRINT_H, color);
		graphics.blit(RenderPipelines.GUI_TEXTURED, HANDS, graphics.guiWidth() - w, y, (float)PRINT_W, 0.0F, w, h, PRINT_W, PRINT_H, PRINT_W * 2, PRINT_H, color);
	}

	// ---- the hands' texture --------------------------------------------------------------------------------------------

	/** Paints the hands the first time they're wanted. */
	private static void ensureHands(Minecraft minecraft) {
		if (handsMade) {
			return;
		}
		NativeImage image = new NativeImage(PRINT_W * 2, PRINT_H, true);
		Random random = new Random(323L);
		paintHand(image, 0, true, random);
		paintHand(image, PRINT_W, false, random);
		minecraft.getTextureManager().register(HANDS, new DynamicTexture(HANDS::toString, image));
		handsMade = true;
	}

	/**
	 * One smeared handprint (fingers up) into {@code image} from column {@code ox}, mirrored for the left hand: the palm, four
	 * fingers and a thumb as ellipses with ragged edges, drips running down from the palm, and spatters round it.
	 */
	private static void paintHand(NativeImage image, int ox, boolean mirror, Random random) {
		int[][] fingers = {{25, 17}, {35, 22}, {46, 21}, {56, 16}};
		for (int y = 0; y < PRINT_H; y++) {
			for (int x = 0; x < PRINT_W; x++) {
				float d = Float.MAX_VALUE;
				d = Math.min(d, ellipse(x, y, 40, 66, 19, 17));
				for (int[] f : fingers) {
					d = Math.min(d, ellipse(x, y, f[0], 50 - f[1] / 2.0F, 4.6F, f[1] / 2.0F + 4));
				}
				d = Math.min(d, ellipse(x, y, 15, 62, 5.5F, 10));
				if (d <= 1.0F && (d < 0.8F || random.nextFloat() < 0.65F)) {
					put(image, ox, mirror, x, y, 150 + random.nextInt(70) - (int)(d * 30), random);
				}
			}
		}
		// drips from the palm's heel
		for (int i = 0; i < 6; i++) {
			int x = 26 + random.nextInt(28), length = 6 + random.nextInt(18);
			for (int y = 80; y < Math.min(PRINT_H, 80 + length); y++) {
				put(image, ox, mirror, x, y, 190 - (y - 80) * 5, random);
				if (random.nextInt(3) > 0) {
					put(image, ox, mirror, x + 1, y, 170 - (y - 80) * 5, random);
				}
			}
		}
		// spatters
		for (int i = 0; i < 46; i++) {
			int cx = random.nextInt(PRINT_W), cy = 20 + random.nextInt(PRINT_H - 20), r = 1 + random.nextInt(2);
			for (int dy = -r; dy <= r; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					if (dx * dx + dy * dy <= r * r + 1 && cx + dx >= 0 && cx + dx < PRINT_W && cy + dy >= 0 && cy + dy < PRINT_H) {
						put(image, ox, mirror, cx + dx, cy + dy, 120 + random.nextInt(60), random);
					}
				}
			}
		}
	}

	/** The squared-ellipse distance of a pixel from a centre (1 on its edge, under 1 inside). */
	private static float ellipse(int x, int y, float cx, float cy, float rx, float ry) {
		float dx = (x - cx) / rx, dy = (y - cy) / ry;
		return dx * dx + dy * dy;
	}

	private static void put(NativeImage image, int ox, boolean mirror, int x, int y, int alpha, Random random) {
		if (x < 0 || x >= PRINT_W || y < 0 || y >= PRINT_H) {
			return;
		}
		int dark = random.nextInt(3) == 0 ? 36 : 0;
		image.setPixel(ox + (mirror ? PRINT_W - 1 - x : x), y, ARGB.color(Mth.clamp(alpha, 30, 235), 130 - dark, 8, 8));
	}
}
