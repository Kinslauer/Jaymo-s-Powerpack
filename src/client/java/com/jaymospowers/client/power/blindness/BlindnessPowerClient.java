package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.blindness.Blind;
import com.jaymospowers.power.blindness.BlindnessEcho;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The blind screen. Blindness is a condition ({@link Blind}), not a power, so this is drawn for anyone it holds:
 * <ul>
 * <li><b>Both eyes gone</b>: the whole view is dark ({@link #BLIND_ALPHA}), and every sound near you ripples on it from
 * where it came ({@link BlindnessEcholocationClient}).</li>
 * <li><b>One eye gone</b>: that side of the view is dark, soft at its inner edge.</li>
 * <li><b>A Resonance holder in the dark</b> (eyes gone, or blindfolded): the dark is thinner, so the living they hear
 * burn through it ({@link BlindnessDevoutSight}), and thinner still from up in Sky Sight.</li>
 * </ul>
 * A blindfold on anyone else is the vanilla Blindness effect ({@code BlindedHud}); the rings play over that too.
 */
public final class BlindnessPowerClient {
	/** The dark's colour; how solid it is with both eyes gone, for a Resonance holder, and from up in Sky Sight. */
	static final int DARK = 0x0A0A12;
	static final int BLIND_ALPHA = 0xF4, DEVOUT_ALPHA = 0xB4, SKY_SIGHT_ALPHA = 0x8C;
	/** One eye gone: how solid its side is, and how much of the view's middle the dark fades out over (share of the width). */
	static final int ONE_EYE_ALPHA = 0xE6;
	static final float ONE_EYE_SOFT = 0.12F;
	static final int SOFT_BANDS = 12;
	/** How fast the dark closes in and lifts (a share a tick). */
	static final float CLOSE = 0.1F;

	private static float both, left, right;

	private BlindnessPowerClient() {
	}

	/** Called once from {@code JaymosPowersClient}. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BlindnessPowerClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("blindness_overlay"), BlindnessPowerClient::render);
		BlindnessEcholocationClient.init();
		BlindnessSockets.register();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.blindness.Unseeing.TYPE, UnseeingRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(BlindnessEcho.TYPE, (payload, context) ->
			context.client().execute(() -> BlindnessEcholocationClient.onEcho(payload)));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			both = left = right = 0.0F;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		int lost = player.isSpectator() ? 0 : Blind.eyesLost(player);
		// a Resonance holder's blindfold is this dark too (anyone else's is the Blindness effect)
		boolean dark = lost == Blind.BOTH || BlindnessDevoutSight.devout(player);
		both = ease(both, dark);
		left = ease(left, !dark && lost == Blind.LEFT);
		right = ease(right, !dark && lost == Blind.RIGHT);
	}

	private static float ease(float value, boolean on) {
		float next = value + ((on ? 1.0F : 0.0F) - value) * CLOSE;
		return Math.abs(next - (on ? 1.0F : 0.0F)) < 0.01F ? (on ? 1.0F : 0.0F) : next;
	}

	/** Whether the local player's view is dark now (so the rings of sound should show). */
	static boolean dark(LocalPlayer player) {
		return both > 0.3F || Blind.sightless(player);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		LocalPlayer player = Minecraft.getInstance().player;
		if (w == 0 || h == 0 || player == null) {
			return;
		}
		if (both > 0.01F) {
			int full = BlindnessDevoutSight.devout(player) ? (int) Mth.lerp(BlindnessDevoutSight.raised(), DEVOUT_ALPHA, SKY_SIGHT_ALPHA) : BLIND_ALPHA;
			graphics.fill(0, 0, w, h, Mth.clamp((int) (full * both), 0, 255) << 24 | DARK);
		}
		// the wearer's left eye sees the left of the view
		side(graphics, w, h, true, left);
		side(graphics, w, h, false, right);
		if (dark(player)) {
			BlindnessEcholocationClient.render(graphics, delta);
		}
	}

	/** One eye's half of the view gone dark: solid from the edge to near the middle, fading out across it. */
	private static void side(GuiGraphicsExtractor graphics, int w, int h, boolean leftSide, float shade) {
		if (shade <= 0.01F) {
			return;
		}
		int soft = Math.max(SOFT_BANDS, (int) (w * ONE_EYE_SOFT));
		int solid = w / 2 - soft / 2;
		int alpha = Mth.clamp((int) (ONE_EYE_ALPHA * shade), 0, 255);
		if (leftSide) {
			graphics.fill(0, 0, solid, h, alpha << 24 | DARK);
		} else {
			graphics.fill(w - solid, 0, w, h, alpha << 24 | DARK);
		}
		for (int i = 0; i < SOFT_BANDS; i++) {
			int a = alpha * (SOFT_BANDS - i) / (SOFT_BANDS + 1);
			int x0 = solid + soft * i / SOFT_BANDS;
			int x1 = solid + soft * (i + 1) / SOFT_BANDS;
			if (leftSide) {
				graphics.fill(x0, 0, x1, h, a << 24 | DARK);
			} else {
				graphics.fill(w - x1, 0, w - x0, h, a << 24 | DARK);
			}
		}
	}
}
