package com.jaymospowers.client.aether.dungeon;

import com.jaymospowers.aether.dungeon.DungeonMobs;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** The Sentry Ruins' mobs, client side: a renderer for each mob, and the golem's bomb (drawn as its item's sprite). */
public final class DungeonClient {
	private DungeonClient() {
	}

	public static void init() {
		EntityRenderers.register(DungeonMobs.SLIDER, ctx -> new DungeonRenderer<>(ctx, SliderModel.create(), 1.5F, DungeonRenderer.slider()));
		EntityRenderers.register(DungeonMobs.DETONATION_SENTRY, ctx -> new DungeonRenderer<>(ctx, DetonationSentryModel.create(), 0.5F, DungeonRenderer.detonationSentry()));
		EntityRenderers.register(DungeonMobs.SENTRY_GOLEM, ctx -> new DungeonRenderer<>(ctx, SentryGolemModel.create(), 0.5F, DungeonRenderer.sentryGolem()));
		EntityRenderers.register(DungeonMobs.MIMIC, ctx -> new DungeonRenderer<>(ctx, MimicModel.create(), 0.6F, DungeonRenderer.mimic()));
		// (0.20.96) the sky warden: the sentry golem's frame, carved bigger (its scale) and paler
		EntityRenderers.register(com.jaymospowers.aether.sky.SkyMobs.SKY_WARDEN, ctx -> new DungeonRenderer<>(ctx, SentryGolemModel.create(), 1.2F, DungeonRenderer.skyWarden()));
		EntityRenderers.register(DungeonMobs.DEMOLITION_BOMB, ctx -> new ThrownItemRenderer<>(ctx, 1.2F, false));
	}
}
