package com.jaymospowers.layers;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * (0.17.116) Layer blocks ({@link LayerBlock}) for every wooden and stone block in {@link LayerList}, each {@code <path>_layer}
 * and built like its block (hardness, sound, tool, burning). Wood is cut with the {@link SawItem saw} in a crafting grid,
 * stone at the stonecutter, both 8 layers a block; 8 layers craft back into the block. Assets and data come from
 * tools/gen_layers.py.
 */
public final class Layers {
	/** Cuts a saw makes before it wears out. */
	public static final int SAW_DURABILITY = 250;
	/** Wooden layers catch and burn as planks do. */
	public static final int WOOD_IGNITE = 5;
	public static final int WOOD_BURN = 20;

	public static Item SAW;
	public static final List<Block> LAYERS = new ArrayList<>();

	private Layers() {
	}

	/** After the Aether's blocks: their layers copy them. */
	public static void init() {
		ResourceKey<Item> sawKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("saw"));
		SAW = Registry.register(BuiltInRegistries.ITEM, sawKey, new SawItem(new Item.Properties().setId(sawKey).durability(SAW_DURABILITY)));
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		for (String baseId : LayerList.BASES) {
			Block base = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(baseId)).orElse(null);
			if (base == null) {
				JaymosPowers.LOGGER.warn("No block {} for its layer", baseId);
				continue;
			}
			String id = Identifier.parse(baseId).getPath() + "_layer";
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
			// (0.17.117) the base's own map colour, fixed: a log's is a function of its AXIS, which a layer doesn't have (the
			// copied function threw "Cannot get property axis" building the layer's states, and the game crashed on start)
			Block layer = Registry.register(BuiltInRegistries.BLOCK, key,
				new LayerBlock(BlockBehaviour.Properties.ofFullCopy(base).mapColor(base.defaultMapColor()).setId(key)));
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(layer, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
			if (base.defaultBlockState().ignitedByLava()) {
				flammable.add(layer, WOOD_IGNITE, WOOD_BURN);
			}
			LAYERS.add(layer);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(SAW));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> LAYERS.forEach(output::accept));
	}
}
