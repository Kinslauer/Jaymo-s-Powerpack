package com.jaymospowers.rough;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import org.jspecify.annotations.Nullable;

/**
 * A hammock (Andy's TFC Hardrock picks, Comforts' idea, our own block): a length of cloth slung between two posts. It can
 * only be hung where both ends have something to tie to, a solid block, a log, a fence or a wall, one block past each end.
 * Like the sleeping bag it sleeps, rests ({@code BedRest} in Kingdoms) and skips the night like a bed but never moves your
 * spawn; it hangs a little above the ground, so you can walk under it, and it breaks at a touch.
 */
public final class Hammock extends BedBlock {
	/** The sling hangs between 9 and 12 sixteenths up. */
	private static final VoxelShape SHAPE = Block.box(0.0, 9.0, 0.0, 16.0, 12.0, 16.0);

	public static Block HAMMOCK;
	public static Item HAMMOCK_ITEM;

	public Hammock(BlockBehaviour.Properties properties) {
		super(DyeColor.RED, properties);
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("hammock"));
		HAMMOCK = Registry.register(BuiltInRegistries.BLOCK, key, new Hammock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.COLOR_RED).sound(SoundType.WOOL).strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("hammock"));
		HAMMOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(HAMMOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(SleepingBag.SLEEPING_BAG_ITEM, HAMMOCK_ITEM));
	}

	/** Hung only between two anchors: past the foot and past the head, each a block that holds a rope. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		if (state == null) {
			return null;
		}
		Direction along = context.getHorizontalDirection();
		BlockPos foot = context.getClickedPos();
		Level level = context.getLevel();
		return anchored(level, foot.relative(along.getOpposite()), along) && anchored(level, foot.relative(along, 2), along.getOpposite()) ? state : null;
	}

	/** Whether the block at {@code pos} holds a rope tied to it from the side facing {@code toward}. */
	static boolean anchored(LevelReader level, BlockPos pos, Direction toward) {
		BlockState anchor = level.getBlockState(pos);
		return anchor.is(BlockTags.LOGS) || anchor.is(BlockTags.FENCES) || anchor.is(BlockTags.WALLS) || anchor.isFaceSturdy(level, pos, toward);
	}

	/** A bed's rule for this world, but it never sets the spawn. */
	@Override
	public BedRule getBedRule(Level level, BlockPos pos) {
		BedRule rule = super.getBedRule(level, pos);
		return new BedRule(rule.canSleep(), BedRule.Rule.NEVER, rule.destroyOnUse(), rule.destroyOnLeave(), rule.errorMessage());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
