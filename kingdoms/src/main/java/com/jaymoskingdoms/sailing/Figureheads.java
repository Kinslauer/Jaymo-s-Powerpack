package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The carvings at the prows of ships (a medieval deco pick): a figurehead block in each of the woods, and the carving each
 * ship wears. A realm's ships (navy, merchant, fishing) wear a carving the realm's colours choose; a whaleship wears a
 * whale; a pirate ship (dark oak, as the pirates build) a carving of its own. A ship is rebuilt from her rig and look
 * alone, so the choice is made from those and is the same every time. Set by {@link Fleet} with her other fittings.
 */
public final class Figureheads {
	/** The woods, by the planks a ship is built of (the values are the woods' names). */
	private static final Map<Block, String> WOODS = new LinkedHashMap<>();
	/** What a realm's ships carve, and what pirates do. */
	private static final FigureheadBlock.Carving[] REALM = {FigureheadBlock.Carving.LION, FigureheadBlock.Carving.GULL, FigureheadBlock.Carving.STAG};
	private static final FigureheadBlock.Carving[] PIRATE = {FigureheadBlock.Carving.SERPENT, FigureheadBlock.Carving.MAIDEN};
	/** How readily a figurehead burns (burn odds, spread odds), as planks do. */
	private static final int BURN = 5, SPREAD = 20;
	/** The figurehead of each wood, by its name. */
	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

	static {
		WOODS.put(Blocks.OAK_PLANKS, "oak");
		WOODS.put(Blocks.SPRUCE_PLANKS, "spruce");
		WOODS.put(Blocks.BIRCH_PLANKS, "birch");
		WOODS.put(Blocks.JUNGLE_PLANKS, "jungle");
		WOODS.put(Blocks.ACACIA_PLANKS, "acacia");
		WOODS.put(Blocks.DARK_OAK_PLANKS, "dark_oak");
		WOODS.put(Blocks.MANGROVE_PLANKS, "mangrove");
		WOODS.put(Blocks.CHERRY_PLANKS, "cherry");
		WOODS.put(Blocks.PALE_OAK_PLANKS, "pale_oak");
		WOODS.put(Blocks.BAMBOO_PLANKS, "bamboo");
	}

	private Figureheads() {
	}

	public static void register() {
		List<Item> items = new ArrayList<>();
		for (Map.Entry<Block, String> wood : WOODS.entrySet()) {
			String name = wood.getValue() + "_figurehead";
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
			Block block = Registry.register(BuiltInRegistries.BLOCK, key, new FigureheadBlock(BlockBehaviour.Properties.ofFullCopy(wood.getKey())
				.mapColor(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion().setId(key)));
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
			items.add(Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
			FlammableBlockRegistry.getDefaultInstance().add(block, BURN, SPREAD);
			BLOCKS.put(wood.getValue(), block);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> items.forEach(output::accept));
	}

	/** {@code parts} with the ship's figurehead at her prow, if she has a stem to set it on. */
	static List<Fleet.Part> fit(List<Fleet.Part> parts, Fleet.Rig rig, Fleet.Look look) {
		Block block = BLOCKS.get(WOODS.getOrDefault(look.planks(), "oak"));
		if (block == null) {
			return parts;
		}
		// her stem: the foremost hull block on her centreline, and the top of it
		int stem = Integer.MIN_VALUE;
		for (Fleet.Part p : parts) {
			if (p.at().getX() == 0 && hull(p.state(), look)) {
				stem = Math.max(stem, p.at().getZ());
			}
		}
		if (stem == Integer.MIN_VALUE) {
			return parts;
		}
		int top = Integer.MIN_VALUE;
		for (Fleet.Part p : parts) {
			if (p.at().getX() == 0 && p.at().getZ() == stem && hull(p.state(), look)) {
				top = Math.max(top, p.at().getY());
			}
		}
		BlockPos at = new BlockPos(0, top, stem + 1);
		for (Fleet.Part p : parts) {
			if (p.at().equals(at)) {
				return parts;
			}
		}
		List<Fleet.Part> out = new ArrayList<>(parts);
		out.add(new Fleet.Part(at, block.defaultBlockState().setValue(FigureheadBlock.FACING, Direction.SOUTH).setValue(FigureheadBlock.CARVING, carving(rig, look))));
		return out;
	}

	private static boolean hull(BlockState state, Fleet.Look look) {
		return state.is(look.planks()) || state.is(look.rail());
	}

	/** What a ship of this rig and look carries at her prow. */
	static FigureheadBlock.Carving carving(Fleet.Rig rig, Fleet.Look look) {
		if (rig == Fleet.Rig.WHALESHIP) {
			return FigureheadBlock.Carving.WHALE;
		}
		// the realm's colours (her flag's cloth) choose: every ship of a realm wears the same
		int colour = Math.floorMod(BuiltInRegistries.BLOCK.getKey(look.flag()).hashCode(), 1 << 20);
		FigureheadBlock.Carving[] from = look.planks() == Blocks.DARK_OAK_PLANKS ? PIRATE : REALM;
		return from[colour % from.length];
	}
}
