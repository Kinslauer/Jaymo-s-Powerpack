package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A faction's storage: the real containers (chests, barrels) registered in its {@link Faction.Holdings#storage()}.
 * Everything here reads and writes the items in them. Only containers in loaded chunks count (an unloaded storehouse
 * is never loaded just to look in it): check {@link #loaded} first when it matters.
 */
public final class Storage {
	/** The rank a member needs to take from their own faction's storage. */
	public static final Rank TAKE_RANK = Rank.KNIGHT;

	private Storage() {
	}

	/** Its containers that are loaded right now. */
	public static List<Container> containers(ServerLevel level, Faction faction) {
		List<Container> out = new ArrayList<>();
		for (BlockPos pos : faction.holdings().storage()) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container) {
				out.add(container);
			}
		}
		return out;
	}

	/** It has storage, and all of it is loaded. */
	public static boolean loaded(ServerLevel level, Faction faction) {
		if (faction.holdings().storage().isEmpty()) {
			return false;
		}
		for (BlockPos pos : faction.holdings().storage()) {
			if (!level.isLoaded(pos)) {
				return false;
			}
		}
		return true;
	}

	/** Copies of every stack in its storage. */
	public static List<ItemStack> contents(ServerLevel level, Faction faction) {
		List<ItemStack> out = new ArrayList<>();
		for (Container c : containers(level, faction)) {
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				ItemStack stack = c.getItem(slot);
				if (!stack.isEmpty()) {
					out.add(stack.copy());
				}
			}
		}
		return out;
	}

	/** Item → how many of it are in storage (in the order first found). */
	public static Map<Item, Integer> totals(ServerLevel level, Faction faction) {
		Map<Item, Integer> totals = new LinkedHashMap<>();
		for (ItemStack stack : contents(level, faction)) {
			totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		return totals;
	}

	public static int count(ServerLevel level, Faction faction, Predicate<ItemStack> what) {
		int n = 0;
		for (Container c : containers(level, faction)) {
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				ItemStack stack = c.getItem(slot);
				if (!stack.isEmpty() && what.test(stack)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	public static int count(ServerLevel level, Faction faction, Item item) {
		return count(level, faction, stack -> stack.is(item));
	}

	/** Food in storage (anything edible), in items. */
	public static int food(ServerLevel level, Faction faction) {
		return count(level, faction, Storage::isFood);
	}

	public static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD);
	}

	/** It holds at least this many of each item. */
	public static boolean has(ServerLevel level, Faction faction, Map<Item, Integer> needs) {
		Map<Item, Integer> totals = totals(level, faction);
		for (var need : needs.entrySet()) {
			if (totals.getOrDefault(need.getKey(), 0) < need.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** The items it takes to place {@code blocks} (one each; air and blocks without an item are skipped). */
	public static Map<Item, Integer> itemsFor(List<BlockState> blocks) {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		for (BlockState state : blocks) {
			Item item = state.getBlock().asItem();
			if (!state.isAir() && item != net.minecraft.world.item.Items.AIR) {
				needs.merge(item, 1, Integer::sum);
			}
		}
		return needs;
	}

	/** What's missing from storage to place {@code blocks} (item → how many short). Empty when it has it all. */
	public static Map<Item, Integer> missing(ServerLevel level, Faction faction, Map<Item, Integer> needs) {
		Map<Item, Integer> totals = totals(level, faction);
		Map<Item, Integer> missing = new LinkedHashMap<>();
		for (var need : needs.entrySet()) {
			int short_ = need.getValue() - totals.getOrDefault(need.getKey(), 0);
			if (short_ > 0) {
				missing.put(need.getKey(), short_);
			}
		}
		return missing;
	}

	/**
	 * Takes up to {@code max} items matching {@code what} out of storage, all of one kind (the first matching stack's,
	 * components included). Returns them (empty if there were none).
	 */
	public static ItemStack take(ServerLevel level, Faction faction, Predicate<ItemStack> what, int max) {
		ItemStack taken = ItemStack.EMPTY;
		for (Container c : containers(level, faction)) {
			for (int slot = 0; slot < c.getContainerSize() && (taken.isEmpty() || taken.getCount() < max); slot++) {
				ItemStack stack = c.getItem(slot);
				if (stack.isEmpty() || !what.test(stack) || (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(stack, taken))) {
					continue;
				}
				int n = Math.min(stack.getCount(), max - (taken.isEmpty() ? 0 : taken.getCount()));
				ItemStack part = c.removeItem(slot, n);
				if (taken.isEmpty()) {
					taken = part;
				} else {
					taken.grow(part.getCount());
				}
				c.setChanged();
			}
			if (!taken.isEmpty() && taken.getCount() >= max) {
				break;
			}
		}
		return taken;
	}

	/** Takes up to {@code max} of {@code item}. */
	public static ItemStack take(ServerLevel level, Faction faction, Item item, int max) {
		return take(level, faction, stack -> stack.is(item), max);
	}

	/** Puts {@code stack} into storage (topping up like stacks first, then empty slots). Returns what didn't fit. */
	public static ItemStack put(ServerLevel level, Faction faction, ItemStack stack) {
		ItemStack left = stack.copy();
		List<Container> containers = containers(level, faction);
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize() && !left.isEmpty(); slot++) {
				ItemStack there = c.getItem(slot);
				if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, left)) {
					int room = Math.min(c.getMaxStackSize(there), there.getMaxStackSize()) - there.getCount();
					if (room > 0) {
						int n = Math.min(room, left.getCount());
						there.grow(n);
						left.shrink(n);
						c.setChanged();
					}
				}
			}
		}
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize() && !left.isEmpty(); slot++) {
				if (c.getItem(slot).isEmpty() && c.canPlaceItem(slot, left)) {
					int n = Math.min(left.getCount(), Math.min(c.getMaxStackSize(left), left.getMaxStackSize()));
					c.setItem(slot, left.split(n));
					c.setChanged();
				}
			}
		}
		return left;
	}

	/** Puts {@code count} of {@code item}; returns how many didn't fit. */
	public static int put(ServerLevel level, Faction faction, Item item, int count) {
		int left = 0;
		while (count > 0) {
			ItemStack stack = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
			count -= stack.getCount();
			left += put(level, faction, stack).getCount();
		}
		return left;
	}

	/** Free slots across its loaded containers. */
	public static int freeSlots(ServerLevel level, Faction faction) {
		int n = 0;
		for (Container c : containers(level, faction)) {
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).isEmpty()) {
					n++;
				}
			}
		}
		return n;
	}

	/** {@code player} may take from {@code faction}'s storage: a member of {@link #TAKE_RANK} or better (or an operator in creative). */
	public static boolean mayTake(ServerPlayer player, Faction faction) {
		if (player.isCreative()) {
			return true;
		}
		Faction.Member member = faction.member(player.getUUID());
		return member != null && Rank.forStanding(member.standing()).ordinal() >= TAKE_RANK.ordinal();
	}

	/** Registers {@code positions} as (all of) {@code faction}'s storage, with {@code depot} where workers load. */
	public static void register(net.minecraft.server.MinecraftServer server, int faction, List<BlockPos> positions, BlockPos depot) {
		Factions.update(server, faction, f -> f.withHoldings(f.holdings().withStorage(positions, java.util.Optional.ofNullable(depot))));
	}

	/** Adds {@code positions} to {@code faction}'s storage (a camp's loot pile growing, a second storehouse). */
	public static void add(net.minecraft.server.MinecraftServer server, int faction, List<BlockPos> positions) {
		Factions.update(server, faction, f -> {
			List<BlockPos> all = new ArrayList<>(f.holdings().storage());
			for (BlockPos pos : positions) {
				if (!all.contains(pos)) {
					all.add(pos.immutable());
				}
			}
			return f.withHoldings(f.holdings().withStorage(all, f.holdings().depot()));
		});
	}
}
