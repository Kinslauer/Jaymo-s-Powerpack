package com.jaymospowers.client.freerun;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.compat.CoreMotionPal;
import com.jaymospowers.freerun.CoreMotion;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class CoreMotionClient {
	private static final boolean PAL = FabricLoader.getInstance().isModLoaded("player_animation_library");

	private CoreMotionClient() {
	}

	public static void init() {
		if (PAL) {
			CoreMotionPal.init();
		}
		KeyMapping airStep = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.jaymospowers.air_step", InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (airStep.consumeClick()) {
				if (minecraft.gui.screen() == null && minecraft.player != null && CoreMotion.canJump(minecraft.player)
					&& ClientPlayNetworking.canSend(CoreMotion.Jump.TYPE)) {
					ClientPlayNetworking.send(new CoreMotion.Jump());
				}
			}
		});
	}

	public static boolean playing(Player player, String group) {
		Minecraft minecraft = Minecraft.getInstance();
		return PAL && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
			&& CoreMotionPal.playing(player, group);
	}

	public static boolean active(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		return PAL && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
			&& CoreMotionPal.active(player);
	}
}
