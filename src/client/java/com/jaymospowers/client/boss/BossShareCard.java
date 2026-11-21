package com.jaymospowers.client.boss;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.core.BossShare;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) Who did what: when a boss you fought falls ({@link BossShare}), a card on the top-left rail for
 * {@link #SHOWN_MS}: the boss's name, then each fighter with their share of the harm (yours in gold), most first.
 */
public final class BossShareCard {
	/** How long the card shows (ms), and its rail row (under the trials' pin). */
	static final long SHOWN_MS = 10_000L;
	static final int HUD_ROW = 50;
	private static final int PAD = 4, LINE = 10, BAR_H = 2;

	private static BossShare.@Nullable Card card;
	private static long until;

	private BossShareCard() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BossShare.Card.TYPE, (payload, context) -> {
			card = payload;
			until = System.currentTimeMillis() + SHOWN_MS;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("boss_share"), BossShareCard::hud);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		BossShare.Card c = card;
		Minecraft minecraft = Minecraft.getInstance();
		if (c == null || minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}
		if (System.currentTimeMillis() > until) {
			card = null;
			HudZones.clear(HudZones.RAIL, "jaymospowers:boss_share");
			return;
		}
		Font font = minecraft.font;
		Component title = Component.translatable("hud.jaymospowers.boss_share.fell", c.boss());
		int w = font.width(title);
		Component[] lines = new Component[c.names().size()];
		for (int i = 0; i < lines.length; i++) {
			int share = c.total() <= 0.0F ? 0 : Math.round(100.0F * c.harm().get(i) / c.total());
			lines[i] = Component.translatable("hud.jaymospowers.boss_share.line", i + 1, c.names().get(i), share, Math.round(c.harm().get(i)));
			w = Math.max(w, font.width(lines[i]));
		}
		w += PAD * 2;
		int h = PAD * 2 + LINE + lines.length * (LINE + BAR_H + 1);
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:boss_share", HUD_ROW, w, h);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE);
		graphics.text(font, title, at[0] + PAD, at[1] + PAD, HudStyle.GOLD, true);
		int y = at[1] + PAD + LINE;
		for (int i = 0; i < lines.length; i++) {
			boolean you = i == c.you();
			graphics.text(font, lines[i], at[0] + PAD, y, you ? HudStyle.GOLD : HudStyle.TEXT, true);
			int full = w - PAD * 2;
			int bar = c.total() <= 0.0F ? 0 : Math.round(full * c.harm().get(i) / c.total());
			graphics.fill(at[0] + PAD, y + LINE - 1, at[0] + PAD + full, y + LINE - 1 + BAR_H, HudStyle.TRACK);
			graphics.fill(at[0] + PAD, y + LINE - 1, at[0] + PAD + bar, y + LINE - 1 + BAR_H, you ? HudStyle.GOLD : HudStyle.DIM);
			y += LINE + BAR_H + 1;
		}
	}
}
