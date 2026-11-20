package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.firefighting.nozzle.NozzleInputController;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
   private ModNetworking() {
   }

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(IncidentHudPayload.TYPE, IncidentHudPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(WindSyncPayload.TYPE, WindSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(NozzleHudPayload.TYPE, NozzleHudPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HoseSyncPayload.TYPE, HoseSyncPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(NozzleLpmPayload.TYPE, NozzleLpmPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(
         NozzleLpmPayload.TYPE, (payload, context) -> context.server().execute(() -> NozzleInputController.handle(context.player(), payload))
      );
   }

   public static void sendHud(ServerPlayer player, IncidentHudPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   public static void sendWind(ServerPlayer player, WindSyncPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   public static void sendNozzleHud(ServerPlayer player, NozzleHudPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   public static void sendHoseSync(ServerPlayer player, HoseSyncPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }
}
