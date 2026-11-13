package com.jaymoskingdoms.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A page of lines the server sent (what an identify found), read and closed. */
final class MagicPageScreen extends Screen {
	private static final int W = 240, PAD = 10;
	private final List<Component> lines;
	private final List<FormattedCharSequence> wrapped = new ArrayList<>();

	MagicPageScreen(Component title, List<Component> lines) {
		super(title);
		this.lines = lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int height() {
		return 24 + Math.max(1, this.wrapped.size()) * Ui.LINE + 36;
	}

	@Override
	protected void init() {
		this.wrapped.clear();
		for (Component c : this.lines) {
			this.wrapped.addAll(this.font.split(c, W - 2 * PAD));
		}
		int left = (this.width - W) / 2, top = (this.height - height()) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.magic.close"), b -> onClose()).bounds(left + W / 2 - 40, top + height() - 24, 80, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = (this.width - W) / 2, top = (this.height - height()) / 2;
		Ui.panel(graphics, left, top, W, height(), Ui.GOLD);
		Ui.line(graphics, this.font, this.title, left + PAD, top + 8, W - 2 * PAD, Ui.GOLD);
		int y = top + 24;
		for (FormattedCharSequence line : this.wrapped) {
			graphics.text(this.font, line, left + PAD, y, Ui.TEXT);
			y += Ui.LINE;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
