package com.jaymospowers.client.armory;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.armory.ArmoryEntities;
import com.jaymospowers.armory.BombGlider;
import com.jaymospowers.armory.BombGliderNet;
import com.jaymospowers.client.JaymosPowersClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * The bomb glider's client half: its renderer and hold screen, and the rider's drop keys (left click, through
 * BombGliderAttackMixin, or the ability 1 key, R). The server checks the rider, the rack and the cooldown.
 */
public final class BombGliderClient {
	private static boolean dropWasDown;

	private BombGliderClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(ArmoryEntities.BOMB_GLIDER, BombGliderRenderer::new);
		MenuScreens.register(ArmoryEntities.BOMB_GLIDER_MENU, BombGliderScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(BombGliderClient::tick);
	}

	/** The glider {@code player} rides, or null. */
	public static @Nullable BombGlider riding(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof BombGlider glider ? glider : null;
	}

	/** The local rider's drop: true if they're on a glider (the click is spent either way). */
	public static boolean drop(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		BombGlider glider = riding(player);
		if (glider == null) {
			return false;
		}
		ClientPlayNetworking.send(new BombGliderNet.Drop(glider.getId()));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		return true;
	}

	private static void tick(Minecraft minecraft) {
		boolean down = minecraft.gui.screen() == null && riding(minecraft.player) != null
			&& JaymosPowersClient.keyMapping(PowerKey.ABILITY_1).isDown();
		if (down && !dropWasDown) {
			drop(minecraft);
		}
		dropWasDown = down;
	}
}
