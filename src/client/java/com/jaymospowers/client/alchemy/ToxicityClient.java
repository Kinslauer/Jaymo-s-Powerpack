package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.toxic.Toxicity;
import com.jaymospowers.alchemy.toxic.ToxinGaugeItem;
import com.jaymospowers.client.UiStyle;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * What a load of toxin does to your own senses (Hunter's Road, patch 6, "D1"): past half the whole view is tinged green,
 * and a slow heartbeat sounds, slower the fuller the load. There is no bar; the gauge ({@link ToxinGaugeItem}) is the only
 * readout, drawn here as a small panel by the crosshair while it is held or worn in the charm slot.
 */
public final class ToxicityClient {
	// ---- tuning ----
	/** The tint's colour and greatest opacity (0-255), and how fast the shown tint follows the load. */
	public static final int TINT_COLOUR = 0x5C9A2C, TINT_ALPHA = 0x58;
	public static final float TINT_EASE = 0.03F;
	/** Ticks between heartbeats at half a load and at a full one (slower as it fills), and the beat's volume and pitch range. */
	public static final int BEAT_FAST = 40, BEAT_SLOW = 100;
	public static final float BEAT_VOLUME = 0.55F, BEAT_PITCH_HIGH = 0.85F, BEAT_PITCH_LOW = 0.5F;
	private static final int ACCENT = 0xFFB8924A, PANEL = 0x90081018, ROW = 11, WIDTH = 120, LOAD_COLOUR = 0xFF8CC63F, TOLERANCE_COLOUR = 0xFFD8A83C;

	private static float shade;
	private static int beatIn;

	private ToxicityClient() {
	}

	static void init() {
		ToxicLayer.init();
		ClientTickEvents.END_CLIENT_TICK.register(ToxicityClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("toxic_tint"), ToxicityClient::renderTint);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("toxin_gauge"), ToxicityClient::renderGauge);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused()) {
			shade = 0.0F;
			return;
		}
		int total = player.isAlive() && !player.isSpectator() ? Toxicity.total(player) : 0;
		float target = total > Toxicity.GREEN_AT ? Mth.clamp((total - Toxicity.GREEN_AT) / (float) (Toxicity.CAPACITY - Toxicity.GREEN_AT), 0.0F, 1.0F) : 0.0F;
		shade += (target - shade) * TINT_EASE;
		if (target <= 0.0F) {
			beatIn = 0;
			return;
		}
		if (--beatIn <= 0) {
			minecraft.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
				BEAT_VOLUME, Mth.lerp(target, BEAT_PITCH_HIGH, BEAT_PITCH_LOW), false);
			beatIn = Math.round(Mth.lerp(target, BEAT_FAST, BEAT_SLOW));
		}
	}

	private static void renderTint(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (shade < 0.01F) {
			return;
		}
		int alpha = Mth.clamp(Math.round(TINT_ALPHA * shade), 0, 255);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | TINT_COLOUR);
	}

	private static void renderGauge(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.gui.screen() != null || !ToxinGaugeItem.reads(player)) {
			return;
		}
		Font font = minecraft.font;
		Toxicity.State state = Toxicity.of(player);
		int line = Toxicity.harmLine(player);
		int next = Toxicity.nextThreshold(player);
		List<Component> mutagens = Toxicity.mutagens(player);
		int height = 60 + ROW * Math.max(1, mutagens.size());
		int[] at = com.jaymospowers.client.hud.HudZones.place(com.jaymospowers.client.hud.HudZones.INSPECT, "jaymospowers:toxin_gauge", 20, WIDTH, height);
		if (at == null) {
			return;
		}
		int x = at[0];
		int y = at[1];
		graphics.fill(x, y, x + WIDTH, y + height, PANEL);
		graphics.fill(x, y, x + 2, y + height, ACCENT);
		int bar = WIDTH - 12;
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.toxin_gauge.load", state.total() / 10), x + 6, y + 4, bar, UiStyle.TEXT);
		UiStyle.bar(graphics, x + 6, y + 14, bar, 3, state.total() / (float) Toxicity.CAPACITY, LOAD_COLOUR);
		// the green line and the harm line, ticked on the bar
		graphics.fill(x + 6 + bar * Toxicity.GREEN_AT / Toxicity.CAPACITY, y + 13, x + 7 + bar * Toxicity.GREEN_AT / Toxicity.CAPACITY, y + 18, 0xFFFFFFFF);
		graphics.fill(x + 6 + bar * line / Toxicity.CAPACITY, y + 13, x + 7 + bar * line / Toxicity.CAPACITY, y + 18, 0xFFE05050);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.toxin_gauge.tolerance", state.tolerance() / 5), x + 6, y + 21, bar, UiStyle.TEXT);
		UiStyle.bar(graphics, x + 6, y + 31, bar, 2, state.tolerance() / (float) Toxicity.TOLERANCE_MAX, TOLERANCE_COLOUR);
		Component nextText = next < 0 ? Component.translatable("gui.jaymospowers.toxin_gauge.next_none")
			: Component.translatable("gui.jaymospowers.toxin_gauge.next", next / 10);
		UiStyle.line(graphics, font, nextText, x + 6, y + 37, bar, UiStyle.HINT);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.toxin_gauge.mutagens"), x + 6, y + 37 + ROW, bar, UiStyle.TEXT);
		int ry = y + 37 + ROW * 2;
		if (mutagens.isEmpty()) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.toxin_gauge.none"), x + 10, ry, bar - 4, UiStyle.HINT);
			return;
		}
		for (Component m : mutagens) {
			UiStyle.line(graphics, font, m, x + 10, ry, bar - 4, UiStyle.TEXT);
			ry += ROW;
		}
	}
}
