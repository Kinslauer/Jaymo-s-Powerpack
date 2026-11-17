package com.jaymospowers.relics;

import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The relic belt ({@link Relics}), worn in the bauble belt slot. Load it like a bundle: click a relic onto it (or it onto a
 * relic); right-click it onto an empty slot or with an empty hand to take the last relic out.
 */
public class RelicBeltItem extends BaubleItem {
	private static final int BAR_COLOR = 0xFFC8A060;

	public RelicBeltItem(Item.Properties properties) {
		super(properties, Baubles.Kind.BELT, List.of());
	}

	@Override
	public boolean needsBond() {
		return false;
	}

	private static boolean put(ItemStack belt, ItemStack relic) {
		List<Integer> in = Relics.pouched(belt);
		if (!(relic.getItem() instanceof RelicItem item) || in.size() >= Relics.POUCHES) {
			return false;
		}
		List<Integer> next = new ArrayList<>(in);
		next.add(Relics.pack(item.kind(), Relics.level(relic)));
		belt.set(Relics.POUCHED, List.copyOf(next));
		relic.shrink(1);
		return true;
	}

	/** Takes the last relic out of the belt, or empty. */
	private static ItemStack take(ItemStack belt) {
		List<Integer> in = Relics.pouched(belt);
		if (in.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int packed = in.getLast();
		belt.set(Relics.POUCHED, List.copyOf(in.subList(0, in.size() - 1)));
		Relics.Kind kind = Relics.kindOf(packed);
		return kind == null ? ItemStack.EMPTY : Relics.relic(kind, Relics.levelOf(packed));
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		ItemStack other = slot.getItem();
		if (action == ClickAction.PRIMARY && other.getItem() instanceof RelicItem) {
			if (put(self, other)) {
				slot.setChanged();
				sound(player, SoundEvents.BUNDLE_INSERT);
			}
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStack out = take(self);
			if (!out.isEmpty()) {
				slot.safeInsert(out);
				sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (!slot.allowModification(player)) {
			return false;
		}
		if (action == ClickAction.PRIMARY && other.getItem() instanceof RelicItem) {
			if (put(self, other)) {
				sound(player, SoundEvents.BUNDLE_INSERT);
			}
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStack out = take(self);
			if (!out.isEmpty()) {
				carried.set(out);
				sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !Relics.pouched(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(13, Math.round(13.0F * Relics.pouched(stack).size() / Relics.POUCHES));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		List<Integer> in = Relics.pouched(stack);
		builder.accept(Component.translatable("tooltip.jaymospowers.relic_belt.pouches", in.size(), Relics.POUCHES).withStyle(ChatFormatting.GRAY));
		for (int packed : in) {
			Relics.Kind kind = Relics.kindOf(packed);
			if (kind != null) {
				builder.accept(Component.literal("  ").append(Relics.relic(kind, Relics.levelOf(packed)).getHoverName()).withStyle(ChatFormatting.GOLD));
				RelicItem.lines(kind, Relics.levelOf(packed), line -> builder.accept(Component.literal("    ").append(line)));
			}
		}
		if (in.isEmpty()) {
			builder.accept(Component.translatable("tooltip.jaymospowers.relic_belt.how").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void sound(Player player, SoundEvent sound) {
		player.playSound(sound, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
	}
}
