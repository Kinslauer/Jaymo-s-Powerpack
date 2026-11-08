package com.jaymospowers.client.menu;

import com.jaymospowers.client.PowerScreen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.60) The character menu (Andy, after Kageborne's one-screen menu): one menu on H, a tab for each part of your
 * character down the left edge of whichever page is open. Each tab is its own screen (the H screen, the K skill tree, the
 * journal...), so nothing is rewritten: this only draws the tabs over the page and switches between them. A page's own
 * key (K, M) opens it as before, and the tabs are on it, so the old keys open their tab.
 *
 * <p>Jaymo's Kingdoms adds its tabs (Skills, Journal, Faction, Party), its rows on the Character page and its pins on the
 * Map by reflection when both are loaded ({@code com.jaymoskingdoms.client.MenuCompat}): every argument here is a plain
 * Java or Minecraft type, so Kingdoms never compiles against Powers.
 */
public final class MenuTabs {
	/** A tab: its size, how far it sticks out of the page's left edge, the gap between tabs, and where the first starts. */
	public static final int TAB_W = 28, TAB_H = 24, TAB_STEP = 26, TAB_TOP = 6, TAB_OUT = 26;
	/** The order the tabs come in (Kingdoms' fall between these). */
	public static final int ORDER_CHARACTER = 0, ORDER_POWERS = 10, ORDER_MAP = 50;

	/**
	 * One tab. {@code owns}: whether a screen is this tab's page (it gets the tabs drawn on it); {@code anchor}: the page's
	 * top-left corner on screen ({@code {x, y}}), the tabs hang off its left edge; {@code shown}: whether it's there now.
	 */
	public record Tab(String id, int order, Supplier<ItemStack> icon, Component label, Runnable open, Predicate<Screen> owns,
		Function<Screen, int[]> anchor, BooleanSupplier shown) {
	}

	private static final List<Tab> TABS = new ArrayList<>();
	/** Extra rows for the Character page: each list a section, each row {label, value} (a single-element row is a heading). */
	private static final List<Supplier<List<Component[]>>> CHARACTER_ROWS = new ArrayList<>();
	/** Extra pins for the Map page: a name (its colour the pin's) and a place in the world you stand in. */
	private static final List<Supplier<List<Map.Entry<Component, BlockPos>>>> PINS = new ArrayList<>();
	/** Screens the tabs are already hooked onto (a resize inits a screen again). */
	private static final Set<Screen> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());
	/** The tab last open, for H to come back to. */
	private static String last = "character";

	private MenuTabs() {
	}

	/** Adds a tab (see {@link Tab}). Called by reflection from Kingdoms, so keep its parameter types. */
	public static void register(String id, int order, Supplier<ItemStack> icon, Component label, Runnable open, Predicate<Screen> owns,
		Function<Screen, int[]> anchor, BooleanSupplier shown) {
		TABS.removeIf(t -> t.id().equals(id));
		TABS.add(new Tab(id, order, icon, label, open, owns, anchor, shown));
		TABS.sort(Comparator.comparingInt(Tab::order));
	}

	/** Adds rows to the Character page. Called by reflection from Kingdoms. */
	public static void characterRows(Supplier<List<Component[]>> rows) {
		CHARACTER_ROWS.add(rows);
	}

	/** Adds pins to the Map page. Called by reflection from Kingdoms. */
	public static void pins(Supplier<List<Map.Entry<Component, BlockPos>>> pins) {
		PINS.add(pins);
	}

	static List<List<Component[]>> characterRows() {
		List<List<Component[]>> out = new ArrayList<>();
		for (Supplier<List<Component[]>> rows : CHARACTER_ROWS) {
			try {
				List<Component[]> got = rows.get();
				if (got != null && !got.isEmpty()) {
					out.add(got);
				}
			} catch (RuntimeException | LinkageError e) {
				// a changed Kingdoms: leave its rows out rather than close the page
			}
		}
		return out;
	}

	static List<Map.Entry<Component, BlockPos>> pins() {
		List<Map.Entry<Component, BlockPos>> out = new ArrayList<>();
		for (Supplier<List<Map.Entry<Component, BlockPos>>> pins : PINS) {
			try {
				List<Map.Entry<Component, BlockPos>> got = pins.get();
				if (got != null) {
					out.addAll(got);
				}
			} catch (RuntimeException | LinkageError e) {
				// as above
			}
		}
		return out;
	}

	/** The tabs there are now. */
	public static List<Tab> shown() {
		List<Tab> out = new ArrayList<>();
		for (Tab t : TABS) {
			if (t.shown().getAsBoolean()) {
				out.add(t);
			}
		}
		return out;
	}

	/** The tab {@code screen} is the page of, or null. */
	public static @Nullable Tab of(Screen screen) {
		for (Tab t : TABS) {
			if (t.owns().test(screen)) {
				return t;
			}
		}
		return null;
	}

	/** Opens the tab named {@code id} (the Character page if it isn't there). */
	public static void open(String id) {
		for (Tab t : shown()) {
			if (t.id().equals(id)) {
				t.open().run();
				return;
			}
		}
		Minecraft.getInstance().gui.setScreen(new CharacterScreen());
	}

	/** H: the tab you had open last. */
	public static void openLast() {
		open(last);
	}

	/** Registers Powers' own tabs and hooks the tabs onto every page as it opens. */
	public static void init() {
		register("character", ORDER_CHARACTER, () -> new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
			Component.translatable("menu.jaymospowers.tab.character"), () -> Minecraft.getInstance().gui.setScreen(new CharacterScreen()),
			s -> s instanceof CharacterScreen, s -> ((CharacterScreen)s).corner(), () -> true);
		register("powers", ORDER_POWERS, PowerScreen::menuIcon, Component.translatable("menu.jaymospowers.tab.powers"),
			() -> Minecraft.getInstance().gui.setScreen(new PowerScreen()), s -> s instanceof PowerScreen, s -> ((PowerScreen)s).corner(), () -> true);
		register("map", ORDER_MAP, () -> new ItemStack(net.minecraft.world.item.Items.MAP), Component.translatable("menu.jaymospowers.tab.map"),
			() -> Minecraft.getInstance().gui.setScreen(new MapScreen()), s -> s instanceof MapScreen, s -> ((MapScreen)s).corner(), () -> true);
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			Tab tab = of(screen);
			if (tab == null) {
				return;
			}
			last = tab.id();
			if (!HOOKED.add(screen)) {
				return;
			}
			// drawn after the page (so over it) and clicked before it: the page's own rebuilds never clear them
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(s, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(s, (int)event.x(), (int)event.y()));
		});
	}

	/** The tabs' top-left corner on {@code screen}: off the page's left edge, kept on screen. */
	private static int[] origin(Screen screen, Tab page) {
		int[] at;
		try {
			at = page.anchor().apply(screen);
		} catch (RuntimeException | LinkageError e) {
			at = null;
		}
		if (at == null || at.length < 2) {
			at = new int[] {screen.width / 2 - 100, screen.height / 2 - 90};
		}
		return new int[] {Math.max(0, at[0] - TAB_OUT), at[1]};
	}

	private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Tab page = of(screen);
		if (page == null) {
			return;
		}
		int[] at = origin(screen, page);
		List<Tab> tabs = shown();
		Tab hovered = null;
		for (int i = 0; i < tabs.size(); i++) {
			Tab t = tabs.get(i);
			int x = at[0], y = at[1] + TAB_TOP + i * TAB_STEP;
			boolean picked = t == page;
			boolean hover = PowerScreen.over(mouseX, mouseY, x, y, TAB_OUT, TAB_H);
			graphics.fill(x, y, x + TAB_W, y + TAB_H, PowerScreen.COLOR_EDGE);
			graphics.fill(x + 1, y + 1, x + TAB_W, y + TAB_H - 1, picked ? PowerScreen.COLOR_LIGHT : PowerScreen.COLOR_SHADE);
			graphics.fill(x + 3, y + 3, x + TAB_W, y + TAB_H - 3, picked ? PowerScreen.COLOR_BODY : hover ? PowerScreen.COLOR_WELL_LIGHT : PowerScreen.COLOR_TAB);
			ItemStack icon;
			try {
				icon = t.icon().get();
			} catch (RuntimeException | LinkageError e) {
				icon = ItemStack.EMPTY;
			}
			if (icon != null && !icon.isEmpty()) {
				graphics.item(icon, x + 6, y + 4);
			}
			if (hover) {
				hovered = t;
			}
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, hovered.label(), mouseX, mouseY);
		}
	}

	/** A click on a tab opens its page; true if the click was on one. */
	private static boolean click(Screen screen, int mouseX, int mouseY) {
		Tab page = of(screen);
		if (page == null) {
			return false;
		}
		int[] at = origin(screen, page);
		List<Tab> tabs = shown();
		for (int i = 0; i < tabs.size(); i++) {
			if (PowerScreen.over(mouseX, mouseY, at[0], at[1] + TAB_TOP + i * TAB_STEP, TAB_OUT, TAB_H)) {
				Tab t = tabs.get(i);
				if (t != page) {
					Minecraft minecraft = Minecraft.getInstance();
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					t.open().run();
				}
				return true;
			}
		}
		return false;
	}
}
