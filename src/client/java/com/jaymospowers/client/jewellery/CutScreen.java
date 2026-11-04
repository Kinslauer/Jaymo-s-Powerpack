package com.jaymospowers.client.jewellery;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.jewellery.Cut;
import com.jaymospowers.jewellery.CutGemItem;
import com.jaymospowers.jewellery.Gems;
import com.jaymospowers.jewellery.JewelleryNet;
import com.jaymospowers.tinkering.smithing.Grade;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * (0.19.87) The cuts on offer for the gem on the wheel: a striped row per cut (the gem cut that way, its name, its
 * strokes and its bonus factor). Click one to start cutting; Esc leaves.
 */
public class CutScreen extends Screen {
	private static final int MAX_W = 300, ROW_H = 20, ACCENT = 0xFFB070FF;

	private final BlockPos pos;
	private final ItemStack gem;
	private final List<JewelleryNet.CutChoice> cuts;

	public CutScreen(BlockPos pos, ItemStack gem, List<JewelleryNet.CutChoice> cuts) {
		super(Component.translatable("screen.jaymospowers.jewellery.cuts"));
		this.pos = pos;
		this.gem = gem;
		this.cuts = cuts;
	}

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int panelH() {
		return UiStyle.HEADER + cuts.size() * ROW_H + 8;
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (height - panelH()) / 2);
	}

	private int at(double mouseX, double mouseY) {
		int x = left() + 6, y = top() + UiStyle.HEADER;
		for (int i = 0; i < cuts.size(); i++) {
			if (UiStyle.over((int) mouseX, (int) mouseY, x, y + i * ROW_H, panelW() - 14, ROW_H - 2)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = at(event.x(), event.y());
		if (i >= 0) {
			ClientPlayNetworking.send(new JewelleryNet.ChooseCut(pos, cuts.get(i).id()));
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private ItemStack icon(Cut cut) {
		Gems.Gem type = Gems.of(gem);
		return type == null ? ItemStack.EMPTY : CutGemItem.of(type, cut, Grade.WELL, 0);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, font, x, y, w, gem, title, gem.getHoverName(), ACCENT, 0);
		int hovered = at(mouseX, mouseY);
		int rowsY = y + UiStyle.HEADER;
		for (int i = 0; i < cuts.size(); i++) {
			JewelleryNet.CutChoice choice = cuts.get(i);
			Cut cut = Cut.byId(choice.id());
			int ry = rowsY + i * ROW_H;
			UiStyle.row(graphics, x + 6, ry, x + w - 8, ry + ROW_H - 2, i, i == hovered);
			graphics.item(icon(cut), x + 8, ry + 1);
			Component strokes = Component.translatable("screen.jaymospowers.jewellery.strokes", choice.strokes());
			Component bonus = Component.translatable("screen.jaymospowers.jewellery.bonus", String.format(Locale.ROOT, "%.2f", cut.bonus));
			int bonusW = font.width(bonus), strokesW = font.width(strokes);
			UiStyle.line(graphics, font, cut.displayName(), x + 28, ry + 5, w - 44 - bonusW - strokesW - 16, UiStyle.TEXT);
			UiStyle.lineRight(graphics, font, strokes, x + w - 14 - bonusW - 8, ry + 5, strokesW, UiStyle.DIM);
			UiStyle.lineRight(graphics, font, bonus, x + w - 12, ry + 5, bonusW, UiStyle.DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
