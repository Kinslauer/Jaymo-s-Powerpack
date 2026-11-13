package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Research;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A day's study ({@link Research}): what is being read and a bar that fills over the study's length; when the server says
 * it is done, what was learned. Closing the book early ends the study and the fee is lost.
 */
final class ResearchScreen extends Screen {
	static final int W = 280, H = 110, PAD = 12;
	private final int total;
	private int age;
	private Component result;
	private boolean ok, closed;
	private Button done;

	private ResearchScreen(Component title, int total) {
		super(title);
		this.total = Math.max(1, total);
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Research.Study.TYPE, (payload, context) ->
			context.client().gui.setScreen(new ResearchScreen(payload.title(), payload.ticks())));
		ClientPlayNetworking.registerGlobalReceiver(Research.Finish.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof ResearchScreen s) {
				s.result = payload.result();
				s.ok = payload.ok();
				s.closed = true;
				s.done.visible = true;
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds((this.width - 80) / 2, (this.height + H) / 2 - 28, 80, 18).build());
		this.done.visible = this.closed;
	}

	@Override
	public void tick() {
		this.age++;
	}

	@Override
	public void onClose() {
		if (!this.closed) {
			this.closed = true;
			ClientPlayNetworking.send(new Research.Close());
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = (this.width - W) / 2, y = (this.height - H) / 2;
		Ui.panel(g, x, y, W, H, Ui.SURVIVAL);
		Ui.line(g, this.font, this.title, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		Ui.rule(g, x + 6, x + W - 6, y + 19);
		if (this.result == null) {
			Ui.bar(g, x + PAD, y + 40, W - PAD * 2, 8, Math.min(1.0F, (this.age + a) / this.total), Ui.SURVIVAL);
			g.centeredText(this.font, Component.translatable("research.jaymoskingdoms.stay"), x + W / 2, y + 60, Ui.DIM);
		} else {
			int ty = y + 28;
			for (FormattedCharSequence part : this.font.split(this.result, W - PAD * 2)) {
				g.text(this.font, part, x + PAD, ty, this.ok ? Ui.TEXT : Ui.BAD);
				ty += 10;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
