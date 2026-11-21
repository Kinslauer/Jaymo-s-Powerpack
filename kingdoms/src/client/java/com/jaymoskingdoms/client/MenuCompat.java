package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.Journal;
import com.jaymoskingdoms.MagicMenu;
import com.jaymoskingdoms.MenuNet;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.TitleChoice;
import com.jaymoskingdoms.WorldTiers;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.7.649) Jaymo's Powers' character menu (H; Andy, after Kageborne's one-screen menu), when Powers is loaded: Kingdoms'
 * tabs on it (Skills, the K screen; Story, the Retaking; Journal & Quests, the M journal; Faction; Party), its rows on the Character page
 * (heritage, class, career, renown, skills, world tier, patron, realm) and its pins on the Map page (the settlements you
 * know, your party). Powers' {@code com.jaymospowers.client.menu.MenuTabs} is reached by reflection with plain Java and
 * Minecraft types only, so Kingdoms never compiles against Powers; without Powers, K and M work as they always did.
 */
public final class MenuCompat {
	static final String POWERS = "jaymospowers";
	/** The room Kingdoms' pages leave at the top of the screen for the character menu's strip of tabs (none without Powers). */
	public static final int STRIP = FabricLoader.getInstance().isModLoaded(POWERS) ? 26 : 0;
	/** The room Kingdoms' pages leave at the left of the screen for Powers' side list (0 with the bar), kept up to date from Powers. */
	public static int SIDE;
	static final String MENU = "com.jaymospowers.client.menu.MenuTabs";
	/** Powers' Map page: when it opens, the settlements are asked for. */
	static final String MAP_SCREEN = "com.jaymospowers.client.menu.MapScreen";
	/** Where Kingdoms' tabs sit among Powers' (Character 0, Powers 10, Map 50). */
	static final int ORDER_CLASS = 15, ORDER_SKILLS = 20, ORDER_FORMS = 22, ORDER_STORY = 25, ORDER_GROWTH = 27, ORDER_JOURNAL = 30, ORDER_FACTION = 40, ORDER_PARTY = 60,
		ORDER_MAGIC = 24, ORDER_VALOR = 26, ORDER_BOONS = 28, ORDER_TIER = 35, ORDER_FOLLOWERS = 62, ORDER_DAILY = 36, ORDER_TITLES = 29, ORDER_REWARDS = 71;
	/** The pins' colour for your party. */
	static final int PARTY_PIN = 0xFFC21A;

	private MenuCompat() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MenuNet.Info.TYPE, (payload, context) -> FactionScreen.info = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FactionScreen.info = null);
		RetakingScreen.register();
		ClientPlayNetworking.registerGlobalReceiver(TitleChoice.Info.TYPE, (payload, context) -> TitleChoice.info = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TitleChoice.info = null);
		PapersScreen.register();
		if (!FabricLoader.getInstance().isModLoaded(POWERS)) {
			return;
		}
		try {
			Class<?> menu = Class.forName(MENU);
			Method register = menu.getMethod("register", String.class, int.class, Supplier.class, Component.class, Runnable.class, Predicate.class,
				Function.class, BooleanSupplier.class);
			tab(register, "skills", ORDER_SKILLS, () -> new ItemStack(Items.EXPERIENCE_BOTTLE), "menu.jaymoskingdoms.tab.skills",
				() -> Minecraft.getInstance().gui.setScreen(new SkillScreen()), s -> s instanceof SkillScreen, s -> ((SkillScreen)s).corner());
			// (docs/Classes-Plan.md: a creative player switches class from a tab on the H menu; anyone with a class can change its
			// first pick there, for a fee: ClassScreen#respec)
			register.invoke(null, "class", ORDER_CLASS, (Supplier<ItemStack>)() -> new ItemStack(Items.IRON_SWORD),
				Component.translatable("menu.jaymoskingdoms.tab.class"), (Runnable)() -> Minecraft.getInstance().gui.setScreen(new ClassScreen(false, true)),
				(Predicate<Screen>)s -> s instanceof ClassScreen, (Function<Screen, int[]>)s -> ((ClassScreen)s).corner(),
				(BooleanSupplier)() -> Minecraft.getInstance().player != null
					&& (Minecraft.getInstance().player.isCreative() || com.jaymoskingdoms.Classes.classOf(Minecraft.getInstance().player) != null));
			// the belt menu: shown once you know a form or have a Spell Engine spell (its Spells tab)
			register.invoke(null, "forms", ORDER_FORMS, (Supplier<ItemStack>)() -> new ItemStack(Items.IRON_SWORD),
				Component.translatable("menu.jaymoskingdoms.tab.forms"),
				(Runnable)() -> Minecraft.getInstance().gui.setScreen(new com.jaymoskingdoms.client.forms.BeltScreen()),
				(Predicate<Screen>)s -> s instanceof com.jaymoskingdoms.client.forms.BeltScreen,
				(Function<Screen, int[]>)s -> ((com.jaymoskingdoms.client.forms.BeltScreen)s).corner(),
				(BooleanSupplier)() -> Minecraft.getInstance().player != null
					&& (!com.jaymoskingdoms.forms.Forms.book(Minecraft.getInstance().player).practice().isEmpty()
						|| com.jaymoskingdoms.client.forms.BeltScreen.hasSpells(Minecraft.getInstance().player)));
			tab(register, "story", ORDER_STORY, () -> new ItemStack(Items.WRITABLE_BOOK), "menu.jaymoskingdoms.tab.story",
				() -> Minecraft.getInstance().gui.setScreen(new RetakingScreen()), s -> s instanceof RetakingScreen, s -> ((RetakingScreen)s).corner());
			tab(register, "growth", ORDER_GROWTH, () -> new ItemStack(Items.ENCHANTED_BOOK), "menu.jaymoskingdoms.tab.growth",
				() -> Minecraft.getInstance().gui.setScreen(new GrowthScreen()), s -> s instanceof GrowthScreen, s -> ((GrowthScreen)s).corner());
			tab(register, "journal", ORDER_JOURNAL, () -> new ItemStack(Journal.JOURNAL), "menu.jaymoskingdoms.tab.journal", MenuCompat::openJournal,
				s -> s instanceof JournalScreen, s -> ((JournalScreen)s).corner());
			tab(register, "faction", ORDER_FACTION, () -> new ItemStack(Items.BELL), "menu.jaymoskingdoms.tab.faction",
				() -> Minecraft.getInstance().gui.setScreen(new FactionScreen()), s -> s instanceof FactionScreen, s -> ((FactionScreen)s).corner());
			tab(register, "party", ORDER_PARTY, () -> new ItemStack(Items.SHIELD), "menu.jaymoskingdoms.tab.party",
				() -> Minecraft.getInstance().gui.setScreen(new PartyScreen()), s -> s instanceof PartyScreen, s -> ((PartyScreen)s).corner());
			tab(register, "tier", ORDER_TIER, () -> new ItemStack(Items.COMPASS), "menu.jaymoskingdoms.tab.tier",
				() -> Minecraft.getInstance().gui.setScreen(new TierScreen(null)), s -> s instanceof TierScreen, s -> new int[] {0, 0});
			tab(register, "valor", ORDER_VALOR, () -> new ItemStack(Items.GOLDEN_SWORD), "menu.jaymoskingdoms.tab.valor",
				() -> Minecraft.getInstance().gui.setScreen(new ValorScreen(null)), s -> s instanceof ValorScreen, s -> new int[] {0, 0});
			register.invoke(null, "boons", ORDER_BOONS, (Supplier<ItemStack>)() -> new ItemStack(Items.NETHER_STAR),
				Component.translatable("menu.jaymoskingdoms.tab.boons"), (Runnable)() -> Minecraft.getInstance().gui.setScreen(new BoonScreen()),
				(Predicate<Screen>)s -> s instanceof BoonScreen, (Function<Screen, int[]>)s -> new int[] {0, 0},
				(BooleanSupplier)() -> Minecraft.getInstance().player != null && Patrons.of(Minecraft.getInstance().player) != null);
			tab(register, "magic", ORDER_MAGIC, () -> new ItemStack(Items.ENCHANTED_BOOK), "menu.jaymoskingdoms.tab.magic", MenuCompat::openMagic,
				s -> s instanceof MagicScreen || s instanceof MagicPageScreen, s -> new int[] {0, 0});
			tab(register, "followers", ORDER_FOLLOWERS, () -> new ItemStack(Items.LEAD), "menu.jaymoskingdoms.tab.followers",
				FollowersScreen::openAll, s -> s instanceof FollowersScreen, s -> new int[] {0, 0});
			// the daily task (World), the titles you wear (You) and the rewards held for you (Notices)
			tab(register, "daily", ORDER_DAILY, () -> new ItemStack(Items.CLOCK), "menu.jaymoskingdoms.tab.daily",
				() -> Minecraft.getInstance().gui.setScreen(new DailyTaskScreen()), s -> s instanceof DailyTaskScreen, s -> ((DailyTaskScreen)s).corner());
			tab(register, "titles", ORDER_TITLES, () -> new ItemStack(Items.NAME_TAG), "menu.jaymoskingdoms.tab.titles",
				() -> Minecraft.getInstance().gui.setScreen(new TitlesScreen()), s -> s instanceof TitlesScreen, s -> ((TitlesScreen)s).corner());
			tab(register, "rewards", ORDER_REWARDS, () -> new ItemStack(Items.CHEST), "menu.jaymoskingdoms.tab.rewards",
				() -> Minecraft.getInstance().gui.setScreen(new RewardsScreen()), s -> s instanceof RewardsScreen, s -> ((RewardsScreen)s).corner());
			PapersScreen.link(menu);
			arrange(menu);
			listen(menu);
			Supplier<List<Map.Entry<Component, BlockPos>>> pins = MenuCompat::pins;
			menu.getMethod("pins", Supplier.class).invoke(null, pins);
		} catch (ReflectiveOperationException | LinkageError e) {
			// an older or newer Powers without the menu: K and M still work on their own
			return;
		}
		syncSide();
		ScreenEvents.BEFORE_INIT.register((minecraft, screen, width, height) -> syncSide());
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen.getClass().getName().equals(MAP_SCREEN)) {
				FactionScreen.ask();
			}
		});
	}

	/** Sorts Kingdoms' tabs into the menu's groups and gives locked ones their hints, each call apart so an older Powers still takes the rest. */
	private static void arrange(Class<?> menu) {
		String[][] groups = {{"skills", "you"}, {"class", "you"}, {"growth", "you"}, {"forms", "powers"}, {"magic", "powers"}, {"valor", "powers"},
			{"boons", "powers"}, {"story", "world"}, {"journal", "world"}, {"tier", "world"}, {"faction", "people"}, {"party", "people"}, {"followers", "people"},
			{"daily", "world"}, {"titles", "you"}, {"rewards", "notices"}};
		for (String[] g : groups) {
			reflect(menu, "group", new Class<?>[] {String.class, String.class}, g[0], g[1]);
		}
		reflect(menu, "wide", new Class<?>[] {String.class}, "skills");
		reflect(menu, "locked", new Class<?>[] {String.class, Supplier.class}, "forms",
			(Supplier<Component>)() -> Component.translatable("menu.jaymoskingdoms.locked.forms"));
		reflect(menu, "locked", new Class<?>[] {String.class, Supplier.class}, "boons",
			(Supplier<Component>)() -> Component.translatable("menu.jaymoskingdoms.locked.boons"));
	}

	/**
	 * Gives the Character page its Kingdoms rows by panel. A Powers without panels takes them as one list, as the page
	 * always did.
	 */
	private static void listen(Class<?> menu) throws ReflectiveOperationException {
		try {
			Method panel = menu.getMethod("characterRows", String.class, Supplier.class);
			panel.invoke(null, "level", (Supplier<List<Component[]>>)MenuCompat::levelRows);
			panel.invoke(null, "header", (Supplier<List<Component[]>>)MenuCompat::headerRows);
			panel.invoke(null, "attributes", (Supplier<List<Component[]>>)MenuCompat::attributeRows);
			panel.invoke(null, "record", (Supplier<List<Component[]>>)MenuCompat::recordRows);
			// (mana, among the vitals; an older Powers without bars just leaves it out)
			reflect(menu, "bars", new Class<?>[] {Supplier.class}, (Supplier<List<Object[]>>)MenuCompat::manaBar);
		} catch (NoSuchMethodException e) {
			Supplier<List<Component[]>> rows = MenuCompat::characterRows;
			menu.getMethod("characterRows", Supplier.class).invoke(null, rows);
		}
	}

	private static void reflect(Class<?> menu, String name, Class<?>[] types, Object... args) {
		try {
			menu.getMethod(name, types).invoke(null, args);
		} catch (ReflectiveOperationException | LinkageError e) {
			// an older Powers without this: the tabs just come without it
		}
	}

	/** The room Powers' side list takes at the left (read from Powers each time a screen opens, as it changes with {@code /menulook}). */
	private static void syncSide() {
		try {
			SIDE = Class.forName(MENU).getField("SIDE").getInt(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			SIDE = 0;
		}
	}

	/** Where a page {@code pageW} wide sits across a screen {@code screenW} wide, clear of Powers' side list. */
	public static int centre(int screenW, int pageW) {
		return Math.max(SIDE, SIDE + (screenW - SIDE - pageW) / 2);
	}

	/** The magic menu's own key: the server sends the screen back. */
	private static void openMagic() {
		if (Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(MagicMenu.Pick.TYPE)) {
			ClientPlayNetworking.send(new MagicMenu.Pick("key", ""));
		}
	}

	private static void tab(Method register, String id, int order, Supplier<ItemStack> icon, String label, Runnable open, Predicate<Screen> owns,
		Function<Screen, int[]> anchor) throws ReflectiveOperationException {
		BooleanSupplier shown = () -> true;
		register.invoke(null, id, order, icon, Component.translatable(label), open, owns, anchor, shown);
	}

	/** M's rule: the journal opens only with one on you. */
	private static void openJournal() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (Journal.carries(minecraft.player)) {
			ClientPlayNetworking.send(new Journal.Ask());
		} else {
			minecraft.player.sendOverlayMessage(Component.translatable("journal.jaymoskingdoms.need").withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component[] row(String label, Component value) {
		return new Component[] {Component.translatable(label), value};
	}

	private static Component none() {
		return Component.translatable("menu.jaymoskingdoms.character.none");
	}

	/** The vitals' mana bar, for the attuned: {label, value, share, colour}. */
	private static List<Object[]> manaBar() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Object[]> bars = new ArrayList<>();
		if (player != null && com.jaymoskingdoms.Mana.hasMana(player)) {
			float now = com.jaymoskingdoms.Mana.current(player), most = com.jaymoskingdoms.Mana.max(player);
			bars.add(new Object[] {Component.translatable("menu.jaymoskingdoms.character.mana"), Component.literal(Math.round(now) + " / " + Math.round(most)),
				most <= 0.0F ? 0.0F : Math.min(1.0F, now / most), 0xFF000000 | ManaClient.COLOR});
		}
		return bars;
	}

	/** The header's one row: your character level. */
	private static List<Component[]> levelRows() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component[]> rows = new ArrayList<>();
		if (player != null) {
			rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.level_label"),
				Component.literal(Integer.toString(com.jaymoskingdoms.Stats.characterLevel(Skills.of(player))))});
		}
		return rows;
	}

	/** Who you are, a line each: heritage, class (and second class), and the title renown has earned. */
	private static List<Component[]> headerRows() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component[]> rows = new ArrayList<>();
		if (player == null) {
			return rows;
		}
		Heritage.Kind heritage = Heritage.of(player);
		if (heritage != null) {
			rows.add(row("menu.jaymoskingdoms.character.heritage", Component.translatable("heritage.jaymoskingdoms." + heritage.key())));
		}
		Classes.PlayerClass cls = Classes.classOf(player);
		if (cls != null) {
			Component name = Component.translatable("class.jaymoskingdoms." + cls.key());
			Classes.PlayerClass second = Classes.second(player);
			rows.add(row("menu.jaymoskingdoms.character.class",
				second == null ? name : Component.translatable("menu.jaymoskingdoms.character.pair", name, Component.translatable("class.jaymoskingdoms." + second.key()))));
		}
		Integer code = player.getAttached(Renown.TITLE);
		Skills.Pillar pillar = code == null ? null : Renown.titlePillar(code);
		if (pillar != null) {
			rows.add(row("menu.jaymoskingdoms.character.renown", Component.translatable("title.jaymoskingdoms." + pillar.key() + (Renown.titleMaster(code) ? ".master" : ""))));
		}
		return rows;
	}

	/** The attributes (Might, Grit...) with what you have in each. */
	private static List<Component[]> attributeRows() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component[]> rows = new ArrayList<>();
		if (player == null) {
			return rows;
		}
		for (com.jaymoskingdoms.Stats.Stat stat : com.jaymoskingdoms.Stats.Stat.values()) {
			if (com.jaymoskingdoms.Stats.shown(player, stat)) {
				rows.add(new Component[] {Component.translatable("stat.jaymoskingdoms." + stat.key()),
					Component.literal(Integer.toString(com.jaymoskingdoms.Stats.value(player, stat)))});
			}
		}
		return rows;
	}

	/** The Record panel's Kingdoms rows: career, skills, world tier, patron, realm and what you have seen. */
	private static List<Component[]> recordRows() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component[]> rows = new ArrayList<>();
		if (player == null) {
			return rows;
		}
		Component none = none();
		Component career = Careers.title(player);
		rows.add(row("menu.jaymoskingdoms.character.career", career == null ? none : career));
		Skills.Progress skills = Skills.of(player);
		for (Skills.Pillar p : Skills.Pillar.values()) {
			rows.add(row("skill.jaymoskingdoms." + p.key(), Component.translatable("menu.jaymoskingdoms.character.level", skills.level(p))));
		}
		if (WorldTiers.unlocked(player)) {
			// (the world tiers, once one past Hearth is open to you)
			rows.add(row("menu.jaymoskingdoms.character.tier", WorldTiers.of(player).title()));
		}
		Patron patron = Patrons.of(player);
		rows.add(row("menu.jaymoskingdoms.character.patron", patron == null ? none : Component.translatable("patron.jaymoskingdoms." + patron.key)));
		// what you have seen of the other side (Exposure), and a sleep the living nightmare haunts (LivingNightmare)
		rows.add(row("menu.jaymoskingdoms.character.exposure", com.jaymoskingdoms.Exposure.describe(com.jaymoskingdoms.Exposure.of(player))));
		// (the illager tongue, once you have any of it: IllagerTongue)
		int illager = player.getAttachedOrElse(com.jaymoskingdoms.IllagerTongue.POINTS, 0);
		if (illager > 0) {
			rows.add(row("menu.jaymoskingdoms.character.illager_tongue", com.jaymoskingdoms.IllagerTongue.describe(illager)));
		}
		if (com.jaymoskingdoms.LivingNightmare.nights(player) > 0) {
			rows.add(row("menu.jaymoskingdoms.character.sleep", Component.translatable("menu.jaymoskingdoms.character.sleep.haunted")));
		}
		MenuNet.Info info = FactionScreen.info;
		if (info != null) {
			rows.add(row("menu.jaymoskingdoms.character.realm", info.sworn()
				? Component.translatable("menu.jaymoskingdoms.character.realm_rank", Component.translatable(info.rank()), info.name()) : none));
		}
		return rows;
	}

	/** The Character page's Kingdoms section. */
	private static List<Component[]> characterRows() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component[]> rows = new ArrayList<>();
		if (player == null) {
			return rows;
		}
		Component none = Component.translatable("menu.jaymoskingdoms.character.none");
		rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.heading")});
		Heritage.Kind heritage = Heritage.of(player);
		rows.add(row("menu.jaymoskingdoms.character.heritage", heritage == null ? none : Component.translatable("heritage.jaymoskingdoms." + heritage.key())));
		Classes.PlayerClass cls = Classes.classOf(player);
		rows.add(row("menu.jaymoskingdoms.character.class", cls == null ? none : Component.translatable("class.jaymoskingdoms." + cls.key())));
		Classes.PlayerClass second = Classes.second(player);
		if (second != null) {
			rows.add(row("menu.jaymoskingdoms.character.second", Component.translatable("class.jaymoskingdoms." + second.key())));
		}
		Component career = Careers.title(player);
		rows.add(row("menu.jaymoskingdoms.character.career", career == null ? none : career));
		Integer code = player.getAttached(Renown.TITLE);
		Skills.Pillar pillar = code == null ? null : Renown.titlePillar(code);
		rows.add(row("menu.jaymoskingdoms.character.renown", pillar == null ? Component.translatable("menu.jaymoskingdoms.character.unknown")
			: Component.translatable("title.jaymoskingdoms." + pillar.key() + (Renown.titleMaster(code) ? ".master" : ""))));
		Skills.Progress skills = Skills.of(player);
		for (Skills.Pillar p : Skills.Pillar.values()) {
			rows.add(row("skill.jaymoskingdoms." + p.key(), Component.translatable("menu.jaymoskingdoms.character.level", skills.level(p))));
		}
		if (WorldTiers.unlocked(player)) {
			// (the world tiers, once one past Hearth is open to you)
			rows.add(row("menu.jaymoskingdoms.character.tier", WorldTiers.of(player).title()));
		}
		Patron patron = Patrons.of(player);
		rows.add(row("menu.jaymoskingdoms.character.patron", patron == null ? none : Component.translatable("patron.jaymoskingdoms." + patron.key)));
		// what you have seen of the other side (Exposure), and a sleep the living nightmare haunts (LivingNightmare)
		rows.add(row("menu.jaymoskingdoms.character.exposure", com.jaymoskingdoms.Exposure.describe(com.jaymoskingdoms.Exposure.of(player))));
		// (the illager tongue, once you have any of it: IllagerTongue)
		int illager = player.getAttachedOrElse(com.jaymoskingdoms.IllagerTongue.POINTS, 0);
		if (illager > 0) {
			rows.add(row("menu.jaymoskingdoms.character.illager_tongue", com.jaymoskingdoms.IllagerTongue.describe(illager)));
		}
		if (com.jaymoskingdoms.LivingNightmare.nights(player) > 0) {
			rows.add(row("menu.jaymoskingdoms.character.sleep", Component.translatable("menu.jaymoskingdoms.character.sleep.haunted")));
		}
		MenuNet.Info info = FactionScreen.info;
		if (info != null) {
			rows.add(row("menu.jaymoskingdoms.character.realm", info.sworn()
				? Component.translatable("menu.jaymoskingdoms.character.realm_rank", Component.translatable(info.rank()), info.name()) : none));
		}
		return rows;
	}

	/** The Map page's pins: the settlements you know, and your party in the world you stand in. */
	private static List<Map.Entry<Component, BlockPos>> pins() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Map.Entry<Component, BlockPos>> pins = new ArrayList<>();
		if (player == null) {
			return pins;
		}
		MenuNet.Info info = FactionScreen.info;
		if (info != null && info.here()) {
			for (MenuNet.Place p : info.places()) {
				pins.add(new AbstractMap.SimpleImmutableEntry<>(Component.literal(p.name()).withColor(p.color()), p.seat()));
			}
		}
		String here = player.level().dimension().identifier().toString();
		for (Parties.Entry e : PartyClient.party()) {
			if (!e.id().equals(player.getUUID()) && e.dimension().equals(here)) {
				pins.add(new AbstractMap.SimpleImmutableEntry<>(e.name().copy().withColor(PARTY_PIN), new BlockPos(e.x(), e.y(), e.z())));
			}
		}
		return pins;
	}
}
