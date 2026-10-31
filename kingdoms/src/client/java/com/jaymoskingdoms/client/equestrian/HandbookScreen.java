package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.equestrian.Breed;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * (0.7.305) The horse handbook (the item, or the command wheel): a page each on taming and trust, tack, gaits and
 * stamina, the command wheel and roster, riding extras, breeding, the breeds' gifts and the coach. The words are lang
 * keys ({@code handbook.jaymoskingdoms.<n>.title} and {@code .body}), so they can be changed without code.
 */
final class HandbookScreen extends Screen {
	/** How many pages there are (lang keys 1 to this). */
	static final int PAGES = 11;
	/**
	 * (0.7.445, Havens patch 8) The coat pages, from page {@link #FIRST_COATS} on: each lists these breeds' coats by name,
	 * the rare one in gold, under its own words.
	 */
	private static final int FIRST_COATS = 10;
	private static final Breed[][] COAT_PAGES = {
		{Breed.DESTRIER, Breed.COURSER, Breed.PALFREY},
		{Breed.DRAFT, Breed.STEPPE_PONY, Breed.DESERT_HORSE}};
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
		Component body = Component.translatable("handbook.jaymoskingdoms." + this.page + ".body");
		graphics.textWithWordWrap(this.font, body, x + MARGIN, y + 39, W - 2 * MARGIN, Ui.TEXT, false);
		int at = this.page - FIRST_COATS;
		if (at >= 0 && at < COAT_PAGES.length) {
			int lines = body.getString().isEmpty() ? 0 : this.font.split(body, W - 2 * MARGIN).size();
			coats(graphics, COAT_PAGES[at], x + MARGIN, y + 39 + lines * Ui.LINE + (lines > 0 ? 4 : 0), W - 2 * MARGIN);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Each of {@code breeds} by name, then its coats by name (the last, rare one in gold). */
	private void coats(GuiGraphicsExtractor graphics, Breed[] breeds, int x, int y, int width) {
		for (Breed breed : breeds) {
			Ui.line(graphics, this.font, Component.translatable(breed.key()), x, y, width, Ui.GOLD);
			y += Ui.LINE + 1;
			MutableComponent list = Component.empty();
			for (int i = 0; i < breed.coats.length; i++) {
				if (i > 0) {
					list.append(Component.literal(", ").withColor(Ui.DIM));
				}
				boolean rare = i == breed.coats.length - 1;
				list.append(Component.translatable("coat.jaymoskingdoms." + breed.coats[i])
					.withColor(rare ? EquestrianClient.RARE_COAT_COLOR : EquestrianClient.COAT_COLOR));
			}
			graphics.textWithWordWrap(this.font, list, x + 4, y, width - 4, Ui.TEXT, false);
			y += this.font.split(list, width - 4).size() * Ui.LINE + 4;
		}
	}
}
