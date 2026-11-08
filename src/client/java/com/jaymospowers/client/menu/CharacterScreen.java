package com.jaymospowers.client.menu;

import com.jaymospowers.api.Power;
import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (0.21.60) The character menu's first page ({@link MenuTabs}): you, turning to follow the mouse, and a sheet of who you
 * are: your power, its fate and mastery, health, armour and level; then whatever Jaymo's Kingdoms adds (heritage, class,
 * career, renown, skills, patron) when it is loaded. Rows that don't fit scroll with the wheel.
 */
public class CharacterScreen extends Screen {
	static final int W = 264, H = 196;
	/** The figure's box (left), the sheet's left edge and a row's height. */
	static final int FIGURE_X = 8, FIGURE_Y = 20, FIGURE_W = 76, FIGURE_H = 120, FIGURE_SIZE = 42, SHEET_X = 92, ROW = 11, HEADING = 14;
	/** The sheet: from below the title to the bottom edge, inset. */
	static final int SHEET_Y = 20, SHEET_BOTTOM = 8;

	/** A row of the sheet: a label and a value, a heading (label only), or a bar (value null, share 0-1). */
	private record Row(Component label, Component value, float share, int color) {
	}

	private int scroll;

	public CharacterScreen() {
		super(Component.translatable("menu.jaymospowers.tab.character"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	/** The window's top-left corner, where the menu's tabs hang. */
	int[] corner() {
		return new int[] {left(), top()};
	}

	private List<Row> rows(LocalPlayer player) {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(Component.translatable("menu.jaymospowers.character.powers"), null, -1.0F, PowerScreen.COLOR_GOLD));
		Power power = Powers.of(player);
		rows.add(new Row(Component.translatable("menu.jaymospowers.character.power"),
			power == null ? Component.translatable("menu.jaymospowers.character.no_power") : power.name(), -1.0F, PowerScreen.COLOR_TEXT));
		if (power != null) {
			Component fate = Component.translatable(Fate.isAscended(player)
				? Fate.isCorrupted(player) ? "screen.jaymospowers.badge.ascended_corrupted" : "screen.jaymospowers.badge.ascended"
				: Fate.isCorrupted(player) ? "screen.jaymospowers.badge.corrupted" : "screen.jaymospowers.badge.mortal");
			rows.add(new Row(Component.translatable("menu.jaymospowers.character.fate"), fate, -1.0F, PowerScreen.COLOR_TEXT));
			if (!player.isCreative()) {
				rows.add(new Row(Component.translatable("menu.jaymospowers.character.mastery"),
					Component.translatable("screen.jaymospowers.mastery.count", Mastery.unlocked(player), Mastery.ALL), -1.0F, PowerScreen.COLOR_TEXT));
				float progress = Mastery.progress(player);
				if (progress >= 0.0F) {
					rows.add(new Row(Component.empty(), null, progress, 0xFF6AA8FF));
				}
			}
		}
		rows.add(new Row(Component.translatable("menu.jaymospowers.character.health"),
			Component.translatable("menu.jaymospowers.character.of", Mth.ceil(player.getHealth()), Mth.ceil(player.getMaxHealth())), -1.0F, PowerScreen.COLOR_TEXT));
		rows.add(new Row(Component.empty(), null, player.getMaxHealth() <= 0.0F ? 0.0F : player.getHealth() / player.getMaxHealth(), 0xFFD84040));
		rows.add(new Row(Component.translatable("menu.jaymospowers.character.armour"), Component.literal(Integer.toString(player.getArmorValue())), -1.0F,
			PowerScreen.COLOR_TEXT));
		rows.add(new Row(Component.translatable("menu.jaymospowers.character.level"), Component.literal(Integer.toString(player.experienceLevel)), -1.0F,
			PowerScreen.COLOR_TEXT));
		for (List<Component[]> section : MenuTabs.characterRows()) {
			for (Component[] r : section) {
				if (r.length == 1) {
					rows.add(new Row(r[0], null, -1.0F, PowerScreen.COLOR_GOLD));
				} else if (r.length >= 2) {
					rows.add(new Row(r[0], r[1], -1.0F, PowerScreen.COLOR_TEXT));
				}
			}
		}
		return rows;
	}

	private static int height(Row row) {
		return row.value() == null && row.share() < 0.0F ? HEADING : row.value() == null ? 7 : ROW;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll = Math.max(0, this.scroll - (int)Math.signum(scrollY) * ROW);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		int x = left(), y = top();
		frame(graphics, x, y, W, H);
		graphics.text(this.font, this.title, x + 8, y + 6, PowerScreen.COLOR_TEXT, false);
		if (player == null) {
			return;
		}
		// you
		int fx = x + FIGURE_X, fy = y + FIGURE_Y;
		recess(graphics, fx, fy, FIGURE_W, FIGURE_H);
		InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, fx + 2, fy + 2, fx + FIGURE_W - 2, fy + FIGURE_H - 2, FIGURE_SIZE, 0.0625F, mouseX,
			mouseY, player);
		UiStyle.lineCentred(graphics, this.font, player.getDisplayName(), fx + FIGURE_W / 2, fy + FIGURE_H + 6, FIGURE_W + 4, PowerScreen.COLOR_TEXT);
		// the sheet
		int sx = x + SHEET_X, sw = W - SHEET_X - 8, top = y + SHEET_Y, bottom = y + H - SHEET_BOTTOM;
		recess(graphics, sx, top, sw, bottom - top);
		List<Row> rows = rows(player);
		int total = 0;
		for (Row r : rows) {
			total += height(r);
		}
		int view = bottom - top - 6;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - view));
		graphics.enableScissor(sx + 1, top + 1, sx + sw - 1, bottom - 1);
		int ry = top + 4 - this.scroll;
		int inner = sw - 10;
		for (Row r : rows) {
			int h = height(r);
			if (ry + h >= top && ry <= bottom) {
				if (r.value() == null && r.share() >= 0.0F) {
					UiStyle.bar(graphics, sx + 4, ry, inner, 3, Mth.clamp(r.share(), 0.0F, 1.0F), r.color());
				} else if (r.value() == null) {
					UiStyle.line(graphics, this.font, r.label(), sx + 4, ry + 3, inner, r.color());
				} else {
					int labelW = this.font.width(r.label());
					UiStyle.line(graphics, this.font, r.label(), sx + 4, ry, Math.min(labelW, inner / 2), PowerScreen.COLOR_DIM);
					UiStyle.lineRight(graphics, this.font, r.value(), sx + 4 + inner, ry, inner - Math.min(labelW, inner / 2) - 6, r.color());
				}
			}
			ry += h;
		}
		graphics.disableScissor();
		if (total > view) {
			UiStyle.scrollbar(graphics, sx + sw - 4, top + 3, bottom - top - 6, view, total, this.scroll);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The H screen's raised window. */
	static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, PowerScreen.COLOR_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PowerScreen.COLOR_SHADE);
		graphics.fill(x + 1, y + 1, x + w - 3, y + h - 3, PowerScreen.COLOR_LIGHT);
		graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, PowerScreen.COLOR_BODY);
	}

	/** The H screen's sunken area. */
	static void recess(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, PowerScreen.COLOR_WELL_LIGHT);
		graphics.fill(x, y, x + w - 1, y + h - 1, PowerScreen.COLOR_SHADE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PowerScreen.COLOR_CANVAS);
	}
}
