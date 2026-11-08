package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The cage trap: a wire cage on a board with a drop door. Bait it; a rat that goes in for the bait is shut in alive.
 * Break the cage with a rat in it and you get the caged rat to carry off ({@link CagedRatItem}); set it down somewhere
 * far away and right-click it to open the door: the rat bolts and doesn't come back.
 */
public class CageTrapBlock extends TrapBlock {
	public static final BooleanProperty CAUGHT = BooleanProperty.create("caught");
	/** The caught rat's coat ({@link Rat#coat}). */
	public static final IntegerProperty COAT = IntegerProperty.create("coat", 0, Rat.COATS - 1);
	private static final VoxelShape NS = Block.box(3, 0, 1, 13, 9, 15);
	private static final VoxelShape EW = Block.box(1, 0, 3, 15, 9, 13);
	/** Only the board stops you: a rat (or your foot) goes in over it. */
	private static final VoxelShape FLOOR_NS = Block.box(3, 0, 1, 13, 1, 15);
	private static final VoxelShape FLOOR_EW = Block.box(1, 0, 3, 15, 1, 13);

	public CageTrapBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(CAUGHT, false)
			.setValue(COAT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING, CAUGHT, COAT);
	}

	private static boolean alongZ(BlockState state) {
		return state.getValue(HorizontalDirectionalBlock.FACING).getAxis() == Direction.Axis.Z;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return alongZ(state) ? NS : EW;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return alongZ(state) ? FLOOR_NS : FLOOR_EW;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CageTrapEntity(pos, state);
	}

	@Override
	protected boolean open(BlockState state) {
		return !state.getValue(CAUGHT);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(CAUGHT)) {
			if (level instanceof ServerLevel server) {
				release(server, pos, state);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/** Opens the door: the rat bolts out the front and away for good. */
	private static void release(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, state.setValue(CAUGHT, false));
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.4F);
		Rat rat = Rats.RAT.create(level, EntitySpawnReason.TRIGGERED);
		if (rat == null) {
			return;
		}
		Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
		Vec3 out = Vec3.atBottomCenterOf(pos).add(front.getStepX() * 0.6, 0.05, front.getStepZ() * 0.6);
		rat.snapTo(out.x, out.y, out.z, front.toYRot(), 0.0F);
		rat.setCoat(state.getValue(COAT));
		rat.release(pos);
		level.addFreshEntity(rat);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (!(level instanceof ServerLevel server) || state.getValue(CAUGHT) || !(entity instanceof Rat rat) || !rat.isAlive()
			|| !(level.getBlockEntity(pos) instanceof CageTrapEntity cage) || !cage.baited()) {
			return;
		}
		cage.eatBait();
		level.setBlockAndUpdate(pos, state.setValue(CAUGHT, true).setValue(COAT, rat.coat()));
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.2F);
		level.playSound(null, pos, Rats.RAT_HURT, SoundSource.NEUTRAL, 0.6F, 1.2F);
		scatter(server, pos, rat);
		// it's in the cage now (drawn there by the cage's renderer), and dropping what it carried
		if (!rat.getMainHandItem().isEmpty()) {
			Block.popResource(level, pos, rat.getMainHandItem().copy());
		}
		rat.discard();
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(CAUGHT) ? 15 : 0;
	}
}
