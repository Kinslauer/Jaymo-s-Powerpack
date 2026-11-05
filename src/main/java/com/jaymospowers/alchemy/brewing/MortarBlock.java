package com.jaymospowers.alchemy.brewing;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) The Mortar and Pestle: put a brewing ingredient in, then grind it with an empty hand, in time with the bar.
 * Use it again to take the ground batch out.
 */
public class MortarBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 5, 12), Block.box(9, 4, 6, 11, 11, 8));

	/**
	 * Set by the client: a use of this mortar while its grinding game is up here judges the stroke and sends it (true
	 * when it did). The server ignores uses during a game: the strokes come as packets.
	 */
	public static BiPredicate<Player, BlockPos> clientStroke = (player, pos) -> false;

	public MortarBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MortarEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Brewing.MORTAR_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<MortarEntity>) MortarEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		return interact(stack, level, pos, player, hand, true);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return interact(ItemStack.EMPTY, level, pos, player, InteractionHand.MAIN_HAND, false);
	}

	private static InteractionResult interact(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, boolean withItem) {
		if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof MortarEntity mortar)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			if (clientStroke.test(player, pos)) {
				return InteractionResult.SUCCESS;
			}
			return !withItem || stack.is(Brewing.GRINDABLE) || !mortar.isEmpty() ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		ServerPlayer server = (ServerPlayer) player;
		if (mortar.grinding(server) || mortar.justFinished(server)) {
			return InteractionResult.SUCCESS;
		}
		mortar.use(server, stack);
		return InteractionResult.SUCCESS;
	}
}
