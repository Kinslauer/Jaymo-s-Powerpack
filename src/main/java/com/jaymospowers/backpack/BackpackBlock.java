package com.jaymospowers.backpack;

import java.util.Map;
import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A set-down backpack, standing on its bedroll with its straps to {@link #FACING}'s opposite (the way its owner faced).
 * Drawn by its block entity's renderer with the worn model. It can't be mined (only a strong explosion tears it apart,
 * see {@link Backpack#blast}): right-click opens it, sneak-right-click with an empty hand picks it up.
 */
public class BackpackBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** The bag, its pockets and bedroll, facing north (outer face to the north). */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(2.0, 0.0, 4.0, 14.0, 13.5, 12.5));

	public BackpackBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BackpackEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof BackpackEntity backpack)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			if (player.isSecondaryUseActive()) {
				ItemStack stack = backpack.pickUp();
				level.removeBlock(pos, false);
				if (Backpack.worn(server) == null && !SmokeVapour.active(server)) {
					Backpack.putOn(server, stack);
				} else {
					server.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
					Backpack.rustle(server);
				}
			} else {
				server.openMenu(backpack);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
