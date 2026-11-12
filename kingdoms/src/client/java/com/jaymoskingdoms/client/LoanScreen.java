package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Loans;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hiring someone else's companion, or parting with one ({@link Loans}, docs/Pawn-Plan.md rows A1, A5, A6). Hire: who
 * they are, whose, their leanings, what they've learned, their standing, and the price to fight or to work. Parting:
 * a word for them to take home, and the gift in your other hand if you like.
 */
final class LoanScreen extends Screen {
	private static final int W = 260;

	private final Loans.OpenLoan ask;
	private int word = 3;
	private boolean gift;

	LoanScreen(Loans.OpenLoan ask) {
		super(Component.translatable(ask.mode() == 0 ? "gui.jaymoskingdoms.loan.title" : "gui.jaymoskingdoms.loan.part_title", ask.name()));
		this.ask = ask;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Loans.OpenLoan.TYPE, (payload, context) -> context.client().gui.setScreen(new LoanScreen(payload)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}

	private boolean hire() {
		return this.ask.mode() == 0;
	}

	private boolean hasGift() {
		return !this.ask.gift().getString().isEmpty();
	}

	private int height() {
		return hire() ? 128 : hasGift() ? 128 : 106;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - height()) / 2;
	}

	private static Component leaning(String key) {
		return Component.translatable("gui.jaymoskingdoms.table.leaning." + key);
	}

	private Component wordLabel() {
		return Component.translatable("gui.jaymoskingdoms.loan.word." + this.word);
	}

	private Component giftLabel() {
		return this.gift ? Component.translatable("gui.jaymoskingdoms.loan.gift.yes", this.ask.gift()) : Component.translatable("gui.jaymoskingdoms.loan.gift.no");
	}

	@Override
	protected void init() {
		int x = left(), y = top(), by = y + height() - 24;
		if (hire()) {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.loan.fight", this.ask.fight()), b -> {
				ClientPlayNetworking.send(new Loans.LoanAct(this.ask.entityId(), 0, 0, false));
				onClose();
			}).bounds(x + 10, by - 22, (W - 30) / 2, 16).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.loan.work", this.ask.work()), b -> {
				ClientPlayNetworking.send(new Loans.LoanAct(this.ask.entityId(), 1, 0, false));
				onClose();
			}).bounds(x + 20 + (W - 30) / 2, by - 22, (W - 30) / 2, 16).build());
		} else {
			addRenderableWidget(Button.builder(wordLabel(), b -> {
				this.word = (this.word + 1) % 4;
				b.setMessage(wordLabel());
			}).bounds(x + W - 140, y + 46, 130, 16).build());
			if (hasGift()) {
				addRenderableWidget(Button.builder(giftLabel(), b -> {
					this.gift = !this.gift;
					b.setMessage(giftLabel());
				}).bounds(x + W - 140, y + 68, 130, 16).build());
			}
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.loan.send"), b -> {
				ClientPlayNetworking.send(new Loans.LoanAct(this.ask.entityId(), 2, this.word, this.gift));
				onClose();
			}).bounds(x + 10, by, 90, 16).build());
		}
		addRenderableWidget(Button.builder(Component.translatable(hire() ? "gui.jaymoskingdoms.table.leave" : "gui.jaymoskingdoms.loan.later"),
			b -> onClose()).bounds(x + W - 100, by, 90, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, height(), Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 10, y + 8, W - 20, Ui.GOLD);
		if (hire()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.whose", this.ask.owner()), x + 10, y + 22, W - 20, Ui.DIM);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.leanings", leaning(this.ask.main()), leaning(this.ask.second())),
				x + 10, y + 36, W - 20, Ui.TEXT);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.learned", this.ask.steps()), x + 10, y + 48, W - 20, Ui.TEXT);
			Ui.line(graphics, this.font, Component.translatable(this.ask.rating() >= 6 ? "gui.jaymoskingdoms.loan.rating.high"
				: this.ask.rating() >= 2 ? "gui.jaymoskingdoms.loan.rating.some" : "gui.jaymoskingdoms.loan.rating.none", this.ask.rating()),
				x + 10, y + 60, W - 20, Ui.TEXT);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.note"), x + 10, y + 74, W - 20, Ui.DIM);
			if (!this.ask.want().getString().isEmpty()) {
				Ui.line(graphics, this.font, this.ask.want(), x + 10, y + 86, W - 20, Ui.GOLD);
			}
		} else {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.part_note", this.ask.owner()), x + 10, y + 24, W - 20, Ui.DIM);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.ask.word"), x + 10, y + 50, W - 150, Ui.TEXT);
			if (hasGift()) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.loan.ask.gift"), x + 10, y + 72, W - 150, Ui.TEXT);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
