package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterDrills;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterItems;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The Training Post's drills, each about twenty seconds, scored 0-100 and paid in TP by the server
 * ({@link ZFighterDrills}):
 * <ul>
 * <li>Rhythm: notes fall in four lanes; hit W A S D as each crosses the line.</li>
 * <li>Ki Control: hold Space to lift your ki, let go and it sinks; keep it inside the drifting band.</li>
 * <li>Shadow Boxing: PUNCH (left click), BLOCK (right click) or DODGE (Space), quickly.</li>
 * <li>Precision: click each target before it shrinks away; the sooner, the more it's worth.</li>
 * <li>(0.17.60) Memory: four coloured pads light up in a sequence; repeat it (W A S D, the arrow keys or a click on each
 * pad). Each round is one longer; one slip ends the drill, which pays for the rounds got through.</li>
 * </ul>
 * (0.11.0) Restyled like the H screen: the drills as striped rows (click one to start it) with the box at the bottom
 * showing the hovered one's rules; while one runs, its name and points sit in the header with a bar for how far along it
 * is, and its rules in the box under the arena.
 */
public class ZFighterDrillScreen extends Screen {
	private static final int W = 260;
	private static final int H = 190;
	private static final int ARENA_TOP = UiStyle.HEADER;
	/** The menu's rows, and the lines of the box under them (or under the arena). */
	private static final int MENU_ROW = 18;
	private static final int MENU_DETAIL_LINES = 4;
	private static final int DRILL_DETAIL_LINES = 2;
	private static final int ARENA_H = 120;
	private static final int ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int COUNT = 20;

	private static final int[] LANE_KEYS = {InputConstants.KEY_W, InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D};
	private static final String[] LANE_NAMES = {"W", "A", "S", "D"};
	private static final int NOTE_FALL = 40;
	private static final int NOTE_GAP = 12;
	private static final int NOTE_WINDOW = 4;

	private static final int KI_TICKS = 400;
	private static final int BOX_WINDOW = 14;
	private static final int TARGET_LIFE = 40;
	private static final float TARGET_SIZE = 14.0F;

	/** (0.17.60) Memory: the first round's length, how many rounds there are, and the sum of every round's length (full marks). */
	private static final int MEM_START = 3, MEM_ROUNDS = 9, MEM_TOTAL = 63;
	/** How long each pad of the sequence is lit and dark (ticks), the wait between rounds, and the longest wait for a press. */
	private static final int MEM_LIT = 14, MEM_GAP = 6, MEM_PAUSE = 24, MEM_TIMEOUT = 160;
	/** A pad's size and its gap from the middle, and how long a pad you press stays lit. */
	private static final int MEM_PAD = 34, MEM_SPREAD = 38, MEM_PRESS = 6;
	/** The pads: up, right, down, left; their keys (W D S A) and their colours. */
	private static final int[] MEM_KEYS = {InputConstants.KEY_W, InputConstants.KEY_D, InputConstants.KEY_S, InputConstants.KEY_A};
	private static final int[] MEM_ARROWS = {InputConstants.KEY_UP, InputConstants.KEY_RIGHT, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT};
	private static final int[] MEM_COLORS = {0xFFE05050, 0xFF4A90E0, 0xFF50C878, 0xFFE8C040};

	private final RandomSource random = RandomSource.create();
	/** -1: the menu; otherwise the drill being played. */
	private int drill = -1;
	private int ticks;
	private int score;
	private int lastScore = -1;
	private int lastDrill = -1;

	// rhythm: each note's lane and spawn tick, and whether it's been hit
	private final int[] noteLane = new int[COUNT];
	private final boolean[] noteDone = new boolean[COUNT];
	// ki control
	private float meter = 0.5F;
	private boolean holding;
	private int inBand;
	// shadow boxing: the prompt (0 punch, 1 block, 2 dodge, -1 waiting), since when, and how many shown
	private int prompt = -1;
	private int promptAt;
	private int nextPromptAt;
	private int prompts;
	// precision: the target's place, since when, how many shown
	private float targetX;
	private float targetY;
	private int targetAt;
	private int targets;
	private float points;
	// memory: the whole sequence, the round (0-based) and its length, shown (0) or being repeated (1) or between rounds (2) or lost (3),
	// ticks into that, how far into the sequence, the pad lit now and until when, the rounds' lengths got through
	private final int[] memSeq = new int[MEM_START + MEM_ROUNDS];
	private int memRound;
	private int memPhase;
	private int memT;
	private int memStep;
	private int memLit = -1;
	private int memLitUntil;
	private int memDone;

	public ZFighterDrillScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.drills"));
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int menuRowY(int i) {
		return top() + UiStyle.HEADER + i * MENU_ROW;
	}

	/** The menu row under the mouse, or -1. */
	private int menuRowAt(double mouseX, double mouseY) {
		for (int i = 0; i < ZFighterDrills.DRILLS; i++) {
			if (UiStyle.over((int)mouseX, (int)mouseY, left() + 6, menuRowY(i), W - 12, MENU_ROW - 2)) {
				return i;
			}
		}
		return -1;
	}

	private static String key(int drill) {
		return "drill.jaymospowers.zfighter." + ZFighterDrills.KEYS[drill];
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void start(int which) {
		drill = which;
		ticks = 0;
		score = 0;
		points = 0.0F;
		meter = 0.5F;
		inBand = 0;
		prompt = -1;
		prompts = 0;
		nextPromptAt = 20;
		targets = 0;
		targetAt = -TARGET_LIFE;
		memRound = 0;
		memPhase = 0;
		memT = 0;
		memStep = 0;
		memLit = -1;
		memDone = 0;
		for (int i = 0; i < memSeq.length; i++) {
			memSeq[i] = random.nextInt(4);
		}
		for (int i = 0; i < COUNT; i++) {
			noteLane[i] = random.nextInt(4);
			noteDone[i] = false;
		}
	}

	private void finish() {
		int result = Mth.clamp(Math.round(drill == ZFighterDrills.PRECISION ? points : score), 0, ZFighterDrills.MAX_SCORE);
		ClientPlayNetworking.send(new ZFighterDrills.Done(drill, result));
		lastScore = result;
		lastDrill = drill;
		drill = -1;
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.2F);
		}
	}

	@Override
	public void tick() {
		if (drill < 0) {
			return;
		}
		ticks++;
		switch (drill) {
			case ZFighterDrills.RHYTHM -> {
				if (ticks > NOTE_GAP * COUNT + NOTE_FALL + NOTE_WINDOW + 10) {
					finish();
				}
			}
			case ZFighterDrills.KI_CONTROL -> {
				meter = Mth.clamp(meter + (holding ? 0.028F : -0.024F), 0.0F, 1.0F);
				float band = band();
				if (Math.abs(meter - band) <= 0.1F) {
					inBand++;
				}
				score = Math.round(100.0F * inBand / KI_TICKS);
				if (ticks >= KI_TICKS) {
					finish();
				}
			}
			case ZFighterDrills.SHADOW_BOXING -> {
				if (prompt >= 0 && ticks - promptAt > BOX_WINDOW) {
					// too slow
					prompt = -1;
					nextPromptAt = ticks + 10 + random.nextInt(15);
				}
				if (prompt < 0 && ticks >= nextPromptAt) {
					if (prompts >= COUNT) {
						finish();
						return;
					}
					prompt = random.nextInt(3);
					promptAt = ticks;
					prompts++;
				}
			}
			case ZFighterDrills.MEMORY -> memoryTick();
			case ZFighterDrills.PRECISION -> {
				if (ticks - targetAt >= TARGET_LIFE) {
					if (targets >= COUNT) {
						finish();
						return;
					}
					targets++;
					targetAt = ticks;
					targetX = 20 + random.nextFloat() * (W - 40);
					targetY = 15 + random.nextFloat() * (ARENA_H - 30);
				}
			}
			default -> {
			}
		}
	}

	private int memLength() {
		return MEM_START + memRound;
	}

	private void memoryTick() {
		memT++;
		if (memLit >= 0 && ticks >= memLitUntil) {
			memLit = -1;
		}
		switch (memPhase) {
			case 0 -> {
				// the sequence, one pad at a time, then it's your turn
				int slot = MEM_LIT + MEM_GAP;
				int shown = memT / slot;
				if (shown < memLength() && memT % slot == 0) {
					memLit = memSeq[shown];
					memLitUntil = ticks + MEM_LIT;
					click(0.9F + memLit * 0.25F);
				}
				if (memT >= memLength() * slot + 8) {
					memPhase = 1;
					memT = 0;
					memStep = 0;
				}
			}
			case 1 -> {
				if (memT > MEM_TIMEOUT) {
					memPhase = 3;
					memT = 0;
				}
			}
			case 2 -> {
				if (memT >= MEM_PAUSE) {
					memPhase = 0;
					memT = 0;
				}
			}
			default -> {
				if (memT >= MEM_PAUSE) {
					finish();
				}
			}
		}
	}

	/** A pad pressed (by key or click) while repeating the sequence. */
	private boolean memoryPress(int pad) {
		if (drill != ZFighterDrills.MEMORY || memPhase != 1) {
			return false;
		}
		memLit = pad;
		memLitUntil = ticks + MEM_PRESS;
		memT = 0;
		if (pad != memSeq[memStep]) {
			memPhase = 3;
			click(0.5F);
			return true;
		}
		click(0.9F + pad * 0.25F);
		memStep++;
		if (memStep >= memLength()) {
			memDone += memLength();
			score = Math.round(100.0F * memDone / MEM_TOTAL);
			memRound++;
			memPhase = memRound >= MEM_ROUNDS ? 3 : 2;
			memT = 0;
			if (memRound < MEM_ROUNDS && this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.4F);
			}
		}
		return true;
	}

	/** The pad's box on screen: 0 up, 1 right, 2 down, 3 left. */
	private int[] memoryPad(int pad) {
		int cx = left() + W / 2, cy = top() + ARENA_TOP + ARENA_H / 2;
		int dx = pad == 1 ? MEM_SPREAD : pad == 3 ? -MEM_SPREAD : 0;
		int dy = pad == 0 ? -MEM_SPREAD : pad == 2 ? MEM_SPREAD : 0;
		return new int[] {cx + dx - MEM_PAD / 2, cy + dy - MEM_PAD / 2};
	}

	/** Where Ki Control's band sits (0-1) right now. */
	private float band() {
		return 0.5F + 0.32F * Mth.sin(ticks * 0.045F) * Mth.cos(ticks * 0.017F);
	}

	private boolean answer(int kind) {
		if (drill != ZFighterDrills.SHADOW_BOXING || prompt < 0) {
			return false;
		}
		if (kind == prompt) {
			score += 5;
			click(1.6F);
		} else {
			click(0.6F);
		}
		prompt = -1;
		nextPromptAt = ticks + 10 + random.nextInt(15);
		return true;
	}

	private void click(float pitch) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.6F, pitch);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (drill == ZFighterDrills.RHYTHM) {
			for (int lane = 0; lane < 4; lane++) {
				if (key == LANE_KEYS[lane]) {
					hitLane(lane);
					return true;
				}
			}
		} else if (drill == ZFighterDrills.KI_CONTROL && key == InputConstants.KEY_SPACE) {
			holding = true;
			return true;
		} else if (drill == ZFighterDrills.SHADOW_BOXING && key == InputConstants.KEY_SPACE) {
			answer(2);
			return true;
		} else if (drill == ZFighterDrills.MEMORY) {
			for (int pad = 0; pad < 4; pad++) {
				if (key == MEM_KEYS[pad] || key == MEM_ARROWS[pad]) {
					memoryPress(pad);
					return true;
				}
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE) {
			holding = false;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (drill < 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int row = menuRowAt(event.x(), event.y());
			if (row >= 0) {
				click(1.2F);
				start(row);
				return true;
			}
		}
		if (drill == ZFighterDrills.SHADOW_BOXING) {
			answer(event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 0);
			return true;
		}
		if (drill == ZFighterDrills.MEMORY) {
			for (int pad = 0; pad < 4; pad++) {
				int[] at = memoryPad(pad);
				if (UiStyle.over((int)event.x(), (int)event.y(), at[0], at[1], MEM_PAD, MEM_PAD)) {
					memoryPress(pad);
					break;
				}
			}
			return true;
		}
		if (drill == ZFighterDrills.PRECISION) {
			int age = ticks - targetAt;
			if (age < TARGET_LIFE) {
				float size = TARGET_SIZE * (1.0F - age / (float)TARGET_LIFE);
				double dx = event.x() - (left() + targetX), dy = event.y() - (top() + ARENA_TOP + targetY);
				if (dx * dx + dy * dy <= size * size) {
					points += 5.0F * Math.min(1.0F, size / TARGET_SIZE + 0.3F);
					targetAt = ticks - TARGET_LIFE;
					click(1.8F);
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void hitLane(int lane) {
		for (int i = 0; i < COUNT; i++) {
			if (noteDone[i] || noteLane[i] != lane) {
				continue;
			}
			int arrive = i * NOTE_GAP + NOTE_FALL;
			if (Math.abs(ticks - arrive) <= NOTE_WINDOW) {
				noteDone[i] = true;
				score += 5;
				click(1.0F + lane * 0.2F);
				return;
			}
		}
		click(0.5F);
	}

	/** How far along the running drill is, 0-1. */
	private float progress() {
		return switch (drill) {
			case ZFighterDrills.RHYTHM -> ticks / (float)(NOTE_GAP * COUNT + NOTE_FALL + NOTE_WINDOW + 10);
			case ZFighterDrills.KI_CONTROL -> ticks / (float)KI_TICKS;
			case ZFighterDrills.SHADOW_BOXING -> prompts / (float)COUNT;
			case ZFighterDrills.PRECISION -> targets / (float)COUNT;
			case ZFighterDrills.MEMORY -> memRound / (float)MEM_ROUNDS;
			default -> 0.0F;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		ItemStack icon = ZFighterItems.TRAINING_POST_ITEM == null ? null : new ItemStack(ZFighterItems.TRAINING_POST_ITEM);
		if (drill < 0) {
			UiStyle.header(graphics, this.font, x, y, W, icon, this.title, Component.translatable("screen.jaymospowers.zfighter.drills_sub"), ACCENT, 0);
			int hovered = menuRowAt(mouseX, mouseY);
			for (int i = 0; i < ZFighterDrills.DRILLS; i++) {
				int ry = menuRowY(i);
				UiStyle.row(graphics, x + 6, ry, x + W - 6, ry + MENU_ROW - 2, i, i == hovered);
				UiStyle.line(graphics, this.font, Component.translatable(key(i)), x + 12, ry + 4, 80, UiStyle.TEXT);
				UiStyle.line(graphics, this.font, Component.translatable(key(i) + ".help"), x + 96, ry + 4, W - 96 - 12, UiStyle.DIM);
			}
			int dy = y + H - UiStyle.detailHeight(MENU_DETAIL_LINES);
			if (hovered >= 0) {
				UiStyle.detail(graphics, this.font, x, dy, W, MENU_DETAIL_LINES, Component.translatable(key(hovered)), ACCENT,
					List.of(Component.translatable(key(hovered) + ".help")));
			} else {
				Component lead = Component.translatable("screen.jaymospowers.zfighter.drills_help");
				Component hint = Component.translatable("screen.jaymospowers.zfighter.drills_pick").withColor(UiStyle.HINT & 0xFFFFFF);
				List<Component> text = lastScore < 0 ? List.of(lead, hint)
					: List.of(Component.translatable("screen.jaymospowers.zfighter.drill_score", Component.translatable(key(lastDrill)), lastScore)
						.withColor(UiStyle.TEXT & 0xFFFFFF), lead, hint);
				UiStyle.detail(graphics, this.font, x, dy, W, MENU_DETAIL_LINES, null, ACCENT, text);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		// running: the drill and its points up top, how far along it is under them
		int barW = 70;
		UiStyle.header(graphics, this.font, x, y, W, icon, Component.translatable(key(drill)), this.title, ACCENT, W - 28 - barW - 16);
		int shown = drill == ZFighterDrills.PRECISION ? Math.round(points) : score;
		UiStyle.lineRight(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter.drill_points", shown), x + W - 8, y + 7, barW,
			UiStyle.TEXT);
		UiStyle.bar(graphics, x + W - 8 - barW, y + 19, barW, 3, progress(), ACCENT);
		int ay = y + ARENA_TOP;
		graphics.fill(x + 6, ay, x + W - 6, ay + ARENA_H, 0x60000000);
		switch (drill) {
			case ZFighterDrills.RHYTHM -> rhythm(graphics, x, ay, a);
			case ZFighterDrills.KI_CONTROL -> kiControl(graphics, x, ay);
			case ZFighterDrills.SHADOW_BOXING -> boxing(graphics, x, ay);
			case ZFighterDrills.PRECISION -> precision(graphics, x, ay, a);
			case ZFighterDrills.MEMORY -> memory(graphics, x, ay);
			default -> {
			}
		}
		UiStyle.detail(graphics, this.font, x, y + H - UiStyle.detailHeight(DRILL_DETAIL_LINES), W, DRILL_DETAIL_LINES, null, ACCENT,
			List.of(Component.translatable(key(drill) + ".help")));
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void rhythm(GuiGraphicsExtractor graphics, int x, int ay, float a) {
		int laneW = 40;
		int lx = x + W / 2 - laneW * 2;
		int line = ay + ARENA_H - 20;
		for (int lane = 0; lane < 4; lane++) {
			graphics.fill(lx + lane * laneW + 1, ay, lx + (lane + 1) * laneW - 1, ay + ARENA_H, 0x30FFFFFF);
			graphics.centeredText(this.font, LANE_NAMES[lane], lx + lane * laneW + laneW / 2, line + 6, 0xFFFFFFFF);
		}
		graphics.fill(lx, line, lx + laneW * 4, line + 2, ACCENT);
		float now = ticks + a;
		for (int i = 0; i < COUNT; i++) {
			if (noteDone[i]) {
				continue;
			}
			float t = (now - i * NOTE_GAP) / NOTE_FALL;
			if (t < 0.0F || t > 1.15F) {
				continue;
			}
			int ny = ay + Math.round(t * (line - ay));
			int nx = lx + noteLane[i] * laneW + 6;
			graphics.fill(nx, ny - 3, nx + laneW - 12, ny + 3, 0xFFFFD54A);
		}
	}

	private void kiControl(GuiGraphicsExtractor graphics, int x, int ay) {
		int bx = x + W / 2 - 15;
		graphics.fill(bx, ay + 4, bx + 30, ay + ARENA_H - 4, UiStyle.BAR_BACK);
		int span = ARENA_H - 8;
		float band = band();
		int bandTop = ay + 4 + Math.round((1.0F - band - 0.1F) * span);
		graphics.fill(bx - 6, bandTop, bx + 36, bandTop + Math.round(0.2F * span), 0x5040C0FF);
		int my = ay + 4 + Math.round((1.0F - meter) * span);
		graphics.fill(bx + 2, my - 2, bx + 28, my + 2, Math.abs(meter - band) <= 0.1F ? 0xFF7CFF7C : 0xFFFF8C1A);
		int left = Math.max(0, KI_TICKS - ticks);
		graphics.text(this.font, (left + 19) / 20 + "s", x + 12, ay + 6, UiStyle.DIM);
	}

	private void boxing(GuiGraphicsExtractor graphics, int x, int ay) {
		if (prompt >= 0) {
			String key = prompt == 0 ? "punch" : prompt == 1 ? "block" : "dodge";
			int color = prompt == 0 ? 0xFFFF6A50 : prompt == 1 ? 0xFF7CB0FF : 0xFF7CFF7C;
			graphics.centeredText(this.font, Component.translatable("drill.jaymospowers.zfighter.box_" + key), this.width / 2, ay + ARENA_H / 2 - 4, color);
			float left = 1.0F - (ticks - promptAt) / (float)BOX_WINDOW;
			UiStyle.bar(graphics, x + W / 2 - 40, ay + ARENA_H / 2 + 10, 80, 3, left, color);
		}
		graphics.text(this.font, prompts + "/" + COUNT, x + 12, ay + 6, UiStyle.DIM);
	}

	private void memory(GuiGraphicsExtractor graphics, int x, int ay) {
		for (int pad = 0; pad < 4; pad++) {
			int[] at = memoryPad(pad);
			boolean lit = pad == memLit;
			int color = MEM_COLORS[pad];
			graphics.fill(at[0], at[1], at[0] + MEM_PAD, at[1] + MEM_PAD, lit ? color : (color & 0xFFFFFF) | 0x50000000);
			graphics.outline(at[0], at[1], MEM_PAD, MEM_PAD, lit ? 0xFFFFFFFF : 0x80FFFFFF);
			String key = Component.translatable("drill.jaymospowers.zfighter.memory_pad_" + pad).getString();
			graphics.text(this.font, key, at[0] + (MEM_PAD - this.font.width(key)) / 2, at[1] + MEM_PAD / 2 - 4, lit ? 0xFF000000 : 0xFFE0E0E0);
		}
		boolean perfect = memRound >= MEM_ROUNDS;
		String state = memPhase == 0 ? "watch" : memPhase == 1 ? "repeat" : memPhase == 2 ? "good" : perfect ? "perfect" : "over";
		int color = memPhase == 3 && !perfect ? 0xFFFF6A50 : memPhase >= 2 ? 0xFF7CFF7C : UiStyle.TEXT;
		graphics.centeredText(this.font, Component.translatable("drill.jaymospowers.zfighter.memory_" + state), x + W / 2, ay + 6, color);
		graphics.text(this.font, Component.translatable("drill.jaymospowers.zfighter.memory_round", Math.min(memRound + 1, MEM_ROUNDS), MEM_ROUNDS).getString(),
			x + 12, ay + 6, UiStyle.DIM);
		if (memPhase == 1) {
			// how long you have to press, and how far through the sequence you are
			UiStyle.bar(graphics, x + W / 2 - 40, ay + ARENA_H - 10, 80, 3, 1.0F - memT / (float)MEM_TIMEOUT, ACCENT);
			graphics.text(this.font, memStep + "/" + memLength(), x + W - 12 - this.font.width(memStep + "/" + memLength()), ay + 6, UiStyle.DIM);
		}
	}

	private void precision(GuiGraphicsExtractor graphics, int x, int ay, float a) {
		float age = ticks - targetAt + a;
		if (age < TARGET_LIFE) {
			int size = Math.max(1, Math.round(TARGET_SIZE * (1.0F - age / TARGET_LIFE)));
			int cx = x + Math.round(targetX);
			int cy = ay + Math.round(targetY);
			graphics.fill(cx - size, cy - size, cx + size, cy + size, 0xFFFF8C1A);
			int inner = Math.max(1, size / 2);
			graphics.fill(cx - inner, cy - inner, cx + inner, cy + inner, 0xFFFFFFFF);
		}
		graphics.text(this.font, targets + "/" + COUNT, x + 12, ay + 6, UiStyle.DIM);
	}
}
