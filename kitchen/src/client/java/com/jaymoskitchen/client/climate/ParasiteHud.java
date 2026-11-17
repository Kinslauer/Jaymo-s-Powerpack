package com.jaymoskitchen.client.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.climate.ParasiteEffect;
import com.jaymoskitchen.net.Payloads;
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
 * The parasite ({@link ParasiteEffect}) on your own screen, once it has settled in and cramps: a faint queasy tint at
 * the edges that slowly comes and goes ({@link #TINT}), and with each cramp a sickly flash from the edges in
 * ({@link #FLASH}) as your head jerks down, doubled over ({@link #DOUBLE_OVER}).
 */
public final class ParasiteHud {
	/** The colour: the parasite effect's own sickly olive. */
	static final int COLOUR = 0x556B2F;
	/** The standing tint: how thick at the very edge (0-255) and how far in (share of the shorter side). */
	static final float TINT = 45.0F, TINT_REACH = 0.18F;
	/** It comes and goes once every this many ticks. */
	static final float TINT_TICKS = 90.0F;
	/** A cramp's flash: how thick at the very edge (0-255), how far in, and how long it fades (ticks). */
	static final float FLASH = 170.0F, FLASH_REACH = 0.4F;
	static final int FLASH_TICKS = 30;
	/** With a cramp the head jerks this far down (degrees), over the first few ticks of the flash. */
	static final float DOUBLE_OVER = 12.0F;
	static final int DOUBLE_TICKS = 4;
	static final int BANDS = 12;

	/** Ticks left of the cramp's flash. */
	private static int flash;

	private ParasiteHud() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Cramp.TYPE, (payload, ctx) -> flash = FLASH_TICKS);
		ClientTickEvents.END_CLIENT_TICK.register(ParasiteHud::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKitchen.id("parasite"), ParasiteHud::draw);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || flash <= 0) {
			flash = 0;
			return;
		}
		if (flash > FLASH_TICKS - DOUBLE_TICKS) {
			// doubled over with it
			player.setXRot(Mth.clamp(player.getXRot() + DOUBLE_OVER / DOUBLE_TICKS, -90.0F, 90.0F));
		}
		flash--;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		boolean cramping = ParasiteEffect.cramping(player.getEffect(ParasiteEffect.PARASITE));
		float f = flash <= 0 ? 0.0F : Mth.clamp((flash - partial) / FLASH_TICKS, 0.0F, 1.0F);
		if (!cramping && f <= 0.0F) {
			return;
		}
		float breath = 0.5F - 0.5F * Mth.cos((player.tickCount + partial) * Mth.TWO_PI / TINT_TICKS);
		float edge = cramping ? TINT * (0.6F + 0.4F * breath) : 0.0F;
		float reach = TINT_REACH;
		if (f > 0.0F) {
			float s = f * f;
			edge = Math.max(edge, FLASH * s);
			reach = Mth.lerp(s, reach, FLASH_REACH);
		}
		frame(graphics, reach, edge);
	}

	/** The colour from the edges in, {@code reach} of the shorter side, {@code edge} thick (0-255) at the edge, thinning inward. */
	private static void frame(GuiGraphicsExtractor graphics, float reach, float edge) {
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float far = Math.min(w, h) * reach, band = far / BANDS;
		for (int i = 0; i < BANDS; i++) {
			float t = 1.0F - i / (float) BANDS;
			int alpha = Math.min(255, Math.round(edge * t * t));
			int a = Math.round(i * band), b = Math.round((i + 1) * band);
			if (alpha <= 0 || b <= a || a * 2 >= Math.min(w, h)) {
				continue;
			}
			int color = alpha << 24 | COLOUR;
			graphics.fill(a, a, w - a, b, color);
			graphics.fill(a, h - b, w - a, h - a, color);
			graphics.fill(a, b, b, h - b, color);
			graphics.fill(w - b, b, w - a, h - b, color);
		}
	}
}
