package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.firefighting.equipment.FirefighterArmorMaterials;
import com.peterwolf.forestfire.firefighting.nozzle.FireHoseNozzleItem;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

public final class ModItems {
   public static final Item PORTABLE_PUMP = blockItem("portable_pump", ModBlocks.PORTABLE_PUMP);
   public static final Item HOSE_SPLITTER = blockItem("hose_splitter", ModBlocks.HOSE_SPLITTER);
   public static final Item INTAKE_STRAINER = blockItem("intake_strainer", ModBlocks.INTAKE_STRAINER);
   public static final Item WATER_TANK_SMALL = blockItem("water_tank_small", ModBlocks.WATER_TANK_SMALL);
   public static final Item WATER_TANK_MEDIUM = blockItem("water_tank_medium", ModBlocks.WATER_TANK_MEDIUM);
   public static final Item WATER_TANK_LARGE = blockItem("water_tank_large", ModBlocks.WATER_TANK_LARGE);
   public static final Item PORTABLE_SPRINKLER = blockItem("portable_sprinkler", ModBlocks.PORTABLE_SPRINKLER);
   public static final Item COMMAND_POST = blockItem("command_post", ModBlocks.COMMAND_POST);
   public static final Item FIRE_HOSE_NOZZLE = register("fire_hose_nozzle", FireHoseNozzleItem::new);
   public static final Item BACKPACK_SPRAYER = register("backpack_sprayer", BackpackSprayerItem::new);
   public static final Item THERMAL_SCANNER = register("thermal_scanner", ThermalScannerItem::new);
   public static final Item FIREFIGHTER_INFO = register("firefighter_info", FirefighterInfoItem::new);
   public static final Item PUMP_FUEL_CAN = register("pump_fuel_can", PumpFuelCanItem::new);
   public static final Item HOSE_CONNECTOR = register("hose_connector", HoseConnectorItem::new);
   public static final Item HOSE_ANCHOR = register("hose_anchor", HoseAnchorItem::new);
   public static final Item HOSE_ROLL_SMALL = register("hose_roll_small", p -> new HoseRollItem(p, 16));
   public static final Item HOSE_ROLL_STANDARD = register("hose_roll_standard", p -> new HoseRollItem(p, 32));
   public static final Item HOSE_ROLL_LARGE = register("hose_roll_large", p -> new HoseRollItem(p, 64));
   public static final Item FIRE_AXE = register("fire_axe", p -> new FireToolItem(p, FireToolItem.ToolType.AXE));
   public static final Item PULASKI = register("pulaski", p -> new FireToolItem(p, FireToolItem.ToolType.PULASKI));
   public static final Item FIRE_SHOVEL = register("fire_shovel", p -> new FireToolItem(p, FireToolItem.ToolType.SHOVEL));
   public static final Item FIRE_RAKE = register("fire_rake", p -> new FireToolItem(p, FireToolItem.ToolType.RAKE));
   public static final Item FF_HELMET = register("firefighter_helmet", p -> new Item(p.humanoidArmor(FirefighterArmorMaterials.FIREFIGHTER, ArmorType.HELMET)));
   public static final Item FF_CHESTPLATE = register(
      "firefighter_jacket", p -> new Item(p.humanoidArmor(FirefighterArmorMaterials.FIREFIGHTER, ArmorType.CHESTPLATE))
   );
   public static final Item FF_LEGGINGS = register(
      "firefighter_trousers", p -> new Item(p.humanoidArmor(FirefighterArmorMaterials.FIREFIGHTER, ArmorType.LEGGINGS))
   );
   public static final Item FF_BOOTS = register("firefighter_boots", p -> new Item(p.humanoidArmor(FirefighterArmorMaterials.FIREFIGHTER, ArmorType.BOOTS)));
   public static final Item BREATHING_MASK = register("breathing_mask", BreathingMaskItem::new);
   public static final Item AIR_TANK = register("air_tank", AirTankItem::new);
   public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
      .title(Component.translatable("itemGroup.peterwolfs_forestfire"))
      .icon(FIRE_HOSE_NOZZLE::getDefaultInstance)
      .displayItems((params, output) -> {
         output.accept(PORTABLE_PUMP);
         output.accept(HOSE_SPLITTER);
         output.accept(HOSE_ROLL_SMALL);
         output.accept(HOSE_ROLL_STANDARD);
         output.accept(HOSE_ROLL_LARGE);
         output.accept(HOSE_CONNECTOR);
         output.accept(HOSE_ANCHOR);
         output.accept(INTAKE_STRAINER);
         output.accept(WATER_TANK_SMALL);
         output.accept(WATER_TANK_MEDIUM);
         output.accept(WATER_TANK_LARGE);
         output.accept(PORTABLE_SPRINKLER);
         output.accept(COMMAND_POST);
         output.accept(FIRE_HOSE_NOZZLE);
         output.accept(BACKPACK_SPRAYER);
         output.accept(THERMAL_SCANNER);
         output.accept(FIREFIGHTER_INFO);
         output.accept(PUMP_FUEL_CAN);
         output.accept(FIRE_AXE);
         output.accept(PULASKI);
         output.accept(FIRE_SHOVEL);
         output.accept(FIRE_RAKE);
         output.accept(FF_HELMET);
         output.accept(FF_CHESTPLATE);
         output.accept(FF_LEGGINGS);
         output.accept(FF_BOOTS);
         output.accept(BREATHING_MASK);
         output.accept(AIR_TANK);
      })
      .build();

   private ModItems() {
   }

   public static void register() {
      ModDataComponents.register();
      FirefighterArmorMaterials.register();
      Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ForestFireMod.id("main"), TAB);
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyOutput)output -> {
         output.accept(FIRE_HOSE_NOZZLE);
         output.accept(FIRE_AXE);
         output.accept(THERMAL_SCANNER);
      });
      ForestFireMod.LOGGER.info("Registered forest fire items");
   }

   private static Item blockItem(String path, Block block) {
      return register(path, properties -> new BlockItem(block, properties.useBlockDescriptionPrefix()));
   }

   private static Item register(String path, Function<Properties, Item> factory) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ForestFireMod.id(path));
      Item item = factory.apply(new Properties().setId(key));
      if (item instanceof BlockItem blockItem) {
         blockItem.registerBlocks(Item.BY_BLOCK, item);
      }

      return (Item)Registry.register(BuiltInRegistries.ITEM, key, item);
   }
}
