package com.jaymoskingdoms.carpentry;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Carpentry (after Better With Mods' "mini blocks" and furniture): timber trim cut from vanilla's woods and a few
 * stones (siding, moulding, corner), wooden benches and tables, and wattle, grate and slat panels that join like
 * iron bars. Every block is registered here by loops over the materials, the way vanilla registers its wood sets; the
 * models, recipes and loot come from {@code kingdoms/tools/gen_carpentry.py}.
 */
public final class Carpentry {
	/** A material the trim is cut from: its id prefix and the block it copies (strength, sound, tool). */
	public record Material(String name, Block base, boolean wood) {
	}

	public static final List<Material> MATERIALS = List.of(
		new Material("oak", Blocks.OAK_PLANKS, true),
		new Material("spruce", Blocks.SPRUCE_PLANKS, true),
		new Material("birch", Blocks.BIRCH_PLANKS, true),
		new Material("jungle", Blocks.JUNGLE_PLANKS, true),
		new Material("acacia", Blocks.ACACIA_PLANKS, true),
		new Material("dark_oak", Blocks.DARK_OAK_PLANKS, true),
		new Material("mangrove", Blocks.MANGROVE_PLANKS, true),
		new Material("cherry", Blocks.CHERRY_PLANKS, true),
		new Material("pale_oak", Blocks.PALE_OAK_PLANKS, true),
		new Material("bamboo", Blocks.BAMBOO_PLANKS, true),
		new Material("stone", Blocks.STONE, false),
		new Material("cobblestone", Blocks.COBBLESTONE, false),
		new Material("stone_brick", Blocks.STONE_BRICKS, false),
		new Material("brick", Blocks.BRICKS, false));

	/** Wooden trim, benches and tables catch fire like planks (burn odds, spread odds). */
	static final int WOOD_BURN = 5;
	static final int WOOD_SPREAD = 20;
	/** The panels catch like fences of twigs. */
	static final int PANEL_BURN = 5;
	static final int PANEL_SPREAD = 20;
	/** The panels' hardness (a fence's is 2). */
	static final float PANEL_STRENGTH = 1.0F;

	/** Every block made here, by id, in the order the creative tab shows them. */
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static Block WATTLE;
	public static Block GRATE;
	public static Block SLATS;

	private Carpentry() {
	}

	public static void register() {
		List<Item> items = new ArrayList<>();
		for (Material m : MATERIALS) {
			for (String shape : new String[] {"siding", "moulding", "corner"}) {
				Function<BlockBehaviour.Properties, Block> make = switch (shape) {
					case "siding" -> SidingBlock::new;
					case "moulding" -> MouldingBlock::new;
					default -> CornerBlock::new;
				};
				items.add(block(m.name() + "_" + shape, make.apply(props(m.name() + "_" + shape, BlockBehaviour.Properties.ofFullCopy(m.base()))), m.wood()));
			}
		}
		for (Material m : MATERIALS) {
			if (!m.wood()) {
				continue;
			}
			for (boolean bench : new boolean[] {true, false}) {
				String name = m.name() + (bench ? "_bench" : "_table");
				items.add(block(name, new FurnitureBlock(props(name, BlockBehaviour.Properties.ofFullCopy(m.base())).noOcclusion(), bench), true));
			}
		}
		WATTLE = panel("wattle", items);
		GRATE = panel("grate", items);
		SLATS = panel("slats", items);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> items.forEach(output::accept));
		BenchSeats.register();
	}

	private static Block panel(String name, List<Item> items) {
		BlockBehaviour.Properties props = props(name, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(PANEL_STRENGTH)
			.sound(SoundType.WOOD).ignitedByLava().noOcclusion());
		Block block = new IronBarsBlock(props);
		items.add(block(name, block, false));
		FlammableBlockRegistry.getDefaultInstance().add(block, PANEL_BURN, PANEL_SPREAD);
		return block;
	}

	/** {@code properties} given the block's id (which 26.3 wants before the block is made). */
	private static BlockBehaviour.Properties props(String name, BlockBehaviour.Properties properties) {
		return properties.setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)));
	}

	/** Registers {@code block} and its item; wooden ones burn. */
	private static Item block(String name, Block block, boolean flammable) {
		Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)), block);
		BLOCKS.put(name, block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		if (flammable) {
			FlammableBlockRegistry.getDefaultInstance().add(block, WOOD_BURN, WOOD_SPREAD);
		}
		return item;
	}
}
