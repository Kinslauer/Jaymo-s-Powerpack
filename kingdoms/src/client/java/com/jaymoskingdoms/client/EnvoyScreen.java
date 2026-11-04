package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DiplomacyNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.7.519) An envoy's offer ({@link DiplomacyNet.EnvoyOffer}): a realm's envoy waiting in your hall asks for a treaty. Sign,
 * Refuse or Not yet (Esc counts as Not yet), in the Kingdoms screens' look with an envoy's blue.
 */
final class EnvoyScreen extends Screen {
	static final int W = 300, PAD = 10, BUTTON_H = 16, GAP = 4;
	/** An envoy's blue. */
	static final int ACCENT = 0xFF8FB0D9;

	private final DiplomacyNet.EnvoyOffer offer;
	private List<FormattedCharSequence> lines = List.of();
	private boolean answered;

	EnvoyScreen(DiplomacyNet.EnvoyOffer offer) {
		super(Component.translatable("gui.jaymoskingdoms.envoy.title", offer.realm()));
		this.offer = offer;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DiplomacyNet.EnvoyOffer.TYPE, (payload, context) -> context.client().gui.setScreen(new EnvoyScreen(payload)));
	}

	@Override
	protected void init() {
		List<Component> text = new ArrayList<>();
		Component kind = Component.translatable("treaty.jaymoskingdoms." + this.offer.kind());
		text.add(Component.translatable(this.offer.renew() ? "gui.jaymoskingdoms.envoy.renew" : "gui.jaymoskingdoms.envoy.offer", this.offer.envoy(), this.offer.realm(), kind,
			this.offer.days()));
		text.add(Component.translatable("treaty.jaymoskingdoms." + this.offer.kind() + ".about"));
		Component creed = Component.translatable("creed.jaymoskingdoms." + this.offer.creed());
		if (this.offer.playerRealm()) {
			text.add(Component.translatable("gui.jaymoskingdoms.envoy.realm_player", this.offer.realm(), creed));
		} else if (!this.offer.temper().isEmpty()) {
			text.add(Component.translatable("gui.jaymoskingdoms.envoy.realm", this.offer.realm(), creed, Component.translatable("temper.jaymoskingdoms." + this.offer.temper())));
		}
		text.add(Component.translatable("gui.jaymoskingdoms.envoy.relation", Component.translatable("relation.jaymoskingdoms." + this.offer.relation()), this.offer.tension()));
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : text) {
			out.addAll(this.font.split(c, W - 2 * PAD));
			out.add(FormattedCharSequence.EMPTY);
		}
		this.lines = out;
		int x = left() + PAD, y = top() + panelH() - PAD - 2 * BUTTON_H - GAP, bw = (W - 2 * PAD - GAP) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.envoy.sign"), b -> answer(DiplomacyNet.SIGN)).bounds(x, y, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.envoy.refuse"), b -> answer(DiplomacyNet.REFUSE))
			.bounds(x + bw + GAP, y, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.envoy.later"), b -> onClose())
			.bounds(x, y + BUTTON_H + GAP, W - 2 * PAD, BUTTON_H).build());
	}

	private void answer(int choice) {
		this.answered = true;
		ClientPlayNetworking.send(new DiplomacyNet.EnvoyAnswer(this.offer.mission(), choice));
		onClose();
	}

	@Override
	public void removed() {
		if (!this.answered) {
			// (Esc or another screen: not yet)
			ClientPlayNetworking.send(new DiplomacyNet.EnvoyAnswer(this.offer.mission(), DiplomacyNet.NOT_YET));
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
