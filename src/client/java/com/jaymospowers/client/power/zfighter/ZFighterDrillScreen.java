package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterDrills;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The Training Post's drills, each about twenty seconds, scored 0-100 and paid in TP by the server
 * ({@link ZFighterDrills}):
 * <ul>
 * <li>Rhythm: notes fall in four lanes; hit W A S D as each crosses the line.</li>
 * <li>Ki Control: hold Space to lift your ki, let go and it sinks; keep it inside the drifting band.</li>
 * <li>Shadow Boxing: PUNCH (left click), BLOCK (right click) or DODGE (Space), quickly.</li>
 * <li>Precision: click each target before it shrinks away; the sooner, the more it's worth.</li>
 * </ul>
 */
public class ZFighterDrillScreen extends Screen {
	private static final int W = 260;
	private static final int H = 190;
	private static final int ARENA_TOP = 40;
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

	private final RandomSource random = RandomSource.create();
	private final List<Button> menu = new ArrayList<>();
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

	public ZFighterDrillScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.drills"));
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	@Override
	protected void init() {
		menu.clear();
		for (int i = 0; i < ZFighterDrills.DRILLS; i++) {
			int d = i;
			menu.add(addRenderableWidget(Button.builder(Component.translatable("drill.jaymospowers.zfighter." + ZFighterDrills.KEYS[i]), b -> start(d))
				.bounds(left() + 20 + (i % 2) * 115, top() + 50 + (i / 2) * 50, 105, 20).build()));
		}
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
		for (int i = 0; i < COUNT; i++) {
			noteLane[i] = random.nextInt(4);
			noteDone[i] = false;
		}
		for (Button button : menu) {
			button.visible = false;
		}
	}

	private void finish() {
		int result = Mth.clamp(Math.round(drill == ZFighterDrills.PRECISION ? points : score), 0, ZFighterDrills.MAX_SCORE);
		ClientPlayNetworking.send(new ZFighterDrills.Done(drill, result));
		lastScore = result;
		lastDrill = drill;
		drill = -1;
		for (Button button : menu) {
			button.visible = true;
		}
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
		if (drill == ZFighterDrills.SHADOW_BOXING) {
			answer(event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 0);
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		graphics.fill(x, y, x + W, y + H, 0xD0101018);
		graphics.fill(x, y, x + W, y + 2, ACCENT);
		Component title = drill < 0 ? this.title : Component.translatable("drill.jaymospowers.zfighter." + ZFighterDrills.KEYS[drill]);
		graphics.centeredText(this.font, title, this.width / 2, y + 6, ACCENT);
		Component help = Component.translatable(drill < 0 ? "screen.jaymospowers.zfighter.drills_help"
			: "drill.jaymospowers.zfighter." + ZFighterDrills.KEYS[drill] + ".help");
		var lines = this.font.split(help, W - 16);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			graphics.centeredText(this.font, lines.get(i), this.width / 2, y + 18 + i * 9, 0xFFA8A8B8);
		}
		if (drill < 0) {
			if (lastScore >= 0) {
				graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.zfighter.drill_score",
					Component.translatable("drill.jaymospowers.zfighter." + ZFighterDrills.KEYS[lastDrill]), lastScore), this.width / 2, y + H - 22, 0xFFFFFFFF);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		int ay = y + ARENA_TOP;
		graphics.fill(x + 6, ay, x + W - 6, ay + ARENA_H, 0x60000000);
		switch (drill) {
			case ZFighterDrills.RHYTHM -> rhythm(graphics, x, ay, a);
			case ZFighterDrills.KI_CONTROL -> kiControl(graphics, x, ay);
			case ZFighterDrills.SHADOW_BOXING -> boxing(graphics, x, ay);
			case ZFighterDrills.PRECISION -> precision(graphics, x, ay, a);
			default -> {
			}
		}
		int shown = drill == ZFighterDrills.PRECISION ? Math.round(points) : score;
		graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.zfighter.drill_points", shown), this.width / 2, y + H - 22, 0xFFFFFFFF);
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
		graphics.fill(bx, ay + 4, bx + 30, ay + ARENA_H - 4, 0xFF202020);
		int span = ARENA_H - 8;
		float band = band();
		int bandTop = ay + 4 + Math.round((1.0F - band - 0.1F) * span);
		graphics.fill(bx - 6, bandTop, bx + 36, bandTop + Math.round(0.2F * span), 0x5040C0FF);
		int my = ay + 4 + Math.round((1.0F - meter) * span);
		graphics.fill(bx + 2, my - 2, bx + 28, my + 2, Math.abs(meter - band) <= 0.1F ? 0xFF7CFF7C : 0xFFFF8C1A);
		int left = Math.max(0, KI_TICKS - ticks);
		graphics.text(this.font, (left + 19) / 20 + "s", x + 12, ay + 6, 0xFFA8A8B8);
	}

	private void boxing(GuiGraphicsExtractor graphics, int x, int ay) {
		if (prompt >= 0) {
			String key = prompt == 0 ? "punch" : prompt == 1 ? "block" : "dodge";
			int color = prompt == 0 ? 0xFFFF6A50 : prompt == 1 ? 0xFF7CB0FF : 0xFF7CFF7C;
			graphics.centeredText(this.font, Component.translatable("drill.jaymospowers.zfighter.box_" + key), this.width / 2, ay + ARENA_H / 2 - 4, color);
			float left = 1.0F - (ticks - promptAt) / (float)BOX_WINDOW;
			graphics.fill(x + W / 2 - 40, ay + ARENA_H / 2 + 10, x + W / 2 - 40 + Math.round(80 * left), ay + ARENA_H / 2 + 13, color);
		}
		graphics.text(this.font, prompts + "/" + COUNT, x + 12, ay + 6, 0xFFA8A8B8);
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
		graphics.text(this.font, targets + "/" + COUNT, x + 12, ay + 6, 0xFFA8A8B8);
	}
}
