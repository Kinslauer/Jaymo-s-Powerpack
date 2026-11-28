package com.jaymospowers.client.dino;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.dino.Dino;
import com.jaymospowers.dino.DinoBlocks;
import com.jaymospowers.dino.DinoEntities;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.entity.TranqDart;
import com.jaymospowers.dino.network.DinoNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** The dinosaurs' client side: renderers, the rider's keys (attack is the attack key), and the status panel. */
public final class DinoClient {
	public static KeyMapping SPECIAL;
	public static KeyMapping ROAR;

	private DinoClient() {
	}

	public static void init() {
		for (Species s : Species.values()) {
			EntityRenderers.register(DinoEntities.dinosaur(s), DinoRenderer::new);
		}
		EntityRenderers.register(DinoEntities.FOSSIL_SKELETON, FossilRenderer::new);
		EntityRenderers.register(DinoEntities.VENOM_SPIT, NoopRenderer::new);
		EntityRenderers.register(DinoEntities.TRANQ_DART, DartRenderer::new);
		BlockEntityRenderers.register(DinoBlocks.SKULL_ENTITY, DinoSkullRenderer::new);
		// riding a dinosaur: the special and roar keys (default M and backslash, clear of the power keys), the attack key bites
		SPECIAL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.dino_special", InputConstants.KEY_M, JaymosPowersClient.CATEGORY));
		ROAR = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.dino_roar", InputConstants.KEY_BACKSLASH, JaymosPowersClient.CATEGORY));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Dino.id("dino_status"), DinoHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(client -> keys());
		// a rider's attack goes to the dinosaur, not a swing of their own arm
		ClientPreAttackCallback.EVENT.register(com.jaymospowers.client.compat.ComboClashClient.BEFORE_CLASH, (minecraft, player, clicks) -> {
			if (riddenDino() == null) {
				return false;
			}
			if (clicks > 0) {
				ClientPlayNetworking.send(new DinoNetwork.RiderAction(DinoNetwork.ATTACK));
			}
			return true;
		});
	}

	public static Dinosaur riddenDino() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getVehicle() instanceof Dinosaur d && d.getControllingPassenger() == mc.player ? d : null;
	}

	private static void keys() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null) {
			return;
		}
		boolean riding = riddenDino() != null;
		while (SPECIAL.consumeClick()) {
			if (riding) {
				ClientPlayNetworking.send(new DinoNetwork.RiderAction(DinoNetwork.SPECIAL));
			}
		}
		while (ROAR.consumeClick()) {
			if (riding) {
				ClientPlayNetworking.send(new DinoNetwork.RiderAction(DinoNetwork.ROAR));
			}
		}
	}

	static class DartRenderer extends ArrowRenderer<TranqDart, ArrowRenderState> {
		private static final Identifier TEXTURE = Dino.id("textures/entity/dino/tranq_dart.png");

		DartRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		protected Identifier getTextureLocation(ArrowRenderState state) {
			return TEXTURE;
		}

		@Override
		public ArrowRenderState createRenderState() {
			return new ArrowRenderState();
		}
	}
}
