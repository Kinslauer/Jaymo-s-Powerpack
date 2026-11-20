package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record NozzleLpmPayload(int action) implements CustomPacketPayload {
   public static final int CLICK = 0;
   public static final int HOLD = 1;
   public static final int RELEASE = 2;
   public static final Type<NozzleLpmPayload> TYPE = new Type(ForestFireMod.id("nozzle_lpm"));
   public static final StreamCodec<RegistryFriendlyByteBuf, NozzleLpmPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, NozzleLpmPayload::action, NozzleLpmPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
