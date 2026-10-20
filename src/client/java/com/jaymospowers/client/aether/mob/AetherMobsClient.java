package com.jaymospowers.client.aether.mob;

import com.jaymospowers.aether.mob.AerbunnyRide;
import com.jaymospowers.aether.mob.AetherMobs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * The Aether's passive animals, client side: their renderers, the jump key the mounts read (a ride is simulated on the
 * rider's client), and the aerbunny's hold on its carrier's fall.
 */
public final class AetherMobsClient {
	private AetherMobsClient() {
	}

	public static void init() {
		EntityRenderers.register(AetherMobs.PHYG, ctx -> new AetherAnimalRenderer<>(ctx, WingedAnimalModel.phyg(), 0.5F, AetherAnimalRenderer.phyg()));
		EntityRenderers.register(AetherMobs.FLYING_COW, ctx -> new AetherAnimalRenderer<>(ctx, WingedAnimalModel.cow(), 0.6F, AetherAnimalRenderer.cow()));
		EntityRenderers.register(AetherMobs.SHEEPUFF, ctx -> new AetherAnimalRenderer<>(ctx, SheepuffModel.create(), 0.6F, AetherAnimalRenderer.sheepuff()));
		EntityRenderers.register(AetherMobs.AERBUNNY, ctx -> new AetherAnimalRenderer<>(ctx, AerbunnyModel.create(), 0.25F, AetherAnimalRenderer.aerbunny()));
		EntityRenderers.register(AetherMobs.MOA, ctx -> new AetherAnimalRenderer<>(ctx, MoaModel.create(), 0.5F, AetherAnimalRenderer.moa()));
		AetherMobs.jumpHeld = AetherMobsClient::jumpHeld;
		ClientTickEvents.END_CLIENT_TICK.register(AetherMobsClient::tick);
	}

	private static boolean jumpHeld() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player != null && !player.isSpectator()) {
			if (AerbunnyRide.tick(player, jumpHeld())) {
				ClientPlayNetworking.send(new AerbunnyRide.Hop());
			}
		}
	}
}
