package com.jaymospowers.enchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (me-inscribe): the Inscription Table ({@link Inscription}). Use it with gear to lay the gear on it, with
 * a gem to set the gem beside it, with a chisel to carve; an empty hand takes back the gear, then the gem.
 */
public class InscriptionTableBlock extends Block implements EntityBlock {
	/** A stone desk: its slab top and thick foot. */
	private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 10, 0, 16, 14, 16), Block.box(3, 0, 3, 13, 10, 13));

	public InscriptionTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InscriptionTableEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof InscriptionTableEntity table)) {
			return InteractionResult.PASS;
		}
		if (stack.is(Inscription.CHISEL)) {
			if (!level.isClientSide()) {
				Inscription.open((ServerPlayer) player, table);
			}
			return InteractionResult.SUCCESS;
		}
		if (Inscription.gem(stack) != null && table.gem().isEmpty()) {
			if (!level.isClientSide()) {
				table.set(table.gear(), stack.split(1));
			}
			return InteractionResult.SUCCESS;
		}
		if (Inscription.gear(stack) && table.gear().isEmpty()) {
			if (!level.isClientSide()) {
				table.set(stack.split(1), table.gem());
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof InscriptionTableEntity table) || table.gear().isEmpty() && table.gem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken;
			if (!table.gear().isEmpty()) {
				taken = table.gear();
				table.set(ItemStack.EMPTY, table.gem());
			} else {
				taken = table.gem();
				table.set(table.gear(), ItemStack.EMPTY);
			}
			player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
		}
		return InteractionResult.SUCCESS;
	}
}
