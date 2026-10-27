package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.delight.DelightHome;
import com.jaymoskitchen.delight.SkilletItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;

/**
 * (Farmer's Delight's client setup, MIT.) The client side of {@link DelightHome}: the stove's grill drawn, the skillet's
 * item models, and a click while holding a cooking skillet to flip it.
 */
public final class DelightHomeClient {
	private DelightHomeClient() {
	}

	public static void register() {
		BlockEntityRenderers.register(DelightHome.STOVE_ENTITY, StoveRenderer::new);
		ItemModels.ID_MAPPER.put(SkilletFlipItemRenderer.ID, SkilletFlipItemRenderer.Unbaked.CODEC);
		ConditionalItemModelProperties.ID_MAPPER.put(SkilletCookingProperty.ID, SkilletCookingProperty.MAP_CODEC);
		SkilletItem.viewer = () -> Minecraft.getInstance().player;
		// attacking (left click) while cooking flips the pan instead
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (!player.isSpectator() && player.isUsingItem() && player.getUseItem().getItem() instanceof SkilletItem && clickCount != 0
				&& !player.getUseItem().has(SkilletItem.FLIP_TIMESTAMP)) {
				ClientPlayNetworking.send(new SkilletItem.Flip());
			}
			return false;
		});
	}
}
