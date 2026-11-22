package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record IncidentHudPayload(
   int incidentId, String name, String status, int burningBlocks, float containment, float windSpeed, float windYaw, float pressure, String nozzleMode
) implements CustomPacketPayload {
   public static final Type<IncidentHudPayload> TYPE = new Type(ForestFireMod.id("incident_hud"));
   public static final StreamCodec<RegistryFriendlyByteBuf, IncidentHudPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      IncidentHudPayload::incidentId,
      ByteBufCodecs.STRING_UTF8,
      IncidentHudPayload::name,
      ByteBufCodecs.STRING_UTF8,
      IncidentHudPayload::status,
      ByteBufCodecs.VAR_INT,
      IncidentHudPayload::burningBlocks,
      ByteBufCodecs.FLOAT,
      IncidentHudPayload::containment,
      ByteBufCodecs.FLOAT,
      IncidentHudPayload::windSpeed,
      ByteBufCodecs.FLOAT,
      IncidentHudPayload::windYaw,
      ByteBufCodecs.FLOAT,
      IncidentHudPayload::pressure,
      ByteBufCodecs.STRING_UTF8,
      IncidentHudPayload::nozzleMode,
      IncidentHudPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
