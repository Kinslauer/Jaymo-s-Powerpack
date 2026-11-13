package com.jaymospowers.client.hud;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * A tip card: a title in the pack's gold and a few wrapped lines under it on the HUD's plate, in the top right corner
 * (where Minecraft's own toasts go), one at a time. For telling the player something once that isn't a gain (a gain goes
 * on the experience meter) or a milestone (the fanfare). It replaces vanilla's toast, which was the only notice of ours
 * not drawn in the pack's look.
 *
 * <p>A card's time only runs while no screen is open, so a tip earned by opening a screen is still there on the way out.
 */
public final class TipCard {
	/** The card: its width, the space inside its edge, and how far it sits from the screen's corner. */
	static final int WIDTH = 160, PAD = 6, MARGIN = 4;
	/** How long a card stays (ms) and how long it takes to fade in and out. */
	static final long STAY_MS = 9000L, FADE_MS = 300L;
	/** The chime: vanilla's toast, pitched low and kept quiet (as the experience meter's). */
	static final float CHIME_PITCH = 0.7F, CHIME_VOLUME = 0.5F;
	static final int QUEUE_MAX = 6;

	private record Card(Component title, Component text) {
	}

	private static final Deque<Card> WAITING = new ArrayDeque<>();
	private static @Nullable Card showing;
	/** How long the card has been up (ms), and the clock when it was last drawn. */
	private static long shown, last;

	private TipCard() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, JaymosPowers.id("tip_card"), TipCard::draw);
	}

	/** Queues a card. */
	public static void show(Component title, Component text) {
		if (WAITING.size() < QUEUE_MAX) {
			WAITING.add(new Card(title, text));
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		long dt = Math.min(100L, now - last);
		last = now;
		if (minecraft.player == null) {
			WAITING.clear();
			showing = null;
			return;
		}
		if (showing == null) {
			showing = WAITING.poll();
			if (showing == null) {
				return;
			}
			shown = 0L;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, CHIME_PITCH, CHIME_VOLUME));
		}
		if (minecraft.gui.screen() == null || shown < FADE_MS) {
			shown += dt;
		}
		if (shown >= STAY_MS) {
			showing = null;
			return;
		}
		float fade = Math.min(1.0F, Math.min(shown, STAY_MS - shown) / (float)FADE_MS);
		int alpha = Math.round(255 * fade);
		if (alpha < 8) {
			return;
		}
		Font font = minecraft.font;
		List<FormattedCharSequence> lines = font.split(showing.text(), WIDTH - 2 * PAD);
		int h = PAD + 10 + lines.size() * 9 + PAD - 1;
		int x = graphics.guiWidth() - WIDTH - MARGIN, y = MARGIN - Math.round((1.0F - fade) * 6);
		HudStyle.plate(graphics, x, y, WIDTH, h, fade(HudStyle.PLATE, fade), fade(HudStyle.RIM_DIM, fade));
		graphics.fill(x, y, x + WIDTH, y + 1, fade(HudStyle.GOLD, fade));
		graphics.text(font, showing.title(), x + PAD, y + PAD, fade(HudStyle.GOLD, fade));
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + PAD, y + PAD + 10 + i * 9, fade(HudStyle.TEXT, fade));
		}
	}

	private static int fade(int color, float share) {
		return Math.round((color >>> 24) * share) << 24 | color & 0xFFFFFF;
	}
}
