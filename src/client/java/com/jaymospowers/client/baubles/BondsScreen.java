package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Bonds;
import com.jaymospowers.client.UiStyle;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * What you are bonded to (up to three things): a row each with its picture and name. Click one to let it go, so another
 * can be taken. A clinging curse will not let go.
 */
public class BondsScreen extends Screen {
	private static final int W = 240, ROW_H = 20, ACCENT = 0xFFB89CFF;

	public BondsScreen() {
		super(Component.translatable("screen.jaymospowers.bonds.title"));
	}

	private List<Bonds.Bond> bonds() {
		return this.minecraft != null && this.minecraft.player != null ? Bonds.bonds(this.minecraft.player) : List.of();
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - (UiStyle.HEADER + Bonds.MOST * ROW_H + 24)) / 2);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		List<Bonds.Bond> bonds = bonds();
		for (int i = 0; i < bonds.size(); i++) {
			if (UiStyle.over((int) event.x(), (int) event.y(), left() + 6, top() + UiStyle.HEADER + i * ROW_H, W - 12, ROW_H - 2)) {
				ClientPlayNetworking.send(new Bonds.Unbond(bonds.get(i).id()));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		List<Bonds.Bond> bonds = bonds();
		int x = left(), y = top();
		int h = UiStyle.HEADER + Bonds.MOST * ROW_H + 24;
		UiStyle.panel(graphics, x, y, W, h, ACCENT);
		UiStyle.header(graphics, this.font, x, y, W, bonds.isEmpty() ? null : bonds.getFirst().shown(), this.title,
			Component.translatable("screen.jaymospowers.bonds.sub", bonds.size(), Bonds.MOST), ACCENT, 0);
		for (int i = 0; i < Bonds.MOST; i++) {
			int ry = y + UiStyle.HEADER + i * ROW_H;
			boolean hover = i < bonds.size() && UiStyle.over(mouseX, mouseY, x + 6, ry, W - 12, ROW_H - 2);
			UiStyle.row(graphics, x + 6, ry, x + W - 6, ry + ROW_H - 2, i, hover);
			if (i < bonds.size()) {
				graphics.item(bonds.get(i).shown(), x + 8, ry + 1);
				UiStyle.line(graphics, this.font, bonds.get(i).shown().getHoverName(), x + 28, ry + 5, W - 40, UiStyle.TEXT);
			} else {
				UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.bonds.free"), x + 28, ry + 5, W - 40, UiStyle.DIM);
			}
		}
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.bonds.hint"), x + 8, y + h - 14, W - 16, UiStyle.DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
