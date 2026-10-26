package com.jaymospowers.alchemy.infusion;

import com.jaymospowers.alchemy.Alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The infusion matrix: a runed stone that hangs in the air two blocks over the centre pedestal. An essence phial
 * (only the trigger; it isn't used up) starts the infusion; an empty hand asks how it is going. The work is
 * {@link MatrixEntity}'s.
 */
public class MatrixBlock extends Block implements EntityBlock {
	/** Light it gives. */
	public static final int LIGHT = 7;
	/** The idle shimmer's colour (calm blue) and its size. */
	public static final int CALM_COLOUR = 0x4F8CFF;
	private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

	public MatrixBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MatrixEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Infusion.MATRIX_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<MatrixEntity>) MatrixEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (stack.is(Alchemy.ESSENCE_PHIAL)) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				Infusion.start(server, pos, sp);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatrixEntity stone) {
			player.sendOverlayMessage(stone.mood());
		}
		return InteractionResult.SUCCESS;
	}

	/** A few blue motes drifting off the stone, always. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0) {
			level.addParticle(new DustParticleOptions(CALM_COLOUR, 0.6F), pos.getX() + 0.3 + random.nextDouble() * 0.4,
				pos.getY() + 0.3 + random.nextDouble() * 0.4, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
		}
	}
}
