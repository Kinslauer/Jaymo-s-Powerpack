package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.19.86) Andy's Plant Fibers pick: "a grass bed". A bedroll of woven grass on the ground, the first bed before wool:
 * two blocks long and only {@link #HEIGHT} sixteenths high. It is a vanilla {@link BedBlock} in everything but looks, as
 * Kingdoms' infirmary cot is: it sleeps, skips the night and sets the spawn like any bed, so the sleep code needs nothing
 * new. No colour and no block entity (drawn by its block model, not the bed renderer). Made of plant fibre and plant
 * string (a data recipe); it burns.
 */
public final class GrassBed extends BedBlock {
	/** How high the bedroll lies (sixteenths of a block); vanilla beds stand 9. */
	static final int HEIGHT = 3;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

	public static Block GRASS_BED;
	public static Item GRASS_BED_ITEM;

	public GrassBed(BlockBehaviour.Properties properties) {
		super(DyeColor.GREEN, properties);
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("grass_bed"));
		GRASS_BED = Registry.register(BuiltInRegistries.BLOCK, key, new GrassBed(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.PLANT).sound(SoundType.GRASS).strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("grass_bed"));
		GRASS_BED_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(GRASS_BED, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.STRAW_BED, GRASS_BED_ITEM));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Drawn from its block model, not the bed renderer. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
