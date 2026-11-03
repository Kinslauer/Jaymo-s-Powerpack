package com.jaymoskingdoms.money;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * (0.7.486) The coin purse: a leather bag that holds up to {@link PurseContents#PURSE_MOST} coins in its
 * {@code jaymoskingdoms:coins} component. Worn in Jaymo's Powers' belt slot it hangs on the hip (and a pickpocket can lift
 * the whole thing); in a pack it is safer but pays second. Use it to pour every loose coin you carry into it; sneak
 * and use to tip it out again. Drop coins on it in an inventory screen to fill it; click it on a pile of coins to scoop
 * them up. {@link Money} spends from it like any other coins.
 */
public class CoinPurseItem extends Item {
	public CoinPurseItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack purse = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			tipOut((net.minecraft.server.level.ServerPlayer)player, purse);
		} else {
			gather(player, purse);
		}
		((net.minecraft.server.level.ServerPlayer)player).sendOverlayMessage(
			Component.translatable("item.jaymoskingdoms.coin_purse.holds", Money.text(PurseContents.of(purse).value())));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Puts every loose coin (and coin stack) in {@code player}'s inventory into {@code purse}, as far as it holds. */
	static void gather(Player player, ItemStack purse) {
		Inventory inv = player.getInventory();
		PurseContents now = PurseContents.of(purse);
		for (int i = 0; i < inv.getContainerSize() && now.room() > 0; i++) {
			ItemStack stack = inv.getItem(i);
			now = fill(now, stack);
		}
		PurseContents.set(purse, now);
		inv.setChanged();
	}

	/** Moves as many of {@code stack}'s coins into {@code now} as fit; returns the new contents (the stack is shrunk). */
	private static PurseContents fill(PurseContents now, ItemStack stack) {
		Coin coin = Coin.of(stack);
		int per = 1;
		if (coin == null) {
			coin = Coin.ofStack(stack);
			per = Coin.STACK_COINS;
		}
		if (coin == null || now.room() <= 0) {
			return now;
		}
		// whole stack items only: a half stack of ten coins would be fiddly
		int items = Math.min(stack.getCount(), now.room() / per);
		if (items <= 0) {
			return now;
		}
		stack.shrink(items);
		return now.with(coin, items * per);
	}

	/** Tips {@code purse} out into {@code player}'s inventory, largest coins first; what doesn't fit lands at their feet. */
	static void tipOut(net.minecraft.server.level.ServerPlayer player, ItemStack purse) {
		PurseContents now = PurseContents.of(purse);
		for (Coin c : new Coin[] {Coin.GOLD, Coin.SILVER, Coin.COPPER}) {
			int n = now.count(c);
			while (n > 0) {
				int k = Math.min(64, n);
				Money.give(player, c.stack(k));
				n -= k;
			}
		}
		PurseContents.set(purse, PurseContents.EMPTY);
	}

	/** Coins held on the cursor and clicked onto the purse in a slot go in. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.PRIMARY && action != ClickAction.SECONDARY || other.isEmpty() || (Coin.of(other) == null && Coin.ofStack(other) == null)) {
			return false;
		}
		PurseContents before = PurseContents.of(self);
		PurseContents after = fill(before, other);
		if (after.equals(before)) {
			return false;
		}
		PurseContents.set(self, after);
		return true;
	}

	/** The purse on the cursor, clicked onto coins in a slot: it scoops them up. */
	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY || !slot.hasItem()) {
			return false;
		}
		ItemStack there = slot.getItem();
		if (Coin.of(there) == null && Coin.ofStack(there) == null) {
			return false;
		}
		PurseContents before = PurseContents.of(self);
		PurseContents after = fill(before, there);
		if (after.equals(before)) {
			return false;
		}
		PurseContents.set(self, after);
		slot.setChanged();
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		PurseContents c = PurseContents.of(stack);
		builder.accept(Component.translatable("item.jaymoskingdoms.coin_purse.holds", Money.text(c.value())).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("item.jaymoskingdoms.coin_purse.coins", c.coins(), PurseContents.PURSE_MOST).withStyle(ChatFormatting.GRAY));
	}
}
