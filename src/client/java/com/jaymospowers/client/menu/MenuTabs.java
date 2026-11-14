package com.jaymospowers.client.menu;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.PowerScreen;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.60) The character menu (Andy, after Kageborne's one-screen menu): one menu on H, a tab for each part of your
 * character in one strip across the top of the screen, whichever page is open (Andy: "too cluttered with menus ... maybe
 * a single scroll up top"): every page an icon, the open one with its name, a Q and an E plate at each end (those keys, or a click on them, turn pages), and the wheel over the
 * strip turns to the next page. When they don't all fit, the strip shows the ones round the open page. Each tab is its own screen (the H screen, the K skill tree, the
 * journal...), so nothing is rewritten: this only draws the tabs over the page and switches between them. A page's own
 * key (K, M) opens it as before, and the tabs are on it, so the old keys open their tab.
 *
 * <p>Jaymo's Kingdoms adds its tabs (Skills, Journal, Faction, Party), its rows on the Character page and its pins on the
 * Map by reflection when both are loaded ({@code com.jaymoskingdoms.client.MenuCompat}): every argument here is a plain
 * Java or Minecraft type, so Kingdoms never compiles against Powers.
 */
public final class MenuTabs {
	/**
	 * The strip: where it sits at the top of the screen and how tall it is, a tab's width, the room round the open page's
	 * name, an arrow's width and its gap from the tabs.
	 */
	public static final int STRIP_Y = 2, STRIP_H = 22, TAB_W = 24, NAME_PAD = 6, ARROW_W = 14, STRIP_GAP = 2;
	/** The room every page leaves at the top of the screen for the strip. */
	public static final int ROOM = STRIP_Y + STRIP_H + 4;
	/** The order the tabs come in (Kingdoms' fall between these). */
	public static final int ORDER_CHARACTER = 0, ORDER_POWERS = 10, ORDER_MAP = 50;

	/**
	 * One tab. {@code owns}: whether a screen is this tab's page (it gets the tabs drawn on it); {@code anchor}: the page's
	 * top-left corner on screen ({@code {x, y}}), no longer used (the strip sits at the top of the screen); {@code shown}: whether it's there now.
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
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !scroll(s, (int)mouseX, (int)mouseY, vertical));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !key(s, event.key()));
		});
	}

	/** A tab on the strip: where it is drawn and how wide. */
	private record Slot(Tab tab, int x, int w) {
	}

	/**
	 * The strip's tabs on {@code screen}, left to right: every tab an icon, the open page's wider with its name. If they
	 * don't all fit between the arrows, only the ones round the open page are there (the arrows and the wheel reach the rest).
	 */
	private static List<Slot> slots(Screen screen, Tab page) {
		List<Tab> tabs = shown();
		int n = tabs.size(), at = Math.max(0, tabs.indexOf(page));
		int name = Minecraft.getInstance().font.width(page.label()) + NAME_PAD;
		int room = screen.width - 2 * (ARROW_W + STRIP_GAP + 4);
		int fit = Math.clamp((room - name) / TAB_W, 1, Math.max(1, n));
		int first = Math.clamp(at - fit / 2, 0, Math.max(0, n - fit));
		int width = Math.min(n, fit) * TAB_W + name, x = (screen.width - width) / 2;
		List<Slot> out = new ArrayList<>();
		for (int i = first; i < Math.min(n, first + fit); i++) {
			int w = TAB_W + (tabs.get(i) == page ? name : 0);
			out.add(new Slot(tabs.get(i), x, w));
			x += w;
		}
		return out;
	}

	/** The tab {@code step} along from {@code page} (round the ends). */
	private static Tab along(Tab page, int step) {
		List<Tab> tabs = shown();
		return tabs.isEmpty() ? page : tabs.get(Math.floorMod(Math.max(0, tabs.indexOf(page)) + step, tabs.size()));
	}

	private static void go(Tab page, Tab to) {
		if (to != page) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			to.open().run();
		}
	}

	private static void plate(GuiGraphicsExtractor graphics, int x, int w, boolean picked, boolean hover) {
		graphics.fill(x, STRIP_Y, x + w, STRIP_Y + STRIP_H, PowerScreen.COLOR_EDGE);
		graphics.fill(x + 1, STRIP_Y + 1, x + w - 1, STRIP_Y + STRIP_H - 1, picked ? PowerScreen.COLOR_LIGHT : PowerScreen.COLOR_SHADE);
		graphics.fill(x + 2, STRIP_Y + 2, x + w - 2, STRIP_Y + STRIP_H - 2,
			picked ? PowerScreen.COLOR_BODY : hover ? PowerScreen.COLOR_WELL_LIGHT : PowerScreen.COLOR_TAB);
	}

	private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Tab page = of(screen);
		if (page == null) {
			return;
		}
		List<Slot> slots = slots(screen, page);
		if (slots.isEmpty()) {
			return;
		}
		var font = Minecraft.getInstance().font;
		Tab hovered = null;
		for (Slot slot : slots) {
			boolean picked = slot.tab() == page, hover = PowerScreen.over(mouseX, mouseY, slot.x(), STRIP_Y, slot.w(), STRIP_H);
			plate(graphics, slot.x(), slot.w(), picked, hover);
			ItemStack icon;
			try {
				icon = slot.tab().icon().get();
			} catch (RuntimeException | LinkageError e) {
				icon = ItemStack.EMPTY;
			}
			if (icon != null && !icon.isEmpty()) {
				graphics.item(icon, slot.x() + (TAB_W - 16) / 2, STRIP_Y + (STRIP_H - 16) / 2);
			}
			if (picked) {
				graphics.text(font, page.label(), slot.x() + TAB_W, STRIP_Y + (STRIP_H - 8) / 2, PowerScreen.COLOR_GOLD, false);
			} else if (hover) {
				hovered = slot.tab();
			}
		}
		// an arrow at each end: the page before, the page after
		int left = slots.getFirst().x() - STRIP_GAP - ARROW_W, right = slots.getLast().x() + slots.getLast().w() + STRIP_GAP;
		for (int side = 0; side < 2; side++) {
			int x = side == 0 ? left : right;
			boolean hover = PowerScreen.over(mouseX, mouseY, x, STRIP_Y, ARROW_W, STRIP_H);
			plate(graphics, x, ARROW_W, false, hover);
			graphics.text(font, side == 0 ? "Q" : "E", x + (ARROW_W - 5) / 2, STRIP_Y + (STRIP_H - 8) / 2, hover ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_TEXT, false);
			if (hover) {
				hovered = along(page, side == 0 ? -1 : 1);
			}
		}
		if (hovered != null) {
			UiStyle.tip(graphics, font, hovered.label(), mouseX, mouseY);
		}
	}

	/** Whether the mouse is on the strip (its tabs or its arrows). */
	private static boolean onStrip(List<Slot> slots, int mouseX, int mouseY) {
		return !slots.isEmpty() && mouseY >= STRIP_Y && mouseY < STRIP_Y + STRIP_H && mouseX >= slots.getFirst().x() - STRIP_GAP - ARROW_W
			&& mouseX < slots.getLast().x() + slots.getLast().w() + STRIP_GAP + ARROW_W;
	}

	/** A click on a tab opens its page, one on an arrow the page before or after; true if the click was on the strip. */
	private static boolean click(Screen screen, int mouseX, int mouseY) {
		Tab page = of(screen);
		if (page == null) {
			return false;
		}
		List<Slot> slots = slots(screen, page);
		if (!onStrip(slots, mouseX, mouseY)) {
			return false;
		}
		for (Slot slot : slots) {
			if (mouseX >= slot.x() && mouseX < slot.x() + slot.w()) {
				go(page, slot.tab());
				return true;
			}
		}
		if (mouseX < slots.getFirst().x() - STRIP_GAP) {
			go(page, along(page, -1));
		} else if (mouseX >= slots.getLast().x() + slots.getLast().w() + STRIP_GAP) {
			go(page, along(page, 1));
		}
		return true;
	}

	/** Q turns to the page before, E to the page after (not while a text box is being typed in); true if the key was one of them. */
	private static boolean key(Screen screen, int key) {
		Tab page = of(screen);
		if (page == null || key != InputConstants.KEY_Q && key != InputConstants.KEY_E || screen.getFocused() instanceof EditBox) {
			return false;
		}
		go(page, along(page, key == InputConstants.KEY_Q ? -1 : 1));
		return true;
	}

	/** The wheel over the strip turns to the page before (up) or after (down); true if the mouse was on the strip. */
	private static boolean scroll(Screen screen, int mouseX, int mouseY, double amount) {
		Tab page = of(screen);
		if (page == null || amount == 0.0 || !onStrip(slots(screen, page), mouseX, mouseY)) {
			return false;
		}
		go(page, along(page, amount > 0 ? -1 : 1));
		return true;
	}
}
