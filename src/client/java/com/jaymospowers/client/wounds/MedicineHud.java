package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.Pain;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.14.4) Wounds phase 4 on your own screen: a warm haze under poppy ({@link Pain#poppied}), a white flash as smelling
 * salts go under your nose ({@link Pain#JOLT}; (0.14.5) with a sharp gasp), a feverish shimmer while a part festers
 * ({@link Bleeding#FESTERING}), and the shakes in withdrawal ({@link Pain#WITHDRAWAL}: the view trembles, as Kitchen's
 * drunk sway moves it). Also opens the body screen when the server says ({@link BodyScreen}).
 */
public final class MedicineHud {
	/** The poppy haze: its colour (RGB) and thickness (0-255), breathing slowly. */
	static final int HAZE = 0xFFB060, HAZE_ALPHA = 0x30;
	/** The fever's shimmer: colour, and how thick at most. */
	static final int FEVER = 0xB8C860, FEVER_ALPHA = 0x28;
	/** The salts' flash: how long (ticks) and how white at first. */
	static final int JOLT_TICKS = 6, JOLT_ALPHA = 0xD0;
	/** (0.14.5) The salts' gasp: how loud, and how high (a drowning gasp, pitched up short). */
	static final float GASP_VOLUME = 0.8F, GASP_PITCH = 1.4F;
	/** The shakes: degrees of tremble at most, and how fast. */
	static final float SHAKE = 0.6F, SHAKE_SPEED = 1.9F;

	private static int ticks;
	/** What the shakes added to the view last tick, so each tick adds only the change. */
	private static float lastYaw, lastPitch;
	/** (0.14.5) The salts' jolt last gasped at (game time), so each jolt gasps once. */
	private static long gasped = Long.MIN_VALUE;

	private MedicineHud() {
	}

	public static void init() {
		BodyScreen.register();
		ClientTickEvents.END_CLIENT_TICK.register(MedicineHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("medicine_hud"), MedicineHud::render);
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		gasp(player);
		boolean shaking = player != null && player.isAlive() && player.hasAttached(Pain.WITHDRAWAL);
		if (!shaking) {
			if (player != null && (lastYaw != 0.0F || lastPitch != 0.0F)) {
				player.setYRot(player.getYRot() - lastYaw);
				player.setXRot(Mth.clamp(player.getXRot() - lastPitch, -90.0F, 90.0F));
			}
			lastYaw = 0.0F;
			lastPitch = 0.0F;
			return;
		}
		if (minecraft.gui.screen() != null) {
			return;
		}
		float yaw = SHAKE * Mth.sin(ticks * SHAKE_SPEED) * Mth.sin(ticks * 0.37F);
		float pitch = SHAKE * 0.6F * Mth.sin(ticks * SHAKE_SPEED * 1.3F + 1.0F);
		player.setYRot(player.getYRot() + (yaw - lastYaw));
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - lastPitch), -90.0F, 90.0F));
		lastYaw = yaw;
		lastPitch = pitch;
	}

	/** (0.14.5) As the salts' jolt starts: a gasp, on the owner's client only. */
	private static void gasp(LocalPlayer player) {
		if (player == null) {
			gasped = Long.MIN_VALUE;
			return;
		}
		Long jolt = player.getAttached(Pain.JOLT);
		if (jolt == null || jolt == gasped) {
			return;
		}
		gasped = jolt;
		long since = player.level().getGameTime() - jolt;
		if (since >= 0 && since < JOLT_TICKS && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
			player.level().playLocalSound(player, SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, GASP_VOLUME, GASP_PITCH);
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isAlive()) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (Pain.poppied(player)) {
			int alpha = (int) (HAZE_ALPHA * (0.75F + 0.25F * Mth.sin(ticks * 0.05F)));
			graphics.fill(0, 0, w, h, alpha << 24 | HAZE);
		}
		if (Bleeding.festering(player) != 0) {
			int alpha = (int) (FEVER_ALPHA * (0.5F + 0.5F * Mth.sin(ticks * 0.21F)));
			graphics.fill(0, 0, w, h, Mth.clamp(alpha, 0, 255) << 24 | FEVER);
		}
		Long jolt = player.getAttached(Pain.JOLT);
		if (jolt != null) {
			long since = player.level().getGameTime() - jolt;
			if (since >= 0 && since < JOLT_TICKS) {
				int alpha = (int) (JOLT_ALPHA * (1.0F - since / (float) JOLT_TICKS));
				graphics.fill(0, 0, w, h, alpha << 24 | 0xFFFFFF);
			}
		}
	}
}
