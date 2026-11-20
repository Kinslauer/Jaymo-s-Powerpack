package com.peterwolf.forestfire.client;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.client.hose.ClientHoseState;
import com.peterwolf.forestfire.client.hose.HoseRenderer;
import com.peterwolf.forestfire.client.hud.FirefighterHud;
import com.peterwolf.forestfire.client.nozzle.NozzleClientInput;
import com.peterwolf.forestfire.network.HoseSyncPayload;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.NozzleHudPayload;
import com.peterwolf.forestfire.network.WindSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;

public final class ForestFireClient implements ClientModInitializer {
   public void onInitializeClient() {
      ClientPlayNetworking.registerGlobalReceiver(
         IncidentHudPayload.TYPE, (payload, context) -> context.client().execute(() -> FirefighterHud.updateIncident(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         WindSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> FirefighterHud.updateWind(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         NozzleHudPayload.TYPE, (payload, context) -> context.client().execute(() -> FirefighterHud.updateNozzle(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(HoseSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientHoseState.apply(payload)));
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> ClientHoseState.clear());
      HoseRenderer.register();
      NozzleClientInput.register();
      HudElementRegistry.addLast(ForestFireMod.id("firefighter_hud"), FirefighterHud::render);
      initPlanesCompatClient();
      ForestFireMod.LOGGER
         .info("Forest Fire client initialized. {}", "Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.");
   }

   private static void initPlanesCompatClient() {
      if (FabricLoader.getInstance().isModLoaded("peterwolfs_planes")) {
         try {
            Class.forName("com.peterwolf.forestfire.compat.planes.PlanesCompatClient").getMethod("init").invoke(null);
         } catch (ReflectiveOperationException var1) {
            ForestFireMod.LOGGER.error("Failed to initialize Planes firefighting client compat", var1);
         }
      }
   }
}
