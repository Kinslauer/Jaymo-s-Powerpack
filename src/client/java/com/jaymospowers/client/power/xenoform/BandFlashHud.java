package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.BandFlash;
import com.jaymospowers.power.xenoform.XenoNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Someone's band flashed in your sight ({@link BandFlash}, on the server): the screen whites out in bright green light for
 * {@link BandFlash#TICKS}, held full for the first part and fading after. It's weaker further off and when you're looking
 * away. On everyone's screen, whatever their own power.
 */
public final class BandFlashHud {
	/** The whiteout's colours: near white at its height, the band's green as it fades. */
	static final int HOT = 0xEAFFEE, COOL = 0x5CF07A;
	/** (0.18.126) How far the height of the flash is toward white from the band's own glow. */
	static final float HOT_WHITE = 0.85F;
	/** Its opacity at full strength, and the share of its time it holds before fading. */
	static final float PEAK_ALPHA = 0.95F, HOLD = 0.4F;
	/** Strength: full within this many blocks, down to {@link #FAR_STRENGTH} at the flash's reach; looking away keeps this much of it. */
	static final double NEAR = 4.0;
	static final float FAR_STRENGTH = 0.6F, AWAY_STRENGTH = 0.45F;

	private static int ticks;
	private static float strength;
	/** The flashing band's glow (RGB). */
	private static int glow = COOL;

	private BandFlashHud() {
	}

	/** From {@link XenoformPowerClient#init}. */
	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(XenoNet.Flash.TYPE, (payload, context) -> flash(payload.at(), payload.glow()));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (ticks > 0) {
				ticks--;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("xenoform_band_flash"), BandFlashHud::render);
	}

	private static void flash(Vec3 at, int colour) {
		glow = colour;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		double distance = eye.distanceTo(at);
		float near = (float)Mth.clamp((distance - NEAR) / Math.max(0.01, BandFlash.RADIUS - NEAR), 0.0, 1.0);
		float facing = (float)Mth.clamp(player.getViewVector(1.0F).dot(at.subtract(eye).normalize()), 0.0, 1.0);
		float s = Mth.lerp(near, 1.0F, FAR_STRENGTH) * Mth.lerp(facing, AWAY_STRENGTH, 1.0F);
		strength = ticks > 0 ? Math.max(strength, s) : s;
		ticks = BandFlash.TICKS;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (ticks <= 0) {
			return;
		}
		// 0 at the flash, 1 as it ends
		float t = Mth.clamp(1.0F - (ticks - delta.getGameTimeDeltaPartialTick(false)) / BandFlash.TICKS, 0.0F, 1.0F);
		float fade = t < HOLD ? 1.0F : 1.0F - (t - HOLD) / (1.0F - HOLD);
		int alpha = Mth.clamp((int)(PEAK_ALPHA * strength * fade * 255.0F), 0, 255);
		int r = (int)Mth.lerp(t, hot(glow >> 16 & 0xFF), glow >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, hot(glow >> 8 & 0xFF), glow >> 8 & 0xFF);
		int b = (int)Mth.lerp(t, hot(glow & 0xFF), glow & 0xFF);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | r << 16 | g << 8 | b);
	}

	/** A channel of the flash at its height: the glow, most of the way to white. */
	private static float hot(int channel) {
		return Mth.lerp(HOT_WHITE, channel, 255);
	}
}
