package com.jaymoskitchen.fresh;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A pickling crock: a glazed jar for steeping food in brine or in vinegar ({@link Curing}). Right-click with a bottle of its
 * liquid to fill it ({@link Curing#PER_BOTTLE} batches a bottle), with food it takes to put the stack in (a batch of the
 * liquid is used up), with an empty hand to take the last stack out; sneak with an empty hand to see how much liquid is left.
 * What steeps for {@link Curing#CURE_TICKS} comes out cured.
 */
public class CrockBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	/** brine or vinegar. */
	public final String method;

	public CrockBlock(String method, BlockBehaviour.Properties properties) {
		super(properties);
		this.method = method;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, Curing.CROCK_ENTITY, CrockEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CrockEntity crock)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean bottle = held.is(method.equals("brine") ? Curing.BRINE : Curing.VINEGAR);
		if (!bottle && !Curing.takes(method, held)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (bottle) {
			if (!crock.fill()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.crock.full"));
				return InteractionResult.SUCCESS;
			}
			if (!player.hasInfiniteMaterials()) {
				held.shrink(1);
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
			return InteractionResult.SUCCESS;
		}
		ItemStack taken = crock.put(held);
		if (taken.isEmpty()) {
			player.sendOverlayMessage(Component.translatable(crock.charges() <= 0 ? "message.jaymoskitchen.crock.dry" : "message.jaymoskitchen.crock.no_room"));
			return InteractionResult.SUCCESS;
		}
		if (!player.hasInfiniteMaterials()) {
			held.shrink(taken.getCount());
		}
		level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 0.9F);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CrockEntity crock)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown() && player instanceof ServerPlayer server) {
			server.sendOverlayMessage(crock.status());
			return InteractionResult.SUCCESS;
		}
		ItemStack out = crock.takeLast();
		if (out.isEmpty()) {
			return InteractionResult.PASS;
		}
		player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
		level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
		return InteractionResult.SUCCESS;
	}
}
