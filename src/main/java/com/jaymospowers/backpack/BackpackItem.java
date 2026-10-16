package com.jaymospowers.backpack;

import java.util.function.Consumer;
import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The backpack in hand: right-click a block to set it down there, or the air to put it on your back. */
public class BackpackItem extends BlockItem {
	public BackpackItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (Backpack.wearing(player)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.backpack.already"));
			}
			return InteractionResult.FAIL;
		}
		if (SmokeVapour.active(player)) {
			// nothing stays on smoke
			SmokeVapour.refuseArmour(player);
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server) {
			ItemStack stack = player.getItemInHand(hand);
			Backpack.putOn(server, stack);
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		long used = contents.nonEmptyItemCopyStream().count();
		builder.accept(Component.translatable("item.jaymospowers.backpack.slots", used, Backpack.ROWS * 9).withStyle(net.minecraft.ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.backpack.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}
}
