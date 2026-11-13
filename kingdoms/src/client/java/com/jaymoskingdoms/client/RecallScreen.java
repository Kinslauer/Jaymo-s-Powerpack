package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Carousing;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The morning after ({@link Carousing}): what you remember of the night, in your own words, one paragraph at a time, on a
 * dark screen that clears from black as you read. It tells nothing the server did not do.
 */
final class RecallScreen extends Screen {
	static final int W = 300, PAD = 14, FADE_TICKS = 50, PARA_GAP = 8;

	private final List<Component> lines;
	private int age;

	private RecallScreen(List<Component> lines) {
		super(Component.translatable("recall.jaymoskingdoms.night.title"));
		this.lines = lines;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Carousing.Recall.TYPE, (payload, context) -> context.client().gui.setScreen(new RecallScreen(payload.lines())));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("recall.jaymoskingdoms.night.rise"), b -> onClose())
			.bounds((this.width - 120) / 2, this.height - 36, 120, 20).build());
	}

	@Override
	public void tick() {
		this.age++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int dark = Math.max(0, 255 - (int)((this.age + a) * 255 / FADE_TICKS));
		g.fill(0, 0, this.width, this.height, 0xFF000000 | 0x0B0A10);
		int x = (this.width - W) / 2, y = 24;
		g.centeredText(this.font, this.title, this.width / 2, y, Ui.GOLD);
		y += 18;
		for (int i = 0; i < this.lines.size(); i++) {
			// each paragraph comes up in turn, as the memory returns
			int shown = Math.min(255, (int)((this.age + a - i * 18) * 255 / 30));
			if (shown <= 0) {
				continue;
			}
			int colour = (shown << 24) | 0xE6E0D0;
			for (FormattedCharSequence part : this.font.split(this.lines.get(i), W - PAD * 2)) {
				g.text(this.font, part, x + PAD, y, colour);
				y += 10;
			}
			y += PARA_GAP;
		}
		if (dark > 0) {
			g.fill(0, 0, this.width, this.height, dark << 24);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
