package com.jaymoskitchen.client;

import com.jaymoskitchen.client.corpse.CorpseRenderer;
import com.jaymoskitchen.client.corpse.ZombiePlayerModel;
import com.jaymoskitchen.client.corpse.ZombiePlayerRenderer;
import com.jaymoskitchen.corpse.PlayerCorpses;
import com.jaymoskitchen.client.horse.CartClient;
import com.jaymoskitchen.client.mill.MachineRenderer;
import com.jaymoskitchen.client.table.CraftingTableRenderer;
import com.jaymoskitchen.client.horse.SaddlebagLayer;
import com.jaymoskitchen.client.horse.SaddlebagModel;
import com.jaymoskitchen.client.quiver.QuiverLayer;
import com.jaymoskitchen.client.quiver.QuiverModel;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.horse.Saddlebags;
import com.jaymoskitchen.mill.Mill;
import com.jaymoskitchen.table.Tables;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.world.entity.EntityTypes;

public class JaymosKitchenClient implements ClientModInitializer {
	@SuppressWarnings("unchecked")
	private static SaddlebagLayer saddlebagLayer(Object renderer, EntityModelSet models) {
		return new SaddlebagLayer((RenderLayerParent<EquineRenderState, EntityModel<EquineRenderState>>) renderer, models);
	}

	@Override
	public void onInitializeClient() {
		com.jaymoskitchen.client.stomach.StomachHud.register();
		// (0.5.25) thirst row, thermometer, heat edges, sips
		com.jaymoskitchen.client.climate.ClimateHud.register();
		// (0.5.43) low on water: faint veins at the screen edges
		com.jaymoskitchen.client.climate.DryVeins.register();
		com.jaymoskitchen.client.herb.PmehTooltip.register();
		// (0.5.1) ragdolls: the bodies the server (or this client, for its own limp player) simulates, drawn here
		com.jaymoskitchen.client.ragdoll.ClientRagdolls.register();
		// (0.5.44) a dragged body follows the hand this client draws
		com.jaymoskitchen.client.ragdoll.DragSim.register();
		com.jaymoskitchen.client.ragdoll.LimpController.register();
		// drinking: the view sways when you're drunk
		com.jaymoskitchen.client.brewing.DrunkSway.register();
		// corpses look like the mob they were; dead players can get back up as zombies wearing their skin
		EntityRendererRegistry.register(Corpses.CORPSE, CorpseRenderer::new);
		EntityRendererRegistry.register(PlayerCorpses.ZOMBIE_PLAYER, ZombiePlayerRenderer::new);
		// (0.5.18) a crushed spider's loose legs
		EntityRendererRegistry.register(com.jaymoskitchen.corpse.SpiderCrush.SPIDER_LEG, com.jaymoskitchen.client.corpse.SpiderLegRenderer::new);
		// (0.5.21) chunks of a body blown apart
		EntityRendererRegistry.register(com.jaymoskitchen.corpse.Gibs.GIB, com.jaymoskitchen.client.corpse.GibRenderer::new);
		ModelLayerRegistry.registerModelLayer(CorpseRenderer.PLAYER_BODY, () -> CorpseRenderer.playerBody(false));
		ModelLayerRegistry.registerModelLayer(CorpseRenderer.PLAYER_BODY_SLIM, () -> CorpseRenderer.playerBody(true));
		ModelLayerRegistry.registerModelLayer(ZombiePlayerModel.TATTERS, () -> ZombiePlayerModel.tatters(false));
		ModelLayerRegistry.registerModelLayer(ZombiePlayerModel.TATTERS_SLIM, () -> ZombiePlayerModel.tatters(true));

		// the horse drop: wagons and carriages, and saddlebags drawn on horses wearing them
		CartClient.register();
		// the mill machines' moving parts, loads and leads
		BlockEntityRenderers.register(Mill.MACHINE_ENTITY, MachineRenderer::new);
		// windmills and waterwheels turning
		BlockEntityRenderers.register(com.jaymoskitchen.mill.power.Power.ROTOR_ENTITY, com.jaymoskitchen.client.mill.RotorRenderer::new);
		// the potter's wheel turning
		BlockEntityRenderers.register(com.jaymoskitchen.mill.machines.PowerMachines.POTTERS_WHEEL_ENTITY, com.jaymoskitchen.client.mill.PottersWheelRenderer::new);
		// what's left on crafting tables
		BlockEntityRenderers.register(Tables.CRAFTING_TABLE, CraftingTableRenderer::new);
		// (0.5.28) what's lying on a cutting board
		BlockEntityRenderers.register(com.jaymoskitchen.delight.Delight.CUTTING_BOARD_ENTITY,
			com.jaymoskitchen.client.delight.CuttingBoardRenderer::new);
		com.jaymoskitchen.client.delight.DelightFarmClient.register();
		// the stove's grill, and the skillet's models
		com.jaymoskitchen.client.delight.DelightHomeClient.register();
		// (0.5.27) hides hanging on drying racks
		BlockEntityRenderers.register(com.jaymoskitchen.tanning.Hides.DRYING_RACK_ENTITY, com.jaymoskitchen.client.tanning.DryingRackRenderer::new);
		ModelLayerRegistry.registerModelLayer(SaddlebagModel.LAYER, SaddlebagModel::createLayer);

		// the quiver worn on players' backs
		ModelLayerRegistry.registerModelLayer(QuiverModel.LAYER, QuiverModel::createLayer);
		ModelLayerRegistry.registerModelLayer(QuiverModel.FLETCHING_LAYER, QuiverModel::createFletchingLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new QuiverLayer(avatarRenderer, context.getModelSet()));
			} else {
				// (0.5.23) arrows stuck in creatures (players have the game's own)
				helper.register(stuckArrows(renderer, context));
			}
			// by type: tags aren't loaded yet while renderers are being made
			if (renderer instanceof AbstractHorseRenderer<?, ?, ?> && (entityType == EntityTypes.HORSE
				|| entityType == EntityTypes.ZOMBIE_HORSE || entityType == EntityTypes.SKELETON_HORSE)) {
				helper.register(saddlebagLayer(renderer, context.getModelSet()));
			}
		});

		com.jaymoskitchen.client.season.SeasonTints.register();
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenButcher.TYPE, (payload, ctx) -> ctx.client().gui.setScreen(new ButcherScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenKitchen.TYPE, (payload, ctx) -> {
			Station station = Station.byIndex(payload.station());
			if (station != null) {
				ctx.client().gui.setScreen(new CookingScreen(payload.pos(), station, payload.stocked()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CookUpdate.TYPE, (payload, ctx) -> {
			if (ctx.client().gui.screen() instanceof CookingScreen screen) {
				screen.onUpdate(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenPrep.TYPE, (payload, ctx) -> ctx.client().gui.setScreen(
			payload.recipe() == Prep.Recipe.JOINT.ordinal() ? new RollingScreen(payload.pos()) : new PrepScreen(payload.pos(), payload.stocked())));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.PrepUpdate.TYPE, (payload, ctx) -> {
			if (ctx.client().gui.screen() instanceof PrepScreen screen) {
				screen.onUpdate(payload);
			} else if (ctx.client().gui.screen() instanceof RollingScreen screen) {
				screen.onUpdate(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SimUpdate.TYPE, (payload, ctx) -> {
			if (ctx.client().gui.screen() instanceof CookingScreen screen) {
				screen.onSimUpdate(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.GrillUpdate.TYPE, (payload, ctx) -> {
			if (ctx.client().gui.screen() instanceof CookingScreen screen) {
				screen.onGrillUpdate(payload);
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.client.renderer.entity.layers.RenderLayer stuckArrows(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
		net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
		return new com.jaymoskitchen.client.archery.StuckArrowsLayer((net.minecraft.client.renderer.entity.RenderLayerParent) renderer, context);
	}
}
