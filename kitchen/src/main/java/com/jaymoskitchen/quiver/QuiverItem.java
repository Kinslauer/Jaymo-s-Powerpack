package com.jaymoskitchen.quiver;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A quiver: carry it anywhere in your inventory and it's worn on your back. Works like a bundle that only takes
 * arrows (up to 32): left-click arrows onto it to load them, right-click it to pull the top stack back out.
 * Bows and crossbows draw from it (see {@code PlayerMixin}).
 */
public class QuiverItem extends Item {
	private static final int BAR_COLOR = 0xFFC8A050;

	public QuiverItem(Item.Properties properties) {
		super(properties);
	}

	public static QuiverContents contents(ItemStack quiver) {
		QuiverContents contents = quiver.get(Quivers.CONTENTS);
		return contents == null ? QuiverContents.EMPTY : contents;
	}

	/** Quiver picked up and clicked onto another slot. */
	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
		QuiverContents contents = contents(self);
		ItemStack other = slot.getItem();
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!QuiverContents.canHold(other)) {
				return false;
			}
			ItemStack taken = slot.safeTake(other.getCount(), contents.space(), player);
			if (!taken.isEmpty()) {
				self.set(Quivers.CONTENTS, contents.withAdded(taken, taken.getCount()));
				playInsertSound(player);
			} else {
				playInsertFailSound(player);
			}
			broadcastChanges(player);
			return true;
		}
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStackTemplate top = contents.top();
			if (top != null) {
				ItemStack remainder = slot.safeInsert(top.create());
				int moved = top.count() - remainder.getCount();
				if (moved > 0) {
					self.set(Quivers.CONTENTS, contents.withRemoved(contents.arrows().size() - 1, moved));
					playRemoveSound(player);
				}
			}
			broadcastChanges(player);
			return true;
		}
		return false;
	}

	/** Something clicked onto the quiver in its slot. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player,
		SlotAccess carriedItem) {
		QuiverContents contents = contents(self);
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!QuiverContents.canHold(other)) {
				// not an arrow: let it swap places like normal
				return false;
			}
			int amount = Math.min(other.getCount(), contents.space());
			if (slot.allowModification(player) && amount > 0) {
				self.set(Quivers.CONTENTS, contents.withAdded(other, amount));
				other.shrink(amount);
				playInsertSound(player);
			} else {
				playInsertFailSound(player);
			}
			broadcastChanges(player);
			return true;
		}
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStackTemplate top = contents.top();
			if (top != null && slot.allowModification(player)) {
				carriedItem.set(top.create());
				self.set(Quivers.CONTENTS, contents.withRemoved(contents.arrows().size() - 1, top.count()));
				playRemoveSound(player);
			}
			broadcastChanges(player);
			return true;
		}
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !contents(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(13, Math.round(13.0F * contents(stack).count() / QuiverContents.CAPACITY));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag tooltipFlag) {
		QuiverContents contents = contents(stack);
		builder.accept(Component.translatable("tooltip.jaymoskitchen.quiver.count", contents.count(), QuiverContents.CAPACITY)
			.withStyle(ChatFormatting.GRAY));
		// top of the quiver first: that's what you'll shoot next
		for (int i = contents.arrows().size() - 1; i >= 0; i--) {
			ItemStackTemplate arrows = contents.arrows().get(i);
			builder.accept(Component.literal("  " + arrows.count() + "x ").append(arrows.create().getHoverName())
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (contents.isEmpty()) {
			builder.accept(Component.translatable("tooltip.jaymoskitchen.quiver.how").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void broadcastChanges(Player player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != null) {
			menu.slotsChanged(player.getInventory());
		}
	}

	private static void playInsertSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
	}

	private static void playRemoveSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
	}

	private static void playInsertFailSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
	}
}
