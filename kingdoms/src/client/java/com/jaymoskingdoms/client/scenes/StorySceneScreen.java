package com.jaymoskingdoms.client.scenes;

import com.jaymoskingdoms.StoryScenes;
import com.jaymoskingdoms.StoryScenes.Shot;
import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.client.ui.FlatButton;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The talk of a story scene ({@link StorySceneClient}), over the film: whoever you meet speaks in the box low on the left, a
 * few letters a tick; your answers come up on the right once they've finished. A line with nothing to answer goes on by
 * itself once it's been read (or with a click or Space). Your reply is said in the box under your own name, shot side-on,
 * before their answer comes.
 * <ul>
 * <li><b>A question</b> sits in the middle on its own, with a yes and a no: click one, press Y or N, or with the microphone
 * on say it ("yes", "I will" / "no", "I won't").</li>
 * <li><b>A rite</b>: its lines in the middle and a bar for the time left. Say each line aloud with the microphone on, or
 * hold the right mouse button until the line is said ({@link #RITE_HOLD}); the first line not yet said is the one held.</li>
 * <li><b>The last line</b> of a scene shows what you were given over it, then the scene lets go.</li>
 * </ul>
 * Esc leaves the scene (it can be met again). The microphone button (on a question or a rite) switches {@link VoiceEars} on
 * or off for good.
 */
public final class StorySceneScreen extends Screen {
	/** Who {@link VoiceEars} listens for while a question or a rite is up. */
	static final String EARS = "story_scene";
	/** Letters a tick; how long a line stays once it's all out before it goes on by itself (ticks). */
	static final float CHARS_PER_TICK = 1.5F;
	static final int READ_TICKS = 50;
	/** How long your own reply is on before their answer (ticks); a rite's line held in (ticks); the last line's stay (ticks). */
	static final int YOUR_HOLD = 35, RITE_HOLD = 40, LAST_HOLD = 100;
	/** The box: margin, width, padding, gap above the lower bar (GUI pixels); reply buttons' width. */
	static final int MARGIN = 10, BOX_W = 220, PAD = 6, GAP = 8, REPLY_W = 170;
	static final int BOX_FILL = 0xC0100010, BORDER_TOP = 0x905000FF, BORDER_BOTTOM = 0x9028007F, YOUR_NAME = 0xFFB0C8FF;
	/** What counts as a spoken yes and no. */
	static final List<String> YES_WORDS = List.of("yes", "yes I will", "I will", "I do", "I accept"), NO_WORDS = List.of("no", "no I won't", "I won't", "never", "not tonight");

	/** Set when the scene is ending by itself, so closing isn't also a leave. */
	boolean closing;
	private @Nullable Shot beat, queued;
	private @Nullable Component mine;
	private int ticks, mineUntil, lineAge;
	private float letters;
	private boolean sent, answersUp;
	private boolean[] said = new boolean[0];
	private float hold;
	private int riteLeft, riteTotal;
	private boolean holdingRight;

	StorySceneScreen() {
		super(Component.translatable("story.jaymoskingdoms.scene.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// the film shows through: no dimming, no blur
	}

	/** The server's next beat: shown now, or after your reply has had its moment. */
	void beat(Shot next) {
		if (this.mine != null) {
			this.queued = next;
		} else {
			show(next);
		}
	}

	private void show(Shot next) {
		this.beat = next;
		this.queued = null;
		this.letters = 0.0F;
		this.lineAge = 0;
		this.sent = false;
		this.answersUp = false;
		this.hold = 0.0F;
		StorySceneClient.showing(next);
		if (next.kind() == Shot.RITE) {
			this.said = new boolean[next.words().size()];
			this.riteLeft = this.riteTotal = Math.max(20, next.seconds() * 20);
		}
		listen();
		rebuild();
	}

	/** The microphone on what this beat wants said: yes or no, or the rite's lines not yet said. */
	private void listen() {
		Shot b = this.beat;
		if (b == null || !VoiceEars.enabled()) {
			VoiceEars.stop(EARS);
			return;
		}
		if (b.kind() == Shot.ASK) {
			List<String> all = new ArrayList<>(YES_WORDS);
			all.addAll(NO_WORDS);
			VoiceEars.listen(EARS, all, (phrase, confidence) -> {
				String p = plain(phrase);
				if (YES_WORDS.stream().anyMatch(w -> plain(w).equals(p))) {
					ask(StoryScenes.YES);
				} else if (NO_WORDS.stream().anyMatch(w -> plain(w).equals(p))) {
					ask(StoryScenes.NO);
				}
			});
		} else if (b.kind() == Shot.RITE) {
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < b.words().size(); i++) {
				if (!this.said[i]) {
					lines.add(plain(b.words().get(i).getString()));
				}
			}
			if (lines.isEmpty()) {
				VoiceEars.stop(EARS);
				return;
			}
			VoiceEars.listen(EARS, lines, (phrase, confidence) -> {
				String p = plain(phrase);
				for (int i = 0; i < b.words().size(); i++) {
					if (!this.said[i] && plain(b.words().get(i).getString()).equals(p)) {
						saidLine(i);
						return;
					}
				}
			});
		} else {
			VoiceEars.stop(EARS);
		}
	}

	/** Lower case, letters and spaces only: how phrases are matched. */
	static String plain(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replace("'", "").replaceAll(" +", " ").trim();
	}

	private boolean lineOut() {
		Shot b = this.beat;
		return b == null || this.letters >= b.line().getString().length();
	}

	@Override
	protected void init() {
		rebuild();
	}

	/** The buttons for what's up now: replies, a yes and a no, the microphone. */
	private void rebuild() {
		clearWidgets();
		Shot b = this.beat;
		if (b == null || this.mine != null) {
			return;
		}
		int bar = StorySceneClient.bar(this.height, 1.0F);
		if (b.kind() == Shot.REPLIES && this.answersUp) {
			int x = this.width - MARGIN - REPLY_W;
			int y = this.height - Math.max(bar, (int)(this.height * StorySceneClient.BAR_SHARE)) - GAP - b.replies().size() * (Ui.BUTTON_H + 3);
			for (int i = 0; i < b.replies().size(); i++) {
				int index = i;
				Component text = b.replies().get(i);
				addRenderableWidget(FlatButton.builder(text, button -> reply(index, text)).bounds(x, y, REPLY_W, Ui.BUTTON_H).build().accent(0xFF000000 | b.color()));
				y += Ui.BUTTON_H + 3;
			}
		}
		if (b.kind() == Shot.ASK && this.answersUp) {
			int cx = this.width / 2, y = this.height / 2 + 14;
			addRenderableWidget(FlatButton.builder(Component.translatable("story.jaymoskingdoms.scene.yes"), button -> ask(StoryScenes.YES))
				.bounds(cx - 74, y, 70, Ui.BUTTON_H).build().accent(Ui.GOOD));
			addRenderableWidget(FlatButton.builder(Component.translatable("story.jaymoskingdoms.scene.no"), button -> ask(StoryScenes.NO))
				.bounds(cx + 4, y, 70, Ui.BUTTON_H).build().accent(Ui.BAD));
		}
		if ((b.kind() == Shot.ASK || b.kind() == Shot.RITE) && VoiceEars.possible()) {
			Component mic = Component.translatable(VoiceEars.enabled() ? "story.jaymoskingdoms.scene.mic_on" : "story.jaymoskingdoms.scene.mic_off");
			int w = Math.max(110, this.font.width(mic) + 12);
			addRenderableWidget(FlatButton.builder(mic, button -> {
				VoiceEars.setEnabled(!VoiceEars.enabled());
				listen();
				rebuild();
			}).bounds(this.width - MARGIN - w, Math.max(bar, (int)(this.height * StorySceneClient.BAR_SHARE)) + 6, w, Ui.BUTTON_H).build());
		}
	}

	private void reply(int index, Component text) {
		this.mine = text;
		this.mineUntil = this.ticks + YOUR_HOLD;
		this.letters = 0.0F;
		StorySceneClient.frame("you");
		StorySceneClient.answer(index);
		rebuild();
	}

	private void ask(int choice) {
		Shot b = this.beat;
		if (b == null || b.kind() != Shot.ASK || this.sent) {
			return;
		}
		this.sent = true;
		VoiceEars.stop(EARS);
		StorySceneClient.answer(choice);
		clearWidgets();
	}

	private void saidLine(int i) {
		if (i < 0 || i >= this.said.length || this.said[i]) {
			return;
		}
		this.said[i] = true;
		this.hold = 0.0F;
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.6F + 0.15F * i);
		}
		boolean all = true;
		for (boolean s : this.said) {
			all &= s;
		}
		if (all && !this.sent) {
			this.sent = true;
			VoiceEars.stop(EARS);
			StorySceneClient.answer(StoryScenes.SAID);
		} else {
			listen();
		}
	}

	@Override
	public void tick() {
		this.ticks++;
		this.lineAge++;
		this.letters += CHARS_PER_TICK;
		if (this.mine != null && this.ticks >= this.mineUntil) {
			this.mine = null;
			if (this.queued != null) {
				show(this.queued);
			} else {
				rebuild();
			}
			return;
		}
		Shot b = this.beat;
		if (b == null || this.mine != null) {
			return;
		}
		if (!this.answersUp && lineOut()) {
			this.answersUp = true;
			rebuild();
		}
		switch (b.kind()) {
			case Shot.LINE -> {
				if (lineOut() && !this.sent && this.lineAge > b.line().getString().length() / CHARS_PER_TICK + READ_TICKS) {
					goOn();
				}
			}
			case Shot.LAST -> {
				if (this.lineAge > b.line().getString().length() / CHARS_PER_TICK + LAST_HOLD) {
					StorySceneClient.end(false);
				}
			}
			case Shot.RITE -> {
				if (this.sent) {
					return;
				}
				if (--this.riteLeft <= 0) {
					this.sent = true;
					VoiceEars.stop(EARS);
					StorySceneClient.answer(StoryScenes.LATE);
					return;
				}
				int next = nextLine();
				if (this.holdingRight && next >= 0) {
					this.hold += 1.0F / RITE_HOLD;
					if (this.hold >= 1.0F) {
						saidLine(next);
					}
				}
			}
			default -> {
			}
		}
	}

	private int nextLine() {
		for (int i = 0; i < this.said.length; i++) {
			if (!this.said[i]) {
				return i;
			}
		}
		return -1;
	}

	/** A click or Space on a plain line: all of it at once, or on to the next. */
	private void goOn() {
		Shot b = this.beat;
		if (b == null || this.mine != null) {
			return;
		}
		if (!lineOut()) {
			this.letters = b.line().getString().length();
			return;
		}
		if (b.kind() == Shot.LINE && !this.sent) {
			this.sent = true;
			StorySceneClient.answer(StoryScenes.GO_ON);
		} else if (b.kind() == Shot.LAST) {
			StorySceneClient.end(false);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN) {
			goOn();
			return true;
		}
		if (this.beat != null && this.beat.kind() == Shot.ASK && this.answersUp) {
			if (key == InputConstants.KEY_Y) {
				ask(StoryScenes.YES);
				return true;
			}
			if (key == InputConstants.KEY_N) {
				ask(StoryScenes.NO);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.holdingRight = true;
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			goOn();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.holdingRight = false;
			this.hold = 0.0F;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		if (!this.closing) {
			this.closing = true;
			StorySceneClient.end(true);
		}
		super.onClose();
	}

	@Override
	public void removed() {
		VoiceEars.stop(EARS);
		super.removed();
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Shot b = this.beat;
		if (b != null) {
			int bar = Math.max(StorySceneClient.bar(this.height, a), 0);
			if (this.mine != null) {
				box(g, Component.translatable("story.jaymoskingdoms.scene.you"), YOUR_NAME, this.mine, this.mine.getString().length(), bar);
			} else if (!b.line().getString().isEmpty()) {
				box(g, b.speaker(), 0xFF000000 | b.color(), b.line(), (int)this.letters, bar);
			}
			if (this.mine == null && b.kind() == Shot.ASK && this.answersUp) {
				question(g, b);
			}
			if (this.mine == null && b.kind() == Shot.RITE) {
				rite(g, b);
			}
			if (b.kind() == Shot.LAST && !b.gifts().isEmpty()) {
				gifts(g, b, bar);
			}
			if (b.kind() == Shot.ASK || b.kind() == Shot.RITE) {
				micNote(g, bar);
			}
			Component hint = Component.translatable("story.jaymoskingdoms.scene.leave");
			g.text(this.font, hint, this.width - this.font.width(hint) - 8, this.height - bar / 2 - 4, 0xFF777777);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** The speaker's box, low on the left: their name, then as much of the line as is out. */
	private void box(GuiGraphicsExtractor g, Component name, int nameColor, Component line, int shown, int bar) {
		String full = line.getString();
		String part = full.substring(0, Mth.clamp(shown, 0, full.length()));
		List<FormattedCharSequence> rows = this.font.split(Component.literal(part), BOX_W - PAD * 2);
		int lines = Math.max(1, this.font.split(line, BOX_W - PAD * 2).size());
		int x0 = MARGIN, x1 = MARGIN + BOX_W;
		int y1 = this.height - bar - GAP, y0 = y1 - PAD * 2 - (lines + 1) * 10 - 2;
		g.fill(x0, y0, x1, y1, BOX_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, BORDER_BOTTOM);
		int y = y0 + PAD;
		g.text(this.font, name, x0 + PAD, y, nameColor);
		y += 12;
		for (FormattedCharSequence row : rows) {
			g.text(this.font, row, x0 + PAD, y, 0xFFFFFFFF);
			y += 10;
		}
	}

	/** The question on its own in the middle. */
	private void question(GuiGraphicsExtractor g, Shot b) {
		List<FormattedCharSequence> rows = this.font.split(b.question(), 260);
		int y = this.height / 2 - 6 - rows.size() * 10;
		int w = 0;
		for (FormattedCharSequence row : rows) {
			w = Math.max(w, this.font.width(row));
		}
		Ui.panel(g, this.width / 2 - w / 2 - 12, y - 8, w + 24, rows.size() * 10 + 44, 0xFF000000 | b.color());
		for (FormattedCharSequence row : rows) {
			g.text(this.font, row, this.width / 2 - this.font.width(row) / 2, y, 0xFFFFFFFF);
			y += 10;
		}
		Component how = Component.translatable(VoiceEars.listening() ? "story.jaymoskingdoms.scene.ask_say" : "story.jaymoskingdoms.scene.ask_click");
		g.centeredText(this.font, how, this.width / 2, this.height / 2 + 34, 0xFF999999);
	}

	/** The rite's lines: said ones lit, the one being held filling, the rest dim; the time left above. */
	private void rite(GuiGraphicsExtractor g, Shot b) {
		int w = 0;
		for (Component c : b.words()) {
			w = Math.max(w, this.font.width(c));
		}
		w = Math.max(w + 24, 200);
		int h = 26 + b.words().size() * 14 + 18;
		int x = this.width / 2 - w / 2, y = this.height / 2 - h / 2 - 10;
		int accent = 0xFF000000 | b.color();
		Ui.panel(g, x, y, w, h, accent);
		float left = this.riteTotal <= 0 ? 0.0F : (float)this.riteLeft / this.riteTotal;
		g.fill(x + 6, y + 6, x + 6 + Math.round((w - 12) * left), y + 8, accent);
		Component head = Component.translatable("story.jaymoskingdoms.scene.rite");
		g.centeredText(this.font, head, this.width / 2, y + 12, 0xFFCCCCCC);
		int next = nextLine();
		int ly = y + 26;
		for (int i = 0; i < b.words().size(); i++) {
			Component line = b.words().get(i);
			int color = this.said[i] ? 0xFF000000 | brighten(b.color()) : i == next ? 0xFFFFFFFF : 0xFF666666;
			int lx = this.width / 2 - this.font.width(line) / 2;
			g.text(this.font, line, lx, ly, color);
			if (i == next && this.hold > 0.0F) {
				g.fill(lx, ly + 10, lx + Math.round(this.font.width(line) * Math.min(1.0F, this.hold)), ly + 11, accent);
			}
			ly += 14;
		}
		Component how = Component.translatable(VoiceEars.listening() ? "story.jaymoskingdoms.scene.rite_say" : "story.jaymoskingdoms.scene.rite_hold");
		g.centeredText(this.font, how, this.width / 2, y + h - 12, 0xFF999999);
	}

	private static int brighten(int rgb) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + 90), gr = Math.min(255, ((rgb >> 8) & 0xFF) + 90), bl = Math.min(255, (rgb & 0xFF) + 90);
		return (r << 16) | (gr << 8) | bl;
	}

	/** What the scene gave you, under the top bar. */
	private void gifts(GuiGraphicsExtractor g, Shot b, int bar) {
		int w = 0;
		for (Component c : b.gifts()) {
			w = Math.max(w, this.font.width(c));
		}
		Component head = Component.translatable("story.jaymoskingdoms.scene.received");
		w = Math.max(w, this.font.width(head)) + 20;
		int h = 16 + b.gifts().size() * 11 + 6;
		int x = this.width / 2 - w / 2, y = bar + 10;
		Ui.panel(g, x, y, w, h, 0xFF000000 | b.color());
		g.centeredText(this.font, head, this.width / 2, y + 5, Ui.GOLD);
		int ly = y + 18;
		for (Component c : b.gifts()) {
			g.centeredText(this.font, c, this.width / 2, ly, 0xFFFFFFFF);
			ly += 11;
		}
	}

	/** Why the microphone isn't hearing, when it should be. */
	private void micNote(GuiGraphicsExtractor g, int bar) {
		Component note = null;
		if (!VoiceEars.possible()) {
			note = Component.translatable("story.jaymoskingdoms.scene.mic_none");
		} else if (VoiceEars.enabled() && VoiceEars.problem() != null) {
			note = Component.translatable("story.jaymoskingdoms.scene.mic_problem", VoiceEars.problem());
		}
		if (note != null) {
			g.text(this.font, note, MARGIN, bar + 8, 0xFFAA7777);
		}
	}
}
