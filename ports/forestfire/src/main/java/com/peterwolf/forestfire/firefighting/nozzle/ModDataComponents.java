package com.peterwolf.forestfire.firefighting.nozzle;

import com.mojang.serialization.Codec;
import com.peterwolf.forestfire.ForestFireMod;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponentType.Builder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModDataComponents {
   public static final DataComponentType<Integer> NOZZLE_MODE = register(
      "nozzle_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
   );
   public static final DataComponentType<String> NOZZLE_ENDPOINT_ID = register(
      "nozzle_endpoint_id", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
   );
   public static final DataComponentType<Integer> WATER_AMOUNT = register(
      "water_amount", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
   );
   public static final DataComponentType<Integer> AIR_AMOUNT = register(
      "air_amount", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
   );
   public static final DataComponentType<String> FIREFIGHTER_ROLE = register(
      "firefighter_role", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
   );
   public static final DataComponentType<Integer> HOSE_REMAINING = register(
      "hose_remaining", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
   );
   public static final DataComponentType<Long> CONNECTOR_PUMP_POS = register(
      "connector_pump_pos", builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG)
   );
   public static final DataComponentType<String> CONNECTOR_MODE = register(
      "connector_mode", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
   );

   private ModDataComponents() {
   }

   public static void register() {
   }

   private static <T> DataComponentType<T> register(String path, UnaryOperator<Builder<T>> operator) {
      return (DataComponentType<T>)Registry.register(
         BuiltInRegistries.DATA_COMPONENT_TYPE, ForestFireMod.id(path), operator.apply(DataComponentType.builder()).build()
      );
   }
}
