package com.jaymospowers.rough;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * A sleeping bag (Andy's TFC Hardrock picks, Comforts' idea, our own block): a wool-lined leather bag you lay on the
 * ground on the road. It sleeps, rests ({@code BedRest} in Kingdoms) and skips the night like a bed, but never moves your
 * spawn, so camping out leaves home where it is. It breaks at a touch, so you pick it up again in the morning. Drawn by
 * its block model, as the grass bed is.
 */
public final class SleepingBag extends BedBlock {
	/** How high the bag lies (sixteenths of a block); vanilla beds stand 9. */
	static final int HEIGHT = 3;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

	public static Block SLEEPING_BAG;
	public static Item SLEEPING_BAG_ITEM;

	public SleepingBag(BlockBehaviour.Properties properties) {
		super(DyeColor.BROWN, properties);
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("sleeping_bag"));
		SLEEPING_BAG = Registry.register(BuiltInRegistries.BLOCK, key, new SleepingBag(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.COLOR_BROWN).sound(SoundType.WOOL).strength(0.1F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("sleeping_bag"));
		SLEEPING_BAG_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(SLEEPING_BAG, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.STRAW_BED, SLEEPING_BAG_ITEM));
	}

	/** A bed's rule for this world, but it never sets the spawn (vanilla sleep and Kingdoms' bed rest both read it). */
	@Override
	public BedRule getBedRule(Level level, BlockPos pos) {
		BedRule rule = super.getBedRule(level, pos);
		return new BedRule(rule.canSleep(), BedRule.Rule.NEVER, rule.destroyOnUse(), rule.destroyOnLeave(), rule.errorMessage());
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
