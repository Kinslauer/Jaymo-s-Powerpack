package com.peterwolf.forestfire.block;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.firefighting.hose.HoseSplitterBlock;
import com.peterwolf.forestfire.firefighting.hose.IntakeStrainerBlock;
import com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlock;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlock;
import com.peterwolf.forestfire.firefighting.water.PortableSprinklerBlock;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlock;
import com.peterwolf.forestfire.firefighting.water.TankSize;
import com.peterwolf.forestfire.world.CommandPostBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
   public static final Block PORTABLE_PUMP = register(
      "portable_pump", PortablePumpBlock::new, Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
   );
   public static final Block HOSE_SPLITTER = register(
      "hose_splitter", HoseSplitterBlock::new, Properties.of().mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)
   );
   public static final Block INTAKE_STRAINER = register(
      "intake_strainer",
      IntakeStrainerBlock::new,
      Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.POPPED)
   );
   public static final Block WATER_TANK_SMALL = register(
      "water_tank_small",
      props -> new PortableWaterTankBlock(props, TankSize.SMALL),
      Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0F).sound(SoundType.METAL)
   );
   public static final Block WATER_TANK_MEDIUM = register(
      "water_tank_medium",
      props -> new PortableWaterTankBlock(props, TankSize.MEDIUM),
      Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5F).sound(SoundType.METAL)
   );
   public static final Block WATER_TANK_LARGE = register(
      "water_tank_large",
      props -> new PortableWaterTankBlock(props, TankSize.LARGE),
      Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F).sound(SoundType.METAL)
   );
   public static final Block PORTABLE_SPRINKLER = register(
      "portable_sprinkler", PortableSprinklerBlock::new, Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion()
   );
   public static final Block COMMAND_POST = register(
      "command_post", CommandPostBlock::new, Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)
   );
   public static final Block GROUND_NOZZLE = register(
      "ground_nozzle",
      GroundNozzleBlock::new,
      Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.POPPED)
   );

   private ModBlocks() {
   }

   public static void register() {
      ForestFireMod.LOGGER.info("Registered forest fire blocks");
   }

   private static Block register(String path, Function<Properties, Block> factory, Properties properties) {
      ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ForestFireMod.id(path));
      Block block = factory.apply(properties.setId(key));
      return (Block)Registry.register(BuiltInRegistries.BLOCK, key, block);
   }
}
