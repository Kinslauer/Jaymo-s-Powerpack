package com.jaymospowers.costume;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Wardrobe (Andy: "a closet or wardrobe block. two blocks tall, like the fridge, and it should allow you to use the
 * /mca editor window, costumes and also the YDM weapon master config. combined into the same screen"). Two blocks tall,
 * set like a door: the lower half is the wardrobe proper (its item, its drop), the upper half comes and goes with it.
 * Opening either half opens the wardrobe's screen on the client ({@link Open}): your looks (MCA's editor), the Costume
 * Table's designer (this counts as a table for sewing, {@link CostumeNet}) and how your weapons hang on you (YDM's
 * Weapon Master), as tabs of one screen.
 */
public class WardrobeBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	// (each half facing north, the doors at the front: a plinth and the body below, the body and its crown above; the
	// model's boxes are these, so nothing beside it is culled away)
	private static final VoxelShape[] LOWER = turned(new double[][] {{0, 0, 2, 16, 2, 16}, {1, 2, 3, 15, 16, 16}});
	private static final VoxelShape[] UPPER = turned(new double[][] {{1, 0, 3, 15, 14, 16}, {0, 14, 2, 16, 16, 16}});

	public WardrobeBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
	}

	/** {@code boxes} (x0, y0, z0, x1, y1, z1, facing north) turned to each facing, by its 2D data value. */
	private static VoxelShape[] turned(double[][] boxes) {
		VoxelShape[] shapes = new VoxelShape[4];
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] b : boxes) {
				double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
				shape = Shapes.or(shape, switch (facing) {
					case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
					case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
					case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
					default -> Block.box(x0, b[1], z0, x1, b[4], z1);
				});
			}
			shapes[facing.get2DDataValue()] = shape;
		}
		return shapes;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int i = state.getValue(FACING).get2DDataValue();
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER[i] : UPPER[i];
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server) {
			BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
			level.playSound(null, lower, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
			ServerPlayNetworking.send(server, new Open(lower));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null;
		}
		// its doors face the one setting it
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
		}
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
			&& !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
			// the other half is gone: so is this one
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			// broken at the top: the lower half goes too, and drops the wardrobe (once) unless in creative
			BlockPos below = pos.below();
			BlockState lower = level.getBlockState(below);
			if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
				if (player.isCreative()) {
					level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				} else {
					level.destroyBlock(below, true, player);
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Server → client: open the wardrobe's screen, for the wardrobe whose lower half is at {@code pos}. */
	public record Open(BlockPos pos) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(com.jaymospowers.JaymosPowers.id("wardrobe_open"));
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Open> CODEC =
			BlockPos.STREAM_CODEC.map(Open::new, Open::pos);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}
}
