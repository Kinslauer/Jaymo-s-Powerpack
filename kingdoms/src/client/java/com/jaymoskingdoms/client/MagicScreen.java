package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MagicMenu;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Magic screen: the everyday spells every caster knows, then the rituals they know to begin (ten seconds still, a book
 * open), and a line for a whisper. A choice goes to the server and the screen closes.
 */
final class MagicScreen extends Screen {
	private static final int W = 230, PAD = 10, BUTTON_H = 16, GAP = 3;
	private final List<String> cantrips, rituals;
	private EditBox whisper;

	MagicScreen(List<String> cantrips, List<String> rituals) {
		super(Component.translatable("gui.jaymoskingdoms.magic.title"));
		this.cantrips = cantrips;
		this.rituals = rituals;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int height() {
		int rows = (this.cantrips.size() + 1) / 2 + this.rituals.size();
		return 44 + rows * (BUTTON_H + GAP) + 30;
	}

	@Override
	protected void init() {
		int left = (this.width - W) / 2, top = (this.height - height()) / 2;
		int y = top + 24, half = (W - 2 * PAD - GAP) / 2;
		for (int i = 0; i < this.cantrips.size(); i++) {
			String id = this.cantrips.get(i);
			int x = left + PAD + (i % 2) * (half + GAP);
			addRenderableWidget(Button.builder(Component.translatable("magic.jaymoskingdoms.cantrip." + id), b -> pick(id)).bounds(x, y, half, BUTTON_H).build());
			if (i % 2 == 1 || i == this.cantrips.size() - 1) {
				y += BUTTON_H + GAP;
			}
		}
		this.whisper = new EditBox(this.font, left + PAD, y, W - 2 * PAD, BUTTON_H, Component.translatable("gui.jaymoskingdoms.magic.whisper_hint"));
		this.whisper.setMaxLength(MagicMenu.WHISPER_MAX);
		this.whisper.setHint(Component.translatable("gui.jaymoskingdoms.magic.whisper_hint"));
		addRenderableWidget(this.whisper);
		y += BUTTON_H + GAP + 12;
		for (String id : this.rituals) {
			addRenderableWidget(Button.builder(Component.translatable("magic.jaymoskingdoms.ritual", Component.translatable("class.jaymoskingdoms.option." + id)),
				b -> pick(id)).bounds(left + PAD, y, W - 2 * PAD, BUTTON_H).build());
			y += BUTTON_H + GAP;
		}
	}

	private void pick(String id) {
		String text = id.equals("whisper") && this.whisper != null ? this.whisper.getValue() : "";
		ClientPlayNetworking.send(new MagicMenu.Pick(id, text));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = (this.width - W) / 2, top = (this.height - height()) / 2;
		Ui.panel(graphics, left, top, W, height(), Ui.GOLD);
		Ui.line(graphics, this.font, this.title, left + PAD, top + 8, W - 2 * PAD, Ui.GOLD);
		if (!this.rituals.isEmpty()) {
			int ry = top + 24 + ((this.cantrips.size() + 1) / 2) * (BUTTON_H + GAP) + BUTTON_H + GAP;
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.magic.rituals"), left + PAD, ry, W - 2 * PAD, Ui.DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
