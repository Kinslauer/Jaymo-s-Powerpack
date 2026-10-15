package com.jaymoskitchen.client;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.cooking.Step;
import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.Payloads.PrepUpdate;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The dough prep board: hold to pour water, hold to pour oil (release inside the green band), then knead by
 * alternating left and right. The pours and the knead run here; the server scores what we report. (0.4.40) Restyled
 * like the H screen: a dark panel with the Kitchen's stripe, a header naming the step, the how-to as striped rows.
 */
public class PrepScreen extends Screen {
	private enum Phase { INTRO, WAITING, WATER, OIL, KNEAD, SENDING, RESULT }

	private static final int PANEL_W = 340;
	private static final int PANEL_H = 210;
	private static final int BUTTON_H = 20;
	/** How long the "just right / too much" verdict shows before moving on. */
	private static final int VERDICT_TICKS = 20;

	private static final int COLOR_TEXT = KitchenUi.TEXT;
	private static final int COLOR_DIM = KitchenUi.DIM;
	private static final int COLOR_GOOD = KitchenUi.GOOD;
	private static final int COLOR_OK = KitchenUi.OK;
	private static final int COLOR_BAD = KitchenUi.BAD;
	/** Pitch of the how-to rows on the intro. */
	private static final int HOW_ROW = 13;
	private static final int COLOR_WATER = 0xFF3F76E4;
	private static final int COLOR_OIL = 0xFFE0B040;
	private static final int COLOR_BOWL = 0xFF8B5A2B;
	private static final int COLOR_BOWL_DARK = 0xFF65401E;
	private static final int COLOR_DOUGH = 0xFFEEDEB4;
	private static final int COLOR_DOUGH_DARK = 0xFFD2BE96;

	private final BlockPos pos;
	private Phase phase = Phase.INTRO;

	private float water;
	private float oil;
	private boolean pouring;
	/** This mix's band offsets (from the server) and the current pour's flow, which speeds up as it pours. */
	private float waterShift;
	private float oilShift;
	private float pourRate;
	private final java.util.Random random = new java.util.Random();
	/** Counts down while a pour's verdict is shown; -1 when no verdict is up. */
	private int verdictTicks = -1;
	private int verdictBand;
	private boolean spilled;

	private int kneads;
	private int kneadTicks;
	/** -1 = last knead was left, 1 = right, 0 = none yet. */
	private int lastKnead;
	private int squish;

	private Prep.Result result;
	private int animClock;

	/** Bit i: recipe i can be made from the pockets and the kitchen storage nearby (the server checked). */
	private final int stocked;

	public PrepScreen(BlockPos pos, int stocked) {
		super(Component.translatable("screen.jaymoskitchen.title"));
		this.pos = pos;
		this.stocked = stocked;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return (this.height - PANEL_H) / 2;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = top() + PANEL_H - 28;
		switch (phase) {
			case INTRO -> {
				// on a cutting board there's a third choice: chopping (the chopped dishes)
				boolean board = isBoard();
				int w = board ? 100 : 120;
				int x0 = board ? cx - 155 : cx - 125;
				Button start = Button.builder(Component.translatable("screen.jaymoskitchen.prep.start"), b -> start())
					.bounds(x0, by, w, BUTTON_H).build();
				start.active = canMake(Prep.Recipe.DOUGH);
				addRenderableWidget(start);
				Button roll = Button.builder(Component.translatable("screen.jaymoskitchen.roll.start"),
						b -> this.minecraft.gui.setScreen(new RollingScreen(pos)))
					.bounds(x0 + w + 5, by, w, BUTTON_H).build();
				roll.active = canMake(Prep.Recipe.JOINT);
				addRenderableWidget(roll);
				if (board) {
					addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.prep.chop"),
							b -> this.minecraft.gui.setScreen(new CookingScreen(pos, Station.BOARD, 0)))
						.bounds(x0 + 2 * (w + 5), by, w, BUTTON_H).build());
				}
			}
			case KNEAD -> {
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.prep.knead_left"), b -> knead(-1))
					.bounds(cx - 105, by, 100, BUTTON_H).build());
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.prep.knead_right"), b -> knead(1))
					.bounds(cx + 5, by, 100, BUTTON_H).build());
			}
			case RESULT -> {
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.prep.again"), b -> setPhase(Phase.INTRO))
					.bounds(cx - 105, by, 100, BUTTON_H).build());
				addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.done"), b -> onClose())
					.bounds(cx + 5, by, 100, BUTTON_H).build());
			}
			default -> {
			}
		}
	}

	/** Whether this prep board is a cutting board (the only place to chop). */
	private boolean isBoard() {
		return this.minecraft.level != null && Station.of(this.minecraft.level.getBlockState(pos)) == Station.BOARD;
	}

	private boolean canMake(Prep.Recipe recipe) {
		return this.minecraft.player != null && (this.minecraft.player.hasInfiniteMaterials()
			|| (stocked & (1 << recipe.ordinal())) != 0
			|| Kitchen.hasIngredients(this.minecraft.player.getInventory(), Prep.ingredients(recipe)));
	}

	private void setPhase(Phase next) {
		phase = next;
		rebuildWidgets();
	}

	private void start() {
		water = 0.0F;
		oil = 0.0F;
		kneads = 0;
		kneadTicks = 0;
		lastKnead = 0;
		spilled = false;
		setPhase(Phase.WAITING);
		ClientPlayNetworking.send(new Payloads.StartPrep(pos, Prep.Recipe.DOUGH.ordinal()));
	}

	/** Called on the client thread with the server's reply. */
	public void onUpdate(PrepUpdate update) {
		switch (update.status()) {
			case PrepUpdate.STATUS_STARTED -> {
				waterShift = update.waterShift();
				oilShift = update.oilShift();
				setPhase(Phase.WATER);
			}
			case PrepUpdate.STATUS_FINISHED -> {
				result = Prep.Result.values()[update.result()];
				setPhase(Phase.RESULT);
			}
			default -> setPhase(Phase.INTRO);
		}
	}

	private boolean isPouring() {
		return (phase == Phase.WATER || phase == Phase.OIL) && verdictTicks < 0;
	}

	private void setPouring(boolean down) {
		if (!isPouring()) {
			return;
		}
		if (down) {
			if (!pouring) {
				// every pour (and every re-grip) flows at a slightly different speed
				float base = phase == Phase.WATER ? Prep.WATER_RATE : Prep.OIL_RATE;
				pourRate = base * (1.0F + random.nextFloat() * 0.4F);
			}
			pouring = true;
		} else if (pouring) {
			pouring = false;
			endPour();
		}
	}

	private void endPour() {
		pouring = false;
		float level = phase == Phase.WATER ? water : oil;
		if (level <= 0.0F) {
			return;
		}
		verdictBand = phase == Phase.WATER ? Prep.waterBand(water, waterShift) : Prep.oilBand(oil, oilShift);
		verdictTicks = VERDICT_TICKS;
	}

	private void knead(int direction) {
		if (phase != Phase.KNEAD) {
			return;
		}
		if (direction != lastKnead) {
			kneads++;
			lastKnead = direction;
			squish = 6 * direction;
			if (kneads >= Prep.KNEADS) {
				finish(true);
			}
		} else {
			// same way twice: the dough just wobbles
			squish = 2 * direction;
		}
	}

	private void finish(boolean kneaded) {
		setPhase(Phase.SENDING);
		ClientPlayNetworking.send(new Payloads.FinishPrep(water, oil, kneaded));
	}

	@Override
	public void tick() {
		super.tick();
		animClock++;
		if (squish != 0) {
			squish -= Integer.signum(squish);
		}
		if (pouring && isPouring()) {
			pourRate += Prep.POUR_ACCEL;
			if (phase == Phase.WATER) {
				water += pourRate;
				if (water >= 1.0F) {
					water = 1.0F;
					spilled = true;
					endPour();
				}
			} else {
				oil += pourRate;
				if (oil >= 1.0F) {
					oil = 1.0F;
					spilled = true;
					endPour();
				}
			}
		}
		if (verdictTicks > 0 && --verdictTicks == 0) {
			verdictTicks = -1;
			if (verdictBand == 0) {
				// a bad pour ruins the mix; no point kneading it
				finish(false);
			} else if (phase == Phase.WATER) {
				setPhase(Phase.OIL);
			} else {
				setPhase(Phase.KNEAD);
			}
		}
		if (phase == Phase.KNEAD && ++kneadTicks > Prep.KNEAD_TICKS) {
			finish(false);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_SPACE && isPouring()) {
			setPouring(true);
			return true;
		}
		if (phase == Phase.KNEAD && (key == InputConstants.KEY_A || key == InputConstants.KEY_LEFT)) {
			knead(-1);
			return true;
		}
		if (phase == Phase.KNEAD && (key == InputConstants.KEY_D || key == InputConstants.KEY_RIGHT)) {
			knead(1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE) {
			setPouring(false);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (isPouring()) {
			setPouring(true);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pouring) {
			setPouring(false);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		KitchenUi.panel(graphics, x, y, PANEL_W, PANEL_H, KitchenUi.ACCENT);
		String sub = switch (phase) {
			case WAITING, SENDING -> "screen.jaymoskitchen.waiting";
			case WATER -> "screen.jaymoskitchen.prep.pour_water";
			case OIL -> "screen.jaymoskitchen.prep.pour_oil";
			case KNEAD -> "screen.jaymoskitchen.prep.knead";
			default -> "screen.jaymoskitchen.prep.make_dough";
		};
		KitchenUi.header(graphics, this.font, x, y, PANEL_W, new ItemStack(ModItems.DOUGH),
			this.title.copy().append(" - ").append(Component.translatable("screen.jaymoskitchen.prep")), Component.translatable(sub),
			KitchenUi.ACCENT, x + PANEL_W - 9);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		switch (phase) {
			case INTRO -> drawIntro(graphics, x, y);
			case WAITING, SENDING -> graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.waiting"),
				this.width / 2, y + PANEL_H / 2, COLOR_DIM);
			case WATER, OIL -> drawPour(graphics, x, y);
			case KNEAD -> drawKnead(graphics, x, y, a);
			case RESULT -> drawResult(graphics, x, y);
		}
	}

	private void drawIntro(GuiGraphicsExtractor graphics, int x, int y) {
		// what goes in, each in its well, and what you keep
		int ry = y + KitchenUi.HEADER;
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + 22, 0, false);
		Map<Step, Integer> ingredients = Prep.ingredients(Prep.Recipe.DOUGH);
		int iconX = x + 11;
		for (Map.Entry<Step, Integer> need : ingredients.entrySet()) {
			KitchenUi.itemWell(graphics, this.font, need.getKey().icon(need.getValue()), iconX, ry + 3);
			iconX += 21;
		}
		KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.prep.bowl_kept"), iconX + 4, ry + 7,
			x + PANEL_W - 9 - iconX - 4, COLOR_DIM);
		// how it goes, a row per step
		ry += 26;
		String[] how = {"screen.jaymoskitchen.prep.how1", "screen.jaymoskitchen.prep.how2", "screen.jaymoskitchen.prep.how3"};
		for (int i = 0; i < how.length; i++) {
			int hy = ry + i * HOW_ROW;
			KitchenUi.row(graphics, x + 6, hy, x + PANEL_W - 6, hy + HOW_ROW - 1, i, false);
			KitchenUi.line(graphics, this.font, Component.translatable(how[i]), x + 9, hy + 3, PANEL_W - 18, COLOR_TEXT);
		}
		ry += how.length * HOW_ROW + 4;
		KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.prep.oil_hint"), x + 9, ry, PANEL_W - 18,
			KitchenUi.HINT);
		// the other things this board does
		KitchenUi.detail(graphics, this.font, x, ry + 14, PANEL_W, 2, null, COLOR_DIM,
			java.util.List.of(Component.translatable("screen.jaymoskitchen.roll.or").withColor(KitchenUi.ACCENT & 0xFFFFFF)));
	}

	private void drawPour(GuiGraphicsExtractor graphics, int x, int y) {
		boolean isWater = phase == Phase.WATER;
		int cx = this.width / 2;
		KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.prep.hold"), x + 9, y + KitchenUi.HEADER + 3,
			PANEL_W - 18, KitchenUi.HINT);

		// gauge: a measuring tube with the acceptable band and the perfect band marked
		int gx = x + 60;
		int gTop = y + 56;
		int gBottom = y + PANEL_H - 20;
		int gh = gBottom - gTop;
		float level = isWater ? water : oil;
		float shift = isWater ? waterShift : oilShift;
		float min = (isWater ? Prep.WATER_MIN : Prep.OIL_MIN) + shift;
		float max = (isWater ? Prep.WATER_MAX : Prep.OIL_MAX) + shift;
		float pMin = (isWater ? Prep.WATER_PERFECT_MIN : Prep.OIL_PERFECT_MIN) + shift;
		float pMax = (isWater ? Prep.WATER_PERFECT_MAX : Prep.OIL_PERFECT_MAX) + shift;
		int liquid = isWater ? COLOR_WATER : COLOR_OIL;
		KitchenUi.well(graphics, gx - 2, gTop - 2, 34, gBottom - gTop + 4);
		graphics.fill(gx, gBottom - Math.round(gh * level), gx + 30, gBottom, liquid);
		markBand(graphics, gx, gBottom, gh, min, max, 0x6055FF55, 3);
		markBand(graphics, gx, gBottom, gh, pMin, pMax, 0xA055FF55, 6);

		// the mixing bowl, filling up
		int bowlCx = x + 220;
		int bowlTop = y + 100;
		float waterIn = water;
		float oilIn = isWater ? 0.0F : oil;
		drawBowl(graphics, bowlCx, bowlTop, waterIn, oilIn, pouring && isPouring() ? liquid : 0);

		if (verdictTicks > 0) {
			String key = spilled ? "spilled" : switch (verdictBand) {
				case 2 -> "just_right";
				case 1 -> "close_enough";
				default -> (level < min ? "too_little" : "too_much");
			};
			int color = verdictBand == 2 ? COLOR_GOOD : verdictBand == 1 ? COLOR_OK : COLOR_BAD;
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.prep." + key), bowlCx, y + 60, color);
		}
	}

	private static void markBand(GuiGraphicsExtractor graphics, int gx, int gBottom, int gh, float from, float to, int color,
		int tick) {
		int y0 = gBottom - Math.round(gh * to);
		int y1 = gBottom - Math.round(gh * from);
		graphics.fill(gx, y0, gx + 30, y1, color);
		graphics.fill(gx - 2 - tick, y0, gx - 2, y0 + 1, 0xFF55FF55);
		graphics.fill(gx - 2 - tick, y1 - 1, gx - 2, y1, 0xFF55FF55);
	}

	/** A bowl drawn as narrowing rows; liquids sit inside it, and a stream pours in while held. */
	private void drawBowl(GuiGraphicsExtractor graphics, int cx, int top, float water, float oil, int streamColor) {
		int rows = 40;
		int fillRows = Math.min(rows - 4, Math.round((water * 0.7F + oil * 0.3F) * (rows - 4)));
		for (int i = 0; i < rows; i++) {
			int half = 60 - i * i / 40;
			int y = top + i;
			graphics.fill(cx - half, y, cx + half, y + 1, i == 0 ? COLOR_BOWL_DARK : COLOR_BOWL);
			int innerHalf = half - 4;
			if (innerHalf > 0 && i > 0 && i < rows - 3) {
				boolean filled = i >= rows - 3 - fillRows;
				int inner = filled ? (oil > 0 && i == rows - 3 - fillRows ? COLOR_OIL : COLOR_WATER) : 0xFF2A1A0E;
				graphics.fill(cx - innerHalf, y, cx + innerHalf, y + 1, inner);
			}
		}
		if (streamColor != 0) {
			int wobble = Math.round(Mth.sin(animClock * 0.8F));
			graphics.fill(cx - 2 + wobble, top - 40, cx + 2 + wobble, top + rows - 4 - fillRows, streamColor);
		}
	}

	private void drawKnead(GuiGraphicsExtractor graphics, int x, int y, float a) {
		int cx = this.width / 2;
		// time left and knead progress
		float timeLeft = 1.0F - Math.min(1.0F, (kneadTicks + a) / Prep.KNEAD_TICKS);
		KitchenUi.bar(graphics, x + 20, y + 40, PANEL_W - 40, 4, timeLeft, timeLeft > 0.3F ? KitchenUi.ACCENT : COLOR_BAD);
		KitchenUi.bar(graphics, x + 20, y + 48, PANEL_W - 40, 6, kneads / (float) Prep.KNEADS, COLOR_GOOD);

		// the dough in its bowl, squashed the way it was last pushed
		int bowlTop = y + 112;
		drawBowl(graphics, cx, bowlTop, 0.0F, 0.0F, 0);
		float stretch = (squish - a * Integer.signum(squish)) / 6.0F;
		int halfW = Math.round(34 + Math.abs(stretch) * 14);
		int height = Math.round(30 - Math.abs(stretch) * 8);
		int shift = Math.round(stretch * 10);
		for (int i = 0; i < height; i++) {
			float t = (i + 0.5F) / height;
			int half = Math.round(halfW * Mth.sqrt(1.0F - (2.0F * t - 1.0F) * (2.0F * t - 1.0F)));
			int yy = bowlTop + 6 - height + i;
			graphics.fill(cx + shift - half, yy, cx + shift + half, yy + 1, i > height * 0.7F ? COLOR_DOUGH_DARK : COLOR_DOUGH);
		}
	}

	private void drawResult(GuiGraphicsExtractor graphics, int x, int y) {
		int cx = this.width / 2;
		if (result.amount() > 0) {
			KitchenUi.outputWell(graphics, this.font, new ItemStack(ModItems.DOUGH, result.amount()), cx - 8, y + 50);
		} else {
			KitchenUi.well(graphics, cx - 13, y + 45, 26, 26);
		}
		int color = result == Prep.Result.PERFECT ? COLOR_GOOD : result == Prep.Result.GOOD ? COLOR_OK : COLOR_BAD;
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.prep.result." + result.name().toLowerCase()),
			cx, y + 80, color);
	}

	@Override
	public void removed() {
		if (phase == Phase.WAITING || phase == Phase.WATER || phase == Phase.OIL || phase == Phase.KNEAD) {
			ClientPlayNetworking.send(new Payloads.Abandon());
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
