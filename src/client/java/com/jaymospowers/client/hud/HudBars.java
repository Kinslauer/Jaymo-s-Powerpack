package com.jaymospowers.client.hud;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.HeldInput;
import com.jaymospowers.core.HudBar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws the bars the server sends ({@link HudBar}) in the execution bars' look ({@code FinisherClient.bars}): black frames,
 * a faint track, a fill that brightens once full. Holds, marks and contests go under the crosshair (the {@code BELOW} zone,
 * under the execution bars), gauges over the hotbar (the {@code FOCUS} zone, nearest the action-bar line).
 * <p>A bar goes once it stops being sent: a hold a little after its last word (how long depends on how often it came), a
 * gauge, mark count or contest after {@link #SHOWN_TICKS}, as an action-bar line used to.
 */
public final class HudBars {
	/** Holds: three bars, each this wide (gui pixels), this tall, this far apart (the execution bars' sizes). */
	static final int HOLD_BARS = 3, HOLD_W = 14, BAR_H = 3, GAP = 3;
	/** Gauges: five segments, each this wide. */
	static final int GAUGE_SEGS = 5, GAUGE_W = 10;
	/** Marks: each this big, this far from the next. */
	static final int MARK = 5, MARK_PITCH = 6;
	/** A contest's bar: this wide. */
	static final int TUG_W = 60;
	/** Gauges, marks and contests stay this long after their last word (ticks); a hold its gap between words and a bit. */
	static final int SHOWN_TICKS = 60, HOLD_LEAST = 6, HOLD_FIRST = 25, HOLD_MOST = 45;
	static final int BACK = 0x90000000, EMPTY = 0x60FFFFFF, CAPTION = 0xFFE0E0E0;
	/** {filling, full} for each {@link HudBar} tint. */
	static final int[][] TINTS = {
		{0xFFB02020, 0xFFFF4040}, {0xFF8A8A8A, 0xFFE0E0E0}, {0xFFB07A10, 0xFFFFC030}, {0xFF7A2FA0, 0xFFC070FF},
		{0xFF1F7F9A, 0xFF50D8FF}, {0xFF2F8A2F, 0xFF60E060}, {0xFF701010, 0xFFC02828}, {0xFFA08A10, 0xFFFFE040}};
	/** The other side of a contest. */
	static final int THEIRS = 0xFFC02828, YOU = 0xFFFFC030, THEM_TEXT = 0xFFE05050;

	private static final class Bar {
		int style, at, gap = -1, until;
		float fill, shown, lastShown;
		@Nullable Component caption, number;

		int kind() {
			return style & 7;
		}

		int[] tint() {
			return TINTS[Math.min(TINTS.length - 1, style >> 3 & 15)];
		}

		int count() {
			return style >> 7 & 255;
		}
	}

	private static final Map<String, Bar> BARS = new LinkedHashMap<>();
	private static int ticks;

	private HudBars() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(HudBar.Show.TYPE, (payload, context) ->
			show(payload.id(), payload.style(), payload.fill(), payload.caption().orElse(null), payload.number().orElse(null)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BARS.clear());
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("hud_bars"), HudBars::draw);
	}

	/** Shows or refreshes a bar (from the server, or one timed on this side). */
	public static void show(String id, int style, float fill, @Nullable Component caption, @Nullable Component number) {
		Bar bar = BARS.get(id);
		if (bar == null) {
			bar = new Bar();
			bar.shown = bar.lastShown = fill;
			BARS.put(id, bar);
		} else {
			bar.gap = ticks - bar.at;
		}
		bar.style = style;
		bar.fill = fill;
		bar.caption = caption;
		bar.number = number;
		bar.at = ticks;
		int kind = style & 7;
		bar.until = ticks + (kind != HudBar.HOLD ? SHOWN_TICKS : bar.gap < 0 ? HOLD_FIRST : Mth.clamp(bar.gap * 3 / 2 + 3, HOLD_LEAST, HOLD_MOST));
		if ((style >> 15 & 1) != 0) {
			HeldInput.bar();
		}
	}

	/** Takes a bar down at once (one timed on this side, let go). */
	public static void clear(String id) {
		BARS.remove(id);
	}

	private static void tick() {
		ticks++;
		for (Iterator<Bar> it = BARS.values().iterator(); it.hasNext(); ) {
			Bar bar = it.next();
			if (ticks > bar.until) {
				it.remove();
				continue;
			}
			bar.lastShown = bar.shown;
			// (eases to the new fill; a fresh start drops straight back)
			bar.shown = bar.fill < bar.shown - 0.25F ? bar.fill : bar.shown + (bar.fill - bar.shown) * 0.5F;
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (BARS.isEmpty() || minecraft.player == null || minecraft.player.isSpectator()) {
			return;
		}
		Font font = minecraft.font;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		for (Map.Entry<String, Bar> entry : BARS.entrySet()) {
			Bar bar = entry.getValue();
			float fill = Mth.clamp(Mth.lerp(partial, bar.lastShown, bar.shown), 0.0F, 1.0F);
			String id = "jaymospowers:bar/" + entry.getKey();
			switch (bar.kind()) {
				case HudBar.GAUGE -> gauge(graphics, font, id, bar, fill);
				case HudBar.MARKS -> marks(graphics, font, id, bar);
				case HudBar.TUG -> tug(graphics, font, id, bar, fill);
				default -> hold(graphics, font, id, bar, fill);
			}
		}
	}

	/** Bars of {@code w} each, filling in turn, each bright once full; the frame round each. */
	private static void segments(GuiGraphicsExtractor graphics, int x, int y, int n, int w, float fill, int[] tint) {
		float per = 1.0F / n;
		for (int i = 0; i < n; i++, x += w + GAP) {
			float share = Mth.clamp((fill - i * per) / per, 0.0F, 1.0F);
			graphics.fill(x - 1, y - 1, x + w + 1, y + BAR_H + 1, BACK);
			graphics.fill(x, y, x + w, y + BAR_H, EMPTY);
			int filled = Math.round(w * share);
			if (filled > 0) {
				graphics.fill(x, y, x + filled, y + BAR_H, share >= 1.0F ? tint[1] : tint[0]);
			}
		}
	}

	private static void hold(GuiGraphicsExtractor graphics, Font font, String id, Bar bar, float fill) {
		int bars = HOLD_BARS * HOLD_W + (HOLD_BARS - 1) * GAP;
		int w = Math.max(bars + 2, bar.caption == null ? 0 : font.width(bar.caption));
		int[] at = HudZones.place(HudZones.BELOW, id, 21, w, BAR_H + 2 + (bar.caption == null ? 0 : 11));
		if (at == null) {
			return;
		}
		int cx = at[0] + w / 2;
		segments(graphics, cx - bars / 2, at[1] + 1, HOLD_BARS, HOLD_W, fill, bar.tint());
		if (bar.caption != null) {
			HudStyle.centred(graphics, font, bar.caption, cx, at[1] + BAR_H + 4, CAPTION);
		}
	}

	private static void gauge(GuiGraphicsExtractor graphics, Font font, String id, Bar bar, float fill) {
		int segs = GAUGE_SEGS * GAUGE_W + (GAUGE_SEGS - 1) * GAP;
		int left = bar.caption == null ? 0 : font.width(bar.caption) + 4, right = bar.number == null ? 0 : font.width(bar.number) + 4;
		int[] at = HudZones.place(HudZones.FOCUS, id, 5, left + segs + 2 + right, 9);
		if (at == null) {
			return;
		}
		if (bar.caption != null) {
			HudStyle.text(graphics, font, bar.caption, at[0], at[1], CAPTION);
		}
		segments(graphics, at[0] + left + 1, at[1] + 3, GAUGE_SEGS, GAUGE_W, fill, bar.tint());
		if (bar.number != null) {
			HudStyle.text(graphics, font, bar.number, at[0] + left + segs + 2 + 4, at[1], CAPTION);
		}
	}

	private static void marks(GuiGraphicsExtractor graphics, Font font, String id, Bar bar) {
		int n = Math.max(1, bar.count()), filled = Math.round(bar.fill * n);
		int row = n * MARK_PITCH - 1;
		int w = Math.max(row, bar.caption == null ? 0 : font.width(bar.caption));
		int[] at = HudZones.place(HudZones.BELOW, id, 21, w, MARK + (bar.caption == null ? 0 : 11));
		if (at == null) {
			return;
		}
		int cx = at[0] + w / 2, x = cx - row / 2;
		// (like the chop meter: gold as they go in, red once the last is in)
		int on = filled >= n ? TINTS[HudBar.RED][1] : bar.tint()[1];
		for (int i = 0; i < n; i++, x += MARK_PITCH) {
			graphics.fill(x, at[1], x + MARK, at[1] + MARK, BACK);
			graphics.fill(x + 1, at[1] + 1, x + MARK - 1, at[1] + MARK - 1, i < filled ? on : 0x33FFFFFF);
		}
		if (bar.caption != null) {
			HudStyle.centred(graphics, font, bar.caption, cx, at[1] + MARK + 3, CAPTION);
		}
	}

	private static void tug(GuiGraphicsExtractor graphics, Font font, String id, Bar bar, float lead) {
		Component you = Component.translatable("hud.jaymospowers.bar.you");
		int left = font.width(you) + 4, right = bar.caption == null ? 0 : font.width(bar.caption) + 4;
		int[] at = HudZones.place(HudZones.BELOW, id, 21, left + TUG_W + 2 + right, 9);
		if (at == null) {
			return;
		}
		HudStyle.text(graphics, font, you, at[0], at[1], YOU);
		int x = at[0] + left + 1, y = at[1] + 3, mine = Math.round(TUG_W * lead);
		graphics.fill(x - 1, y - 1, x + TUG_W + 1, y + BAR_H + 1, BACK);
		graphics.fill(x, y, x + mine, y + BAR_H, YOU);
		graphics.fill(x + mine, y, x + TUG_W, y + BAR_H, THEIRS);
		// (the even mark)
		graphics.fill(x + TUG_W / 2, y - 2, x + TUG_W / 2 + 1, y + BAR_H + 2, 0xFFFFFFFF);
		if (bar.caption != null) {
			HudStyle.text(graphics, font, bar.caption, x + TUG_W + 1 + 4, at[1], THEM_TEXT);
		}
	}
}
