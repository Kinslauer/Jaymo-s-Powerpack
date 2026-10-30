package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.forms.Forms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.7.371) The Five-Art Belt screen: one button a slot, each click moving on to the next form you know (and a bare slot).
 * It opens when you sneak and use a training post or a sparring dummy empty-handed; the server only takes the change while
 * you still stand by one.
 */
final class BeltScreen extends Screen {
	static final int WIDTH = 190, ROW = 22, BUTTON_H = 18, PAD = 8, HEAD = 26;

	private final Button[] slots = new Button[Forms.BELT_SLOTS];
	private int left, top;

	BeltScreen() {
		super(Component.translatable("gui.jaymoskingdoms.belt.title"));
	}

	@Override
	protected void init() {
		this.left = (this.width - WIDTH) / 2;
		this.top = (this.height - (HEAD + Forms.BELT_SLOTS * ROW + 50)) / 2;
		for (int i = 0; i < Forms.BELT_SLOTS; i++) {
			int slot = i;
			this.slots[i] = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(slot))
				.bounds(this.left + PAD, this.top + HEAD + i * ROW, WIDTH - 2 * PAD, BUTTON_H).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.belt.done"), b -> onClose())
			.bounds(this.left + PAD, this.top + HEAD + Forms.BELT_SLOTS * ROW + 22, WIDTH - 2 * PAD, BUTTON_H).build());
		labels();
	}

	/** This slot's next choice: a bare slot, then each form known in turn. */
	private void cycle(int slot) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		Forms.Book book = Forms.book(this.minecraft.player);
		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(book.known());
		String next = options.get((Math.max(0, options.indexOf(book.belt().get(slot))) + 1) % options.size());
		if (ClientPlayNetworking.canSend(Forms.Assign.TYPE)) {
			ClientPlayNetworking.send(new Forms.Assign(slot, next));
		}
	}

	private void labels() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		Forms.Book book = Forms.book(this.minecraft.player);
		for (int i = 0; i < Forms.BELT_SLOTS; i++) {
			Forms.Form form = Forms.get(book.belt().get(i));
			this.slots[i].setMessage(form == null ? Component.translatable("gui.jaymoskingdoms.belt.bare", i + 1)
				: Component.translatable("gui.jaymoskingdoms.belt.slot", i + 1, form.name(), book.rank(form.id())));
		}
	}

	@Override
	public void tick() {
		labels();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui.panel(graphics, this.left, this.top, WIDTH, HEAD + Forms.BELT_SLOTS * ROW + 50, Ui.GOLD);
		graphics.centeredText(this.font, this.title, this.left + WIDTH / 2, this.top + 9, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.belt.hint"), this.left + PAD,
			this.top + HEAD + Forms.BELT_SLOTS * ROW + 6, WIDTH - 2 * PAD, Ui.DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
