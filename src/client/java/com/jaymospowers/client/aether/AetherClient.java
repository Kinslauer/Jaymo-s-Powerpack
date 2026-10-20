package com.jaymospowers.client.aether;

import com.jaymospowers.aether.FloatingBlockEntity;
import com.jaymospowers.aether.QuicksoilSpeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The Aether's client side: the rising gravitite block's renderer, and quicksoil's speed cap on the player. (Leaves,
 * aerclouds, glass and plants need no render-layer calls: 26.3 picks the layer from each texture's transparency, and
 * the models mark the aerclouds and quicksoil glass translucent.)
 */
public final class AetherClient {
	private AetherClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(FloatingBlockEntity.TYPE, FloatingBlockRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(AetherClient::tick);
		com.jaymospowers.client.aether.mob.AetherMobsClient.init();
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player != null && !player.isSpectator()) {
			QuicksoilSpeed.cap(player.getVehicle() != null ? player.getVehicle() : player);
		}
	}
}
