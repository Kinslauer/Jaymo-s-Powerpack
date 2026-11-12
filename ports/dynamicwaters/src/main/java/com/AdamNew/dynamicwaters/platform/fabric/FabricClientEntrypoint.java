package com.AdamNew.dynamicwaters.platform.fabric;

import com.AdamNew.dynamicwaters.client.ClientSetup;
import com.AdamNew.dynamicwaters.client.FlowZoneClient;
import com.AdamNew.dynamicwaters.client.IceFloeRenderer;
import com.AdamNew.dynamicwaters.client.MainMenuButtonHandler;
import com.AdamNew.dynamicwaters.client.VanillaFlowClientEvents;
import com.AdamNew.dynamicwaters.client.particle.RiverBubbleParticle;
import com.AdamNew.dynamicwaters.network.ModNetwork;
import com.AdamNew.dynamicwaters.network.SyncFlowZonesPacket;
import com.AdamNew.dynamicwaters.network.SyncMainRiversPacket;
import com.AdamNew.dynamicwaters.network.SyncSeedPacket;
import com.AdamNew.dynamicwaters.network.SyncVanillaFlowPacket;
import com.AdamNew.dynamicwaters.network.SyncWatershedsPacket;
import com.AdamNew.dynamicwaters.registry.ModEntities;
import com.AdamNew.dynamicwaters.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

public class FabricClientEntrypoint implements ClientModInitializer {
	private static boolean ownServer(ClientPlayNetworking.Context context) {
		return context.player().connection.getConnection().isMemoryConnection();
	}

	@Override
	public void onInitializeClient() {
		ClientSetup.clientSetup();
		EntityRendererRegistry.register(ModEntities.ICE_FLOE.get(), IceFloeRenderer::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.RIVER_BUBBLE.get(), RiverBubbleParticle.Provider::new);

		ClientPlayNetworking.registerGlobalReceiver(SyncSeedPacket.TYPE, (payload, context) -> ModNetwork.receive(payload, ownServer(context)));
		ClientPlayNetworking.registerGlobalReceiver(SyncWatershedsPacket.TYPE, (payload, context) -> ModNetwork.receive(payload, ownServer(context)));
		ClientPlayNetworking.registerGlobalReceiver(SyncMainRiversPacket.TYPE, (payload, context) -> ModNetwork.receive(payload, ownServer(context)));
		ClientPlayNetworking.registerGlobalReceiver(SyncVanillaFlowPacket.TYPE, (payload, context) -> VanillaFlowClientEvents.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(SyncFlowZonesPacket.TYPE, (payload, context) -> FlowZoneClient.receive(payload));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			VanillaFlowClientEvents.onLoggingOut();
			FlowZoneClient.onLoggingOut();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(FlowZoneClient::render);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				MainMenuButtonHandler.onScreenInit(screen, Screens.getWidgets(screen), Screens.getWidgets(screen)::add));
	}
}
