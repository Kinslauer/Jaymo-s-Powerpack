package com.jaymoskingdoms.client;

import java.util.ArrayList;
import java.util.List;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A page of lines the server sent (what an identify found), read and closed; it fits under the menu strip, and a page too long scrolls slowly through. */
final class MagicPageScreen extends Screen {
	private static final int W = 300, PAD = 10;
	private final List<Component> lines;
	private final List<FormattedCharSequence> wrapped = new ArrayList<>();

	MagicPageScreen(Component title, List<Component> lines) {
		super(title);
		this.lines = lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int height() {
		return Math.min(24 + Math.max(1, this.wrapped.size()) * Ui.LINE + 36, this.height - 8 - MenuCompat.STRIP);
	}

	private int panelW() {
		return Math.min(W, this.width - 12 - MenuCompat.SIDE);
	}

	private int left() {
		return MenuCompat.centre(this.width, panelW());
	}

	private int top() {
		return Math.max(4 + MenuCompat.STRIP, (this.height - height()) / 2);
	}

	@Override
	protected void init() {
		this.wrapped.clear();
		for (Component c : this.lines) {
			this.wrapped.addAll(this.font.split(c, panelW() - 2 * PAD - 4));
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.magic.close"), b -> onClose())
			.bounds(left() + panelW() / 2 - 40, top() + height() - 24, 80, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = left(), top = top(), w = panelW();
		Ui.window(graphics, left, top, w, height());
		Ui.line(graphics, this.font, this.title, left + PAD, top + 8, w - 2 * PAD, Ui.GOLD);
		int y = top + 24, room = Math.max(1, (height() - 60) / Ui.LINE), over = Math.max(0, this.wrapped.size() - room) * Ui.LINE;
		int shift = over > 0 ? Ui.slide(over, Ui.SCROLL_MS_PER_LINE / Ui.LINE, Ui.SCROLL_PAUSE_MS) : 0;
		graphics.enableScissor(left, y - 1, left + w, y + room * Ui.LINE);
		for (int i = 0; i < this.wrapped.size(); i++) {
			graphics.text(this.font, this.wrapped.get(i), left + PAD, y + i * Ui.LINE - shift, Ui.TEXT);
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, left + w - PAD + 2, y, room * Ui.LINE - 1, room * Ui.LINE, room * Ui.LINE + over, shift);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
