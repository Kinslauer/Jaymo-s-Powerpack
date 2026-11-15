package com.jaymospowers.tinkering.coke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The brick blast furnace's mouth ({@link BlastFurnaceEntity}): set it in the middle of one side of the tower, a block up,
 * facing out. It glows while it works. Right-click with iron ingots or coal coke to load it, with an empty hand to take
 * the steel and then the ash (sneaking: what's still waiting).
 */
public class BlastFurnaceBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public BlastFurnaceBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BlastFurnaceEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != CokeAndSteel.BLAST_FURNACE_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<BlastFurnaceEntity>) BlastFurnaceEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof BlastFurnaceEntity furnace) || stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (furnace.accepts(stack)) {
			if (!level.isClientSide()) {
				furnace.insert(stack, player.hasInfiniteMaterials());
				level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof BlastFurnaceEntity furnace)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken = furnace.take(player.isShiftKeyDown());
			if (!taken.isEmpty()) {
				player.getInventory().placeItemBackInInventory(taken, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Flame and smoke at the mouth and the roar of a hot fire while it works. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		if (random.nextDouble() < 0.1) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F, false);
		}
		Direction out = state.getValue(FACING);
		double x = pos.getX() + 0.5 + out.getStepX() * 0.52, z = pos.getZ() + 0.5 + out.getStepZ() * 0.52;
		level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.8, z, 0.0, 0.02, 0.0);
		level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.3, z, 0.0, 0.0, 0.0);
	}
}
