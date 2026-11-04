package com.jaymoskingdoms;

import java.util.function.Consumer;
import java.util.stream.LongStream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * (0.7.497) The key ring: a bundle that takes keys only and doesn't dump its contents when used. Keys stack to
 * {@link Locks#KEY_STACK}, so each weighs 1/16 and a ring holds 16. It is not in the bundles tag, so it gets no dye recipe
 * or scroll-select. Worn in Jaymo's Powers' belt bauble slot (by a tag), it opens its keys' locks ({@link Locks#holdsKey}).
 */
public class KeyRingItem extends BundleItem {
	public KeyRingItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		if (action == ClickAction.PRIMARY && !slot.getItem().isEmpty() && !slot.getItem().is(Locks.KEY)) {
			return false;
		}
		return super.overrideStackedOnOther(self, slot, action, player);
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action == ClickAction.PRIMARY && !other.isEmpty() && !other.is(Locks.KEY)) {
			return false;
		}
		return super.overrideOtherStackedOnMe(self, other, slot, action, player, carried);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.jaymoskingdoms.key_ring").withStyle(ChatFormatting.GRAY));
	}

	/** The key ids on {@code ring}. */
	static LongStream ids(ItemStack ring) {
		return ring.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopies()
			.filter(k -> k.is(Locks.KEY) && k.has(Locks.LOCK_ID)).mapToLong(k -> k.get(Locks.LOCK_ID));
	}
}
