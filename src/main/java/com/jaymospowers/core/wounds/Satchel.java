package com.jaymospowers.core.wounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * (0.19.20) The healer's satchel (docs/Healing-Plan.md section 4): leather and string, a bundle that holds only medicine
 * (the kit of {@link MedicineItems}, poultices, drinks and sickbed foods), up to {@link #CAPACITY} items. Click it with
 * medicine on the cursor to put it in, with an empty cursor to take a stack out (right click, like a bundle). Carried
 * anywhere in the inventory, treatments take {@link #SHORTEN} less time ({@link #shorten}), and when the stack in hand
 * runs out after a treatment, the next one comes from the satchel ({@link #refill}).
 */
public final class Satchel {
	/** (0.19.20) Medicine items a satchel holds, and the share a treatment is shortened by while one is carried. */
	public static final int CAPACITY = 64;
	public static final float SHORTEN = 0.25F;

	private Satchel() {
	}

	/** Whether {@code stack} is medicine a satchel takes (not another satchel, not herbs). */
	public static boolean accepts(ItemStack stack) {
		Item item = stack.getItem();
		return !stack.isEmpty() && (item instanceof MedicineItems.MedicineItem || item instanceof MedicineItems.DrinkItem
			|| item instanceof MedicineItems.SickbedItem);
	}

	static List<ItemStack> contents(ItemStack satchel) {
		List<ItemStack> items = new ArrayList<>();
		satchel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().forEach(items::add);
		return items;
	}

	private static void store(ItemStack satchel, List<ItemStack> items) {
		satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	/** How many items {@code satchel} holds. */
	public static int count(ItemStack satchel) {
		int n = 0;
		for (ItemStack stack : contents(satchel)) {
			n += stack.getCount();
		}
		return n;
	}

	/** Puts as much of {@code stack} into {@code satchel} as fits (the caller shrinks {@code stack}). Returns how many went in. */
	static int insert(ItemStack satchel, ItemStack stack) {
		if (!accepts(stack)) {
			return 0;
		}
		List<ItemStack> items = contents(satchel);
		int room = CAPACITY - count(satchel);
		int left = Math.min(room, stack.getCount());
		int put = 0;
		for (ItemStack held : items) {
			if (left <= 0) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
				int n = Math.min(left, held.getMaxStackSize() - held.getCount());
				held.grow(n);
				left -= n;
				put += n;
			}
		}
		while (left > 0) {
			int n = Math.min(left, stack.getMaxStackSize());
			items.add(stack.copyWithCount(n));
			left -= n;
			put += n;
		}
		if (put > 0) {
			store(satchel, items);
		}
		return put;
	}

	/** Takes the last stack out of {@code satchel} (empty if there is none). */
	static ItemStack removeLast(ItemStack satchel) {
		List<ItemStack> items = contents(satchel);
		if (items.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = items.remove(items.size() - 1);
		store(satchel, items);
		return out;
	}

	/** Takes up to a stack of {@code item} out of {@code satchel} (empty if it holds none). */
	static ItemStack take(ItemStack satchel, Item item) {
		List<ItemStack> items = contents(satchel);
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).is(item)) {
				ItemStack out = items.remove(i);
				store(satchel, items);
				return out;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether {@code player} carries a satchel anywhere in their inventory. */
	public static boolean carrying(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() instanceof SatchelItem) {
				return true;
			}
		}
		return false;
	}

	/** {@code ticks} of a treatment, a quarter shorter if {@code player} carries a satchel (never under a tick). */
	public static int shorten(Player player, int ticks) {
		return carrying(player) ? Math.max(1, Math.round(ticks * (1.0F - SHORTEN))) : ticks;
	}

	/** The stack of {@code item} in {@code hand} has run out: the next of it from the first satchel that holds some. */
	static void refill(ServerPlayer player, InteractionHand hand, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack satchel = player.getInventory().getItem(i);
			if (!(satchel.getItem() instanceof SatchelItem)) {
				continue;
			}
			ItemStack out = take(satchel, item);
			if (!out.isEmpty()) {
				player.setItemInHand(hand, out);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.satchel_refill", out.getHoverName())
					.withStyle(ChatFormatting.GRAY));
				return;
			}
		}
	}

	/** The satchel itself: a bundle-style click-to-fill item. */
	public static final class SatchelItem extends Item {
		public SatchelItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public boolean canFitInsideContainerItems() {
			return false;
		}

		/** The satchel is on the cursor and clicked on a slot: medicine in the slot goes in, an empty slot gets a stack out. */
		@Override
		public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
			if (action != ClickAction.SECONDARY) {
				return false;
			}
			ItemStack in = slot.getItem();
			if (in.isEmpty()) {
				ItemStack out = removeLast(self);
				if (!out.isEmpty()) {
					player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
					ItemStack rest = slot.safeInsert(out);
					if (!rest.isEmpty()) {
						insert(self, rest);
					}
				}
			} else if (accepts(in)) {
				int n = insert(self, in);
				if (n > 0) {
					in.shrink(n);
					slot.setChanged();
					player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
				}
			}
			return true;
		}

		/** Medicine (or nothing) on the cursor is clicked onto the satchel in a slot: it goes in, or a stack comes out onto the cursor. */
		@Override
		public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
			if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
				return false;
			}
			if (other.isEmpty()) {
				ItemStack out = removeLast(self);
				if (!out.isEmpty()) {
					player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
					access.set(out);
				}
				return true;
			}
			if (accepts(other)) {
				int n = insert(self, other);
				if (n > 0) {
					other.shrink(n);
					player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
				}
				return true;
			}
			return false;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymospowers.healers_satchel.count", count(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.jaymospowers.healers_satchel.hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
