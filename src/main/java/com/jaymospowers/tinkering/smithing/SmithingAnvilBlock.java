package com.jaymospowers.tinkering.smithing;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) The Smithing Anvil (after Overgeared's): lay hot ingots on it, right-click with a smithing hammer to choose
 * what to forge, then strike in time with the bar. Tongs or an empty hand take what's on it.
 */
public class SmithingAnvilBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Anvil tiers: a stone anvil, an iron Smithing Anvil (more may follow). */
	public static final int STONE_TIER = 0, IRON_TIER = 1;
	/** The vanilla anvil's shape, long along X or Z. */
	private static final VoxelShape X_SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(3, 4, 4, 13, 5, 12),
		Block.box(4, 5, 6, 12, 10, 10), Block.box(0, 10, 3, 16, 16, 13));
	private static final VoxelShape Z_SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(4, 4, 3, 12, 5, 13),
		Block.box(6, 5, 4, 10, 10, 12), Block.box(3, 10, 0, 13, 16, 16));

	/**
	 * Set by the client: a hammer swung at this anvil while its game is up there judges the blow and sends it (true
	 * when it did). The server ignores swings during a game: the blows come as packets.
	 */
	public static BiPredicate<Player, BlockPos> clientBlow = (player, pos) -> false;

	public final int tier;

	public SmithingAnvilBlock(Block.Properties properties) {
		this(properties, IRON_TIER);
	}

	public SmithingAnvilBlock(Block.Properties properties, int tier) {
		super(properties);
		this.tier = tier;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Like a vanilla anvil, its long side runs across where you face. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SmithingAnvilEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Smithing.SMITHING_ANVIL_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<SmithingAnvilEntity>) SmithingAnvilEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof SmithingAnvilEntity anvil)) {
			return InteractionResult.PASS;
		}
		if (stack.getItem() instanceof SmithingHammerItem) {
			if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.SUCCESS;
			}
			if (level.isClientSide()) {
				clientBlow.test(player, pos);
			} else {
				anvil.hammer((ServerPlayer) player, stack);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Smithing.HOT_INGOT) || stack.is(Smithing.FORGING_BLANK)) {
			if (!level.isClientSide()) {
				anvil.put((ServerPlayer) player, stack);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.getItem() instanceof TongsItem) {
			return takeFrom(anvil, level, player);
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return level.getBlockEntity(pos) instanceof SmithingAnvilEntity anvil ? takeFrom(anvil, level, player) : InteractionResult.PASS;
	}

	private static InteractionResult takeFrom(SmithingAnvilEntity anvil, Level level, Player player) {
		if (anvil.work().isEmpty() && anvil.result().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken = anvil.take();
			if (!taken.isEmpty()) {
				player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
