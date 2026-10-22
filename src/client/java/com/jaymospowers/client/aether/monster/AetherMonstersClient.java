package com.jaymospowers.client.aether.monster;

import com.jaymospowers.aether.monster.AetherMonsters;
import com.jaymospowers.aether.monster.AetherShot;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** The Aether's hostile mobs, client side: a renderer for each mob and each kind of shot (the shots are drawn as their item sprites). */
public final class AetherMonstersClient {
	private AetherMonstersClient() {
	}

	public static void init() {
		EntityRenderers.register(AetherMonsters.ZEPHYR, ctx -> new AetherMonsterRenderer<>(ctx, ZephyrModel.create(), 0.6F, AetherMonsterRenderer.zephyr()));
		EntityRenderers.register(AetherMonsters.TEMPEST, ctx -> new AetherMonsterRenderer<>(ctx, TempestModel.create(), 0.4F, AetherMonsterRenderer.tempest()));
		EntityRenderers.register(AetherMonsters.COCKATRICE, ctx -> new AetherMonsterRenderer<>(ctx, CockatriceModel.create(), 0.6F, AetherMonsterRenderer.cockatrice()));
		EntityRenderers.register(AetherMonsters.BLUE_SWET, ctx -> new AetherMonsterRenderer<>(ctx, SwetModel.create(), 0.5F, AetherMonsterRenderer.swet(false)));
		EntityRenderers.register(AetherMonsters.GOLDEN_SWET, ctx -> new AetherMonsterRenderer<>(ctx, SwetModel.create(), 0.5F, AetherMonsterRenderer.swet(true)));
		EntityRenderers.register(AetherMonsters.AECHOR_PLANT, ctx -> new AetherMonsterRenderer<>(ctx, AechorPlantModel.create(), 0.35F, AetherMonsterRenderer.aechorPlant()));
		EntityRenderers.register(AetherMonsters.CARRION_SPROUT, ctx -> new AetherMonsterRenderer<>(ctx, CarrionSproutModel.create(), 0.4F, AetherMonsterRenderer.carrionSprout()));
		EntityRenderers.register(AetherMonsters.SKEPHID, ctx -> new AetherMonsterRenderer<>(ctx, SkephidModel.create(), 0.4F, AetherMonsterRenderer.skephid()));
		EntityRenderers.register(AetherMonsters.ARKENIUM_TALUTON, ctx -> new AetherMonsterRenderer<>(ctx, ArkeniumTalutonModel.create(), 0.6F, AetherMonsterRenderer.arkeniumTaluton()));
		EntityRenderers.register(AetherMonsters.GRAVITITE_TALUTON, ctx -> new AetherMonsterRenderer<>(ctx, GravititeTalutonModel.create(), 0.5F, AetherMonsterRenderer.gravititeTaluton()));
		EntityRenderers.register(AetherMonsters.BLADESHROOM_HUNTER, ctx -> new AetherMonsterRenderer<>(ctx, BladeshroomHunterModel.create(), 0.5F, AetherMonsterRenderer.bladeshroomHunter()));

		for (AetherShot.Kind kind : AetherShot.Kind.values()) {
			float size = switch (kind) {
				case ZEPHYR_WEB -> 1.5F;
				case SKEPHID_WEB -> 1.0F;
				case THUNDERBALL -> 1.3F;
				case VENOMOUS_DART -> 0.8F;
				case TOXIC_DART -> 0.6F;
				case DEBRIS -> 1.1F;
			};
			boolean bright = kind == AetherShot.Kind.THUNDERBALL;
			EntityRenderers.register(AetherMonsters.shotType(kind), ctx -> new ThrownItemRenderer<>(ctx, size, bright));
		}
	}
}
