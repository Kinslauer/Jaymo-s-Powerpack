package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A beetle box: a ventilated crate with a colony of flesh beetles in it, the slow way to clean bones ({@link Skeletons},
 * {@link BeetleBoxEntity}). Use frame bones on it to put them in, an empty hand to take them out; rotten flesh starts a
 * colony again in a box that has stood empty too long.
 */
public class BeetleBoxBlock extends BaseEntityBlock {
	public BeetleBoxBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BeetleBoxEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, Skeletons.BEETLE_BOX_ENTITY, BeetleBoxEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof BeetleBoxEntity box) {
			InteractionResult result = box.use(player, held);
			return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof BeetleBoxEntity box) {
			return box.takeOut(player);
		}
		return InteractionResult.PASS;
	}

	/** At work, the beetles show as specks round the lid's vents. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof BeetleBoxEntity box && box.working() && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0, 0);
		}
	}
}
