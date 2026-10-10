package com.jaymoskitchen.client;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Grill;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Sear;
import com.jaymoskitchen.cooking.Smoke;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.cooking.Step;
import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.Payloads.CookUpdate;
import com.jaymoskitchen.net.Payloads.GrillUpdate;
import com.jaymoskitchen.net.Payloads.SimUpdate;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import net.minecraft.world.level.block.Blocks;

/**
 * The cooking minigame. Pick a dish, then press the steps in recipe order. The buttons reshuffle after every
 * press, so you have to find the next one each time. The server judges every press.
 */
public class CookingScreen extends Screen {
	private enum Phase { SELECT, WAITING, COOKING, GRILLING, SMOKING, SEARING, RESULT }

	private static final int PANEL_W = 340;
	private static final int PANEL_H = 240;
	/** Vertical spacing of the dish list. */
	private static final int ROW_PITCH = 23;
	/** Dish rows shown at once; longer lists (a furnace, an oven with pot and pan on the hob) scroll. */
	private static final int LIST_ROWS = 8;
	private static final int BUTTON_W = 100;
	private static final int BUTTON_H = 20;
	private static final int DECOYS = 3;

	/** See-through, so the stove and what's cooking on it show behind the panel. */
	private static final int COLOR_PANEL = 0x80201812;
	private static final int COLOR_BORDER = 0xFF8B5A2B;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA0A0A0;
	private static final int COLOR_DONE = 0xFF55FF55;
	private static final int COLOR_CURRENT = 0xFFFFDD55;
	private static final int COLOR_BAD = 0xFFFF5555;

	private final BlockPos pos;
	private final Station station;
	private final Random random = new Random();

	private Phase phase = Phase.SELECT;
	/** Index of the first dish row shown in the pick list. */
	private int listScroll;
	private Dish dish;
	private int progress;
	private int mistakes;
	private int parTicks;
	private int limitTicks;
	private int cookTicks;
	private int flashTicks;
	private Quality result;
	// grill state, as of the last server update; the side that's down keeps cooking client-side
	private final float[] grillDone = new float[2];
	private int grillDown;
	private float grillPerTick;
	/** The fire's strength (1.0 = normal); the flames are drawn taller when it's hotter. */
	private float grillHeat = 1.0F;
	private int sinceGrillUpdate;
	/** Ticks since this grill/oven session began (drives the slide-into-the-oven animation). */
	private int sinceGrillStart;
	/** Ticks into the flip animation, or -1. */
	private int flipAnim = -1;
	private int animClock;
	// smoker state: the heat simulation runs here, the server judges the totals we report
	private float heat;
	private boolean fanning;
	private float gustVelocity;
	private int gustTicks;
	private int nextGust;
	private float cooling;
	// blast furnace searing: a marker ping-pongs across the bar; each round has a new target and speed
	private float searMarker;
	private float searDirection;
	private float searSpeed;
	private float searZone;
	private int searRound;
	private int searRoundTicks;
	/** Ticks left showing the last round's result before the next round starts; 0 while a round is live. */
	private int searFeedback;
	/** Per finished round: 2 perfect, 1 good, 0 miss. */
	private final List<Integer> searResults = new ArrayList<>();
	private int smokeTicks;
	private int cureTicks;
	private int scorchTicks;
	private int coldTicks;
	/** The buttons on offer for the current dish (its steps plus decoys), reshuffled after each press. */
	private List<Step> board = List.of();

	/** Bit i: the i-th dish can be made from the pockets and the kitchen storage nearby (the server checked). */
	private final int stocked;

	public CookingScreen(BlockPos pos, Station station, int stocked) {
		super(Component.translatable("screen.jaymoskitchen.title"));
		this.pos = pos;
		this.station = station;
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
		switch (phase) {
			case SELECT -> initSelect();
			case COOKING -> initCooking();
			case GRILLING -> initGrilling();
			case RESULT -> initResult();
			case SMOKING, SEARING -> {
			}
			case WAITING -> {
			}
		}
	}

	private void initSelect() {
		List<Dish> dishes = station.dishes();
		listScroll = Math.max(0, Math.min(listScroll, dishes.size() - LIST_ROWS));
		int end = Math.min(dishes.size(), listScroll + LIST_ROWS);
		for (int i = listScroll; i < end; i++) {
			Dish d = dishes.get(i);
			Button button = Button.builder(dishName(d), b -> chooseDish(d))
				.bounds(left() + 10, top() + 30 + (i - listScroll) * ROW_PITCH, 130, BUTTON_H)
				.build();
			button.active = this.minecraft.player != null && (this.minecraft.player.hasInfiniteMaterials()
				|| (stocked & (1 << i)) != 0 || Kitchen.hasIngredients(this.minecraft.player.getInventory(), d.ingredients()));
			addRenderableWidget(button);
		}
		if (dishes.size() > LIST_ROWS) {
			Button up = Button.builder(Component.literal("▲"), b -> scrollList(-1))
				.bounds(left() + PANEL_W - 30, top() + 30, 20, BUTTON_H).build();
			up.active = listScroll > 0;
			addRenderableWidget(up);
			Button down = Button.builder(Component.literal("▼"), b -> scrollList(1))
				.bounds(left() + PANEL_W - 30, top() + 30 + (LIST_ROWS - 1) * ROW_PITCH, 20, BUTTON_H).build();
			down.active = end < dishes.size();
			addRenderableWidget(down);
		}
	}

	private void scrollList(int rows) {
		int max = Math.max(0, station.dishes().size() - LIST_ROWS);
		int scroll = Math.max(0, Math.min(listScroll + rows, max));
		if (scroll != listScroll) {
			listScroll = scroll;
			rebuildWidgets();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (phase == Phase.SELECT && scrollY != 0 && station.dishes().size() > LIST_ROWS) {
			scrollList(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void initCooking() {
		int x0 = left() + PANEL_W - 10 - BUTTON_W * 2 - 6;
		int y0 = top() + 52;
		for (int i = 0; i < board.size(); i++) {
			Step step = board.get(i);
			int col = i % 2;
			int row = i / 2;
			addRenderableWidget(Button.builder(Component.literal("    ").append(Component.translatable(step.translationKey())),
					b -> pressStep(step))
				.bounds(x0 + col * (BUTTON_W + 6), y0 + row * (BUTTON_H + 4), BUTTON_W, BUTTON_H)
				.build());
		}
	}

	private void initResult() {
		int cx = this.width / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.again"), b -> {
			phase = Phase.SELECT;
			rebuildWidgets();
		}).bounds(cx - 105, top() + PANEL_H - 32, 100, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.done"), b -> onClose())
			.bounds(cx + 5, top() + PANEL_H - 32, 100, BUTTON_H).build());
	}

	private void chooseDish(Dish d) {
		dish = d;
		phase = Phase.WAITING;
		rebuildWidgets();
		ClientPlayNetworking.send(new Payloads.StartCooking(pos, d.ordinal(), station.ordinal()));
	}

	private void pressStep(Step step) {
		ClientPlayNetworking.send(new Payloads.PressStep(step.ordinal()));
	}

	/** Called on the client thread with the server's view of the session. */
	public void onUpdate(CookUpdate update) {
		Dish updated = Dish.byIndex(update.dish());
		if (updated == null) {
			return;
		}
		switch (update.status()) {
			case CookUpdate.STATUS_REJECTED -> {
				// refused (missing ingredients, the fire's out...): back to the dish list, with its buttons
				phase = Phase.SELECT;
				rebuildWidgets();
			}
			case CookUpdate.STATUS_FINISHED -> {
				dish = updated;
				progress = update.progress();
				mistakes = update.mistakes();
				result = Quality.values()[update.quality()];
				phase = Phase.RESULT;
				closeAtOven();
			}
			default -> {
				if (phase != Phase.COOKING) {
					cookTicks = 0;
					board = buildBoard(updated);
				}
				if (update.mistakes() > mistakes) {
					flashTicks = 6;
				}
				dish = updated;
				progress = update.progress();
				mistakes = update.mistakes();
				parTicks = update.parTicks();
				limitTicks = update.limitTicks();
				phase = Phase.COOKING;
				Collections.shuffle(board, random);
			}
		}
		rebuildWidgets();
	}

	private void initGrilling() {
		int cx = this.width / 2;
		int by = top() + PANEL_H - 24;
		if (isOven()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.remove"), b -> serve())
				.bounds(cx - 50, by, 100, BUTTON_H).build());
			return;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.flip"), b -> flip())
			.bounds(cx - 105, by, 100, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.serve"), b -> serve())
			.bounds(cx + 5, by, 100, BUTTON_H).build());
	}

	/** Soul campfires burn blue (and hotter; the server already accounts for that). */
	private boolean isSoulFire() {
		return this.minecraft.level != null && this.minecraft.level.getBlockState(pos).is(Blocks.SOUL_CAMPFIRE);
	}

	private boolean isOven() {
		return dish != null && dish.method() == Dish.Method.OVEN;
	}

	private void flip() {
		if (phase == Phase.GRILLING && flipAnim < 0 && !isOven()) {
			flipAnim = 0;
			ClientPlayNetworking.send(new Payloads.GrillAction(Payloads.GrillAction.FLIP));
		}
	}

	private void serve() {
		if (phase == Phase.GRILLING) {
			ClientPlayNetworking.send(new Payloads.GrillAction(Payloads.GrillAction.SERVE));
		}
	}

	/** Called on the client thread with the server's view of the grill. */
	public void onGrillUpdate(GrillUpdate update) {
		Dish updated = Dish.byIndex(update.dish());
		if (updated == null) {
			return;
		}
		dish = updated;
		grillDone[0] = update.doneA();
		grillDone[1] = update.doneB();
		grillDown = update.down();
		grillPerTick = update.perTick();
		grillHeat = update.heat();
		sinceGrillUpdate = 0;
		if (update.status() == GrillUpdate.STATUS_FINISHED) {
			result = Quality.values()[update.quality()];
			phase = Phase.RESULT;
			if (closeAtOven()) {
				return;
			}
			rebuildWidgets();
		} else if (phase != Phase.GRILLING) {
			sinceGrillStart = 0;
			phase = Phase.GRILLING;
			rebuildWidgets();
		}
	}

	private float doneness(int side, float partialTick) {
		return grillDone[side] + (side == grillDown ? grillPerTick * (sinceGrillUpdate + partialTick) : 0.0F);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (phase == Phase.GRILLING && event.key() == InputConstants.KEY_SPACE) {
			if (isOven()) {
				serve();
			} else {
				flip();
			}
			return true;
		}
		if (phase == Phase.SMOKING && event.key() == InputConstants.KEY_SPACE) {
			fanning = true;
			return true;
		}
		if (phase == Phase.SEARING && event.key() == InputConstants.KEY_SPACE) {
			sear();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE) {
			fanning = false;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (phase == Phase.SMOKING) {
			fanning = true;
			return true;
		}
		if (phase == Phase.SEARING) {
			sear();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (fanning) {
			fanning = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Called on the client thread when a smoking or searing session starts or finishes. */
	public void onSimUpdate(SimUpdate update) {
		Dish updated = Dish.byIndex(update.dish());
		if (updated == null) {
			return;
		}
		dish = updated;
		if (update.status() == SimUpdate.STATUS_FINISHED) {
			result = Quality.values()[update.quality()];
			phase = Phase.RESULT;
			closeAtOven();
		} else if (updated.method() == Dish.Method.SEAR) {
			startSear();
		} else {
			heat = 0.25F;
			fanning = false;
			gustVelocity = 0.0F;
			gustTicks = 0;
			nextGust = Smoke.GUST_GAP_MIN + random.nextInt(Smoke.GUST_GAP_MAX - Smoke.GUST_GAP_MIN);
			cooling = Mth.lerp(random.nextFloat(), Smoke.COOLING_MIN, Smoke.COOLING_MAX);
			smokeTicks = 0;
			cureTicks = 0;
			scorchTicks = 0;
			coldTicks = 0;
			phase = Phase.SMOKING;
		}
		rebuildWidgets();
	}

	private void tickSmoke() {
		smokeTicks++;
		heat -= cooling;
		if (fanning) {
			heat += Smoke.FANNING;
		}
		// wind: every so often a gust flares the coals or blows the heat away for a moment
		if (gustTicks > 0) {
			heat += gustVelocity;
			gustTicks--;
		} else if (--nextGust <= 0) {
			gustVelocity = (random.nextBoolean() ? 1 : -1) * Mth.lerp(random.nextFloat(), Smoke.GUST_MIN, Smoke.GUST_MAX);
			gustTicks = Smoke.GUST_TICKS_MIN + random.nextInt(Smoke.GUST_TICKS_MAX - Smoke.GUST_TICKS_MIN);
			nextGust = Smoke.GUST_GAP_MIN + random.nextInt(Smoke.GUST_GAP_MAX - Smoke.GUST_GAP_MIN);
		}
		heat = Mth.clamp(heat, 0.0F, 1.0F);

		if (heat >= Smoke.BAND_MIN && heat <= Smoke.BAND_MAX) {
			cureTicks++;
		} else if (heat < Smoke.BAND_MIN && cureTicks > 0) {
			coldTicks++;
		}
		if (heat > Smoke.SCORCH) {
			scorchTicks++;
		}

		if (cureTicks >= Smoke.CURE_TICKS) {
			finishSmoke(true);
		} else if (scorchTicks >= Smoke.SCORCH_CHARRED || smokeTicks >= Smoke.TIME_LIMIT) {
			finishSmoke(false);
		}
	}

	private void startSear() {
		searResults.clear();
		searRound = 0;
		searFeedback = 0;
		nextSearRound();
		phase = Phase.SEARING;
	}

	private void nextSearRound() {
		searZone = Sear.ZONE_WIDTH / 2 + random.nextFloat() * (1.0F - Sear.ZONE_WIDTH);
		searSpeed = Sear.SPEED + searRound * Sear.SPEED_STEP + (random.nextFloat() * 2.0F - 1.0F) * Sear.SPEED_JITTER;
		// start from whichever end is further from the target, heading the other way
		searMarker = searZone > 0.5F ? 0.0F : 1.0F;
		searDirection = searZone > 0.5F ? 1.0F : -1.0F;
		searRoundTicks = 0;
	}

	private void sear() {
		if (phase != Phase.SEARING || searFeedback > 0) {
			return;
		}
		float off = Math.abs(searMarker - searZone);
		endSearRound(off <= Sear.CORE_WIDTH / 2 ? 2 : off <= Sear.ZONE_WIDTH / 2 ? 1 : 0);
	}

	private void endSearRound(int result) {
		searResults.add(result);
		searFeedback = 12;
	}

	private void tickSear() {
		if (searFeedback > 0) {
			if (--searFeedback == 0) {
				searRound++;
				if (searRound >= Sear.ROUNDS) {
					finishSear();
				} else {
					nextSearRound();
				}
			}
			return;
		}
		searMarker += searDirection * searSpeed;
		if (searMarker >= 1.0F) {
			searMarker = 2.0F - searMarker;
			searDirection = -1.0F;
		} else if (searMarker <= 0.0F) {
			searMarker = -searMarker;
			searDirection = 1.0F;
		}
		if (++searRoundTicks > Sear.ROUND_TICKS) {
			endSearRound(0);
		}
	}

	private void finishSear() {
		int perfects = 0;
		int goods = 0;
		for (int r : searResults) {
			if (r == 2) {
				perfects++;
			} else if (r == 1) {
				goods++;
			}
		}
		phase = Phase.WAITING;
		rebuildWidgets();
		ClientPlayNetworking.send(new Payloads.SearFinish(perfects, goods, searResults.size() - perfects - goods));
	}

	private void finishSmoke(boolean cured) {
		fanning = false;
		phase = Phase.WAITING;
		rebuildWidgets();
		ClientPlayNetworking.send(new Payloads.SmokeFinish(scorchTicks, coldTicks, cured));
	}

	private void drawSearing(GuiGraphicsExtractor graphics, int x, int y, float a) {
		graphics.centeredText(this.font, dishName(dish), this.width / 2, y + 22, COLOR_TEXT);
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.sear_hint"), this.width / 2, y + 34,
			COLOR_DIM);
		// the marker moves in whole ticks; draw it partway to its next spot so it glides
		float marker = Mth.clamp(searMarker + (searFeedback == 0 ? searDirection * searSpeed * a : 0.0F), 0.0F, 1.0F);
		SearView.draw(graphics, this.font, dish, this.width / 2, y + 50, x + 20, x + PANEL_W - 20, y + PANEL_H - 26, marker,
			searZone, searRound, searResults, searFeedback, animClock + a);
	}

	private void drawSmoking(GuiGraphicsExtractor graphics, int x, int y, float a) {
		graphics.centeredText(this.font, dishName(dish), this.width / 2, y + 22, COLOR_TEXT);
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.smoke_hint"), this.width / 2, y + 34,
			COLOR_DIM);
		SmokerView.draw(graphics, dish, this.width / 2, y + 48, x + 20, x + PANEL_W - 20, y + PANEL_H - 26, heat, fanning,
			cureTicks / (float) Smoke.CURE_TICKS, scorchTicks / (float) Smoke.SCORCH_CHARRED,
			1.0F - smokeTicks / (float) Smoke.TIME_LIMIT, animClock + a);
	}

	private void drawGrilling(GuiGraphicsExtractor graphics, int x, int y, float a) {
		graphics.centeredText(this.font, dishName(dish), this.width / 2, y + 22, COLOR_TEXT);
		graphics.centeredText(this.font,
			Component.translatable(isOven() ? "screen.jaymoskitchen.oven_hint" : "screen.jaymoskitchen.grill_hint"),
			this.width / 2, y + 34, COLOR_DIM);
		if (isOven()) {
			// the oven has one "side": side 0 is always in the heat
			OvenView.draw(graphics, dish, this.width / 2, y + PANEL_H - 30, doneness(0, a), grillHeat, sinceGrillStart + a,
				animClock + a);
			return;
		}
		float top = doneness(1 - grillDown, a);
		float bottom = doneness(grillDown, a);
		float flipProgress = flipAnim < 0 ? -1.0F : Math.min(1.0F, (flipAnim + a) / Grill.FLIP_TICKS);
		GrillView.draw(graphics, dish, this.width / 2, y + PANEL_H - 30, top, bottom, flipProgress, grillHeat, isSoulFire(),
			animClock + a);
	}

	private List<Step> buildBoard(Dish d) {
		Set<Step> steps = new LinkedHashSet<>(d.steps());
		List<Step> decoys = new ArrayList<>(Dish.DECOY_POOL);
		decoys.removeAll(steps);
		Collections.shuffle(decoys, random);
		steps.addAll(decoys.subList(0, Math.min(DECOYS, decoys.size())));
		// every action is always on the board, so the right one has to be picked out
		for (Step step : Step.values()) {
			if (step.isAction()) {
				steps.add(step);
			}
		}
		return new ArrayList<>(steps);
	}

	@Override
	public void tick() {
		super.tick();
		animClock++;
		if (phase == Phase.COOKING) {
			cookTicks++;
		}
		if (phase == Phase.GRILLING) {
			sinceGrillUpdate++;
			sinceGrillStart++;
		}
		if (phase == Phase.SMOKING) {
			tickSmoke();
		}
		if (phase == Phase.SEARING) {
			tickSear();
		}
		if (flipAnim >= 0 && ++flipAnim > Grill.FLIP_TICKS) {
			flipAnim = -1;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int border = flashTicks > 0 ? COLOR_BAD : COLOR_BORDER;
		// the frame's four edges only, so the panel inside stays see-through
		graphics.fill(x - 2, y - 2, x + PANEL_W + 2, y, border);
		graphics.fill(x - 2, y + PANEL_H, x + PANEL_W + 2, y + PANEL_H + 2, border);
		graphics.fill(x - 2, y, x, y + PANEL_H, border);
		graphics.fill(x + PANEL_W, y, x + PANEL_W + 2, y + PANEL_H, border);
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		Component stationName = Component.translatable(station.translationKey());
		graphics.centeredText(this.font, this.title.copy().append(" - ").append(stationName), this.width / 2, y + 8, COLOR_CURRENT);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		switch (phase) {
			case SELECT -> drawSelect(graphics, x, y);
			case WAITING -> graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.waiting"),
				this.width / 2, y + PANEL_H / 2, COLOR_DIM);
			case COOKING -> drawCooking(graphics, x, y);
			case GRILLING -> drawGrilling(graphics, x, y, a);
			case SMOKING -> drawSmoking(graphics, x, y, a);
			case SEARING -> drawSearing(graphics, x, y, a);
			case RESULT -> drawResult(graphics, x, y);
		}
	}

	private void drawSelect(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.text(this.font, Component.translatable("screen.jaymoskitchen.pick"), x + 10, y + 20, COLOR_DIM);
		List<Dish> dishes = station.dishes();
		int end = Math.min(dishes.size(), listScroll + LIST_ROWS);
		for (int i = listScroll; i < end; i++) {
			int iconX = x + 146;
			int iconY = y + 32 + (i - listScroll) * ROW_PITCH;
			for (Map.Entry<Step, Integer> need : dishes.get(i).ingredients().entrySet()) {
				ItemStack stack = need.getKey().icon(need.getValue());
				graphics.item(stack, iconX, iconY);
				graphics.itemDecorations(this.font, stack, iconX, iconY, null);
				iconX += 18;
			}
		}
		String hint = "screen.jaymoskitchen.hint." + station.name().toLowerCase();
		if (net.minecraft.locale.Language.getInstance().has(hint)) {
			graphics.centeredText(this.font, Component.translatable(hint), this.width / 2, y + PANEL_H - 16, COLOR_DIM);
		}
	}

	private void drawCooking(GuiGraphicsExtractor graphics, int x, int y) {
		// recipe card
		graphics.item(new ItemStack(ModItems.dish(dish)), x + 10, y + 22);
		graphics.text(this.font, dishName(dish), x + 30, y + 26, COLOR_TEXT);
		List<Step> steps = dish.steps();
		for (int i = 0; i < steps.size(); i++) {
			int color = i < progress ? COLOR_DONE : i == progress ? COLOR_CURRENT : COLOR_DIM;
			String mark = i < progress ? "✔ " : (i + 1) + ". ";
			graphics.text(this.font, Component.literal(mark).append(Component.translatable(steps.get(i).translationKey())),
				x + 12, y + 52 + i * 12, color);
		}

		// timer bar: green while a Perfect is still possible, then amber, then it burns
		int barX = x + 10;
		int barY = y + 40;
		int barW = PANEL_W - 20;
		float remaining = 1.0F - Math.min(1.0F, cookTicks / (float) Math.max(1, limitTicks));
		int barColor = cookTicks <= parTicks ? COLOR_DONE : COLOR_CURRENT;
		graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF000000);
		graphics.fill(barX, barY, barX + Math.round(barW * remaining), barY + 5, barColor);
		int parX = barX + Math.round(barW * (1.0F - parTicks / (float) Math.max(1, limitTicks)));
		graphics.fill(parX, barY - 2, parX + 1, barY + 7, COLOR_TEXT);

		// mistakes
		Component strikes = Component.translatable("screen.jaymoskitchen.mistakes", mistakes, Quality.MAX_MISTAKES);
		graphics.text(this.font, strikes, x + PANEL_W - 10 - this.font.width(strikes), y + 26,
			mistakes > 0 ? COLOR_BAD : COLOR_DIM);

		// icons on the step buttons (widgets are laid out in board order)
		int x0 = x + PANEL_W - 10 - BUTTON_W * 2 - 6;
		for (int i = 0; i < board.size(); i++) {
			graphics.item(new ItemStack(board.get(i).item()), x0 + (i % 2) * (BUTTON_W + 6) + 3,
				y + 52 + (i / 2) * (BUTTON_H + 4) + 2);
		}
	}

	private void drawResult(GuiGraphicsExtractor graphics, int x, int y) {
		int cx = this.width / 2;
		ItemStack stack = new ItemStack(ModItems.dish(dish));
		result.apply(stack, dish);
		graphics.item(stack, cx - 8, y + 40);
		graphics.centeredText(this.font, stack.getHoverName(), cx, y + 64, COLOR_TEXT);
		String prefix = switch (dish.method()) {
			case SEQUENCE -> "result.jaymoskitchen.";
			case GRILL -> "result.jaymoskitchen.grill.";
			case OVEN -> "result.jaymoskitchen.oven.";
			case SMOKE -> "result.jaymoskitchen.smoke.";
			case SEAR -> "result.jaymoskitchen.sear.";
		};
		String resultKey = prefix + result.name().toLowerCase();
		graphics.centeredText(this.font, Component.translatable(resultKey), cx, y + 84, COLOR_TEXT);
		if (dish.method() == Dish.Method.SEQUENCE) {
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.mistakes", mistakes, Quality.MAX_MISTAKES),
				cx, y + 100, COLOR_DIM);
		}
	}

	private static Component dishName(Dish d) {
		return Component.translatable(ModItems.dish(d).getDescriptionId());
	}

	@Override
	public void removed() {
		if (phase == Phase.COOKING || phase == Phase.GRILLING || phase == Phase.SMOKING || phase == Phase.SEARING
			|| phase == Phase.WAITING) {
			ClientPlayNetworking.send(new Payloads.Abandon());
		}
		super.removed();
	}

	/** At the oven the dish finishes on the hob (see Kitchen#award): step back and watch it rather than read a card. */
	private boolean closeAtOven() {
		if (station.isStove()) {
			onClose();
			return true;
		}
		return false;
	}

	/** No blur or dimming behind the panel: the world (the pot, the grill) stays in view. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
