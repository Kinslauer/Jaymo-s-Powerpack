package com.jaymospowers.client.hud;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UltimateCinematic;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * A title card: the one centred card for a big moment (a level, a heritage chosen, a realm sworn to, a ladder king
 * felled): a big title with one line under it on a thin plate with gold rules, fading in and out. It takes over
 * vanilla's title (a mixin on {@code Hud} sends the title, subtitle and timings of every title packet here), so each
 * mod's fanfare and a command's {@code /title} look the same.
 *
 * <p>Films' rules: while your own ultimate film is running or the zoned HUD is hidden the card waits where it is (its
 * time doesn't run) and shows again after; a new title replaces the one showing.
 */
public final class TitleCard {
	/** Vanilla's default timing (ticks): fade in, stay, fade out. */
	static final int FADE_IN = 10, STAY = 70, FADE_OUT = 20;
	static final float TITLE_SCALE = 2.0F;
	/** How far down the screen the card's centre sits (share of its height), and the space inside its edge. */
	static final float CENTRE_AT = 0.32F;
	static final int PAD_X = 18, PAD_Y = 8, GAP = 5;
	/** How far the card drifts up as it fades in (px), and how much of the plate's alpha it keeps. */
	static final float DRIFT = 8.0F;
	static final float PLATE_SHARE = 0.55F;
	/** A line wider than this share of the screen is cut short. */
	static final float MAX_WIDTH_SHARE = 0.8F;

	private static @Nullable Component title, subtitle;
	private static int fadeIn = FADE_IN, stay = STAY, fadeOut = FADE_OUT;
	/** How long the card has been up (ms) and the clock when it was last drawn. */
	private static long shown, last;

	private TitleCard() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, JaymosPowers.id("title_card"), TitleCard::draw);
	}

	/** Timings in ticks (from the title packet). */
	public static void setTimes(int in, int hold, int out) {
		fadeIn = in;
		stay = hold;
		fadeOut = out;
	}

	/** A new title starts the card over. */
	public static void setTitle(@Nullable Component text) {
		title = text == null || text.getString().isEmpty() ? null : text;
		shown = 0L;
	}

	public static void setSubtitle(@Nullable Component text) {
		subtitle = text == null || text.getString().isEmpty() ? null : text;
	}

	public static void clear() {
		title = null;
		subtitle = null;
		fadeIn = FADE_IN;
		stay = STAY;
		fadeOut = FADE_OUT;
	}

	/** Whether a card is up (or waiting out a film). */
	public static boolean showing() {
		return title != null;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		long dt = Math.min(100L, now - last);
		last = now;
		if (minecraft.player == null) {
			clear();
			return;
		}
		Component title = TitleCard.title, subtitle = TitleCard.subtitle;
		if (title == null || UltimateCinematic.filming() || HudZones.hidden()) {
			return;
		}
		long in = fadeIn * 50L, hold = stay * 50L, out = fadeOut * 50L, total = in + hold + out;
		shown += dt;
		if (shown >= total) {
			clear();
			return;
		}
		float fade = shown < in ? shown / (float)Math.max(1L, in) : shown > in + hold ? (total - shown) / (float)Math.max(1L, out) : 1.0F;
		fade = Mth.clamp(fade, 0.0F, 1.0F);
		int alpha = Math.round(255 * fade);
		if (alpha < 8) {
			return;
		}
		Font font = minecraft.font;
		int maxW = Math.round(graphics.guiWidth() * MAX_WIDTH_SHARE);
		int titleW = Math.min(Math.round(font.width(title) * TITLE_SCALE), maxW);
		int subW = subtitle == null ? 0 : Math.min(font.width(subtitle), maxW);
		int w = Math.max(titleW, subW) + 2 * PAD_X;
		int titleH = Math.round(font.lineHeight * TITLE_SCALE);
		int h = PAD_Y + titleH + (subtitle == null ? 0 : GAP + font.lineHeight) + PAD_Y;
		int x = (graphics.guiWidth() - w) / 2;
		int y = Math.round(graphics.guiHeight() * CENTRE_AT - h / 2.0F - (1.0F - fade) * DRIFT);
		HudStyle.plate(graphics, x, y, w, h, withAlpha(HudStyle.PLATE, fade * PLATE_SHARE), 0);
		int gold = withAlpha(HudStyle.GOLD, fade);
		graphics.fill(x, y, x + w, y + 1, gold);
		graphics.fill(x, y + h - 1, x + w, y + h, gold);
		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2.0F, y + PAD_Y);
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		graphics.centeredText(font, title, 0, 0, alpha << 24 | 0xFFFFFF);
		graphics.pose().popMatrix();
		if (subtitle != null) {
			graphics.centeredText(font, subtitle, graphics.guiWidth() / 2, y + PAD_Y + titleH + GAP, withAlpha(HudStyle.DIM, fade));
		}
	}

	private static int withAlpha(int color, float share) {
		return Math.round((color >>> 24 == 0 ? 255 : color >>> 24) * share) << 24 | color & 0xFFFFFF;
	}
}
