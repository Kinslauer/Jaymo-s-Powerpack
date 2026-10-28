package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.7.305) The horse handbook (the item, or the command wheel): a page each on taming and trust, tack, gaits and
 * stamina, the command wheel and roster, riding extras, breeding, the breeds' gifts and the coach. The words are lang
 * keys ({@code handbook.jaymoskingdoms.<n>.title} and {@code .body}), so they can be changed without code.
 */
final class HandbookScreen extends Screen {
	/** How many pages there are (lang keys 1 to this). */
	static final int PAGES = 9;
	private static final int W = 240, H = 200, MARGIN = 10;
	private int page = 1;
	private Button back, next;

	HandbookScreen() {
		super(Component.translatable("item.jaymoskingdoms.horse_handbook"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	@Override
	protected void init() {
		int x = left(), y = top() + H - 22;
		this.back = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(x + MARGIN, y, 20, 16).build());
		this.next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(x + W - MARGIN - 20, y, 20, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W / 2 - 28, y, 56, 16).build());
		turn(0);
	}

	private void turn(int by) {
		this.page = Math.max(1, Math.min(PAGES, this.page + by));
		this.back.active = this.page > 1;
		this.next.active = this.page < PAGES;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		turn(scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		graphics.text(this.font, this.title, x + MARGIN, y + 8, Ui.DIM, false);
		String count = this.page + " / " + PAGES;
		graphics.text(this.font, count, x + W - MARGIN - this.font.width(count), y + 8, Ui.HINT, false);
		Ui.line(graphics, this.font, Component.translatable("handbook.jaymoskingdoms." + this.page + ".title"), x + MARGIN, y + 22, W - 2 * MARGIN,
			Ui.GOLD);
		Ui.rule(graphics, x + 4, x + W - 4, y + 33);
		graphics.textWithWordWrap(this.font, Component.translatable("handbook.jaymoskingdoms." + this.page + ".body"), x + MARGIN, y + 39,
			W - 2 * MARGIN, Ui.TEXT, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
