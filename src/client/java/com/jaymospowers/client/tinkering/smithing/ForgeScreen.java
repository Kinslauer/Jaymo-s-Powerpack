package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.tinkering.smithing.SmithingNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (Smithing) What to hammer the hot ingots on the anvil into: a striped row per choice (its icon, name and the ingots
 * it takes), cheapest first. Click one to start forging it.
 */
public class ForgeScreen extends Screen {
	private static final int MAX_W = 300, ROW_H = 20, ACCENT = 0xFFFF8A3D;

	private final BlockPos pos;
	private final List<SmithingNet.Choice> choices;
	private int scroll;

	public ForgeScreen(BlockPos pos, List<SmithingNet.Choice> choices) {
		super(Component.translatable("screen.jaymospowers.forge.title"));
		this.pos = pos;
		this.choices = choices;
	}

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int visibleRows() {
		return Math.max(1, Math.min(choices.size(), (height - 16 - UiStyle.HEADER - 8) / ROW_H));
	}

	private int panelH() {
		return UiStyle.HEADER + visibleRows() * ROW_H + 8;
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (height - panelH()) / 2);
	}

	private int maxScroll() {
		return Math.max(0, choices.size() - visibleRows());
	}

	/** The row under the mouse, or -1. */
	private int at(double mouseX, double mouseY) {
		int x = left() + 6, y = top() + UiStyle.HEADER;
		for (int row = 0; row < visibleRows(); row++) {
			int i = row + scroll;
			if (i < choices.size() && UiStyle.over((int) mouseX, (int) mouseY, x, y + row * ROW_H, panelW() - 14, ROW_H - 2)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = at(event.x(), event.y());
		if (i >= 0) {
			ClientPlayNetworking.send(new SmithingNet.Choose(pos, choices.get(i).id()));
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		scroll = Mth.clamp(scroll, 0, maxScroll());
		int x = left(), y = top(), w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, font, x, y, w, choices.isEmpty() ? null : choices.getFirst().icon(), title,
			Component.translatable("screen.jaymospowers.forge.sub"), ACCENT, 0);
		int hovered = at(mouseX, mouseY);
		int rowsY = y + UiStyle.HEADER;
		for (int row = 0; row < visibleRows(); row++) {
			int i = row + scroll;
			if (i >= choices.size()) {
				break;
			}
			SmithingNet.Choice choice = choices.get(i);
			int ry = rowsY + row * ROW_H;
			UiStyle.row(graphics, x + 6, ry, x + w - 8, ry + ROW_H - 2, i, i == hovered);
			graphics.item(choice.icon(), x + 8, ry + 1);
			Component cost = Component.translatable("screen.jaymospowers.forge.ingots", choice.ingots());
			int costW = font.width(cost);
			UiStyle.line(graphics, font, choice.icon().getHoverName(), x + 28, ry + 5, w - 44 - costW - 6, UiStyle.TEXT);
			UiStyle.lineRight(graphics, font, cost, x + w - 12, ry + 5, costW, UiStyle.DIM);
		}
		UiStyle.scrollbar(graphics, x + w - 4, rowsY, visibleRows() * ROW_H, visibleRows(), choices.size(), scroll);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
