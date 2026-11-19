package com.jaymospowers.client.hud;

import com.jaymospowers.client.menu.MenuTabs;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The UI tutorial as tips you meet (Andy, 2026-11-19, picked over a walkthrough up front): the first time a part of the
 * screen shows, one {@link TipCard} says what lives there. Parts: each {@link HudZones} zone (around the crosshair is one
 * part), the character menu the first time it is open, the traveller's journal the first time you carry one (Jaymo's
 * Kingdoms, by item id), and after the first of these, how to move HUD pieces ({@code /hudmove}, {@link HudArrange}).
 * Powers have their own cards ({@link PowerTips}). Each card is shown once per player, flagged
 * {@code ui_tip.<player uuid>.<part>} in {@code config/jaymospowers-client.properties}; one waits until the card before
 * it is gone, and none comes in the first {@link #SETTLE_TICKS} of a session or while a screen is open (the menu's own
 * card excepted).
 */
public final class UiTips {
	/** How often (ticks) the screen is looked at. */
	static final int EVERY = 10;
	/** Ticks into a session before any card (the landing's screens and the aim page come first). */
	static final int SETTLE_TICKS = 200;
	/** A zone counts as showing if a piece drew in it this lately (ms). */
	static final long SEEN_MS = 1000L;
	/** The parts, in the order they are offered when several show at once: {part, zones...}. */
	private static final List<String[]> ZONES = List.of(
		new String[] {"body", HudZones.DOCK_LEFT},
		new String[] {"right", HudZones.DOCK_RIGHT},
		new String[] {"crosshair", HudZones.ABOVE, HudZones.BELOW, HudZones.INSPECT},
		new String[] {"focus", HudZones.FOCUS},
		new String[] {"rail", HudZones.RAIL});
	private static final Identifier JOURNAL = Identifier.fromNamespaceAndPath("jaymoskingdoms", "journal");
	private static final String MENU_KEY = "key.jaymospowers.menu", JOURNAL_KEY = "key.jaymoskingdoms.journal";

	private UiTips() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(UiTips::tick);
	}

	private static String flag(LocalPlayer player, String part) {
		return "ui_tip." + player.getUUID() + "." + part;
	}

	private static boolean shown(LocalPlayer player, String part) {
		return "true".equals(ZFighterClientConfig.get(flag(player, part), "false"));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.tickCount < SETTLE_TICKS || player.tickCount % EVERY != 0 || TipCard.busy()) {
			return;
		}
		if (minecraft.gui.screen() != null) {
			if (MenuTabs.of(minecraft.gui.screen()) != null && !shown(player, "menu")) {
				offer(player, "menu", Component.keybind(MENU_KEY));
			}
			return;
		}
		if (HudZones.hidden()) {
			return;
		}
		Set<String> zones = new HashSet<>();
		for (String[] piece : HudZones.seen(SEEN_MS)) {
			zones.add(piece[0]);
		}
		boolean any = false;
		for (String[] part : ZONES) {
			boolean done = shown(player, part[0]);
			any |= done;
			if (done) {
				continue;
			}
			for (int i = 1; i < part.length; i++) {
				if (zones.contains(part[i])) {
					offer(player, part[0], Component.keybind(MENU_KEY));
					return;
				}
			}
		}
		if (any && !shown(player, "move")) {
			offer(player, "move");
			return;
		}
		if (!shown(player, "journal")) {
			Item journal = BuiltInRegistries.ITEM.getValue(JOURNAL);
			if (journal != null && journal != net.minecraft.world.item.Items.AIR && player.getInventory().contains(stack -> stack.is(journal))) {
				offer(player, "journal", Component.keybind(JOURNAL_KEY));
			}
		}
	}

	private static void offer(LocalPlayer player, String part, Object... args) {
		ZFighterClientConfig.set(flag(player, part), "true");
		TipCard.show(Component.translatable("ui_tip.jaymospowers." + part + ".title"), Component.translatable("ui_tip.jaymospowers." + part, args));
	}
}
