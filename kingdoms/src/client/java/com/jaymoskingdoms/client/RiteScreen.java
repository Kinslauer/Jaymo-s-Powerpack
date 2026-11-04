package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Rites;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.7.513) The Leaving Rite's small screen (docs/Patrons-B-Plan.md section 5.5), opened by {@link Rites.OpenRite} when a town
 * cleric offers the rite from MCA's Interact page: what it does, the price, Pay or Not now. Names and numbers only.
 */
final class RiteScreen extends Screen {
	private static final int W = 240, H = 132;
	private final Rites.OpenRite offer;
	private final Patron from;
	private final Patron to;

	RiteScreen(Rites.OpenRite offer) {
		super(Component.translatable("gui.jaymoskingdoms.rite.title"));
		this.offer = offer;
		Patron f = Patron.byKey(offer.from());
		Patron t = Patron.byKey(offer.to());
		this.from = f != null ? f : Patron.TRUTH;
		this.to = t != null ? t : Patron.TRUTH;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	@Override
	protected void init() {
		int x = left(), y = top() + H - 24;
		Button pay = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.rite.pay"), b -> {
			ClientPlayNetworking.send(new Rites.TakeRite(this.offer.entityId()));
			onClose();
		}).bounds(x + 10, y, 80, 16).build());
		pay.active = this.offer.canPay();
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.rite.leave"), b -> onClose()).bounds(x + W - 90, y, 80, 16).build());
	}

	private int paragraph(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : this.font.split(text, width)) {
			graphics.text(this.font, line, x, y, color);
			y += Ui.LINE;
		}
		return y;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, H, this.from.colour);
		Ui.line(graphics, this.font, this.title, x + 10, y + 8, W - 20, Ui.GOLD);
		int tx = x + 10, tw = W - 20, ty = y + 22;
		boolean trial = this.offer.daysLeft() >= 0;
		ty = paragraph(graphics, trial
			? Component.translatable("gui.jaymoskingdoms.rite.trial", this.from.named(), this.offer.daysLeft())
			: Component.translatable("gui.jaymoskingdoms.rite.streak", this.from.named(), this.to.named()), tx, ty, tw, Ui.TEXT);
		ty = paragraph(graphics, Component.translatable(trial ? "gui.jaymoskingdoms.rite.after" : "gui.jaymoskingdoms.rite.after_streak", this.offer.daysAfter()), tx, ty + 2, tw, Ui.DIM);
		ty = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.rite.scar"), tx, ty + 2, tw, Ui.HINT);
		ty = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.rite.price", this.offer.price()), tx, ty + 2, tw, Ui.TEXT);
		if (!this.offer.canPay()) {
			paragraph(graphics, Component.translatable("gui.jaymoskingdoms.rite.cannot"), tx, ty, tw, Ui.BAD);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
