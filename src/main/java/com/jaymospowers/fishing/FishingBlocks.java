package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The angler's blocks (Track B's leftovers and Track D): the rod pod with its bite alarm ({@link RodPodEntity}), the crab
 * pot ({@link CrabPotEntity}), the keepnet ({@link KeepnetEntity}) and the trophy plaque ({@link TrophyPlaqueEntity}).
 * Each faces the way it was set down; the pot and the net sit in water.
 */
public final class FishingBlocks {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public static Block ROD_POD, CRAB_POT, KEEPNET, TROPHY_PLAQUE;
	public static BlockEntityType<RodPodEntity> ROD_POD_ENTITY;
	public static BlockEntityType<CrabPotEntity> CRAB_POT_ENTITY;
	public static BlockEntityType<KeepnetEntity> KEEPNET_ENTITY;
	public static BlockEntityType<TrophyPlaqueEntity> TROPHY_PLAQUE_ENTITY;

	private FishingBlocks() {
	}

	static void init() {
		ROD_POD = block("rod_pod", p -> new Kind(p, Kind.POD), BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion());
		CRAB_POT = block("crab_pot", p -> new Kind(p, Kind.POT), BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion());
		KEEPNET = block("keepnet", p -> new Kind(p, Kind.NET), BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.WOOL).noOcclusion());
		TROPHY_PLAQUE = block("trophy_plaque", p -> new Kind(p, Kind.PLAQUE),
			BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion());
		ROD_POD_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("rod_pod"),
			new BlockEntityType<>(RodPodEntity::new, Set.of(ROD_POD)));
		CRAB_POT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("crab_pot"),
			new BlockEntityType<>(CrabPotEntity::new, Set.of(CRAB_POT)));
		KEEPNET_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("keepnet"),
			new BlockEntityType<>(KeepnetEntity::new, Set.of(KEEPNET)));
		TROPHY_PLAQUE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("trophy_plaque"),
			new BlockEntityType<>(TrophyPlaqueEntity::new, Set.of(TROPHY_PLAQUE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(ROD_POD);
			output.accept(CRAB_POT);
			output.accept(KEEPNET);
			output.accept(TROPHY_PLAQUE);
		});
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** Turn a box given for a block facing north to {@code facing}. */
	static VoxelShape turn(VoxelShape north, Direction facing) {
		VoxelShape[] out = {Shapes.empty()};
		north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			double[] a = rot(x0, z0, facing), b = rot(x1, z1, facing);
			out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1, Math.max(a[1], b[1])));
		});
		return out[0];
	}

	private static double[] rot(double x, double z, Direction facing) {
		return switch (facing) {
			case EAST -> new double[] {1.0 - z, x};
			case SOUTH -> new double[] {1.0 - x, 1.0 - z};
			case WEST -> new double[] {z, 1.0 - x};
			default -> new double[] {x, z};
		};
	}

	/** One block class for all four, by kind: they differ in shape, footing and what a click does. */
	public static final class Kind extends Block implements EntityBlock, SimpleWaterloggedBlock {
		static final int POD = 0, POT = 1, NET = 2, PLAQUE = 3;
		private static final VoxelShape POD_SHAPE = Block.box(1, 0, 6, 15, 11, 12);
		private static final VoxelShape POT_SHAPE = Block.box(2, 0, 2, 14, 11, 14);
		private static final VoxelShape NET_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
		private static final VoxelShape PLAQUE_SHAPE = Block.box(1, 3, 13.5, 15, 13, 16);
		final int kind;

		Kind(BlockBehaviour.Properties properties, int kind) {
			super(properties);
			this.kind = kind;
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, WATERLOGGED);
		}

		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
			Direction facing;
			if (kind == PLAQUE) {
				// on a wall, facing out from it
				facing = context.getClickedFace();
				if (facing.getAxis() == Direction.Axis.Y) {
					return null;
				}
			} else {
				facing = context.getHorizontalDirection();
			}
			if ((kind == POT || kind == NET) && !water) {
				return null;
			}
			return defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, water);
		}

		/** Each kind's shape turned to each facing (by 2D data value), worked out once. */
		private final VoxelShape[] shapes = new VoxelShape[4];

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			Direction facing = state.getValue(FACING);
			VoxelShape shape = shapes[facing.get2DDataValue()];
			if (shape == null) {
				VoxelShape north = switch (kind) {
					case POD -> POD_SHAPE;
					case POT -> POT_SHAPE;
					case NET -> NET_SHAPE;
					default -> PLAQUE_SHAPE;
				};
				shape = turn(north, facing);
				shapes[facing.get2DDataValue()] = shape;
			}
			return shape;
		}

		@Override
		protected FluidState getFluidState(BlockState state) {
			return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
			if (state.getValue(WATERLOGGED)) {
				ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
			}
			return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
		}

		@Override
		public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return switch (kind) {
				case POD -> new RodPodEntity(pos, state);
				case POT -> new CrabPotEntity(pos, state);
				case NET -> new KeepnetEntity(pos, state);
				default -> new TrophyPlaqueEntity(pos, state);
			};
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			if (level.isClientSide()) {
				return null;
			}
			return switch (kind) {
				case POD -> (BlockEntityTicker<T>) (BlockEntityTicker<RodPodEntity>) RodPodEntity::serverTick;
				case POT -> (BlockEntityTicker<T>) (BlockEntityTicker<CrabPotEntity>) CrabPotEntity::serverTick;
				case NET -> (BlockEntityTicker<T>) (BlockEntityTicker<KeepnetEntity>) KeepnetEntity::serverTick;
				default -> null;
			};
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
			super.setPlacedBy(level, pos, state, by, stack);
			if (by instanceof Player player && level.getBlockEntity(pos) instanceof CrabPotEntity pot) {
				pot.setOwner(player.getName().getString());
			}
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
			BlockEntity be = level.getBlockEntity(pos);
			InteractionResult result = switch (be) {
				case RodPodEntity pod -> pod.useWith(stack, player, hand);
				case CrabPotEntity pot -> pot.useWith(stack, player);
				case TrophyPlaqueEntity plaque -> plaque.useWith(stack, player);
				case null, default -> InteractionResult.TRY_WITH_EMPTY_HAND;
			};
			return result;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			return switch (level.getBlockEntity(pos)) {
				case RodPodEntity pod -> pod.useEmpty(player);
				case CrabPotEntity pot -> pot.useEmpty(player);
				case KeepnetEntity net -> net.open(player);
				case TrophyPlaqueEntity plaque -> plaque.useEmpty(player);
				case null, default -> InteractionResult.PASS;
			};
		}

		@Override
		protected boolean isSignalSource(BlockState state) {
			return kind == POD;
		}

		@Override
		protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
			return kind == POD && level.getBlockEntity(pos) instanceof RodPodEntity pod && pod.ringing() ? 15 : 0;
		}
	}
}
