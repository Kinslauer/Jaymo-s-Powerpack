package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record NozzleHudPayload(String mode, float pressure01, boolean flowActive, String tension, int remainingDistance, boolean connected)
   implements CustomPacketPayload {
   public static final Type<NozzleHudPayload> TYPE = new Type(ForestFireMod.id("nozzle_hud"));
   public static final StreamCodec<RegistryFriendlyByteBuf, NozzleHudPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8,
      NozzleHudPayload::mode,
      ByteBufCodecs.FLOAT,
      NozzleHudPayload::pressure01,
      ByteBufCodecs.BOOL,
      NozzleHudPayload::flowActive,
      ByteBufCodecs.STRING_UTF8,
      NozzleHudPayload::tension,
      ByteBufCodecs.VAR_INT,
      NozzleHudPayload::remainingDistance,
      ByteBufCodecs.BOOL,
      NozzleHudPayload::connected,
      NozzleHudPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
