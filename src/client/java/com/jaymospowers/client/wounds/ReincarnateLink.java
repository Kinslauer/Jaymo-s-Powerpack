package com.jaymospowers.client.wounds;

import com.jaymospowers.client.menu.MenuTabs;
import com.jaymospowers.core.wounds.Reincarnate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Reincarnate link on the character menu's Record panel ({@link Reincarnate}): asks once more, since it ends the
 * character for good, then tells the server. Shown only with Jaymo's Kingdoms (it tells the new life's story) and while
 * the player could choose it.
 */
public final class ReincarnateLink {
	/** How long the Reincarnate button of the asking screen stays greyed (ticks), against a click meant for something else. */
	static final int SURE_DELAY = 40;

	private ReincarnateLink() {
	}

	public static void init() {
		boolean story = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
		MenuTabs.characterLink(() -> {
			Player player = Minecraft.getInstance().player;
			return !story || player == null || !Reincarnate.can(player) ? null : Component.translatable("menu.jaymospowers.reincarnate");
		}, ReincarnateLink::ask);
	}

	private static void ask() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen back = minecraft.gui.screen();
		ConfirmScreen sure = new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(Reincarnate.Ask.INSTANCE);
				minecraft.gui.setScreen(null);
			} else {
				minecraft.gui.setScreen(back);
			}
		}, Component.translatable("menu.jaymospowers.reincarnate.title"), Component.translatable("menu.jaymospowers.reincarnate.message"),
			Component.translatable("menu.jaymospowers.reincarnate.yes"), Component.translatable("menu.jaymospowers.reincarnate.no"));
		sure.setDelay(SURE_DELAY);
		minecraft.gui.setScreen(sure);
	}
}
