package com.jaymospowers.alchemy.brewing;

import com.jaymospowers.alchemy.cauldron.CauldronBrewing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) The Brewer's Alembic: copper and glass, set over a fire. Two or three bottles of one brew distil into one
 * fewer bottle, a grade better. Not the essence {@code alembic} of the crucible.
 */
public class BrewersAlembicBlock extends Block implements EntityBlock {
	/** How many bottles stand in the rack. */
	public static final IntegerProperty BOTTLES = IntegerProperty.create("bottles", 0, 3);
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 8, 13), Block.box(6, 8, 6, 10, 13, 10), Block.box(10, 10, 7, 15, 12, 9));

	public BrewersAlembicBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BOTTLES, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BOTTLES);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BrewersAlembicEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Brewing.BREWERS_ALEMBIC_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<BrewersAlembicEntity>) BrewersAlembicEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		return interact(stack, level, pos, player, hand);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return interact(ItemStack.EMPTY, level, pos, player, InteractionHand.MAIN_HAND);
	}

	private static InteractionResult interact(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof BrewersAlembicEntity alembic)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			alembic.use((ServerPlayer) player, stack, hand);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(BOTTLES) >= BrewersAlembicEntity.MIN_BOTTLES && random.nextInt(5) == 0 && CauldronBrewing.heated(level, pos)) {
			level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.8, pos.getY() + 0.85, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
		}
	}
}
