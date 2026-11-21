package com.jaymospowers.client.menu;

import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.UiStyle;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The menu's Notices page ({@link MenuTabs}): the last notices ({@link NoticeLog}), newest first, each with the time it
 * came, wrapped in full and scrolled with the wheel.
 */
public class NoticesScreen extends Screen {
	static final int W = 300, H = 196;
	/** The time column's width, a row's padding and the wheel's step (px). */
	static final int TIME_W = 28, PAD = 3, STEP = 12;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private int scroll;

	public NoticesScreen() {
		super(Component.translatable("menu.jaymospowers.tab.notices"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return MenuTabs.centreX(this.width, W);
	}

	private int top() {
		return Math.max(MenuTabs.ROOM, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	/** The room the list has: its top and its height. */
	private int listTop() {
		return top() + 26;
	}

	private int listHeight() {
		return H - 26 - 8;
	}

	private int textW() {
		return W - 16 - TIME_W - 8;
	}

	private int rowHeight(int lines) {
		return lines * UiStyle.LINE + 2 * PAD;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= (int)Math.signum(scrollY) * STEP;
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		UiStyle.window(graphics, x, y, W, H);
		UiStyle.line(graphics, this.font, Component.translatable("menu.jaymospowers.notices.title"), x + 8, y + 8, W - 16, PowerScreen.COLOR_GOLD);
		UiStyle.rule(graphics, x + 6, x + W - 6, y + 20);
		UiStyle.recess(graphics, x + 6, listTop() - 2, W - 12, listHeight() + 4);
		List<NoticeLog.Notice> notices = NoticeLog.all();
		if (notices.isEmpty()) {
			UiStyle.wrapped(graphics, this.font, Component.translatable("menu.jaymospowers.notices.none"), x + 12, listTop() + 4, W - 24, 6, UiStyle.LINE,
				PowerScreen.COLOR_DIM, false);
		}
		int total = 0;
		for (NoticeLog.Notice n : notices) {
			total += rowHeight(this.font.split(n.text(), textW()).size());
		}
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - listHeight()));
		graphics.enableScissor(x + 7, listTop() - 1, x + W - 7, listTop() + listHeight() + 1);
		int ry = listTop() - this.scroll, index = 0;
		for (NoticeLog.Notice n : notices) {
			List<FormattedCharSequence> lines = this.font.split(n.text(), textW());
			int h = rowHeight(lines.size());
			if (ry + h > listTop() && ry < listTop() + listHeight()) {
				UiStyle.row(graphics, x + 8, ry, x + W - 10, ry + h - 1, index, false);
				graphics.text(this.font, CLOCK.format(Instant.ofEpochMilli(n.at())), x + 12, ry + PAD, PowerScreen.COLOR_DIM);
				for (int i = 0; i < lines.size(); i++) {
					graphics.text(this.font, lines.get(i), x + 12 + TIME_W + 4, ry + PAD + i * UiStyle.LINE, PowerScreen.COLOR_TEXT);
				}
			}
			ry += h;
			index++;
		}
		graphics.disableScissor();
		UiStyle.scrollbar(graphics, x + W - 9, listTop(), listHeight(), listHeight(), total, this.scroll);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
