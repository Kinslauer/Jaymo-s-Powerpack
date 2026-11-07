package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BaneMarks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.7.620, Rivals patch 6) The marks screen ({@link BaneMarks}), from sneaking and using a grindstone with a blade in hand:
 * the eight marks, each with what's known of it for your Bane (its place, "one of them", "not its", or nothing yet), and the
 * three you mean to carve, picked in order. Carve cuts them into the blade; Clear grinds the blade smooth. Drawn in Kingdoms'
 * panel ({@link Ui}). Carved marks show on the blade's tooltip.
 */
public final class BaneMarksScreen extends Screen {
	static final int PANEL_W = 300, PANEL_H = 214, HEADER = 34, BUTTON_W = 66, BUTTON_H = 18, GAP = 4;

	private final BaneMarks.Board board;
	private final List<String> picked = new ArrayList<>();

	BaneMarksScreen(BaneMarks.Board board) {
		super(Component.translatable("bane.jaymoskingdoms.screen.title"));
		this.board = board;
		this.picked.addAll(board.carved());
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BaneMarks.Board.TYPE, (payload, context) -> context.client().gui.setScreen(new BaneMarksScreen(payload)));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			List<String> carved = BaneMarks.CARVED == null ? List.of() : stack.getOrDefault(BaneMarks.CARVED, List.of());
			if (!carved.isEmpty()) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymoskingdoms.bane_marks", BaneMarks.names(carved))
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	@Override
	protected void init() {
		int cols = 4, w = (panelW() - 12 - (cols - 1) * GAP) / cols;
		for (int i = 0; i < BaneMarks.MARKS.size(); i++) {
			String mark = BaneMarks.MARKS.get(i);
			int x = left() + 6 + (i % cols) * (w + GAP), y = top() + HEADER + (i / cols) * (BUTTON_H + 26);
			Button b = Button.builder(Component.translatable("bane.jaymoskingdoms.mark." + mark), btn -> pick(mark)).bounds(x, y, w, BUTTON_H).build();
			b.active = !picked.contains(mark) && picked.size() < BaneMarks.KEY_LENGTH;
			addRenderableWidget(b);
		}
		int fy = top() + PANEL_H - 24;
		addRenderableWidget(Button.builder(Component.translatable("bane.jaymoskingdoms.screen.clear"), b -> {
			picked.clear();
			rebuildWidgets();
		}).bounds(left() + 6, fy, BUTTON_W, BUTTON_H).build());
		Button carve = Button.builder(Component.translatable("bane.jaymoskingdoms.screen.carve"), b -> {
			ClientPlayNetworking.send(new BaneMarks.Carve(List.copyOf(picked)));
			onClose();
		}).bounds(left() + 6 + BUTTON_W + GAP, fy, BUTTON_W, BUTTON_H).build();
		carve.active = picked.isEmpty() || picked.size() == BaneMarks.KEY_LENGTH;
		addRenderableWidget(carve);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(left() + panelW() - 6 - BUTTON_W, fy, BUTTON_W, BUTTON_H).build());
	}

	private void pick(String mark) {
		if (!picked.contains(mark) && picked.size() < BaneMarks.KEY_LENGTH) {
			picked.add(mark);
			rebuildWidgets();
		}
	}

	/** What's known of mark {@code i}, as the line under its button. */
	private Component hint(int i) {
		int k = i < board.known().size() ? board.known().get(i) : Integer.MIN_VALUE;
		if (k == BaneMarks.OUT) {
			return Component.translatable("bane.jaymoskingdoms.screen.out");
		}
		if (k == BaneMarks.IN) {
			return Component.translatable("bane.jaymoskingdoms.screen.in");
		}
		if (k >= 0 && k < BaneMarks.KEY_LENGTH) {
			return Component.translatable("bane.jaymoskingdoms.place." + k);
		}
		return Component.translatable("bane.jaymoskingdoms.screen.unknown");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		Ui.panel(graphics, x, y, w, PANEL_H, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 9, y + 7, w - 18, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("bane.jaymoskingdoms.screen.sub", board.bane()), x + 9, y + 18, w - 18, Ui.DIM);
		int cols = 4, bw = (w - 12 - (cols - 1) * GAP) / cols;
		for (int i = 0; i < BaneMarks.MARKS.size(); i++) {
			int bx = x + 6 + (i % cols) * (bw + GAP), by = y + HEADER + (i / cols) * (BUTTON_H + 26);
			int k = i < board.known().size() ? board.known().get(i) : Integer.MIN_VALUE;
			int color = k == BaneMarks.OUT ? Ui.HINT : k >= BaneMarks.IN ? Ui.GOLD : Ui.DIM;
			Ui.line(graphics, this.font, hint(i), bx + 2, by + BUTTON_H + 3, bw - 4, color);
		}
		int py = y + HEADER + 2 * (BUTTON_H + 26) + 6;
		Ui.rule(graphics, x + 6, x + w - 6, py - 3);
		Component order = picked.isEmpty() ? Component.translatable("bane.jaymoskingdoms.screen.none") : BaneMarks.names(picked);
		Ui.line(graphics, this.font, Component.translatable("bane.jaymoskingdoms.screen.order", order), x + 9, py + 2, w - 18, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("bane.jaymoskingdoms.screen.how"), x + 9, py + 14, w - 18, Ui.HINT);
		Ui.line(graphics, this.font, Component.translatable("bane.jaymoskingdoms.screen.swear"), x + 9, py + 24, w - 18, Ui.HINT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
