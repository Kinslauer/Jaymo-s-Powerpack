package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteMind;
import com.jaymospowers.power.web.WebSymbioteState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * When the bond reaches a named stage ({@link WebSymbioteMind#stage}: Attached, Integrated, Cooperative, Dominant) a card
 * with a picture slides in from the top for a few seconds, toast style, in the strain's colours: the picture
 * ({@code textures/gui/web_stage/<stage>.png}, made by {@code tools/gen_web_stage_cards.py}) is tinted at draw time. The
 * stage you have when you join is not announced, only the ones you climb to.
 */
final class WebStageCards {
	private static final int CARD_W = 170, CARD_H = 40, PICTURE = 32, TOP = 14, MARGIN = 4;
	private static final long SLIDE_MS = 400L, HOLD_MS = 4500L;
	private static final int FILL = 0xE0100818;

	/** The stage seen last tick (-1: none yet this session), and the card being shown. */
	private static int seen = -1;
	private static int showing = -1;
	private static int showingColor;
	private static long shownAt;

	private WebStageCards() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WebStageCards::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			seen = -1;
			showing = -1;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("web_stage_card"), WebStageCards::render);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !WebSymbiote.isBonded(player)) {
			seen = -1;
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		int stage = WebSymbioteMind.stage(state.bond());
		if (seen >= 0 && stage > seen) {
			showing = stage;
			showingColor = WebSymbioteHud.strainColor(state);
			shownAt = Util.getMillis();
		}
		seen = stage;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (showing < 0) {
			return;
		}
		long age = Util.getMillis() - shownAt;
		long total = SLIDE_MS * 2 + HOLD_MS;
		if (age >= total) {
			showing = -1;
			return;
		}
		// slides down in, holds, slides back up
		float in = Mth.clamp(age / (float)SLIDE_MS, 0.0F, 1.0F);
		float out = Mth.clamp((total - age) / (float)SLIDE_MS, 0.0F, 1.0F);
		float slide = Math.min(in, out);
		int y = TOP - Math.round((1.0F - slide) * (CARD_H + TOP));
		Minecraft minecraft = Minecraft.getInstance();
		int x = (graphics.guiWidth() - CARD_W) / 2;
		String stage = WebSymbioteMind.stageName(WebSymbioteMind.STAGE_AT[showing]);
		graphics.fill(x, y, x + CARD_W, y + CARD_H, FILL);
		graphics.fill(x, y, x + 2, y + CARD_H, showingColor);
		graphics.outline(x, y, CARD_W, CARD_H, showingColor & 0x80FFFFFF);
		Identifier picture = JaymosPowers.id("textures/gui/web_stage/" + stage + ".png");
		graphics.blit(RenderPipelines.GUI_TEXTURED, picture, x + 6, y + (CARD_H - PICTURE) / 2, 0.0F, 0.0F, PICTURE, PICTURE, 48, 48, 48, 48, showingColor);
		int tx = x + 6 + PICTURE + 6;
		int room = CARD_W - (tx - x) - 6;
		UiStyle.line(graphics, minecraft.font, Component.translatable("gui.jaymospowers.web_symbiote.stage." + stage), tx, y + 8, room, showingColor);
		UiStyle.wrapped(graphics, minecraft.font, Component.translatable("gui.jaymospowers.web_symbiote.card." + stage), tx, y + 20, room, 2, 9, UiStyle.DIM, false);
	}
}
