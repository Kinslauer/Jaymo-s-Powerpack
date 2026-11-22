package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record WindSyncPayload(float speed, float yawDegrees) implements CustomPacketPayload {
   public static final Type<WindSyncPayload> TYPE = new Type(ForestFireMod.id("wind_sync"));
   public static final StreamCodec<RegistryFriendlyByteBuf, WindSyncPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.FLOAT, WindSyncPayload::speed, ByteBufCodecs.FLOAT, WindSyncPayload::yawDegrees, WindSyncPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
