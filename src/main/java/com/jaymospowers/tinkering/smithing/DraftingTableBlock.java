package com.jaymospowers.tinkering.smithing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (Smithing) The Drafting Table: hold a blueprint and use the table with paper in your inventory to copy it. The copy
 * is one level lower ({@link Blueprints#copyOf}); the original stays with you.
 */
public class DraftingTableBlock extends Block {
	public DraftingTableBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!stack.is(Blueprints.BLUEPRINT_ITEM) || !stack.has(Blueprints.BLUEPRINT)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int paper = paperSlot(player.getInventory());
		if (paper < 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.drafting_no_paper"));
			return InteractionResult.SUCCESS;
		}
		if (paper >= 0 && !player.isCreative()) {
			player.getInventory().getItem(paper).shrink(1);
		}
		ItemStack copy = Blueprints.copyOf(stack);
		player.getInventory().placeItemBackInInventory(copy, Prediction.SERVER_ONLY);
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.drafting_done", copy.getHoverName()));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.drafting_hint"));
		}
		return InteractionResult.SUCCESS;
	}

	private static int paperSlot(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(Items.PAPER)) {
				return i;
			}
		}
		return -1;
	}
}
