package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.horse.Saddlebags;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client setup for the carts: their renderers, models and the plow's screen. */
public final class CartClient {
	private CartClient() {
	}

	public static void register() {
		EntityRendererRegistry.register(Saddlebags.WAGON, CartRenderer::new);
		EntityRendererRegistry.register(Saddlebags.COACH, CartRenderer::new);
		EntityRendererRegistry.register(Saddlebags.SUPPLY_CART, CartRenderer::new);
		EntityRendererRegistry.register(Saddlebags.ANIMAL_CART, CartRenderer::new);
		EntityRendererRegistry.register(Saddlebags.PLOW, CartRenderer::new);
		ModelLayerRegistry.registerModelLayer(NiftyCartModels.WAGON, NiftyCartModels::wagon);
		ModelLayerRegistry.registerModelLayer(NiftyCartModels.COACH, NiftyCartModels::coach);
		ModelLayerRegistry.registerModelLayer(NiftyCartModels.SUPPLY_CART, NiftyCartModels::supplyCart);
		ModelLayerRegistry.registerModelLayer(NiftyCartModels.ANIMAL_CART, NiftyCartModels::animalCart);
		ModelLayerRegistry.registerModelLayer(NiftyCartModels.PLOW, NiftyCartModels::plow);
		MenuScreens.register(Saddlebags.PLOW_MENU, PlowScreen::new);
	}
}
