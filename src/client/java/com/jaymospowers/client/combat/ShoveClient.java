package com.jaymospowers.client.combat;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.combat.Shove;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The shove on this side ({@link Shove}): the Shove key (backslash, rebindable). A press with a weapon out or a free off
 * hand tells the server and lunges half a step; the server judges the rest (feathers, cooldown, who is hit).
 */
public final class ShoveClient {
	private static KeyMapping shoveKey;
	/** Ticks until the next shove (the server's {@link Shove#COOLDOWN}, kept here so the lunge isn't free). */
	private static int cooldown;

	private ShoveClient() {
	}

	public static void init() {
		shoveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.shove", InputConstants.KEY_BACKSLASH, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(ShoveClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (cooldown > 0) {
			cooldown--;
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			return;
		}
		while (shoveKey.consumeClick()) {
			if (cooldown > 0 || !Shove.ready(player)) {
				continue;
			}
			cooldown = Shove.COOLDOWN;
			ClientPlayNetworking.send(Shove.Press.INSTANCE);
			if (player.onGround() && !player.isPassenger()) {
				Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
				player.setDeltaMovement(look.x * Shove.LUNGE, player.getDeltaMovement().y, look.z * Shove.LUNGE);
			}
		}
	}
}
