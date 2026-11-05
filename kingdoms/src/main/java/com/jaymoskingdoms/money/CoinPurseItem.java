package com.jaymoskingdoms.money;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import org.jspecify.annotations.Nullable;
import com.jaymoskingdoms.Currency;

/**
 * (0.7.498) The coin purse: a leather bag that holds up to {@link PurseContents#PURSE_MOST} coins in its
 * {@code jaymoskingdoms:coins} component, of any mix of the realms' coins and copper bits (not emeralds, nor the
 * greenskins' or pirates' coins). Worn in Jaymo's Powers' belt slot it hangs on the hip (and a pickpocket can lift
 * the whole thing); in a pack it is safer but pays second. Use it to pour every loose coin you carry into it; sneak
 * and use to tip it out again. Drop coins on it in an inventory screen to fill it; click it on a pile of coins to scoop
 * them up. (0.7.568) Like a bundle, right click it with an empty hand in a slot to take out a stack of its biggest coin, and
 * drop one purse on another to pour it in. {@link Money} spends from it like any other coins.
 */
public class CoinPurseItem extends Item {
	/** The fullness bar colour (gold), and when full (red, as on a bundle). */
	private static final int BAR = 0xFFE8B83A, FULL_BAR = 0xFFFF5555;

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
			tipOut((ServerPlayer)player, purse);
		} else {
			gather(player, purse);
		}
		((ServerPlayer)player).sendOverlayMessage(Component.translatable("item.jaymoskingdoms.coin_purse.holds", PurseContents.of(purse).list()));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** The coin {@code stack} is, if a purse holds it; else null. */
	private static @Nullable Currency coinOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Currency c = Currency.byItem(stack.getItem());
		return c != null && c.money() ? c : null;
	}

	/** Puts every loose coin in {@code player}'s inventory into {@code purse}, as far as it holds. */
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
		Currency coin = coinOf(stack);
		if (coin == null || now.room() <= 0) {
			return now;
		}
		int items = Math.min(stack.getCount(), now.room());
		if (items <= 0) {
			return now;
		}
		stack.shrink(items);
		return now.with(coin, items);
	}

	/** Tips {@code purse} out into {@code player}'s inventory, biggest coins first; what doesn't fit lands at their feet. */
	static void tipOut(ServerPlayer player, ItemStack purse) {
		PurseContents now = PurseContents.of(purse);
		List<Currency> coins = new ArrayList<>(now.held().keySet());
		coins.sort((a, b) -> Integer.compare(b.base, a.base));
		for (Currency c : coins) {
			int n = now.count(c);
			while (n > 0) {
				int k = Math.min(64, n);
				Money.give(player, new ItemStack(c.item(), k));
				n -= k;
			}
		}
		PurseContents.set(purse, PurseContents.EMPTY);
	}

	/**
	 * (0.7.568) Clicks on the purse in a slot, like a bundle's: coins on the cursor go in (either button); another purse on
	 * the cursor is poured in ({@link #overrideStackedOnOther}); right click with nothing on the cursor takes out one stack
	 * of its biggest coin.
	 */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.PRIMARY && action != ClickAction.SECONDARY) {
			return false;
		}
		if (other.isEmpty()) {
			if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
				return false;
			}
			// an empty purse picks up as usual
			ItemStack out = takeStack(self);
			if (out.isEmpty()) {
				return false;
			}
			carried.set(out);
			sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			return true;
		}
		if (coinOf(other) == null) {
			return false;
		}
		PurseContents before = PurseContents.of(self);
		PurseContents after = slot.allowModification(player) ? fill(before, other) : before;
		if (after.equals(before)) {
			sound(player, SoundEvents.BUNDLE_INSERT_FAIL);
			return true;
		}
		PurseContents.set(self, after);
		sound(player, SoundEvents.BUNDLE_INSERT);
		return true;
	}

	/**
	 * (0.7.568) The purse on the cursor, clicked onto a slot, like a bundle: right click on coins scoops them up; right click
	 * on an empty slot puts out one stack of its biggest coin; either click on another purse pours this one into it, as far
	 * as it has room.
	 */
	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.PRIMARY && action != ClickAction.SECONDARY) {
			return false;
		}
		ItemStack there = slot.getItem();
		if (there.getItem() instanceof CoinPurseItem && there != self) {
			PurseContents mine = PurseContents.of(self);
			if (mine.isEmpty() || !slot.allowModification(player)) {
				return false;
			}
			PurseContents[] poured = pour(mine, PurseContents.of(there));
			if (poured[0].equals(mine)) {
				sound(player, SoundEvents.BUNDLE_INSERT_FAIL);
				return true;
			}
			PurseContents.set(self, poured[0]);
			PurseContents.set(there, poured[1]);
			slot.setChanged();
			sound(player, SoundEvents.BUNDLE_INSERT);
			return true;
		}
		if (action != ClickAction.SECONDARY) {
			return false;
		}
		if (there.isEmpty()) {
			ItemStack out = takeStack(self);
			if (out.isEmpty()) {
				return false;
			}
			ItemStack rest = slot.safeInsert(out);
			if (!rest.isEmpty()) {
				Currency c = coinOf(rest);
				if (c != null) {
					PurseContents.set(self, PurseContents.of(self).with(c, rest.getCount()));
				}
			} else {
				sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			}
			return true;
		}
		if (coinOf(there) == null) {
			return false;
		}
		PurseContents before = PurseContents.of(self);
		PurseContents after = slot.allowModification(player) ? fill(before, there) : before;
		if (after.equals(before)) {
			sound(player, SoundEvents.BUNDLE_INSERT_FAIL);
			return true;
		}
		PurseContents.set(self, after);
		slot.setChanged();
		sound(player, SoundEvents.BUNDLE_INSERT);
		return true;
	}

	/** Takes one stack (up to 64) of {@code purse}'s biggest coin out of it; empty if it holds none. */
	static ItemStack takeStack(ItemStack purse) {
		PurseContents now = PurseContents.of(purse);
		Currency biggest = null;
		for (Currency c : now.held().keySet()) {
			if (biggest == null || c.base > biggest.base) {
				biggest = c;
			}
		}
		if (biggest == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = new ItemStack(biggest.item());
		int n = Math.min(now.count(biggest), out.getMaxStackSize());
		out.setCount(n);
		PurseContents.set(purse, now.with(biggest, -n));
		return out;
	}

	/** {@code from} poured into {@code into}, biggest coins first, as far as it has room: {what's left in from, into now}. */
	static PurseContents[] pour(PurseContents from, PurseContents into) {
		List<Currency> coins = new ArrayList<>(from.held().keySet());
		coins.sort((a, b) -> Integer.compare(b.base, a.base));
		for (Currency c : coins) {
			int before = into.count(c);
			into = into.with(c, from.count(c));
			from = from.with(c, -(into.count(c) - before));
		}
		return new PurseContents[] {from, into};
	}

	private static void sound(Player player, SoundEvent sound) {
		player.playSound(sound, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
	}

	/** (0.7.568) A gold bar under the icon, like a bundle's, showing how full it is. */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !PurseContents.of(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(1 + Mth.floor(PurseContents.of(stack).fullness() * 12.0F), 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return PurseContents.of(stack).room() <= 0 ? FULL_BAR : BAR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		PurseContents c = PurseContents.of(stack);
		builder.accept(Component.translatable("item.jaymoskingdoms.coin_purse.holds", c.list()).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("item.jaymoskingdoms.coin_purse.coins", c.coins(), PurseContents.PURSE_MOST).withStyle(ChatFormatting.GRAY));
	}
}
