package com.jaymospowers.armory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Future Workbench: the courier shop's bench of Los Perrito, the only place a hoverboard is put together. Use it
 * with the parts in your pack (it takes them from there) and the board comes out charged for a minute.
 */
public final class FutureWorkbenchBlock extends Block {
	/** What one hoverboard takes: iron for the hull, copper for the coils, redstone for the power cell. */
	static final int IRON = 6, COPPER = 4, REDSTONE = 8;

	FutureWorkbenchBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		Inventory pack = player.getInventory();
		if (count(pack, Items.IRON_INGOT) < IRON || count(pack, Items.COPPER_INGOT) < COPPER || count(pack, Items.REDSTONE) < REDSTONE) {
			sp.sendOverlayMessage(Component.translatable("message.jaymospowers.future_workbench.needs", IRON, COPPER, REDSTONE).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS_SERVER;
		}
		take(pack, Items.IRON_INGOT, IRON);
		take(pack, Items.COPPER_INGOT, COPPER);
		take(pack, Items.REDSTONE, REDSTONE);
		ItemStack board = new ItemStack(Armory.HOVERBOARD);
		pack.placeItemBackInInventory(board, net.minecraft.util.Prediction.SERVER_ONLY);
		level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.4F);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static int count(Inventory pack, Item item) {
		int n = 0;
		for (int i = 0; i < pack.getContainerSize(); i++) {
			ItemStack stack = pack.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Inventory pack, Item item, int amount) {
		for (int i = 0; i < pack.getContainerSize() && amount > 0; i++) {
			ItemStack stack = pack.getItem(i);
			if (stack.is(item)) {
				int n = Math.min(amount, stack.getCount());
				stack.shrink(n);
				amount -= n;
			}
		}
	}
}
