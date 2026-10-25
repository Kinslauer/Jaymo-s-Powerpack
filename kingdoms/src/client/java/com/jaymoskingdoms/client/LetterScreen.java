package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Letters;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.200) A letter ({@link Letters}) in the Kingdoms screens' look, a parchment accent. A blank one is written: who it's
 * for (their character name) and what it says, then sealed. A sealed one is read: who it's for and from, and its words
 * (scroll for more).
 */
final class LetterScreen extends Screen {
	static final int W = 280, PAD = 10, BUTTON_H = 16, GAP = 4, BODY_H = 120, READ_LINES = 16;
	/** The letters' accent: parchment. */
	static final int ACCENT = 0xFFD9B77A;

	private final InteractionHand hand;
	/** Null while writing. */
	private final Letters.@Nullable Letter letter;
	private @Nullable EditBox to;
	private @Nullable MultiLineEditBox body;
	private List<FormattedCharSequence> lines = List.of();
	private int scroll;

	private LetterScreen(InteractionHand hand, Letters.@Nullable Letter letter) {
		super(Component.translatable(letter == null ? "gui.jaymoskingdoms.letter.write" : "gui.jaymoskingdoms.letter.read"));
		this.hand = hand;
		this.letter = letter;
	}

	static void register() {
		Letters.open = hand -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}
			ItemStack stack = mc.player.getItemInHand(hand);
			if (stack.is(Letters.SEALED)) {
				Letters.Letter letter = Letters.of(stack);
				if (letter != null) {
					mc.gui.setScreen(new LetterScreen(hand, letter));
				}
			} else if (stack.is(Letters.LETTER)) {
				mc.gui.setScreen(new LetterScreen(hand, null));
			}
		};
	}

	@Override
	protected void init() {
		int x = left() + PAD, w = W - 2 * PAD;
		if (this.letter == null) {
			int y = top() + 27;
			this.to = new EditBox(this.font, x, y, w, 16, Component.translatable("gui.jaymoskingdoms.letter.to_hint"));
			this.to.setMaxLength(Letters.NAME_MAX);
			this.to.setHint(Component.translatable("gui.jaymoskingdoms.letter.to_hint"));
			addRenderableWidget(this.to);
			this.body = MultiLineEditBox.builder().setX(x).setY(y + 16 + GAP).setPlaceholder(Component.translatable("gui.jaymoskingdoms.letter.body_hint"))
				.build(this.font, w, BODY_H, Component.translatable("gui.jaymoskingdoms.letter.body_hint"));
			this.body.setCharacterLimit(Letters.TEXT_MAX);
			addRenderableWidget(this.body);
			int by = y + 16 + GAP + BODY_H + GAP, bw = (w - GAP) / 2;
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.letter.seal"), b -> seal()).bounds(x, by, bw, BUTTON_H).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.letter.cancel"), b -> onClose()).bounds(x + bw + GAP, by, bw, BUTTON_H)
				.build());
			setInitialFocus(this.to);
			return;
		}
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : this.letter.lines()) {
			out.addAll(this.font.split(c, w));
			out.add(FormattedCharSequence.EMPTY);
		}
		if (!out.isEmpty()) {
			out.removeLast();
		}
		this.lines = out;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.letter.close"), b -> onClose())
			.bounds(x, top() + panelH() - PAD - BUTTON_H, w, BUTTON_H).build());
	}

	private void seal() {
		if (this.to == null || this.body == null) {
			return;
		}
		String name = this.to.getValue().strip(), text = this.body.getValue().strip();
		if (name.isEmpty()) {
			setFocused(this.to);
			return;
		}
		if (text.isEmpty()) {
			setFocused(this.body);
			return;
		}
		ClientPlayNetworking.send(new Letters.Seal(this.hand == InteractionHand.OFF_HAND, name, text));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, panelH(), ACCENT);
		if (this.letter == null) {
			Ui.line(graphics, this.font, this.title, x + PAD, y + 8, W - 2 * PAD, ACCENT);
			Ui.rule(graphics, x + 6, x + W - 6, y + 21);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		Component title = this.letter.title().orElseGet(() -> Component.translatable("gui.jaymoskingdoms.letter.to", this.letter.to()));
		Ui.line(graphics, this.font, title, x + PAD, y + 8, W - 2 * PAD, ACCENT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.letter.from", this.letter.from()), x + PAD, y + 19, W - 2 * PAD, Ui.TEXT);
		Ui.rule(graphics, x + 6, x + W - 6, y + 31);
		int ly = y + 37;
		int shown = Math.min(READ_LINES, this.lines.size());
		for (int i = 0; i < shown; i++) {
			graphics.text(this.font, this.lines.get(this.scroll + i), x + PAD, ly, Ui.TEXT);
			ly += Ui.LINE;
		}
		if (this.lines.size() > READ_LINES) {
			Ui.scrollbar(graphics, x + W - 5, y + 37, READ_LINES * Ui.LINE, READ_LINES, this.lines.size(), this.scroll);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.letter != null && this.lines.size() > READ_LINES) {
			this.scroll = Mth.clamp(this.scroll - (int)Math.signum(scrollY), 0, this.lines.size() - READ_LINES);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelH() {
		if (this.letter == null) {
			return 27 + 16 + GAP + BODY_H + GAP + BUTTON_H + PAD;
		}
		return 37 + Math.min(READ_LINES, Math.max(1, this.lines.size())) * Ui.LINE + GAP + BUTTON_H + PAD + 2;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}
}
