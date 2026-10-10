package com.jaymoskitchen.mill;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The mill drop, after Horse Power (by Gory_Moon; LGPL-3.0, see LICENSE_horsepower): the hand grindstone and horse
 * mill, the chopping block and horse chopper, the press and horse press, and flour.
 */
public final class Mill {
	public static final Map<MachineKind, MachineBlock> MACHINES = new EnumMap<>(MachineKind.class);
	public static Block FILLER;
	public static MachinePartBlock PART;
	public static BlockEntityType<MachineEntity> MACHINE_ENTITY;
	public static Item FLOUR;

	private Mill() {
	}

	public static void register() {
		List<Item> items = new ArrayList<>();
		for (MachineKind kind : MachineKind.values()) {
			boolean stone = kind.work == MachineKind.Work.GRIND;
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.mapColor(stone ? MapColor.STONE : MapColor.WOOD)
				.instrument(stone ? NoteBlockInstrument.BASEDRUM : NoteBlockInstrument.BASS)
				.strength(stone ? 2.0F : 2.5F)
				.sound(stone ? SoundType.STONE : SoundType.WOOD)
				.noOcclusion()
				.pushReaction(PushReaction.IMMOVEABLE);
			if (stone) {
				properties.requiresCorrectToolForDrops();
			} else {
				properties.ignitedByLava();
			}
			MachineBlock block = block(kind.id, props -> new MachineBlock(kind, props), properties);
			MACHINES.put(kind, block);
			items.add(blockItem(kind.id, block));
		}
		FILLER = block("machine_filler", MachineFillerBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion().noLootTable().pushReaction(PushReaction.IMMOVEABLE));
		PART = block("machine_part", MachinePartBlock::new, BlockBehaviour.Properties.of().noCollision().noOcclusion().noLootTable());

		Set<Block> machines = new HashSet<>(MACHINES.values());
		MACHINE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("machine"),
			new BlockEntityType<>(MachineEntity::new, machines));

		ResourceKey<Item> flourKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("flour"));
		FLOUR = Registry.register(BuiltInRegistries.ITEM, flourKey, new Item(new Item.Properties().setId(flourKey)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : items) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(new ItemStack(FLOUR)));
	}

	private static <B extends Block> B block(String name, java.util.function.Function<BlockBehaviour.Properties, B> factory,
		BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}
}
