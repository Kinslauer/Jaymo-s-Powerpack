package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ChalkMarks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Chalk in hand, against a wall: which sign to draw ({@link ChalkMarks}). Someone the Underworld has not let in knows no
 * signs, and can only scribble.
 */
final class ChalkScreen extends Screen {
	static final int W = 220, PAD = 10, BUTTON_H = 20, GAP = 4;
	private final BlockPos pos;
	private final int face;
	private final boolean reads;

	ChalkScreen(BlockPos pos, int face, boolean reads) {
		super(Component.translatable("chalk.jaymoskingdoms.title"));
		this.pos = pos;
		this.face = face;
		this.reads = reads;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int count() {
		return this.reads ? ChalkMarks.NAMES.size() : 1;
	}

	private int top() {
		return Math.max(4, (this.height - (34 + this.count() * (BUTTON_H + GAP) + PAD)) / 2);
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2, y = top() + 30;
		for (int i = 0; i < count(); i++) {
			int glyph = this.reads ? i : ChalkMarks.SCRIBBLE;
			String name = ChalkMarks.NAMES.get(glyph);
			addRenderableWidget(Button.builder(Component.translatable("chalk.jaymoskingdoms.pick." + name), b -> {
				ClientPlayNetworking.send(new ChalkMarks.Place(this.pos, this.face, glyph));
				onClose();
			}).bounds(x + PAD, y, W - PAD * 2, BUTTON_H).build());
			y += BUTTON_H + GAP;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = (this.width - W) / 2, y = top();
		Ui.panel(g, x, y, W, 34 + count() * (BUTTON_H + GAP) + PAD, Ui.GOLD);
		Ui.line(g, this.font, this.title, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		Ui.rule(g, x + 6, x + W - 6, y + 19);
		if (!this.reads) {
			g.text(this.font, Component.translatable("chalk.jaymoskingdoms.unlettered").getString(), x + PAD, y + 22, Ui.DIM);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
