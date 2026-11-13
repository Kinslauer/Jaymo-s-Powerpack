package com.jaymospowers.client.packlook;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UiStyle;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.Command;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Andy's UI checklist, the other mods' screens he picked: Cooking for Blockheads and Pantry, Shippy Ships' holds, the
 * rune altar, the spell binding table, the elephant's packs, the creeper minion tracker, Better Advancements' window and
 * Simple Voice Chat's panels are drawn in vanilla's light inventory grey, next to our dark screens.
 *
 * <p>Nothing of theirs is copied or shipped. When one of the textures in {@link #TEXTURES} is read from its own mod, the
 * pixels in vanilla's six inventory greys are swapped for {@link UiStyle}'s window tones ({@link #repaint}); every other
 * pixel (arrows, icons, their own art) is left as it is. Those screens write their labels in vanilla's dark grey, which
 * would vanish on the dark panel, so while one of {@link #SCREENS} is open that grey is drawn light ({@link #text}).
 * Screens of the same mods that use vanilla's own chest texture are not listed, and stay as vanilla as the inventory.
 *
 * <p>Andy: "can you do the same but in the vanilla grey, and allow us to swap between". {@code /screenlook dark|grey}
 * picks, per player, kept in {@link #FILE}: grey leaves those mods' screens exactly as they ship them. Swapping reloads
 * the textures, as F3+T does.
 */
public final class DarkPanels {
	/** Vanilla's inventory greys (panel, bevel light, bevel shade, slot, slot edge, a mid step) and what each becomes. */
	private static final int[][] SWAPS = {
		{0xC6C6C6, UiStyle.WINDOW_BODY}, {0xC7C7C7, UiStyle.WINDOW_BODY}, {0xFFFFFF, UiStyle.WINDOW_LIGHT}, {0x555555, UiStyle.WINDOW_SHADE},
		{0x8B8B8B, UiStyle.WINDOW_CANVAS}, {0x373737, UiStyle.WINDOW_EDGE}, {0x6F6F6F, 0xFF19191D},
	};
	/** Vanilla's label grey, and what it is drawn as on a repainted screen. */
	private static final int LABEL = 0xFF404040, LABEL_LIGHT = 0xFFD8D8E0;

	private static final Set<Identifier> TEXTURES = Set.of(
		id("cookingforblockheads", "gui"), id("cookingforblockheads", "oven"), id("cookingforblockheads", "spice_rack"),
		id("cookingforblockheads", "container/chicken_sink"), id("cookingforblockheads", "container/cookie_jar"),
		id("pantryforblockheads", "container/artisan_press"),
		id("shippy-ships", "6x21"), id("shippy-ships", "9x11"),
		id("runes", "crafting_altar"),
		id("mutantmonsters", "creeper_minion_tracker"),
		id("naturalist", "container/elephant"),
		id("spell_engine", "spell_binding"),
		id("betteradvancements", "window"), id("betteradvancements", "tabs"),
		id("voicechat", "gui_audio_devices"), id("voicechat", "gui_create_group"), id("voicechat", "gui_enter_password"),
		id("voicechat", "gui_group"), id("voicechat", "gui_join_group"), id("voicechat", "gui_voicechat"),
		id("voicechat", "gui_voicechat_settings"), id("voicechat", "gui_volumes"));

	/** The screens that draw those textures (a screen that extends one counts). */
	private static final Set<String> SCREENS = Set.of(
		"net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen",
		"net.blay09.mods.cookingforblockheads.client.gui.screen.OvenScreen",
		"net.blay09.mods.cookingforblockheads.client.gui.screen.SpiceRackScreen",
		"net.blay09.mods.cookingforblockheads.client.gui.screen.ChickenSinkScreen",
		"net.blay09.mods.cookingforblockheads.client.gui.screen.CookieJarScreen",
		"net.blay09.mods.pantryforblockheads.client.screen.ArtisanPressScreen",
		"com.caesiusleo.shippyships.client.Ship4XLContainerScreen",
		"com.caesiusleo.shippyships.client.ShipContainerScreen",
		"com.caesiusleo.shippyships.client.ShipDoubleLargeContainerScreen",
		"com.caesiusleo.shippyships.client.ShipLargeContainerScreen",
		"net.runes.client.RuneCraftingScreen",
		"fuzs.mutantmonsters.common.client.gui.screens.CreeperMinionTrackerScreen",
		"com.crispytwig.naturalist.client.gui.screens.inventory.ElephantInventoryScreen",
		"net.spell_engine.spellbinding.SpellBindingScreen",
		"betteradvancements.common.gui.BetterAdvancementsScreen",
		"de.maxhenkel.voicechat.gui.CreateGroupScreen",
		"de.maxhenkel.voicechat.gui.EnterPasswordScreen",
		"de.maxhenkel.voicechat.gui.VoiceChatScreen",
		"de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen",
		"de.maxhenkel.voicechat.gui.audiodevice.SelectDeviceScreen",
		"de.maxhenkel.voicechat.gui.group.GroupScreen",
		"de.maxhenkel.voicechat.gui.group.JoinGroupScreen",
		"de.maxhenkel.voicechat.gui.volume.AdjustVolumesScreen");

	private static final Map<Class<?>, Boolean> KNOWN = new ConcurrentHashMap<>();
	private static final String FILE = "jaymospowers-screenlook.properties";
	/** Whether the repaint is on (dark), read before the first texture loads. */
	private static volatile boolean dark = !"grey".equals(load().getProperty("look", "dark"));

	private DarkPanels() {
	}

	private static Identifier id(String namespace, String name) {
		return Identifier.fromNamespaceAndPath(namespace, "textures/gui/" + name + ".png");
	}

	/** True while the other mods' screens are painted dark (false: left vanilla grey). */
	public static boolean dark() {
		return dark;
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("screenlook")
				.executes(c -> {
					c.getSource().sendFeedback(Component.translatable(dark ? "message.jaymospowers.screenlook.is_dark" : "message.jaymospowers.screenlook.is_grey"));
					return Command.SINGLE_SUCCESS;
				})
				.then(ClientCommands.literal("dark").executes(c -> pick(c.getSource(), true)))
				.then(ClientCommands.literal("grey").executes(c -> pick(c.getSource(), false)))));
	}

	private static int pick(FabricClientCommandSource source, boolean wanted) {
		if (wanted != dark) {
			dark = wanted;
			Properties state = new Properties();
			state.setProperty("look", wanted ? "dark" : "grey");
			try (OutputStream out = Files.newOutputStream(path())) {
				state.store(out, "Jaymo's Powers: other mods' screens dark or vanilla grey (/screenlook dark|grey).");
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't write {}", path(), e);
			}
			source.getClient().reloadResourcePacks();
		}
		source.sendFeedback(Component.translatable(wanted ? "message.jaymospowers.screenlook.dark" : "message.jaymospowers.screenlook.grey"));
		return Command.SINGLE_SUCCESS;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static Properties load() {
		Properties state = new Properties();
		if (Files.exists(path())) {
			try (InputStream in = Files.newInputStream(path())) {
				state.load(in);
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't read {}", path(), e);
			}
		}
		return state;
	}

	/** Swaps vanilla's inventory greys in {@code image} for the dark window's, if {@code texture} is one of ours to repaint. */
	public static void repaint(Identifier texture, NativeImage image) {
		if (!dark || !TEXTURES.contains(texture)) {
			return;
		}
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getPixel(x, y);
				if (pixel >>> 24 != 0xFF) {
					continue;
				}
				for (int[] swap : SWAPS) {
					if ((pixel & 0xFFFFFF) == swap[0]) {
						image.setPixel(x, y, swap[1]);
						break;
					}
				}
			}
		}
	}

	/** The colour to draw text in: vanilla's label grey turned light while a repainted screen is open, else unchanged. */
	public static int text(int color) {
		if (color != LABEL || !dark) {
			return color;
		}
		Screen screen = Minecraft.getInstance().gui.screen();
		return screen != null && KNOWN.computeIfAbsent(screen.getClass(), DarkPanels::repainted) ? LABEL_LIGHT : color;
	}

	private static boolean repainted(Class<?> type) {
		for (Class<?> c = type; c != null && c != Screen.class; c = c.getSuperclass()) {
			if (SCREENS.contains(c.getName())) {
				return true;
			}
		}
		return false;
	}
}
