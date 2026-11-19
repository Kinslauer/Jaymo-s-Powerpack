package com.jaymoskingdoms.client;

import com.jaymoskingdoms.AskTopics;
import com.jaymoskingdoms.TalkNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.5.39) Answering someone who came over to ask you something ({@code Approaches}): what they said, and a button per
 * answer (greyed out when you can't: no coin for the fine, no food to give). The server does the rest; their reply
 * floats over their head. (0.7.44) Drawn like the H screen.
 * A talk that can be asked anything ({@link AskTopics}) also has a box to type a question in (Enter asks it), and its follow-up
 * questions stand in a panel beside it, when the screen is wide enough.
 */
final class TalkScreen extends Screen {
	static final int PANEL_W = 280, PAD = 10, BUTTON_H = 20, GAP = 4;
	/** The follow-up panel's width, and the gap between it and the talk. */
	static final int SIDE_W = 170, SIDE_GAP = 6;

	private final TalkNet.Ask ask;
	private boolean answered;
	/** (AskTopics) This talk takes a typed question, and its last {@link #follow} answers are follow-ups. */
	private final boolean typed;
	private final int follow;
	private @org.jspecify.annotations.Nullable EditBox box;
	/** The next talk card from this entity takes typing ({@link AskTopics.Typable}), with this many follow-ups. */
	private static int typableId = -1, typableFollow;

	private TalkScreen(TalkNet.Ask ask) {
		super(ask.speaker());
		// (an illager's words, as far as you know their tongue: IllagerWords)
		this.ask = new TalkNet.Ask(ask.entityId(), ask.speaker(), IllagerWords.resolve(ask.words()), ask.options(), ask.enabled());
		this.typed = ask.entityId() == typableId;
		this.follow = this.typed ? Math.min(typableFollow, ask.options().size()) : 0;
		if (this.typed) {
			typableId = -1;
		}
	}

	/**
	 * (0.7.591) A question that came while another screen was up (the death screen, a new life's story, MCA's own chat,
	 * the journal...): it waits for that screen to close rather than knocking it away, and lapses after {@link #WAIT_TICKS}.
	 */
	static final int WAIT_TICKS = 20 * 20;
	private static TalkNet.@org.jspecify.annotations.Nullable Ask waiting;
	private static int waited;

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AskTopics.Typable.TYPE, (payload, context) -> {
			typableId = payload.entityId();
			typableFollow = payload.follow();
		});
		ClientPlayNetworking.registerGlobalReceiver(TalkNet.Ask.TYPE, (payload, context) -> {
			Screen open = context.client().gui.screen();
			if (open == null || open instanceof TalkScreen) {
				context.client().gui.setScreen(new TalkScreen(payload));
			} else {
				waiting = payload;
				waited = 0;
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (waiting == null) {
				return;
			}
			if (client.player == null || ++waited > WAIT_TICKS) {
				waiting = null;
			} else if (client.gui.screen() == null && client.player.isAlive()) {
				TalkNet.Ask ask = waiting;
				waiting = null;
				client.gui.setScreen(new TalkScreen(ask));
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int textWidth() {
		return PANEL_W - PAD * 2;
	}

	private int panelHeight() {
		return 24 + this.font.split(this.ask.words(), textWidth()).size() * 10 + 8 + mainCount() * (BUTTON_H + GAP) + (this.typed ? BUTTON_H + GAP : 0) + PAD;
	}

	/** Whether the follow-ups stand in a panel of their own (the screen is wide enough), or in the list. */
	private boolean side() {
		return this.follow > 0 && this.width >= PANEL_W + SIDE_GAP + SIDE_W + 8;
	}

	/** How many answers are in the talk's own list. */
	private int mainCount() {
		return side() ? this.ask.options().size() - this.follow : this.ask.options().size();
	}

	private int left() {
		return side() ? (this.width - PANEL_W - SIDE_GAP - SIDE_W) / 2 : (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	@Override
	protected void init() {
		int y = top() + 24 + this.font.split(this.ask.words(), textWidth()).size() * 10 + 8;
		for (int i = 0; i < mainCount(); i++) {
			int option = i;
			Button b = FlatButton.builder(this.ask.options().get(i), button -> pick(option)).bounds(left() + PAD, y, PANEL_W - PAD * 2, BUTTON_H).build();
			b.active = this.ask.enabled().get(i);
			addRenderableWidget(b);
			y += BUTTON_H + GAP;
		}
		if (this.typed) {
			this.box = new EditBox(this.font, left() + PAD, y, PANEL_W - PAD * 2, BUTTON_H, Component.translatable("talk.jaymoskingdoms.ask.box"));
			this.box.setMaxLength(AskTopics.LONGEST);
			this.box.setHint(Component.translatable("talk.jaymoskingdoms.ask.box"));
			addRenderableWidget(this.box);
			setInitialFocus(this.box);
		}
		if (side()) {
			int sx = left() + PANEL_W + SIDE_GAP, sy = top() + 24;
			for (int i = mainCount(); i < this.ask.options().size(); i++) {
				int option = i;
				addRenderableWidget(FlatButton.builder(this.ask.options().get(i), button -> pick(option)).bounds(sx + PAD, sy, SIDE_W - PAD * 2, BUTTON_H).build());
				sy += BUTTON_H + GAP;
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.box != null && this.box.isFocused() && (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
			|| event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
			String text = this.box.getValue().strip();
			if (!text.isEmpty()) {
				// the answer comes back as a new talk card, which takes this one's place
				ClientPlayNetworking.send(new AskTopics.Typed(this.ask.entityId(), text));
				this.box.setValue("");
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void pick(int option) {
		this.answered = true;
		ClientPlayNetworking.send(new TalkNet.Answer(this.ask.entityId(), option));
		super.onClose();
	}

	@Override
	public void onClose() {
		if (!this.answered) {
			ClientPlayNetworking.send(new TalkNet.Answer(this.ask.entityId(), -1));
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelHeight();
		Ui.panel(graphics, x, y, PANEL_W, h, Ui.GOLD);
		if (side()) {
			int sx = x + PANEL_W + SIDE_GAP, sh = 24 + this.follow * (BUTTON_H + GAP) + PAD;
			Ui.panel(graphics, sx, y, SIDE_W, sh, Ui.GOLD);
			Ui.line(graphics, this.font, Component.translatable("talk.jaymoskingdoms.ask.follow"), sx + PAD, y + 8, SIDE_W - PAD * 2, Ui.GOLD);
			Ui.rule(graphics, sx + 6, sx + SIDE_W - 6, y + 19);
		}
		Ui.line(graphics, this.font, this.title, x + PAD, y + 8, PANEL_W - PAD * 2, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + 19);
		int ty = y + 24;
		for (FormattedCharSequence line : this.font.split(this.ask.words(), textWidth())) {
			graphics.text(this.font, line, x + PAD, ty, QuestScreen.COLOR_TEXT);
			ty += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
