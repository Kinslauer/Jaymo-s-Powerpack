package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.delight.DelightFarm;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** (Farmer's Delight's rotten tomato, MIT; our own code.) Draws a thrown rotten tomato as the item itself, like a snowball. */
public final class DelightFarmClient {
	private DelightFarmClient() {
	}

	public static void register() {
		EntityRendererRegistry.register(DelightFarm.ROTTEN_TOMATO_ENTITY, ThrownItemRenderer::new);
	}
}
