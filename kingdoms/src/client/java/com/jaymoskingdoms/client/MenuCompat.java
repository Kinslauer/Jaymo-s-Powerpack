package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.Journal;
import com.jaymoskingdoms.MenuNet;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
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
	static final String MENU = "com.jaymospowers.client.menu.MenuTabs";
	/** Powers' Map page: when it opens, the settlements are asked for. */
	static final String MAP_SCREEN = "com.jaymospowers.client.menu.MapScreen";
	/** Where Kingdoms' tabs sit among Powers' (Character 0, Powers 10, Map 50). */
	static final int ORDER_CLASS = 15, ORDER_SKILLS = 20, ORDER_STORY = 25, ORDER_JOURNAL = 30, ORDER_FACTION = 40, ORDER_PARTY = 60;
	/** The pins' colour for your party. */
	static final int PARTY_PIN = 0xFFC21A;

	private MenuCompat() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MenuNet.Info.TYPE, (payload, context) -> FactionScreen.info = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FactionScreen.info = null);
		RetakingScreen.register();
		if (!FabricLoader.getInstance().isModLoaded(POWERS)) {
			return;
		}
		try {
			Class<?> menu = Class.forName(MENU);
			Method register = menu.getMethod("register", String.class, int.class, Supplier.class, Component.class, Runnable.class, Predicate.class,
				Function.class, BooleanSupplier.class);
			tab(register, "skills", ORDER_SKILLS, () -> new ItemStack(Items.EXPERIENCE_BOTTLE), "menu.jaymoskingdoms.tab.skills",
				() -> Minecraft.getInstance().gui.setScreen(new SkillScreen()), s -> s instanceof SkillScreen, s -> ((SkillScreen)s).corner());
			// (docs/Classes-Plan.md: a creative player switches class from a tab on the H menu)
			register.invoke(null, "class", ORDER_CLASS, (Supplier<ItemStack>)() -> new ItemStack(Items.IRON_SWORD),
				Component.translatable("menu.jaymoskingdoms.tab.class"), (Runnable)() -> Minecraft.getInstance().gui.setScreen(new ClassScreen(false, true)),
				(Predicate<Screen>)s -> s instanceof ClassScreen, (Function<Screen, int[]>)s -> ((ClassScreen)s).corner(),
				(BooleanSupplier)() -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative());
			tab(register, "story", ORDER_STORY, () -> new ItemStack(Items.WRITABLE_BOOK), "menu.jaymoskingdoms.tab.story",
				() -> Minecraft.getInstance().gui.setScreen(new RetakingScreen()), s -> s instanceof RetakingScreen, s -> ((RetakingScreen)s).corner());
			tab(register, "journal", ORDER_JOURNAL, () -> new ItemStack(Journal.JOURNAL), "menu.jaymoskingdoms.tab.journal", MenuCompat::openJournal,
				s -> s instanceof JournalScreen, s -> ((JournalScreen)s).corner());
			tab(register, "faction", ORDER_FACTION, () -> new ItemStack(Items.BELL), "menu.jaymoskingdoms.tab.faction",
				() -> Minecraft.getInstance().gui.setScreen(new FactionScreen()), s -> s instanceof FactionScreen, s -> ((FactionScreen)s).corner());
			tab(register, "party", ORDER_PARTY, () -> new ItemStack(Items.SHIELD), "menu.jaymoskingdoms.tab.party",
				() -> Minecraft.getInstance().gui.setScreen(new PartyScreen()), s -> s instanceof PartyScreen, s -> ((PartyScreen)s).corner());
			Supplier<List<Component[]>> rows = MenuCompat::characterRows;
			menu.getMethod("characterRows", Supplier.class).invoke(null, rows);
			Supplier<List<Map.Entry<Component, BlockPos>>> pins = MenuCompat::pins;
			menu.getMethod("pins", Supplier.class).invoke(null, pins);
		} catch (ReflectiveOperationException | LinkageError e) {
			// an older or newer Powers without the menu: K and M still work on their own
			return;
		}
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen.getClass().getName().equals(MAP_SCREEN)) {
				FactionScreen.ask();
			}
		});
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
		rows.add(row("menu.jaymoskingdoms.character.tier", WorldTiers.of(player).title()));
		Patron patron = Patrons.of(player);
		rows.add(row("menu.jaymoskingdoms.character.patron", patron == null ? none : Component.translatable("patron.jaymoskingdoms." + patron.key)));
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
