package com.jaymoskitchen.delight;

import com.jaymoskitchen.cooking.Quality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A feast set down ({@link Feast}): right-click it for a serving (with a bowl in hand for the bowl-served ones) until
 * it's gone; what's left on the tray clears away with one more click. It has to stand on something solid.
 */
public class FeastBlock extends HorizontalDirectionalBlock {
	/** Servings left; at 0 only the leftovers are on the tray. */
	public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, Feast.SERVINGS);
	/** How well it was cooked (a Quality's ordinal): every serving comes out that way. */
	public static final IntegerProperty QUALITY = IntegerProperty.create("quality", 0, Quality.values().length - 1);

	private static final VoxelShape TRAY = Block.box(1, 0, 1, 15, 2, 15);
	private static final VoxelShape FULL = Block.box(1, 0, 1, 15, 10, 15);
	private static final VoxelShape PUMPKIN = Block.box(2, 0, 2, 14, 12, 14);

	private final Feast feast;

	public FeastBlock(Properties properties, Feast feast) {
		super(properties);
		this.feast = feast;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SERVINGS, Feast.SERVINGS)
			.setValue(QUALITY, Quality.GOOD.ordinal()));
	}

	public Feast feast() {
		return feast;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SERVINGS, QUALITY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (feast == Feast.STUFFED_PUMPKIN) {
			return PUMPKIN;
		}
		return state.getValue(SERVINGS) == 0 ? TRAY : FULL;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int servings = state.getValue(SERVINGS);
		if (servings == 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean bowl = feast.needsBowl();
		if (bowl && !held.is(Items.BOWL)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.feast.bowl"));
			}
			return InteractionResult.SUCCESS;
		}
		if (!bowl && !held.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		serve(state, level, pos, player, bowl ? held : null);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int servings = state.getValue(SERVINGS);
		if (servings == 0) {
			// clear the tray away
			if (!level.isClientSide()) {
				level.destroyBlock(pos, true, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (feast.needsBowl()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.feast.bowl"));
			}
			return InteractionResult.SUCCESS;
		}
		serve(state, level, pos, player, null);
		return InteractionResult.SUCCESS;
	}

	private void serve(BlockState state, Level level, BlockPos pos, Player player, ItemStack bowl) {
		if (level.isClientSide()) {
			return;
		}
		Quality quality = Quality.values()[state.getValue(QUALITY)];
		ItemStack serving = feast.serving(quality);
		if (bowl != null) {
			bowl.consume(1, player);
		}
		if (!player.getInventory().add(serving)) {
			Block.popResource(level, pos.above(), serving);
		}
		int left = state.getValue(SERVINGS) - 1;
		if (left == 0 && !feast.leftovers()) {
			level.removeBlock(pos, false);
		} else {
			level.setBlock(pos, state.setValue(SERVINGS, left), Block.UPDATE_ALL);
		}
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
	}
}
