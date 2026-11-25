package com.jaymospowers.client.power.web;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.web.WebSymbioteSettings.Setting;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The symbiote's Settings, opened from its Status page ({@link WebSymbioteStatusScreen}): one row per thing that can be
 * switched off (fidgets, jealousy, requests, hints, the edge membrane), each with what it does under its name, and a
 * click anywhere on the row flips it. Kept by {@link WebSymbioteSettingsClient}. Escape goes back to the Status page.
 */
final class WebSymbioteSettingsScreen extends Screen {
	private static final int W = 250, ROW_H = 24, ACCENT = 0xFF8A5AB0, SWITCH_W = 30;

	private final Screen parent;

	WebSymbioteSettingsScreen(Screen parent) {
		super(Component.translatable("screen.jaymospowers.web_symbiote_settings"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		// no blur: the world stays in view
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private int left() {
		return (width - W) / 2;
	}

	private int height() {
		return UiStyle.HEADER + Setting.values().length * ROW_H + 22;
	}

	private int top() {
		return Math.max(6, (height - height()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + i * ROW_H;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		for (int i = 0; i < Setting.values().length; i++) {
			if (UiStyle.over((int)event.x(), (int)event.y(), left() + 4, rowY(i), W - 8, ROW_H - 2)) {
				WebSymbioteSettingsClient.toggle(Setting.values()[i]);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		int x = left();
		int y = top();
		UiStyle.panel(graphics, x, y, W, height(), ACCENT);
		UiStyle.header(graphics, font, x, y, W, null, title, Component.translatable("gui.jaymospowers.web_symbiote.settings.subtitle"), ACCENT, 0);
		Setting[] all = Setting.values();
		for (int i = 0; i < all.length; i++) {
			String id = all[i].name().toLowerCase(Locale.ROOT);
			int ry = rowY(i);
			boolean over = UiStyle.over(mouseX, mouseY, x + 4, ry, W - 8, ROW_H - 2);
			boolean off = WebSymbioteSettingsClient.isOff(all[i]);
			UiStyle.row(graphics, x + 4, ry, x + W - 4, ry + ROW_H - 2, i, over);
			int textW = W - 20 - SWITCH_W - 8;
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.settings." + id), x + 10, ry + 3, textW, UiStyle.TEXT);
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.settings." + id + ".tip"), x + 10, ry + 13, textW, UiStyle.HINT);
			int sx = x + W - 10 - SWITCH_W;
			graphics.fill(sx, ry + 5, sx + SWITCH_W, ry + 17, off ? UiStyle.ROW_ALT : UiStyle.ROW_PICKED);
			graphics.outline(sx, ry + 5, SWITCH_W, 12, off ? 0x40FFFFFF : ACCENT);
			Component state = Component.translatable("gui.jaymospowers.web_symbiote.settings." + (off ? "off" : "on"));
			UiStyle.lineCentred(graphics, font, state, sx + SWITCH_W / 2, ry + 7, SWITCH_W - 2, off ? UiStyle.HINT : UiStyle.TEXT);
		}
		UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.settings.back"), x + W - 8, y + height() - 12, 80, UiStyle.HINT);
	}
}
