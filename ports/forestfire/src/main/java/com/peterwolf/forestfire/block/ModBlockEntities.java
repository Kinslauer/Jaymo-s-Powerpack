package com.peterwolf.forestfire.block;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.firefighting.hose.HoseSplitterBlockEntity;
import com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import com.peterwolf.forestfire.firefighting.water.PortableSprinklerBlockEntity;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import com.peterwolf.forestfire.world.CommandPostBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
   public static final BlockEntityType<PortablePumpBlockEntity> PORTABLE_PUMP = register(
      "portable_pump", FabricBlockEntityTypeBuilder.create(PortablePumpBlockEntity::new, new Block[]{ModBlocks.PORTABLE_PUMP}).build()
   );
   public static final BlockEntityType<PortableWaterTankBlockEntity> WATER_TANK = register(
      "water_tank",
      FabricBlockEntityTypeBuilder.create(
            PortableWaterTankBlockEntity::new, new Block[]{ModBlocks.WATER_TANK_SMALL, ModBlocks.WATER_TANK_MEDIUM, ModBlocks.WATER_TANK_LARGE}
         )
         .build()
   );
   public static final BlockEntityType<HoseSplitterBlockEntity> HOSE_SPLITTER = register(
      "hose_splitter", FabricBlockEntityTypeBuilder.create(HoseSplitterBlockEntity::new, new Block[]{ModBlocks.HOSE_SPLITTER}).build()
   );
   public static final BlockEntityType<PortableSprinklerBlockEntity> SPRINKLER = register(
      "portable_sprinkler", FabricBlockEntityTypeBuilder.create(PortableSprinklerBlockEntity::new, new Block[]{ModBlocks.PORTABLE_SPRINKLER}).build()
   );
   public static final BlockEntityType<CommandPostBlockEntity> COMMAND_POST = register(
      "command_post", FabricBlockEntityTypeBuilder.create(CommandPostBlockEntity::new, new Block[]{ModBlocks.COMMAND_POST}).build()
   );
   public static final BlockEntityType<GroundNozzleBlockEntity> GROUND_NOZZLE = register(
      "ground_nozzle", FabricBlockEntityTypeBuilder.create(GroundNozzleBlockEntity::new, new Block[]{ModBlocks.GROUND_NOZZLE}).build()
   );

   private ModBlockEntities() {
   }

   public static void register() {
      ForestFireMod.LOGGER.info("Registered forest fire block entities");
   }

   private static <T extends BlockEntity> BlockEntityType<T> register(String path, BlockEntityType<T> type) {
      ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, ForestFireMod.id(path));
      return (BlockEntityType<T>)Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
   }
}
