package com.jaymospowers.client.menu;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.backpack.Backpack;
import com.jaymospowers.backpack.BackpackNet;
import com.jaymospowers.baubles.BaublesNet;
import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.baubles.BaublesScreen;
import com.jaymospowers.client.sound.SoundBrowserScreen;
import com.mojang.brigadier.Command;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The character menu (Andy, after Kageborne's one-screen menu): one menu on H that holds every page a player can open on
 * their own, the inventory included. Each page is its own screen (the inventory, the H character sheet, the K skill
 * tree, the journal...), so nothing is rewritten: this only draws the menu over the page and switches between pages. A
 * page's own key (K, M, E) opens it as before, and the menu is on it.
 *
 * <p>Pages are sorted into groups (You, Powers, World, People, Notices) and shown one of two ways, picked with the
 * button on the menu or {@code /menulook bar|side} (kept in {@code config/jaymospowers-menulook.properties}): a bar
 * across the top with a dropdown list of pages for each group (the default), or a slim list of every page down the left
 * side, as in a Codex. A page that isn't open to you yet shows greyed with a hint instead of hiding. On any page E (the
 * inventory key) or Esc closes the menu; the wheel over the bar turns pages; H opens the page you had open last.
 * {@link MenuBar} draws it.
 *
 * <p>Jaymo's Kingdoms adds its tabs (Skills, Journal, Faction, Party...), their groups and locks, its rows on the
 * Character page and its pins on the Map by reflection when both are loaded ({@code com.jaymoskingdoms.client.MenuCompat}):
 * every argument here is a plain Java or Minecraft type, so Kingdoms never compiles against Powers.
 */
public final class MenuTabs {
	/** The groups, in the order they come across the bar (or down the list). A page not given a group is in You. */
	public static final List<String> GROUPS = List.of("you", "powers", "world", "people", "notices");
	/** The bar: where its plates sit at the top of the screen, how tall they are, and the band the bar fills. */
	public static final int STRIP_Y = 2, STRIP_H = 22, BAR_H = STRIP_Y + STRIP_H + 2;
	/** The side list's width. */
	public static final int SIDE_W = 96;
	/** The room every page leaves at the top of the screen for the bar (0 with the side list). */
	public static int ROOM = BAR_H + 2;
	/** The room every page leaves at the left of the screen for the side list (0 with the bar). */
	public static int SIDE;
	/** The order the tabs come in (Kingdoms' fall between these). */
	public static final int ORDER_INVENTORY = -10, ORDER_CHARACTER = 0, ORDER_BAUBLES = 4, ORDER_BACKPACK = 6, ORDER_POWERS = 10,
		ORDER_MAP = 50, ORDER_SOUNDS = 55;

	/**
	 * One tab. {@code owns}: whether a screen is this tab's page (it gets the menu drawn on it); {@code anchor}: the page's
	 * top-left corner on screen ({@code {x, y}}), no longer used; {@code shown}: whether it's open to you now (when it isn't
	 * and a hint was given with {@link #locked}, it shows greyed with the hint).
	 */
	public record Tab(String id, int order, Supplier<ItemStack> icon, Component label, Runnable open, Predicate<Screen> owns,
		Function<Screen, int[]> anchor, BooleanSupplier shown) {
	}

	private static final List<Tab> TABS = new ArrayList<>();
	/** Tab id to its group, and to the hint a locked tab shows (kept by id, so they may be set before the tab is). */
	private static final Map<String, String> GROUP_OF = new HashMap<>();
	private static final Map<String, Supplier<Component>> HINT_OF = new HashMap<>();
	/** The tabs that are full-width pages. */
	private static final Set<String> WIDE = new HashSet<>();
	/** Extra rows for the Character page, by the panel they belong to (see {@link #characterRows(String, Supplier)}). */
	private static final Map<String, List<Supplier<List<Component[]>>>> CHARACTER_ROWS = new HashMap<>();
	/** Links on the Character page's Record panel: a label and what it opens (see {@link #characterLink}). */
	private static final List<Map.Entry<Supplier<Component>, Runnable>> CHARACTER_LINKS = new ArrayList<>();
	/** Extra pins for the Map page: a name (its colour the pin's) and a place in the world you stand in. */
	private static final List<Supplier<List<Map.Entry<Component, BlockPos>>>> PINS = new ArrayList<>();
	/** Screens the menu is already hooked onto (a resize inits a screen again). */
	private static final Set<Screen> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final String FILE = "jaymospowers-menulook.properties";
	/** The tab last open, for H to come back to. */
	private static String last = "character";
	/** True for the side list, false for the bar. */
	private static boolean side = load();

	private MenuTabs() {
	}

	/** Adds a tab (see {@link Tab}), in the You group until {@link #group} says otherwise. Called by reflection from Kingdoms, so keep its parameter types. */
	public static void register(String id, int order, Supplier<ItemStack> icon, Component label, Runnable open, Predicate<Screen> owns,
		Function<Screen, int[]> anchor, BooleanSupplier shown) {
		TABS.removeIf(t -> t.id().equals(id));
		TABS.add(new Tab(id, order, icon, label, open, owns, anchor, shown));
		TABS.sort(Comparator.comparingInt(Tab::order));
	}

	/** Puts the tab named {@code id} in {@code group} (one of {@link #GROUPS}). Called by reflection from Kingdoms. */
	public static void group(String id, String group) {
		GROUP_OF.put(id, group);
	}

	/** Makes the tab named {@code id} show greyed with {@code hint} while its {@code shown} says no. Called by reflection from Kingdoms. */
	public static void locked(String id, Supplier<Component> hint) {
		HINT_OF.put(id, hint);
	}

	/**
	 * Marks the tab named {@code id} as a full-width page: with the side list picked it still gets the bar, as the list
	 * would cover the page. Called by reflection from Kingdoms.
	 */
	public static void wide(String id) {
		WIDE.add(id);
	}

	static boolean isWide(Tab tab) {
		return WIDE.contains(tab.id());
	}

	/** Adds rows to the Character page's Record panel (the form Kingdoms first used). Called by reflection from Kingdoms. */
	public static void characterRows(Supplier<List<Component[]>> rows) {
		characterRows("record", rows);
	}

	/**
	 * Adds rows to a panel of the Character page: {@code header} (each row's value is a line of who you are: heritage,
	 * class, title), {@code level} (one row, its value the level the header shows), {@code attributes},
	 * {@code resistances} or {@code record}; each row {label, value}, a single-element row a heading. Called by
	 * reflection from Kingdoms.
	 */
	public static void characterRows(String panel, Supplier<List<Component[]>> rows) {
		CHARACTER_ROWS.computeIfAbsent(panel, p -> new ArrayList<>()).add(rows);
	}

	/** Adds a link to the Character page's Record panel (Kingdoms' "Show my papers"). Called by reflection from Kingdoms. */
	public static void characterLink(Supplier<Component> label, Runnable open) {
		CHARACTER_LINKS.add(Map.entry(label, open));
	}

	static List<Map.Entry<Supplier<Component>, Runnable>> characterLinks() {
		return CHARACTER_LINKS;
	}

	/** Adds pins to the Map page. Called by reflection from Kingdoms. */
	public static void pins(Supplier<List<Map.Entry<Component, BlockPos>>> pins) {
		PINS.add(pins);
	}

	static List<Component[]> characterRows(String panel) {
		List<Component[]> out = new ArrayList<>();
		for (Supplier<List<Component[]>> rows : CHARACTER_ROWS.getOrDefault(panel, List.of())) {
			try {
				List<Component[]> got = rows.get();
				if (got != null) {
					out.addAll(got);
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

	private static boolean isOpen(Tab t) {
		try {
			return t.shown().getAsBoolean();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** The tabs open to you now (the wheel and H turn through these). */
	public static List<Tab> shown() {
		List<Tab> out = new ArrayList<>();
		for (Tab t : TABS) {
			if (isOpen(t)) {
				out.add(t);
			}
		}
		return out;
	}

	/** The tabs to list: the ones open to you, and the locked ones that have a hint. */
	static List<Tab> listed() {
		List<Tab> out = new ArrayList<>();
		for (Tab t : TABS) {
			if (isOpen(t) || HINT_OF.containsKey(t.id())) {
				out.add(t);
			}
		}
		return out;
	}

	/** The tabs of {@code group} to list. */
	static List<Tab> listed(String group) {
		List<Tab> out = new ArrayList<>();
		for (Tab t : listed()) {
			if (groupOf(t).equals(group)) {
				out.add(t);
			}
		}
		return out;
	}

	static String groupOf(Tab tab) {
		String group = GROUP_OF.get(tab.id());
		return group != null && GROUPS.contains(group) ? group : "you";
	}

	/** Why {@code tab} is locked now (empty when it has no words for it), or null if it isn't locked. */
	static @Nullable Component lock(Tab tab) {
		Supplier<Component> hint = HINT_OF.get(tab.id());
		if (hint == null || isOpen(tab)) {
			return null;
		}
		try {
			Component got = hint.get();
			return got == null ? Component.empty() : got;
		} catch (RuntimeException | LinkageError e) {
			return Component.empty();
		}
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

	/** Whether the side list is on (false: the bar). */
	static boolean side() {
		return side;
	}

	/** Where a page {@code pageW} wide sits across a screen {@code screenW} wide, clear of the side list. */
	public static int centreX(int screenW, int pageW) {
		return Math.max(SIDE, SIDE + (screenW - SIDE - pageW) / 2);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static boolean load() {
		boolean wanted = false;
		if (Files.exists(path())) {
			try (InputStream in = Files.newInputStream(path())) {
				Properties state = new Properties();
				state.load(in);
				wanted = "side".equals(state.getProperty("look"));
			} catch (IOException | RuntimeException e) {
				// nothing read: the bar
			}
		}
		apply(wanted);
		return wanted;
	}

	private static void apply(boolean wanted) {
		ROOM = wanted ? 0 : BAR_H + 2;
		SIDE = wanted ? SIDE_W : 0;
	}

	/** Picks the bar or the side list, keeps the pick, and opens the page you are on again so it lays out for it. */
	static void setLook(boolean wanted) {
		if (wanted != side) {
			side = wanted;
			apply(wanted);
			Properties state = new Properties();
			state.setProperty("look", wanted ? "side" : "bar");
			try (OutputStream out = Files.newOutputStream(path())) {
				state.store(out, "Jaymo's Powers: the menu as a bar with dropdowns or a side list (/menulook bar|side).");
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't write {}", path(), e);
			}
		}
		Screen screen = Minecraft.getInstance().gui.screen();
		Tab tab = screen == null ? null : of(screen);
		if (tab != null) {
			tab.open().run();
		}
	}

	private static int command(FabricClientCommandSource source, boolean wanted) {
		setLook(wanted);
		source.sendFeedback(Component.translatable(wanted ? "message.jaymospowers.menulook.side" : "message.jaymospowers.menulook.bar"));
		return Command.SINGLE_SUCCESS;
	}

	private static @Nullable LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	/** Registers Powers' own tabs and hooks the menu onto every page as it opens. */
	public static void init() {
		register("inventory", ORDER_INVENTORY, () -> new ItemStack(Items.CHEST), Component.translatable("menu.jaymospowers.tab.inventory"),
			() -> {
				if (player() != null) {
					Minecraft.getInstance().gui.setScreen(new InventoryScreen(player()));
				}
			}, s -> s instanceof InventoryScreen, s -> new int[] {0, 0}, () -> player() != null && !player().isCreative());
		register("character", ORDER_CHARACTER, () -> new ItemStack(Items.PLAYER_HEAD),
			Component.translatable("menu.jaymospowers.tab.character"), () -> Minecraft.getInstance().gui.setScreen(new CharacterScreen()),
			s -> s instanceof CharacterScreen, s -> ((CharacterScreen)s).corner(), () -> true);
		register("baubles", ORDER_BAUBLES, () -> new ItemStack(Items.AMETHYST_SHARD), Component.translatable("container.jaymospowers.baubles"),
			() -> {
				if (ClientPlayNetworking.canSend(BaublesNet.Open.TYPE)) {
					ClientPlayNetworking.send(new BaublesNet.Open());
				}
			}, s -> s instanceof BaublesScreen, s -> new int[] {0, 0}, () -> ClientPlayNetworking.canSend(BaublesNet.Open.TYPE));
		register("backpack", ORDER_BACKPACK, () -> new ItemStack(Items.LEATHER), Component.translatable("menu.jaymospowers.tab.backpack"),
			() -> ClientPlayNetworking.send(new BackpackNet.Key(false)), s -> false, s -> new int[] {0, 0},
			() -> player() != null && Backpack.wearing(player()) && Minecraft.getInstance().getConnection() != null);
		locked("backpack", () -> Component.translatable("menu.jaymospowers.locked.backpack"));
		register("powers", ORDER_POWERS, PowerScreen::menuIcon, Component.translatable("menu.jaymospowers.tab.powers"),
			() -> Minecraft.getInstance().gui.setScreen(new PowerScreen()), s -> s instanceof PowerScreen, s -> ((PowerScreen)s).corner(), () -> true);
		register("map", ORDER_MAP, () -> new ItemStack(Items.MAP), Component.translatable("menu.jaymospowers.tab.map"),
			() -> Minecraft.getInstance().gui.setScreen(new MapScreen()), s -> s instanceof MapScreen, s -> ((MapScreen)s).corner(), () -> true);
		register("sounds", ORDER_SOUNDS, () -> new ItemStack(Items.NOTE_BLOCK), Component.translatable("menu.jaymospowers.tab.sounds"),
			() -> Minecraft.getInstance().gui.setScreen(new SoundBrowserScreen(null)), s -> s instanceof SoundBrowserScreen, s -> new int[] {0, 0},
			() -> true);
		group("inventory", "you");
		group("character", "you");
		group("baubles", "you");
		group("backpack", "you");
		group("powers", "powers");
		group("trials", "powers");
		group("map", "world");
		group("sounds", "world");
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("menulook")
				.executes(c -> {
					c.getSource().sendFeedback(Component.translatable(side ? "message.jaymospowers.menulook.is_side" : "message.jaymospowers.menulook.is_bar"));
					return Command.SINGLE_SUCCESS;
				})
				.then(ClientCommands.literal("bar").executes(c -> command(c.getSource(), false)))
				.then(ClientCommands.literal("side").executes(c -> command(c.getSource(), true)))));
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
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> MenuBar.draw(s, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !MenuBar.click(s, (int)event.x(), (int)event.y()));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !MenuBar.scroll(s, (int)mouseX, (int)mouseY, vertical));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !key(s, event));
		});
	}

	/** E (the inventory key) closes the menu from any page, unless a text box is being typed in; true if the key was that. */
	private static boolean key(Screen screen, KeyEvent event) {
		if (!Minecraft.getInstance().options.keyInventory.matches(event) || screen.getFocused() instanceof EditBox) {
			return false;
		}
		screen.onClose();
		return true;
	}
}
