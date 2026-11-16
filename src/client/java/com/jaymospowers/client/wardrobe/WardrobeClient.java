package com.jaymospowers.client.wardrobe;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeScreen;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.costume.WardrobeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The Wardrobe's screen ({@link WardrobeBlock}): one screen with a tab for each part of how you dress and carry yourself.
 * <ul>
 * <li><b>Clothes</b>: MCA's editor held to its clothing page ({@link WardrobeClothes}), when MCA's setting for
 * {@code /mca editor} lets this player use it (the server says).</li>
 * <li><b>Costume</b>: the Costume Table's designer, once a table has gone into this wardrobe.</li>
 * <li><b>Weapons</b>: YDM's Weapon Master settings, where each carried item hangs on the body (found by name: it is optional).</li>
 * </ul>
 * Each tab is that mod's own screen, whole, with the same row of tabs over it, so nothing of theirs is squeezed or cut:
 * switching tabs keeps what was set (the clothes are sent, Weapon Master writes its config). The wardrobe stays open
 * while any of its tabs (or a page they open, like the suit editor) is up, and shuts with them.
 */
public final class WardrobeClient {
	/** The tab row's buttons: width, height, and the gap between them. */
	static final int TAB_W = 62, TAB_H = 14, TAB_GAP = 4;
	/** YDM's Weapon Master's panel: its height (centred on the screen), for the tab row above it. */
	private static final int WEAPON_PANEL_H = 184;
	private static final String MCA = "mca", WEAPON_MASTER = "weaponmaster_ydm";
	private static final String WEAPON_SCREEN = "yourdailymodder.weaponmaster.gui.WeaponMasterScreen";
	private static final String WEAPON_CONFIG = "yourdailymodder.weaponmaster.setup.config.ModConfigs";

	public enum Tab {
		CLOTHES, COSTUME, WEAPONS;

		Component label() {
			return Component.translatable("screen.jaymospowers.wardrobe." + name().toLowerCase(java.util.Locale.ROOT));
		}

		boolean available() {
			return switch (this) {
				case CLOTHES -> clothes && FabricLoader.getInstance().isModLoaded(MCA);
				case COSTUME -> costumes;
				case WEAPONS -> FabricLoader.getInstance().isModLoaded(WEAPON_MASTER);
			};
		}
	}

	/** The open wardrobe's lower half, or null while none is open. */
	private static @Nullable BlockPos open;
	/** The tab showing, and the last one shown (the next wardrobe opens on it). */
	private static Tab showing = Tab.CLOTHES;
	/** What the open wardrobe offers: the Costume tab (a table went in), the Clothes tab (MCA's setting allows it). */
	private static boolean costumes, clothes;

	private WardrobeClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WardrobeBlock.Open.TYPE, (payload, context) -> {
			open = payload.pos();
			costumes = payload.costumes();
			clothes = payload.clothes();
			List<Tab> tabs = available();
			if (tabs.isEmpty()) {
				open = null;
				if (context.client().player != null) {
					context.client().player.sendOverlayMessage(Component.translatable("message.jaymospowers.wardrobe.empty"));
				}
				return;
			}
			show(showing.available() ? showing : tabs.get(0));
		});
		// the wardrobe shuts when its screens do
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (open != null && minecraft.gui.screen() == null) {
				if (minecraft.level != null) {
					minecraft.level.playLocalSound(open, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.9F, false);
				}
				open = null;
			}
		});
		// Weapon Master's screen is another mod's: its tab row goes on after it is laid out (and again on every resize)
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (open != null && screen.getClass().getName().equals(WEAPON_SCREEN)) {
				List<AbstractWidget> widgets = Screens.getWidgets(screen);
				tabs(width / 2, Math.max(2, height / 2 - WEAPON_PANEL_H / 2 - TAB_H - 4), Tab.WEAPONS, widgets::add);
			}
		});
	}

	/** The open wardrobe's lower half, or null: the Costume tab sews against it. */
	public static @Nullable BlockPos open() {
		return open;
	}

	/** Whether a wardrobe is open on {@code tab}. */
	public static boolean showing(Tab tab) {
		return open != null && showing == tab;
	}

	/**
	 * The wardrobe's tab row, centred on {@code centreX} at {@code y}: one button per tab that can show, the one showing
	 * greyed out, as MCA's own page row does.
	 */
	public static void tabs(int centreX, int y, Tab here, Consumer<AbstractWidget> add) {
		List<Tab> tabs = available();
		int x = centreX - rowWidth() / 2;
		for (Tab tab : tabs) {
			FlatButton button = FlatButton.builder(tab.label(), b -> show(tab)).bounds(x, y, TAB_W, TAB_H).build();
			button.active = tab != here;
			add.accept(button);
			x += TAB_W + TAB_GAP;
		}
	}

	/** How wide the tab row is. */
	public static int rowWidth() {
		int n = available().size();
		return n * TAB_W + (n - 1) * TAB_GAP;
	}

	private static List<Tab> available() {
		List<Tab> tabs = new ArrayList<>();
		for (Tab tab : Tab.values()) {
			if (tab.available()) {
				tabs.add(tab);
			}
		}
		return tabs;
	}

	/** Switches the open wardrobe to {@code tab}: what the tab showing now has set is kept first. */
	public static void show(Tab tab) {
		BlockPos pos = open;
		if (pos == null || !tab.available()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		keep(minecraft.gui.screen());
		Screen next = switch (tab) {
			case CLOTHES -> WardrobeClothes.open(minecraft.player.getUUID());
			case COSTUME -> new CostumeScreen(pos);
			case WEAPONS -> weaponMaster();
		};
		if (next == null) {
			return;
		}
		showing = tab;
		minecraft.gui.setScreen(next);
	}

	/** Before leaving a tab: the clothes are sent (as MCA's Done button sends them), Weapon Master writes its config. */
	private static void keep(@Nullable Screen screen) {
		if (screen == null) {
			return;
		}
		if (showing == Tab.CLOTHES && FabricLoader.getInstance().isModLoaded(MCA)) {
			WardrobeClothes.keep(screen);
		} else if (screen.getClass().getName().equals(WEAPON_SCREEN)) {
			try {
				Class.forName(WEAPON_CONFIG).getMethod("changeConfig").invoke(null);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Couldn't save YDM's Weapon Master settings from the wardrobe", e);
			}
		}
	}

	private static @Nullable Screen weaponMaster() {
		try {
			return (Screen)Class.forName(WEAPON_SCREEN).getConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Couldn't open YDM's Weapon Master settings from the wardrobe", e);
			return null;
		}
	}
}
