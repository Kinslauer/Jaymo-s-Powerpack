package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The life aim's page on a rough start (Andy: "your starting journal entry should resemble a scrap of paper"): a single
 * torn scrap of parchment, burnt dark at its ragged edges, with who you are under a heading and then the aim. The town
 * starts keep Minecraft's book page ({@link AimPageClient}). Done or Esc puts it away.
 */
public class ScrapScreen extends Screen {
	static final Identifier SCRAP = JaymosKingdoms.id("textures/gui/journal/scrap.png");
	/** The texture's size, and the box on it the writing goes in (texture pixels). */
	static final int SCRAP_W = 176, SCRAP_H = 208, TEXT_X = 26, TEXT_Y = 30, TEXT_W = 124, LINE = 10;
	/** Room left round the scrap when it's scaled up to fit the window. */
	static final int MARGIN = 16;
	/** The Done button under the scrap (screen pixels). */
	static final int BUTTON_W = 80, BUTTON_H = 20, BUTTON_GAP = 4;
	/** The gap between who you are and the aim, and the faint rule drawn in it (texture pixels). */
	static final int GAP = 8, RULE_W = 48;
	/** Ink colours: brown-black writing, a faded rule. */
	static final int INK = 0xFF2B1A0C, FAINT = 0x803A2614;

	private final Component heading;
	private final Component who;
	private final Component aim;

	public ScrapScreen(Component heading, Component who, Component aim) {
		super(heading);
		this.heading = heading;
		this.who = who;
		this.aim = aim;
	}

	/** Whole-number scale that fits the scrap in the window, at least 1. */
	private int scale() {
		return Math.max(1, Math.min((this.width - MARGIN) / SCRAP_W, (this.height - MARGIN - BUTTON_H - BUTTON_GAP) / SCRAP_H));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int k = scale();
		graphics.pose().pushMatrix();
		graphics.pose().translate((this.width - SCRAP_W * k) / 2.0F, top());
		graphics.pose().scale(k, k);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SCRAP, 0, 0, 0.0F, 0.0F, SCRAP_W, SCRAP_H, SCRAP_W, SCRAP_H);
		int y = TEXT_Y;
		int hw = this.font.width(this.heading);
		graphics.text(this.font, this.heading, (SCRAP_W - hw) / 2, y, INK, false);
		y += LINE + 4;
		for (FormattedCharSequence seq : this.font.split(this.who, TEXT_W)) {
			graphics.text(this.font, seq, TEXT_X, y, INK, false);
			y += LINE;
		}
		y += GAP / 2;
		graphics.fill((SCRAP_W - RULE_W) / 2, y, (SCRAP_W + RULE_W) / 2, y + 1, FAINT);
		y += GAP / 2 + 2;
		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(this.aim, TEXT_W));
		int last = Mth.clamp((SCRAP_H - TEXT_Y - y) / LINE, 0, lines.size());
		for (FormattedCharSequence seq : lines.subList(0, last)) {
			graphics.text(this.font, seq, TEXT_X, y, INK, false);
			y += LINE;
		}
		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The scrap's top edge on screen: the scrap and the button under it, centred together. */
	private float top() {
		return (this.height - SCRAP_H * scale() - BUTTON_GAP - BUTTON_H) / 2.0F;
	}

	@Override
	protected void init() {
		int y = (int) top() + SCRAP_H * scale() + BUTTON_GAP;
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds((this.width - BUTTON_W) / 2, y, BUTTON_W, BUTTON_H).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
