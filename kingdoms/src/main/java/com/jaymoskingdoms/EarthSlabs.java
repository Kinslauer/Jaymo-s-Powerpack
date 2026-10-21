package com.jaymoskingdoms;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (batch 4) Half steps of earth: the dirt slab, the grass slab (tinted as grass blocks are; covered over it goes back to
 * dirt, and it creeps onto lit dirt slabs beside it) and the dirt path slab (a shovel on a dirt or grass slab; like the
 * path a pixel low, and dirt again once something solid is set on it). {@link Roads} lays path slabs where a King's Road
 * steps up or down a block, and they're road underfoot ({@link RoadPace}).
 */
public final class EarthSlabs {
	/** Grass slabs creep onto dirt slabs with at least this much light above them. */
	static final int GRASS_LIGHT = 9;

	public static Block DIRT_SLAB, GRASS_SLAB, DIRT_PATH_SLAB;
	public static Item DIRT_SLAB_ITEM, GRASS_SLAB_ITEM, DIRT_PATH_SLAB_ITEM;

	private EarthSlabs() {
	}

	static void register() {
		DIRT_SLAB = block("dirt_slab", DirtSlab::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
		GRASS_SLAB = block("grass_slab", GrassSlab::new, BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).randomTicks()
			.sound(SoundType.GRASS));
		DIRT_PATH_SLAB = block("dirt_path_slab", PathSlab::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.65F)
			.sound(SoundType.GRASS));
		DIRT_SLAB_ITEM = item("dirt_slab", DIRT_SLAB);
		GRASS_SLAB_ITEM = item("grass_slab", GRASS_SLAB);
		DIRT_PATH_SLAB_ITEM = item("dirt_path_slab", DIRT_PATH_SLAB);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.DIRT_PATH, GRASS_SLAB_ITEM);
			output.insertAfter(GRASS_SLAB_ITEM, DIRT_SLAB_ITEM);
			output.insertAfter(DIRT_SLAB_ITEM, DIRT_PATH_SLAB_ITEM);
		});
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** {@code state}'s half (and water) on another earth slab. */
	static BlockState as(Block slab, BlockState state) {
		return slab.defaultBlockState().setValue(SlabBlock.TYPE, state.getValue(SlabBlock.TYPE))
			.setValue(SlabBlock.WATERLOGGED, state.getValue(SlabBlock.WATERLOGGED));
	}

	/** A path slab with this much above it would be dirt again (as a dirt path would). */
	static boolean covered(BlockState above) {
		return above.isSolid() && !(above.getBlock() instanceof FenceGateBlock);
	}

	/** A shovel on a dirt or grass slab with air over it makes it a path slab. */
	static InteractionResult flatten(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!stack.is(ItemTags.SHOVELS) || !level.getBlockState(pos.above()).isAir()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		if (!level.isClientSide()) {
			level.setBlock(pos, as(DIRT_PATH_SLAB, state), Block.UPDATE_ALL_IMMEDIATE);
			stack.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	/** Dirt, cut in half. */
	static class DirtSlab extends SlabBlock {
		DirtSlab(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			return flatten(stack, state, level, pos, player, hand);
		}
	}

	/** Grass on a half block of dirt: dies back to a dirt slab under cover, spreads to lit dirt slabs nearby. */
	static final class GrassSlab extends DirtSlab {
		GrassSlab(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (dark(level, pos)) {
				level.setBlockAndUpdate(pos, as(DIRT_SLAB, state));
				return;
			}
			BlockPos to = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
			BlockState there = level.getBlockState(to);
			if (there.is(DIRT_SLAB) && !dark(level, to)) {
				level.setBlockAndUpdate(to, as(GRASS_SLAB, there));
			}
		}

		/** Covered over (a solid block on it, or water in it), or too dark for grass. */
		private static boolean dark(ServerLevel level, BlockPos pos) {
			BlockState above = level.getBlockState(pos.above());
			return above.isSolidRender() || level.getBlockState(pos).getValue(WATERLOGGED) || level.getMaxLocalRawBrightness(pos.above()) < GRASS_LIGHT
				&& level.getMaxLocalRawBrightness(pos) < GRASS_LIGHT;
		}
	}

	/** A half step of dirt path, a pixel low as the path is. */
	static final class PathSlab extends SlabBlock {
		private static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 7, 16), TOP = Block.box(0, 8, 0, 16, 15, 16),
			DOUBLE = Block.box(0, 0, 0, 16, 15, 16);

		PathSlab(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(TYPE)) {
				case BOTTOM -> BOTTOM;
				case TOP -> TOP;
				case DOUBLE -> DOUBLE;
			};
		}

		@Override
		protected boolean useShapeForLightOcclusion(BlockState state) {
			return true;
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			BlockState state = super.getStateForPlacement(context);
			return state != null && covered(context.getLevel().getBlockState(context.getClickedPos().above())) ? as(DIRT_SLAB, state) : state;
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			if (direction == Direction.UP && covered(neighbour)) {
				ticks.scheduleTick(pos, this, 1);
			}
			return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (covered(level.getBlockState(pos.above()))) {
				level.setBlockAndUpdate(pos, as(DIRT_SLAB, state));
			}
		}
	}

	/** The half step a road surface of {@code top} gets where it climbs a block: the path slab, or the stone's own slab. */
	static @Nullable BlockState stepFor(BlockState top) {
		Block b = top.getBlock();
		Block slab = b == Blocks.DIRT_PATH || b == Blocks.COARSE_DIRT || b == Blocks.GRAVEL ? DIRT_PATH_SLAB
			: b == Blocks.COBBLESTONE ? Blocks.COBBLESTONE_SLAB
			: b == Blocks.MOSSY_COBBLESTONE ? Blocks.MOSSY_COBBLESTONE_SLAB
			: b == Blocks.STONE_BRICKS ? Blocks.STONE_BRICK_SLAB
			: b == Blocks.ANDESITE ? Blocks.ANDESITE_SLAB
			: b == Blocks.SMOOTH_SANDSTONE ? Blocks.SMOOTH_SANDSTONE_SLAB
			: b == Blocks.SMOOTH_RED_SANDSTONE ? Blocks.SMOOTH_RED_SANDSTONE_SLAB
			: b == Blocks.CUT_SANDSTONE ? Blocks.CUT_SANDSTONE_SLAB
			: b == Blocks.CUT_RED_SANDSTONE ? Blocks.CUT_RED_SANDSTONE_SLAB
			: b == Blocks.SANDSTONE ? Blocks.SANDSTONE_SLAB
			: b == Blocks.RED_SANDSTONE ? Blocks.RED_SANDSTONE_SLAB
			: b == Blocks.PACKED_MUD ? Blocks.MUD_BRICK_SLAB
			: null;
		return slab == null ? null : slab.defaultBlockState();
	}
}
