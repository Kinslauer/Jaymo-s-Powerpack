package com.jaymospowers.client.finisher;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.finisher.PlayerQuestioning;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.96) Andy: "can you make one?": a player held down for questioning ({@link PlayerQuestioning}) answers from this
 * screen: the truth, a lie, silence, or words of their own, once a question's put. The one holding them asks from it:
 * who leads you, where's home, a threat, (Will) swear, let them go, finish them. Both see what's been said.
 */
public class QuestionScreen extends Screen {
	private static final int MAX_W = 300;
	private static final int ROW = 16;
	private static final int LOG_LINES = 7;
	private static final int ASK_ACCENT = 0xFFC8963C, HELD_ACCENT = 0xFFC83C3C;
	/** What's been said, newest last (kept while the screen is shut and opened again by the server). */
	private static final List<Component> LOG = new ArrayList<>();
	private static final int LOG_KEPT = 12;

	/** Asking ({@code true}) or held down. */
	private final boolean asking;
	private final boolean swear;
	/** (Held) The question put to you and not yet answered, or -1. */
	private static int pending = -1;
	private @Nullable EditBox words;
	private final List<Button> answers = new ArrayList<>();

	private QuestionScreen(Component title, boolean asking, boolean swear) {
		super(title);
		this.asking = asking;
		this.swear = swear;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlayerQuestioning.OpenAsk.TYPE, (payload, context) -> {
			LOG.clear();
			pending = -1;
			context.client().gui.setScreen(new QuestionScreen(Component.translatable("screen.jaymospowers.question.ask", payload.held()), true, payload.swear()));
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerQuestioning.OpenHeld.TYPE, (payload, context) -> {
			LOG.clear();
			pending = -1;
			context.client().gui.setScreen(new QuestionScreen(Component.translatable("screen.jaymospowers.question.held", payload.asker()), false, false));
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerQuestioning.Said.TYPE, (payload, context) -> {
			LOG.add(payload.line());
			while (LOG.size() > LOG_KEPT) {
				LOG.removeFirst();
			}
			if (payload.question() >= 0) {
				pending = payload.question();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerQuestioning.Close.TYPE, (payload, context) -> {
			pending = -1;
			if (context.client().gui.screen() instanceof QuestionScreen) {
				context.client().gui.setScreen(null);
			}
		});
	}

	private int rows() {
		return asking ? (swear ? 6 : 5) : 3;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return UiStyle.HEADER + rows() * ROW + (asking ? 0 : 20) + 6 + UiStyle.detailHeight(LOG_LINES);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + i * ROW;
	}

	@Override
	protected void init() {
		answers.clear();
		int x = left() + 8, w = panelW() - 16;
		if (asking) {
			int[] questions = swear
				? new int[] {PlayerQuestioning.LEADER, PlayerQuestioning.HOME, PlayerQuestioning.THREATEN, PlayerQuestioning.SWEAR, PlayerQuestioning.SPARE,
					PlayerQuestioning.FINISH}
				: new int[] {PlayerQuestioning.LEADER, PlayerQuestioning.HOME, PlayerQuestioning.THREATEN, PlayerQuestioning.SPARE, PlayerQuestioning.FINISH};
			for (int i = 0; i < questions.length; i++) {
				int q = questions[i];
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.question.q" + q), b -> {
					ClientPlayNetworking.send(new PlayerQuestioning.Ask(q));
					if (q == PlayerQuestioning.SPARE || q == PlayerQuestioning.FINISH) {
						this.onClose();
					}
				}).bounds(x, rowY(i) + 1, w, 14).build());
			}
			return;
		}
		String[] keys = {"truth", "lie", "silent"};
		int[] hows = {PlayerQuestioning.TRUTH, PlayerQuestioning.LIE, PlayerQuestioning.SILENT};
		for (int i = 0; i < 3; i++) {
			int how = hows[i];
			answers.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.question." + keys[i]), b -> send(how, ""))
				.bounds(x, rowY(i) + 1, w, 14).build()));
		}
		int wy = rowY(3) + 2;
		this.words = addRenderableWidget(new EditBox(this.font, x, wy, w - 54, 14, Component.translatable("screen.jaymospowers.question.words")));
		this.words.setMaxLength(PlayerQuestioning.WORDS_MAX);
		answers.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.question.say"), b -> {
			if (this.words != null) {
				send(PlayerQuestioning.WORDS, this.words.getValue());
				this.words.setValue("");
			}
		}).bounds(x + w - 50, wy, 50, 14).build()));
	}

	private void send(int how, String said) {
		if (pending >= 0) {
			ClientPlayNetworking.send(new PlayerQuestioning.Answer(how, said));
			pending = -1;
		}
	}

	@Override
	public void tick() {
		super.tick();
		for (Button b : answers) {
			b.active = pending >= 0;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// (the asker may step away from the screen and still hold them; the held can't wish it away)
		return asking;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		int accent = asking ? ASK_ACCENT : HELD_ACCENT;
		UiStyle.panel(graphics, x, y, w, panelH(), accent);
		Component sub = asking ? Component.translatable("screen.jaymospowers.question.ask_sub")
			: pending >= 0 ? Component.translatable("screen.jaymospowers.question.held_q", Component.translatable("message.jaymospowers.question.q" + pending))
			: Component.translatable("screen.jaymospowers.question.held_wait");
		UiStyle.header(graphics, this.font, x, y, w, null, this.title, sub, accent, 0);
		int dy = y + panelH() - UiStyle.detailHeight(LOG_LINES);
		List<Component> shown = LOG.isEmpty() ? List.of(Component.translatable("screen.jaymospowers.question.empty")) : List.copyOf(LOG);
		// (the newest at the bottom: only the last that fit)
		while (shown.size() > 1 && UiStyle.detailLines(this.font, w, null, shown) > LOG_LINES) {
			shown = shown.subList(1, shown.size());
		}
		UiStyle.detail(graphics, this.font, x, dy, w, LOG_LINES, null, accent, shown);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
