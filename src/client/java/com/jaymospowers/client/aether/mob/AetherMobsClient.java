package com.jaymospowers.client.aether.mob;

import com.jaymospowers.aether.mob.AerbunnyRide;
import com.jaymospowers.aether.mob.AetherAnimals;
import com.jaymospowers.aether.mob.AetherMobs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

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
		EntityRenderers.register(AetherAnimals.KIRRID, ctx -> new AetherAnimalRenderer<>(ctx, KirridModel.create(), 0.6F, AetherAnimalRenderer.kirrid()));
		EntityRenderers.register(AetherAnimals.BURRUKAI, ctx -> new AetherAnimalRenderer<>(ctx, BurrukaiModel.create(), 0.9F, AetherAnimalRenderer.burrukai()));
		EntityRenderers.register(AetherAnimals.TAEGORE, ctx -> new AetherAnimalRenderer<>(ctx, TaegoreModel.create(), 0.8F, AetherAnimalRenderer.taegore()));
		EntityRenderers.register(AetherAnimals.SKYROOT_LIZARD, ctx -> new AetherAnimalRenderer<>(ctx, SkyrootLizardModel.create(), 0.3F, AetherAnimalRenderer.lizard()));
		EntityRenderers.register(AetherAnimals.PRISMALLARD, ctx -> new AetherAnimalRenderer<>(ctx, PrismallardModel.create(), 0.25F, AetherAnimalRenderer.prismallard()));
		EntityRenderers.register(AetherAnimals.GLITTERWING, ctx -> new AetherAnimalRenderer<>(ctx, GlitterwingModel.create(), 0.15F, AetherAnimalRenderer.insect(true)));
		EntityRenderers.register(AetherAnimals.SHROUDWING, ctx -> new AetherAnimalRenderer<>(ctx, ShroudwingModel.create(), 0.1F, AetherAnimalRenderer.insect(false)));
		EntityRenderers.register(AetherAnimals.AERWHALE, ctx -> new AetherAnimalRenderer<>(ctx, AerwhaleModel.create(), 0.0F, AetherAnimalRenderer.aerwhale()));
		EntityRenderers.register(AetherAnimals.PRISMALLARD_EGG_ENTITY, ThrownItemRenderer::new);
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
