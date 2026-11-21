package com.jaymospowers.buildblocks;

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
 * A vertical slab ({@link VerticalSlabBlock}, {@code <path>_vslab}) and a quarter step ({@link QuarterStepBlock},
 * {@code <path>_quarter}) for every block in {@link BuildList} (the blocks the layer blocks cover), each built like its
 * block (hardness, sound, tool, burning). Wood is cut at the saw (four blocks into eight vertical slabs, a vertical slab into
 * two quarter steps), stone at the stonecutter. Assets and data come from tools/gen_building_blocks.py. The mixed
 * slab is {@link MixedSlabs}.
 */
public final class BuildBlocks {
	/** Wooden pieces catch and burn as planks do. */
	public static final int WOOD_IGNITE = 5;
	public static final int WOOD_BURN = 20;

	public static final List<Block> VERTICAL_SLABS = new ArrayList<>();
	public static final List<Block> QUARTER_STEPS = new ArrayList<>();

	private BuildBlocks() {
	}

	/** After the Aether's blocks: they copy them. */
	public static void init() {
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		for (String baseId : BuildList.BASES) {
			Block base = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(baseId)).orElse(null);
			if (base == null) {
				JaymosPowers.LOGGER.warn("No block {} for its vertical slab and quarter step", baseId);
				continue;
			}
			String path = Identifier.parse(baseId).getPath();
			boolean burns = base.defaultBlockState().ignitedByLava();
			for (boolean slab : new boolean[] {true, false}) {
				String id = path + (slab ? "_vslab" : "_quarter");
				ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
				// not full, so it hides none of the faces round it; the base's own map colour, fixed (a log's depends on its axis)
				BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(base).mapColor(base.defaultMapColor()).noOcclusion().setId(key);
				Block block = Registry.register(BuiltInRegistries.BLOCK, key, slab ? new VerticalSlabBlock(properties) : new QuarterStepBlock(properties));
				ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
				Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
				if (burns) {
					flammable.add(block, WOOD_IGNITE, WOOD_BURN);
				}
				(slab ? VERTICAL_SLABS : QUARTER_STEPS).add(block);
			}
		}
		MixedSlabs.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			VERTICAL_SLABS.forEach(output::accept);
			QUARTER_STEPS.forEach(output::accept);
		});
	}
}
