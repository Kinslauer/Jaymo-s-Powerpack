package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DailyTasks;
import com.jaymoskingdoms.money.Money;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The H menu's Daily task page (World): today's small task, a bar of how far along it is, the time until the day turns, the
 * streak of days claimed in a row (and the best), what a claim pays, and a Claim button. Skipping a day costs nothing.
 */
final class DailyTaskScreen extends Screen {
	private static final int W = 240, H = 148, PAD = 10, BUTTON_W = 90, BUTTON_H = 16;

	DailyTaskScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.daily"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return MenuCompat.centre(this.width, W);
	}

	private int top() {
		return Math.max(MenuCompat.STRIP + 4, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private DailyTasks.State state() {
		return this.minecraft.player == null ? DailyTasks.State.EMPTY : this.minecraft.player.getAttachedOrElse(DailyTasks.STATE, DailyTasks.State.EMPTY);
	}

	private boolean open(DailyTasks.State s, long now) {
		return s.day() >= 0L && now < s.ends();
	}

	private int buttonX() {
		return left() + W - PAD - BUTTON_W;
	}

	private int buttonY() {
		return top() + H - PAD - BUTTON_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		DailyTasks.State s = state();
		long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
		if (Ui.over((int)event.x(), (int)event.y(), buttonX(), buttonY(), BUTTON_W, BUTTON_H)) {
			if (open(s, now) && s.done() && !s.claimed()) {
				ClientPlayNetworking.send(new DailyTasks.Claim());
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = W - 2 * PAD;
		DailyTasks.State s = state();
		long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
		boolean live = open(s, now);
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.tab.daily"), x + PAD, y + 8, w, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + W - 6, y + 20);
		if (!live) {
			Ui.wrapped(graphics, this.font, Component.translatable("gui.jaymoskingdoms.daily.waiting"), x + PAD, y + 28, w, 4, Ui.LINE, Ui.DIM, false);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		DailyTasks.Kind kind = s.kindOf();
		Component task = Component.translatable("gui.jaymoskingdoms.daily.task." + kind.key(), s.goal());
		int lines = Ui.wrapped(graphics, this.font, task, x + PAD, y + 28, w, 2, Ui.LINE, s.claimed() ? Ui.DIM : Ui.TEXT, false);
		int by = y + 28 + Math.max(1, lines) * Ui.LINE + 6;
		Ui.bar(graphics, x + PAD, by, w, 6, s.goal() == 0 ? 0.0F : Mth.clamp(s.progress() / (float)s.goal(), 0.0F, 1.0F), s.done() ? Ui.GOOD : Ui.GOLD);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.daily.progress", s.progress(), s.goal()), x + W - PAD, by + 9, w / 2, Ui.DIM);
		long left = Math.max(0L, s.ends() - now);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.daily.reset", left / 1200L, String.format("%02d", (left % 1200L) / 20L)), x + PAD,
			by + 9, w / 2 - 4, Ui.HINT);
		int sy = by + 24;
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.daily.streak", DailyTasks.streakNow(s), s.best()), x + PAD, sy, w, Ui.TEXT);
		Component pays = Component.translatable("gui.jaymoskingdoms.daily.pays", Money.text(Money.worth(DailyTasks.payNow(s))), DailyTasks.STREAK_MOST);
		Ui.line(graphics, this.font, pays, x + PAD, sy + 12, w, Ui.DIM);
		boolean ready = s.done() && !s.claimed();
		Ui.line(graphics, this.font, Component.translatable(s.claimed() ? "gui.jaymoskingdoms.daily.claimed" : s.done() ? "gui.jaymoskingdoms.daily.ready" : "gui.jaymoskingdoms.daily.open"),
			x + PAD, buttonY() + 4, w - BUTTON_W - 8, s.claimed() ? Ui.GOOD : s.done() ? Ui.GOLD : Ui.DIM);
		Ui.button(graphics, this.font, buttonX(), buttonY(), BUTTON_W, BUTTON_H, Component.translatable("gui.jaymoskingdoms.daily.claim"), Ui.GOOD,
			ready && Ui.over(mouseX, mouseY, buttonX(), buttonY(), BUTTON_W, BUTTON_H), ready);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
