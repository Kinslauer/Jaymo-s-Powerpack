package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Lineage;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.7.184) The lineage prompt ({@link Lineage}): sneak-using your own child, a teen or older, asks whether to progress
 * your bloodline's story and live on as them, in the Kingdoms screens' look (amber). Your things go down to the child or
 * stay with your elder; Not yet (or Esc) leaves everything as it is.
 */
final class LineageScreen extends Screen {
	static final int W = 300, PAD = 10, BUTTON_H = 16, GAP = 4;
	/** The lineage's accent: a warm amber. */
	static final int ACCENT = 0xFFD08A4A;

	private final Lineage.Ask ask;
	private List<FormattedCharSequence> lines = List.of();
	private boolean answered;

	LineageScreen(Lineage.Ask ask) {
		super(Component.translatable("gui.jaymoskingdoms.lineage.title"));
		this.ask = ask;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Lineage.Ask.TYPE, (payload, context) -> context.client().gui.setScreen(new LineageScreen(payload)));
	}

	@Override
	protected void init() {
		List<Component> text = new ArrayList<>();
		Component child = Component.literal(this.ask.child());
		text.add(this.ask.heritage().isEmpty() ? Component.translatable("gui.jaymoskingdoms.lineage.you_become_plain", child)
			: Component.translatable("gui.jaymoskingdoms.lineage.you_become", child, Component.translatable("heritage.jaymoskingdoms." + this.ask.heritage())));
		text.add(Component.translatable("gui.jaymoskingdoms.lineage.elder"));
		if (!this.ask.power().isEmpty()) {
			text.add(Component.translatable("gui.jaymoskingdoms.lineage.power", Component.translatable(this.ask.power()), child));
		}
		text.add(Component.translatable("gui.jaymoskingdoms.lineage.things"));
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : text) {
			out.addAll(this.font.split(c, W - 2 * PAD));
			out.add(FormattedCharSequence.EMPTY);
		}
		this.lines = out;
		int x = left() + PAD, y = top() + panelH() - PAD - 2 * BUTTON_H - GAP, bw = (W - 2 * PAD - GAP) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.lineage.hand_down"), b -> answer(Lineage.HAND_DOWN))
			.bounds(x, y, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.lineage.elder_keeps"), b -> answer(Lineage.ELDER_KEEPS))
			.bounds(x + bw + GAP, y, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.lineage.not_yet"), b -> onClose())
			.bounds(x, y + BUTTON_H + GAP, W - 2 * PAD, BUTTON_H).build());
	}

	private void answer(int choice) {
		this.answered = true;
		ClientPlayNetworking.send(new Lineage.Answer(choice));
		onClose();
	}

	@Override
	public void removed() {
		if (!this.answered) {
			ClientPlayNetworking.send(new Lineage.Answer(Lineage.NOT_YET));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, panelH(), ACCENT);
		Ui.line(graphics, this.font, this.title, x + PAD, y + 8, W - 2 * PAD, ACCENT);
		Ui.rule(graphics, x + 6, x + W - 6, y + 21);
		int ly = y + 27;
		for (FormattedCharSequence line : this.lines) {
			graphics.text(this.font, line, x + PAD, ly, Ui.TEXT);
			ly += Ui.LINE;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelH() {
		return 27 + this.lines.size() * Ui.LINE + 2 * BUTTON_H + GAP + PAD + 4;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}
}
