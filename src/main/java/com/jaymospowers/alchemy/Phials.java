package com.jaymospowers.alchemy;

import com.jaymospowers.alchemy.codex.Codex;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * Glass phials: an empty one ({@link Alchemy#PHIAL}) and one holding up to {@link #SIZE} of a single essence
 * ({@link Alchemy#ESSENCE_PHIAL}, the essence in its {@link Alchemy#ESSENCES} component). Phials carry essence from
 * crucibles and jars to wherever it's spent (the infusion altar, the enchanting table, the rod).
 */
public final class Phials {
	/** The most one phial holds. */
	public static final int SIZE = 10;

	private Phials() {
	}

	/** A phial holding {@code amount} (capped at {@link #SIZE}) of one essence; an empty phial for 0. */
	public static ItemStack of(Essence essence, int amount) {
		if (amount <= 0) {
			return new ItemStack(Alchemy.PHIAL);
		}
		ItemStack stack = new ItemStack(Alchemy.ESSENCE_PHIAL);
		stack.set(Alchemy.ESSENCES, EssenceList.of(essence, Math.min(SIZE, amount)));
		return stack;
	}

	/** What's in a phial (or any item with essence in it); empty for anything else. */
	public static EssenceList contents(ItemStack stack) {
		EssenceList in = stack.get(Alchemy.ESSENCES);
		return in == null ? EssenceList.EMPTY : in;
	}

	/** Everything the player carries in phials, summed. */
	public static EssenceList carried(Player player) {
		EssenceList sum = EssenceList.EMPTY;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(Alchemy.ESSENCE_PHIAL)) {
				sum = sum.plus(contents(stack).scaled(stack.getCount()));
			}
		}
		return sum;
	}

	/**
	 * Draws {@code cost} out of the phials the player carries, emptying or part-emptying them; nothing is taken unless
	 * all of it is there. Creative players pay nothing. Returns whether it was paid.
	 */
	public static boolean pay(Player player, EssenceList cost) {
		if (player.isCreative()) {
			return true;
		}
		if (!carried(player).covers(cost)) {
			return false;
		}
		for (Essence e : cost.amounts().keySet()) {
			int owed = cost.get(e);
			var inv = player.getInventory();
			for (int slot = 0; slot < inv.getContainerSize() && owed > 0; slot++) {
				ItemStack stack = inv.getItem(slot);
				if (!stack.is(Alchemy.ESSENCE_PHIAL) || contents(stack).get(e) <= 0) {
					continue;
				}
				// one phial at a time out of the stack
				while (owed > 0 && !stack.isEmpty()) {
					int in = contents(stack).get(e);
					int take = Math.min(in, owed);
					owed -= take;
					ItemStack rest = of(e, in - take);
					stack.shrink(1);
					if (!player.getInventory().add(rest)) {
						player.drop(rest, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
					if (in - take > 0) {
						break;
					}
				}
			}
		}
		return true;
	}

	/** An essence phial; its name and colour come from what's in it. */
	public static class EssencePhialItem extends Item {
		public EssencePhialItem(Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			Essence e = contents(stack).main();
			if (e == null) {
				return super.getName(stack);
			}
			// what the viewer hasn't discovered stays unnamed (the lens and the codex teach the names)
			return Codex.seenByViewer(e) ? Component.translatable("item.jaymospowers.essence_phial.of", Component.translatable(e.key()))
				: Component.translatable("item.jaymospowers.essence_phial.unknown");
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			@Nullable Essence e = contents(stack).main();
			if (e != null) {
				builder.accept(Component.translatable("tooltip.jaymospowers.essence_phial.amount", contents(stack).get(e), SIZE)
					.withStyle(ChatFormatting.GRAY));
				if (!Codex.seenByViewer(e)) {
					builder.accept(Component.translatable("tooltip.jaymospowers.essence_phial.unknown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				}
			}
		}
	}
}
