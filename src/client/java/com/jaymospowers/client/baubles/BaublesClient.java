package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.baubles.BaublesNet;
import com.jaymospowers.baubles.Fairies;
import com.jaymospowers.client.JaymosPowersClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** The bauble screen, the inventory tab that opens it, and the Baubles key (unbound by default). */
public final class BaublesClient {
	private static KeyMapping key;
	/** (Vanilla+) The back slot's swap key, unbound by default. */
	private static KeyMapping backKey;

	private BaublesClient() {
	}

	public static void init() {
		MenuScreens.register(Baubles.MENU, BaublesScreen::new);
		// (0.20.12) the pink fairy: her flat sprite, wings flapping, always fully lit
		EntityRenderers.register(Fairies.PINK_FAIRY, ctx -> new ThrownItemRenderer<>(ctx, 0.75F, true));
		WornFairies.init();
		BondsClient.init();
		// (0.21.37) every other worn bauble drawn on the body
		WornLooks.init();
		// (Village mods patch 5) the trade hats, drawn on the head with their own models
		TradeHatLayer.init();
		// (Artifacts) the Artifacts baubles' moves
		ArtifactBaublesClient.init();
		BaublesTab.init();
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.baubles", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		backKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.back_slot", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (backKey.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(com.jaymospowers.baubles.BackSlot.Swap.TYPE)) {
					ClientPlayNetworking.send(new com.jaymospowers.baubles.BackSlot.Swap());
				}
			}
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(BaublesNet.Open.TYPE)) {
					ClientPlayNetworking.send(new BaublesNet.Open());
				}
			}
		});
	}
}
