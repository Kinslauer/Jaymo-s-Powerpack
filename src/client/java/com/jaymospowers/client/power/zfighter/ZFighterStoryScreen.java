package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSagas;
import com.jaymospowers.power.zfighter.ZFighterSideQuests;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * (0.17.61, Andy's NOEA picks) The Story screen, opened from the training screen: the sagas and what each needs, a
 * difficulty to start one at, the step you are on and its count, and your side jobs. Everything it shows is the
 * player's synced saga and job data; the buttons ask the server ({@link ZFighterNet}).
 */
public class ZFighterStoryScreen extends Screen {
	private static final int MAX_W = 340;
	private static final int H = 236;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_ROW = 0x30FFFFFF;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;
	private static final int COLOR_ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int COLOR_GOLD = 0xFF000000 | ZFighterPower.SUPER_COLOR;
	private static final int BUTTON_W = 54;
	private static final int ROW = 16;

	/** The difficulty the next saga starts at (kept between openings). */
	private static int difficulty = ZFighterSagas.NORMAL;

	private final Screen previous;
	private final List<Button> startButtons = new ArrayList<>();
	private final List<Button> jobButtons = new ArrayList<>();
	private Button difficultyButton;
	private Button abandonButton;

	public ZFighterStoryScreen(Screen previous) {
		super(Component.translatable("screen.jaymospowers.zfighter_story.title"));
		this.previous = previous;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int buttonX() {
		return left() + panelW() - BUTTON_W - 6;
	}

	/** Where the saga rows start. */
	private int sagasY() {
		return top() + 52;
	}

	private int questY() {
		return sagasY() + ZFighterSagas.sagaCount() * ROW + 10;
	}

	private int jobsY() {
		return top() + H - 8 - ZFighterSideQuests.MAX_JOBS * ROW - 12;
	}

	@Override
	protected void init() {
		startButtons.clear();
		jobButtons.clear();
		int w = panelW();
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_story.back"), b -> onClose())
			.bounds(left() + 8, top() + 7, 44, 14).build());
		difficultyButton = addRenderableWidget(Button.builder(Component.empty(), b -> difficulty = (difficulty + 1) % ZFighterSagas.DIFFICULTY_KEYS.length)
			.bounds(left() + w - 92, top() + 28, 86, 16).build());
		for (int i = 0; i < ZFighterSagas.sagaCount(); i++) {
			int saga = i;
			startButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_story.begin"),
				b -> send(ZFighterNet.SAGA_START, saga * 3 + difficulty)).bounds(buttonX(), sagasY() + i * ROW - 3, BUTTON_W, 14).build()));
		}
		abandonButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_story.abandon"),
			b -> send(ZFighterNet.SAGA_ABANDON, 0)).bounds(buttonX(), questY() - 3, BUTTON_W, 14).build());
		for (int i = 0; i < ZFighterSideQuests.MAX_JOBS; i++) {
			int job = i;
			jobButtons.add(addRenderableWidget(Button.builder(Component.literal("x"), b -> send(ZFighterNet.JOB_ABANDON, job))
				.bounds(left() + w - 26, jobsY() + 12 + i * ROW - 3, 20, 14).build()));
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.previous);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static void send(int action, int index) {
		ClientPlayNetworking.send(new ZFighterNet.Train(action, index));
	}

	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		String s = text.getString();
		if (this.font.width(s) > width) {
			s = this.font.plainSubstrByWidth(s, width - this.font.width("...")) + "...";
		}
		graphics.text(this.font, s, x, y, color);
	}

	private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int lines, int color) {
		var split = this.font.split(text, width);
		for (int i = 0; i < Math.min(lines, split.size()); i++) {
			graphics.text(this.font, split.get(i), x, y + i * 9, color);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		ZFighterSagas.Data data = ZFighterSagas.of(player);
		int x = left();
		int y = top();
		int w = panelW();
		graphics.fill(x, y, x + w, y + H, COLOR_PANEL);
		graphics.fill(x, y, x + w, y + 2, COLOR_ACCENT);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 6, COLOR_ACCENT);
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter_story.difficulty"), x + 8, y + 32, COLOR_DIM);
		difficultyButton.setMessage(Component.translatable("screen.jaymospowers.zfighter_story." + ZFighterSagas.DIFFICULTY_KEYS[difficulty]));
		difficultyButton.active = !data.active();
		// the sagas
		for (int i = 0; i < ZFighterSagas.sagaCount(); i++) {
			int ry = sagasY() + i * ROW;
			graphics.fill(x + 6, ry - 3, x + w - 6, ry + ROW - 4, COLOR_ROW);
			boolean done = data.finished(i);
			boolean open = ZFighterSagas.unlocked(data, i);
			boolean current = data.saga() == i;
			int color = done ? COLOR_GOLD : current ? COLOR_ACCENT : open ? COLOR_TEXT : COLOR_DIM;
			String state = done ? "done" : current ? "current" : open ? "open" : "locked";
			line(graphics, Component.translatable("screen.jaymospowers.zfighter_story.saga", Component.translatable("saga.jaymospowers." + ZFighterSagas.key(i) + ".title"),
				ZFighterSagas.stepCount(i), Component.translatable("screen.jaymospowers.zfighter_story." + state)), x + 10, ry, w - BUTTON_W - 24, color);
			Button begin = startButtons.get(i);
			begin.visible = true;
			begin.active = open && !done && !data.active();
		}
		// the step now
		int qy = questY();
		if (data.active()) {
			ZFighterSagas.Step step = ZFighterSagas.step(data.saga(), data.step());
			line(graphics, Component.translatable("screen.jaymospowers.zfighter_story.now", Component.translatable("saga.jaymospowers." + ZFighterSagas.key(data.saga()) + ".title"),
				data.step() + 1, ZFighterSagas.stepCount(data.saga()), Component.translatable("screen.jaymospowers.zfighter_story." + ZFighterSagas.DIFFICULTY_KEYS[data.difficulty()])),
				x + 8, qy, w - BUTTON_W - 22, COLOR_GOLD);
			Component what = ZFighterSagas.objective(step, data.difficulty());
			int have = ZFighterSagas.have(player, data);
			int need = ZFighterSagas.need(step, data.difficulty());
			if (have >= 0 && need > 0) {
				what = Component.translatable("screen.jaymospowers.zfighter_story.count", what, Math.min(have, need), need);
			}
			wrapped(graphics, what, x + 8, qy + 12, w - 16, 2, COLOR_TEXT);
			wrapped(graphics, Component.translatable("saga.jaymospowers." + ZFighterSagas.key(data.saga()) + ".step" + data.step()), x + 8, qy + 32, w - 16, 3, COLOR_DIM);
		} else {
			wrapped(graphics, Component.translatable("screen.jaymospowers.zfighter_story.none"), x + 8, qy, w - 16, 3, COLOR_DIM);
		}
		abandonButton.visible = data.active();
		// the jobs
		int jy = jobsY();
		graphics.fill(x + 6, jy - 3, x + w - 6, jy - 2, COLOR_ROW);
		ZFighterSideQuests.Data jobs = ZFighterSideQuests.of(player);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_story.jobs", jobs.jobs().size(), ZFighterSideQuests.MAX_JOBS), x + 8, jy, w - 16, COLOR_GOLD);
		for (int i = 0; i < jobButtons.size(); i++) {
			boolean has = i < jobs.jobs().size();
			jobButtons.get(i).visible = has;
			if (has) {
				ZFighterSideQuests.Job job = jobs.jobs().get(i);
				line(graphics, Component.translatable("screen.jaymospowers.zfighter_story.job", ZFighterSideQuests.describe(job), job.have(), job.need()),
					x + 10, jy + 12 + i * ROW, w - 40, COLOR_TEXT);
			} else if (i == 0) {
				line(graphics, Component.translatable("screen.jaymospowers.zfighter_story.no_jobs"), x + 10, jy + 12, w - 20, COLOR_DIM);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
