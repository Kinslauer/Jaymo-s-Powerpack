package com.jaymoskitchen.client;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Rolling;
import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.Payloads.PrepUpdate;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Rolling a joint on the prep board, in three steps:
 * <ol>
 * <li>Grind: hold the mouse and circle it around the grinder to turn it.</li>
 * <li>Sprinkle: hold the mouse over the paper; the ground herb falls where the cursor is. Spread it evenly.</li>
 * <li>Roll: hold the mouse and drag it up and down at a steady pace. Too fast tears the paper.</li>
 * </ol>
 * Played here; the server is told how it went and judges it ({@link Rolling#score}).
 */
public class RollingScreen extends Screen {
	private enum Phase { WAITING, GRIND, SPRINKLE, ROLL, SENDING, RESULT }

	private static final int PANEL_W = 340;
	private static final int PANEL_H = 210;
	private static final int BUTTON_H = 20;
	private static final int VERDICT_TICKS = 20;
	private static final int GRINDER_RADIUS = 42;
	private static final int PAPER_MARGIN = 34;

	private static final int COLOR_PANEL = 0xE0201812;
	private static final int COLOR_BORDER = 0xFF8B5A2B;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA0A0A0;
	private static final int COLOR_GOOD = 0xFF55FF55;
	private static final int COLOR_OK = 0xFFFFDD55;
	private static final int COLOR_BAD = 0xFFFF5555;
	private static final int COLOR_HERB = 0xFF5E9A3A;
	private static final int COLOR_HERB_DARK = 0xFF3E6E26;
	private static final int COLOR_PAPER = 0xFFF2EAD8;
	private static final int COLOR_PAPER_SHADE = 0xFFD8CCB0;
	private static final int COLOR_METAL = 0xFF9AA0A8;
	private static final int COLOR_METAL_DARK = 0xFF5E646C;

	private final BlockPos pos;
	private final Random random = new Random();
	private Phase phase = Phase.WAITING;
	private boolean started;
	private int animClock;

	/** The mouse button is down (each step is "hold and move"). */
	private boolean holding;
	private double mouseX;
	private double mouseY;

	// grind
	private int grindTarget;
	private float grindAngle;
	private float lastAngle = Float.NaN;
	private int grindTicks;
	private boolean ground;

	// sprinkle
	private final float[] segments = new float[Rolling.SEGMENTS];
	private float supply;
	private float sprinkleRate;
	private float evenness;

	// roll
	private float rollProgress;
	private float tears;
	private float maxSpeed;
	private float moved;
	private double lastMouseY = Double.NaN;
	private int rollTicks;
	private int tearFlash;

	/** A message shown briefly between steps; when it runs out the next step starts (or the attempt ends). */
	private int verdictTicks = -1;
	private String verdictKey = "";
	private int verdictColor;
	private Runnable afterVerdict = () -> {
	};

	private Prep.Result result;

	public RollingScreen(BlockPos pos) {
		super(Component.translatable("screen.jaymoskitchen.title"));
		this.pos = pos;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return (this.height - PANEL_H) / 2;
	}

	@Override
	protected void init() {
		if (!started) {
			started = true;
			ClientPlayNetworking.send(new Payloads.StartPrep(pos, Prep.Recipe.JOINT.ordinal()));
		}
		if (phase == Phase.RESULT) {
			int cx = this.width / 2;
			int by = top() + PANEL_H - 28;
			addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.roll.again"), b -> {
				started = false;
				phase = Phase.WAITING;
				rebuildWidgets();
			}).bounds(cx - 105, by, 100, BUTTON_H).build());
			addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.done"), b -> onClose())
				.bounds(cx + 5, by, 100, BUTTON_H).build());
		}
	}

	/** Called on the client thread with the server's reply. */
	public void onUpdate(PrepUpdate update) {
		switch (update.status()) {
			case PrepUpdate.STATUS_STARTED -> startGrind();
			case PrepUpdate.STATUS_FINISHED -> {
				result = Prep.Result.values()[update.result()];
				phase = Phase.RESULT;
				rebuildWidgets();
			}
			default -> onClose();
		}
	}

	private void startGrind() {
		grindTarget = Rolling.GRIND_TURNS_MIN + random.nextInt(Rolling.GRIND_TURNS_MAX - Rolling.GRIND_TURNS_MIN + 1);
		grindAngle = 0.0F;
		lastAngle = Float.NaN;
		grindTicks = 0;
		ground = false;
		java.util.Arrays.fill(segments, 0.0F);
		supply = Rolling.SUPPLY;
		sprinkleRate = Mth.lerp(random.nextFloat(), Rolling.SPRINKLE_RATE_MIN, Rolling.SPRINKLE_RATE_MAX);
		evenness = 0.0F;
		rollProgress = 0.0F;
		tears = 0.0F;
		maxSpeed = Mth.lerp(random.nextFloat(), Rolling.ROLL_MAX_SPEED_MIN, Rolling.ROLL_MAX_SPEED_MAX);
		moved = 0.0F;
		rollTicks = 0;
		verdictTicks = -1;
		phase = Phase.GRIND;
		rebuildWidgets();
	}

	private float turns() {
		return Math.abs(grindAngle) / Mth.TWO_PI;
	}

	private void verdict(String key, int color, Runnable then) {
		verdictKey = key;
		verdictColor = color;
		verdictTicks = VERDICT_TICKS;
		afterVerdict = then;
		holding = false;
	}

	private void finish(boolean rolled) {
		phase = Phase.SENDING;
		holding = false;
		rebuildWidgets();
		ClientPlayNetworking.send(new Payloads.FinishRoll(ground, evenness, tears, rolled));
	}

	@Override
	public void tick() {
		super.tick();
		animClock++;
		if (tearFlash > 0) {
			tearFlash--;
		}
		if (verdictTicks > 0) {
			if (--verdictTicks == 0) {
				verdictTicks = -1;
				afterVerdict.run();
			}
			return;
		}
		switch (phase) {
			case GRIND -> tickGrind();
			case SPRINKLE -> tickSprinkle();
			case ROLL -> tickRoll();
			default -> {
			}
		}
	}

	private void tickGrind() {
		if (turns() >= grindTarget) {
			ground = true;
			verdict("screen.jaymoskitchen.roll.ground", COLOR_GOOD, () -> phase = Phase.SPRINKLE);
		} else if (++grindTicks > Rolling.GRIND_TICKS) {
			verdict("screen.jaymoskitchen.roll.too_slow", COLOR_BAD, () -> finish(false));
		}
	}

	private void tickSprinkle() {
		if (!holding || supply <= 0.0F) {
			return;
		}
		float amount = Math.min(sprinkleRate, supply);
		supply -= amount;
		int paperLeft = left() + PAPER_MARGIN;
		int paperWidth = PANEL_W - PAPER_MARGIN * 2;
		// it doesn't fall perfectly straight
		double x = mouseX + (random.nextFloat() - 0.5F) * paperWidth / Rolling.SEGMENTS * 0.7F;
		int segment = (int) Math.floor((x - paperLeft) / paperWidth * Rolling.SEGMENTS);
		if (segment >= 0 && segment < Rolling.SEGMENTS) {
			segments[segment] += amount;
		}
		if (supply <= 0.0F) {
			evenness = Rolling.evenness(segments);
			if (evenness < Rolling.EVEN_ENOUGH) {
				verdict("screen.jaymoskitchen.roll.uneven", COLOR_BAD, () -> finish(false));
			} else {
				verdict(evenness >= Rolling.PERFECT_EVENNESS ? "screen.jaymoskitchen.roll.even" : "screen.jaymoskitchen.roll.lumpy",
					evenness >= Rolling.PERFECT_EVENNESS ? COLOR_GOOD : COLOR_OK, () -> phase = Phase.ROLL);
			}
		}
	}

	private void tickRoll() {
		float speed = moved;
		moved = 0.0F;
		if (speed > maxSpeed) {
			tears += (speed - maxSpeed) * 0.5F;
			tearFlash = 6;
			rollProgress += maxSpeed;
		} else if (speed >= Rolling.ROLL_MIN_SPEED) {
			rollProgress += speed;
		}
		if (tears >= Rolling.TEAR_LIMIT) {
			verdict("screen.jaymoskitchen.roll.torn", COLOR_BAD, () -> finish(false));
		} else if (rollProgress >= Rolling.ROLL_LENGTH) {
			finish(true);
		} else if (++rollTicks > Rolling.ROLL_TICKS) {
			verdict("screen.jaymoskitchen.roll.too_slow", COLOR_BAD, () -> finish(false));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if ((phase == Phase.GRIND || phase == Phase.SPRINKLE || phase == Phase.ROLL) && verdictTicks < 0) {
			holding = true;
			lastAngle = Float.NaN;
			lastMouseY = Double.NaN;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Mouse movement is read every frame: angle around the grinder, and how far it moved up and down. */
	private void trackMouse(int mx, int my) {
		mouseX = mx;
		mouseY = my;
		if (!holding || verdictTicks >= 0) {
			lastAngle = Float.NaN;
			lastMouseY = Double.NaN;
			return;
		}
		if (phase == Phase.GRIND) {
			double dx = mx - this.width / 2.0;
			double dy = my - grinderY();
			if (dx * dx + dy * dy < 64) {
				// too close to the middle to tell which way it's turning
				lastAngle = Float.NaN;
				return;
			}
			float angle = (float) Mth.atan2(dy, dx);
			if (!Float.isNaN(lastAngle)) {
				grindAngle += Mth.wrapDegrees((angle - lastAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			}
			lastAngle = angle;
		} else if (phase == Phase.ROLL) {
			if (!Double.isNaN(lastMouseY)) {
				moved += (float) Math.abs(my - lastMouseY);
			}
			lastMouseY = my;
		}
	}

	private int grinderY() {
		return top() + 116;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float a) {
		trackMouse(mx, my);
		int x = left();
		int y = top();
		graphics.fill(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, tearFlash > 0 ? COLOR_BAD : COLOR_BORDER);
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		graphics.centeredText(this.font, this.title.copy().append(" - ").append(Component.translatable("screen.jaymoskitchen.roll")),
			this.width / 2, y + 8, COLOR_OK);
		super.extractRenderState(graphics, mx, my, a);

		switch (phase) {
			case WAITING, SENDING -> graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.waiting"),
				this.width / 2, y + PANEL_H / 2, COLOR_DIM);
			case GRIND -> drawGrind(graphics, x, y);
			case SPRINKLE -> drawSprinkle(graphics, x, y);
			case ROLL -> drawRoll(graphics, x, y);
			case RESULT -> drawResult(graphics, x, y);
		}
		if (verdictTicks > 0) {
			graphics.centeredText(this.font, Component.translatable(verdictKey), this.width / 2, y + 40, verdictColor);
		}
	}

	private void drawStepHeader(GuiGraphicsExtractor graphics, int y, String titleKey, String hintKey) {
		graphics.centeredText(this.font, Component.translatable(titleKey), this.width / 2, y + 22, COLOR_TEXT);
		graphics.centeredText(this.font, Component.translatable(hintKey), this.width / 2, y + 52, COLOR_DIM);
	}

	private void bar(GuiGraphicsExtractor graphics, int x0, int x1, int y, float fill, int color) {
		graphics.fill(x0, y, x1, y + 4, 0xFF000000);
		graphics.fill(x0, y, x0 + Math.round((x1 - x0) * Mth.clamp(fill, 0.0F, 1.0F)), y + 4, color);
	}

	private void drawGrind(GuiGraphicsExtractor graphics, int x, int y) {
		drawStepHeader(graphics, y, "screen.jaymoskitchen.roll.step_grind", "screen.jaymoskitchen.roll.hint_grind");
		bar(graphics, x + 20, x + PANEL_W - 20, y + 34, 1.0F - grindTicks / (float) Rolling.GRIND_TICKS, COLOR_OK);
		int cx = this.width / 2;
		int cy = grinderY();
		// the grinder body
		disc(graphics, cx, cy, GRINDER_RADIUS + 3, COLOR_METAL_DARK);
		disc(graphics, cx, cy, GRINDER_RADIUS, COLOR_METAL);
		// herb inside, getting finer (and less of it showing) as it's ground
		float done = Mth.clamp(turns() / grindTarget, 0.0F, 1.0F);
		disc(graphics, cx, cy, Math.round(GRINDER_RADIUS * 0.75F), COLOR_HERB_DARK);
		for (int i = 0; i < 14; i++) {
			float ang = i * 2.4F + grindAngle * 0.3F;
			float rad = GRINDER_RADIUS * 0.6F * ((i * 37 % 10) / 10.0F);
			int size = Math.max(1, Math.round(5 * (1.0F - done)) + 1);
			int hx = cx + Math.round(Mth.cos(ang) * rad);
			int hy = cy + Math.round(Mth.sin(ang) * rad);
			graphics.fill(hx, hy, hx + size, hy + size, COLOR_HERB);
		}
		// teeth turning with the lid
		for (int i = 0; i < 6; i++) {
			float ang = grindAngle + i * Mth.TWO_PI / 6.0F;
			int tx = cx + Math.round(Mth.cos(ang) * GRINDER_RADIUS * 0.88F);
			int ty = cy + Math.round(Mth.sin(ang) * GRINDER_RADIUS * 0.88F);
			graphics.fill(tx - 3, ty - 3, tx + 3, ty + 3, COLOR_METAL_DARK);
		}
		graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, COLOR_METAL_DARK);
		Component count = Component.translatable("screen.jaymoskitchen.roll.turns", (int) turns(), grindTarget);
		graphics.text(this.font, count, x + PANEL_W - 14 - this.font.width(count), y + PANEL_H - 22, COLOR_TEXT);
		bar(graphics, x + 14, x + 120, y + PANEL_H - 18, done, COLOR_GOOD);
	}

	private void drawSprinkle(GuiGraphicsExtractor graphics, int x, int y) {
		drawStepHeader(graphics, y, "screen.jaymoskitchen.roll.step_sprinkle", "screen.jaymoskitchen.roll.hint_sprinkle");
		bar(graphics, x + 20, x + PANEL_W - 20, y + 34, supply / Rolling.SUPPLY, COLOR_HERB);
		int paperLeft = x + PAPER_MARGIN;
		int paperRight = x + PANEL_W - PAPER_MARGIN;
		int paperTop = y + 150;
		graphics.fill(paperLeft, paperTop, paperRight, paperTop + 22, COLOR_PAPER);
		graphics.fill(paperLeft, paperTop + 18, paperRight, paperTop + 22, COLOR_PAPER_SHADE);
		// one pile per stretch of paper; the dashed line is an even share
		float target = Rolling.SUPPLY / Rolling.SEGMENTS;
		int segWidth = (paperRight - paperLeft) / Rolling.SEGMENTS;
		int maxPile = 60;
		int targetY = paperTop - Math.round(maxPile * 0.5F);
		for (int i = 0; i < Rolling.SEGMENTS; i++) {
			int sx = paperLeft + i * segWidth;
			int height = Math.min(maxPile, Math.round(segments[i] / target * maxPile * 0.5F));
			float share = segments[i] / target;
			int color = share > 1.35F ? COLOR_BAD : share >= 0.65F ? COLOR_HERB : COLOR_HERB_DARK;
			graphics.fill(sx + 2, paperTop - height, sx + segWidth - 2, paperTop + 2, color);
			if (i > 0) {
				graphics.fill(sx, paperTop + 2, sx + 1, paperTop + 18, COLOR_PAPER_SHADE);
			}
		}
		for (int dx = paperLeft; dx < paperRight; dx += 6) {
			graphics.fill(dx, targetY, dx + 3, targetY + 1, COLOR_OK);
		}
		// herb falling from the cursor
		if (holding && supply > 0.0F && verdictTicks < 0) {
			for (int i = 0; i < 5; i++) {
				int fy = (int) mouseY + ((animClock * 7 + i * 13) % Math.max(1, paperTop - (int) mouseY));
				int fx = (int) mouseX + (i * 5 % 9) - 4;
				graphics.fill(fx, fy, fx + 2, fy + 2, COLOR_HERB);
			}
		}
	}

	private void drawRoll(GuiGraphicsExtractor graphics, int x, int y) {
		drawStepHeader(graphics, y, "screen.jaymoskitchen.roll.step_roll", "screen.jaymoskitchen.roll.hint_roll");
		bar(graphics, x + 20, x + PANEL_W - 20, y + 34, 1.0F - rollTicks / (float) Rolling.ROLL_TICKS, COLOR_OK);
		int cx = this.width / 2;
		float done = Mth.clamp(rollProgress / Rolling.ROLL_LENGTH, 0.0F, 1.0F);
		// the flat paper shrinks as it wraps into the roll at the bottom
		int paperLeft = cx - 110;
		int paperRight = cx + 110;
		int rollY = y + 170;
		int flat = Math.round(70 * (1.0F - done));
		graphics.fill(paperLeft, rollY - flat, paperRight, rollY, COLOR_PAPER);
		graphics.fill(paperLeft + 6, rollY - flat + 6, paperRight - 6, rollY - Math.max(0, flat / 3), COLOR_HERB);
		int thickness = 6 + Math.round(8 * done);
		graphics.fill(paperLeft - 2, rollY - thickness / 2, paperRight + 2, rollY + thickness / 2, COLOR_PAPER_SHADE);
		graphics.fill(paperLeft - 2, rollY - thickness / 2, paperRight + 2, rollY - thickness / 2 + 2, COLOR_PAPER);
		// tear marks along the roll
		int marks = (int) (tears / Rolling.TEAR_LIMIT * 8);
		for (int i = 0; i < marks; i++) {
			int tx = paperLeft + 20 + i * 24;
			graphics.fill(tx, rollY - thickness / 2, tx + 2, rollY + thickness / 2, COLOR_BAD);
		}
		bar(graphics, x + 14, x + 160, y + PANEL_H - 18, done, COLOR_GOOD);
		bar(graphics, x + PANEL_W - 160, x + PANEL_W - 14, y + PANEL_H - 18, tears / Rolling.TEAR_LIMIT, COLOR_BAD);
		graphics.text(this.font, Component.translatable("screen.jaymoskitchen.roll.rolled"), x + 14, y + PANEL_H - 30, COLOR_DIM);
		Component tearLabel = Component.translatable("screen.jaymoskitchen.roll.tears");
		graphics.text(this.font, tearLabel, x + PANEL_W - 14 - this.font.width(tearLabel), y + PANEL_H - 30,
			tearFlash > 0 ? COLOR_BAD : COLOR_DIM);
	}

	private void drawResult(GuiGraphicsExtractor graphics, int x, int y) {
		int cx = this.width / 2;
		if (result.amount() > 0) {
			ItemStack stack = new ItemStack(ModItems.JOINT, result.amount());
			graphics.item(stack, cx - 8, y + 50);
			graphics.itemDecorations(this.font, stack, cx - 8, y + 50, null);
		}
		int color = result == Prep.Result.PERFECT ? COLOR_GOOD : result == Prep.Result.GOOD ? COLOR_OK : COLOR_BAD;
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.roll.result." + result.name().toLowerCase()),
			cx, y + 80, color);
	}

	/** A filled circle, drawn as horizontal strips. */
	private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
			graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
		}
	}

	@Override
	public void removed() {
		if (phase == Phase.WAITING || phase == Phase.GRIND || phase == Phase.SPRINKLE || phase == Phase.ROLL) {
			ClientPlayNetworking.send(new Payloads.Abandon());
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
