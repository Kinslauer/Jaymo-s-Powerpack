package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blossom land's building blocks (docs/BlossomLands-Plan.md, patch 3; the ideas are from Andy's Sengoku Jidai picks,
 * the blocks and their art are ours): shoji panels (paper in a lattice, joining like panes), tatami, lime plaster, red
 * and black lacquered planks, the paper lantern (standing or hung), the one-block stone lantern, thatch for anyone's
 * roof (block, slab, stairs), and the ward rope ({@link Wards}); what stands inside a house is in
 * {@link BlossomFurnishings}. Models, textures, recipes, loot and names come from
 * {@code kingdoms/tools/gen_blossom_blocks.py}.
 */
public final class BlossomBlocks {
	/** Light from the paper lantern and the stone lantern. */
	static final int PAPER_LIGHT = 14, STONE_LIGHT = 13;
	/** Burn and spread odds: paper and rope like wool, thatch like hay, lacquered planks like planks. */
	static final int PAPER_BURN = 30, PAPER_SPREAD = 60, THATCH_BURN = 60, THATCH_SPREAD = 20, PLANK_BURN = 5, PLANK_SPREAD = 20;

	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static Block SHOJI, TATAMI, PLASTER, RED_LACQUER, BLACK_LACQUER, PAPER_LANTERN, STONE_LANTERN, THATCH, THATCH_SLAB, THATCH_STAIRS, WARD_ROPE;
	public static ResourceKey<PoiType> WARD = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JaymosKingdoms.id("ward_rope"));

	static final List<Item> ITEMS = new ArrayList<>();

	private BlossomBlocks() {
	}

	public static void register() {
		SHOJI = block("shoji", new IronBarsBlock(props("shoji").mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.WOOD).ignitedByLava().noOcclusion()),
			PAPER_BURN, PAPER_SPREAD);
		TATAMI = block("tatami", new RotatedPillarBlock(props("tatami").mapColor(MapColor.PLANT).strength(0.6F).sound(SoundType.GRASS).ignitedByLava()),
			THATCH_BURN, THATCH_SPREAD);
		PLASTER = block("plaster", new Block(props("plaster").mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops().strength(1.25F, 4.0F)
			.sound(SoundType.CALCITE)), 0, 0);
		RED_LACQUER = block("red_lacquered_planks", new Block(copy("red_lacquered_planks", Blocks.CHERRY_PLANKS).mapColor(MapColor.COLOR_RED)),
			PLANK_BURN, PLANK_SPREAD);
		BLACK_LACQUER = block("black_lacquered_planks", new Block(copy("black_lacquered_planks", Blocks.CHERRY_PLANKS).mapColor(MapColor.COLOR_BLACK)),
			PLANK_BURN, PLANK_SPREAD);
		PAPER_LANTERN = block("paper_lantern", new LanternBlock(props("paper_lantern").mapColor(MapColor.COLOR_ORANGE).strength(0.3F)
			.sound(SoundType.WOOL).lightLevel(state -> PAPER_LIGHT).noOcclusion().ignitedByLava()), PAPER_BURN, PAPER_SPREAD);
		STONE_LANTERN = block("stone_lantern", new Shaped(props("stone_lantern").mapColor(MapColor.STONE).requiresCorrectToolForDrops()
			.strength(1.5F, 6.0F).lightLevel(state -> STONE_LIGHT).noOcclusion(), Shapes.or(Block.box(3, 0, 3, 13, 2, 13),
			Block.box(6, 2, 6, 10, 7, 10), Block.box(4, 7, 4, 12, 12, 12), Block.box(2, 12, 2, 14, 14, 14), Block.box(6, 14, 6, 10, 16, 10))), 0, 0);
		THATCH = block("thatch", new Block(thatch("thatch")), THATCH_BURN, THATCH_SPREAD);
		THATCH_SLAB = block("thatch_slab", new SlabBlock(thatch("thatch_slab")), THATCH_BURN, THATCH_SPREAD);
		THATCH_STAIRS = block("thatch_stairs", new Stairs(THATCH.defaultBlockState(), thatch("thatch_stairs")), THATCH_BURN, THATCH_SPREAD);
		WARD_ROPE = block("ward_rope", new IronBarsBlock(props("ward_rope").mapColor(MapColor.SAND).strength(0.2F).sound(SoundType.WOOL)
			.ignitedByLava().noOcclusion().noCollision()), PAPER_BURN, PAPER_SPREAD);
		PoiHelper.register(JaymosKingdoms.id("ward_rope"), 0, 1, WARD_ROPE);
		BlossomFurnishings.register();
		BlossomDress.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ITEMS.forEach(output::accept));
	}

	private static BlockBehaviour.Properties thatch(String name) {
		return props(name).mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.GRASS).ignitedByLava();
	}

	/** New properties with the block's id (which 26.3 wants before the block is made). */
	static BlockBehaviour.Properties props(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)));
	}

	static BlockBehaviour.Properties copy(String name, Block of) {
		return BlockBehaviour.Properties.ofFullCopy(of).setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)));
	}

	static Block block(String name, Block block, int burn, int spread) {
		return block(name, block, burn, spread, BlockItem::new);
	}

	/** Registers a block and its item ({@code item} makes it: a door's item sets both halves). */
	static Block block(String name, Block block, int burn, int spread, BiFunction<Block, Item.Properties, Item> item) {
		Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)), block);
		BLOCKS.put(name, block);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		ITEMS.add(Registry.register(BuiltInRegistries.ITEM, key, item.apply(block, new Item.Properties().setId(key).useBlockDescriptionPrefix())));
		if (burn > 0) {
			FlammableBlockRegistry.getDefaultInstance().add(block, burn, spread);
		}
		return block;
	}

	/** A block whose outline and collision are its model's own (a small model in a full cube hides its neighbours' faces). */
	static final class Shaped extends Block {
		private final VoxelShape shape;

		Shaped(BlockBehaviour.Properties properties, VoxelShape shape) {
			super(properties);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return this.shape;
		}
	}

	/** (vanilla keeps the stairs' constructor to itself) */
	static final class Stairs extends StairBlock {
		Stairs(BlockState base, BlockBehaviour.Properties properties) {
			super(base, properties);
		}
	}
}
