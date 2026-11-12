package com.jaymospowers.gear.belt;

import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.gear.GearPicks;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A belt of throwables, worn in the bauble belt slot: the fighting cousin of the tool belt. Its tier sets how many different
 * stacks it carries ({@link #slots}). Load it like a bundle (click throwables on it, or it on them; right-click it onto an
 * empty slot or an empty cursor to take the picked stack out); the belt keys cycle the pick and throw it
 * ({@link Belt}).
 */
public class ThrowerBeltItem extends BaubleItem {
	private static final int BAR_COLOR = 0xFFB07A3A;

	private final int slots;

	public ThrowerBeltItem(Item.Properties properties, int slots) {
		super(properties, Baubles.Kind.BELT, List.of());
		this.slots = slots;
	}

	public int slots() {
		return this.slots;
	}

	public static BeltContents contents(ItemStack belt) {
		BeltContents contents = belt.get(GearPicks.BELT);
		return contents == null ? BeltContents.EMPTY : contents;
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		BeltContents contents = contents(self);
		ItemStack other = slot.getItem();
		if (action == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!Throwables.isThrowable(other)) {
				return false;
			}
			ItemStack taken = slot.safeTake(other.getCount(), other.getCount(), player);
			if (taken.isEmpty()) {
				return true;
			}
			BeltContents next = contents.insert(taken, this.slots);
			self.set(GearPicks.BELT, next);
			// whatever the belt had no room for goes back
			if (!taken.isEmpty()) {
				slot.safeInsert(taken);
			}
			sound(player, SoundEvents.BUNDLE_INSERT);
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStackTemplate picked = contents.selected();
			if (picked != null) {
				ItemStack remainder = slot.safeInsert(picked.create());
				int moved = picked.count() - remainder.getCount();
				if (moved > 0) {
					self.set(GearPicks.BELT, contents.without(moved));
					sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		BeltContents contents = contents(self);
		if (action == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!Throwables.isThrowable(other)) {
				return false;
			}
			if (slot.allowModification(player)) {
				self.set(GearPicks.BELT, contents.insert(other, this.slots));
				sound(player, SoundEvents.BUNDLE_INSERT);
			}
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStackTemplate picked = contents.selected();
			if (picked != null && slot.allowModification(player)) {
				carried.set(picked.create());
				self.set(GearPicks.BELT, contents.without(picked.count()));
				sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			}
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
		return Math.min(13, Math.round(13.0F * contents(stack).stacks().size() / this.slots));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		BeltContents contents = contents(stack);
		builder.accept(Component.translatable("tooltip.jaymospowers.thrower_belt.slots", contents.stacks().size(), this.slots).withStyle(ChatFormatting.GRAY));
		for (int i = 0; i < contents.stacks().size(); i++) {
			ItemStackTemplate held = contents.stacks().get(i);
			builder.accept(Component.literal((i == contents.pick() ? "> " : "  ") + held.count() + "x ").append(held.create().getHoverName())
				.withStyle(i == contents.pick() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
		}
		if (contents.isEmpty()) {
			builder.accept(Component.translatable("tooltip.jaymospowers.thrower_belt.how").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void sound(Entity entity, net.minecraft.sounds.SoundEvent sound) {
		entity.playSound(sound, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
	}
}
