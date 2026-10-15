package com.jaymospowers.enchanting;

import java.util.List;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * The enchanting table ({@link Enchanting}): the item in the top slot, the price in the four below it. The screen sends
 * the chosen enchantment as a menu button (its id in the enchantment registry, the same on both sides).
 */
public class EnchantingMenu extends AbstractContainerMenu {
	public static final int ITEM = 0, MATERIALS = 4, SLOTS = 1 + MATERIALS;
	public static final int[][] SLOT_AT = {{22, 26}, {13, 54}, {31, 54}, {13, 72}, {31, 72}};
	public static final int INVENTORY_X = 36, INVENTORY_Y = 144;
	private final ContainerLevelAccess access;
	private final SimpleContainer table = new SimpleContainer(SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			EnchantingMenu.this.slotsChanged(this);
		}
	};

	/** Client side. */
	public EnchantingMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public EnchantingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Enchanting.MENU, id);
		this.access = access;
		this.addSlot(new Slot(table, ITEM, SLOT_AT[0][0], SLOT_AT[0][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Enchanting.enchantable(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 1; i < SLOTS; i++) {
			this.addSlot(new Slot(table, i, SLOT_AT[i][0], SLOT_AT[i][1]));
		}
		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public ItemStack item() {
		return table.getItem(ITEM);
	}

	public Container materials() {
		return table;
	}

	/** Which material slot pays for each ingredient (-1 where none does). */
	public static int[] match(List<EnchantRecipes.Ing> cost, Container table) {
		int[] found = new int[cost.size()];
		boolean[] taken = new boolean[SLOTS];
		for (int i = 0; i < cost.size(); i++) {
			found[i] = cost.get(i).find(table, 1, SLOTS, taken);
			if (found[i] >= 0) {
				taken[found[i]] = true;
			}
		}
		return found;
	}

	public static boolean paid(int[] found) {
		for (int slot : found) {
			if (slot < 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> chosen = registry.get(buttonId).orElse(null);
		if (chosen == null) {
			return false;
		}
		ItemStack stack = item();
		Enchanting.Option option = Enchanting.options(player, stack).stream().filter(o -> o.enchantment().equals(chosen)).findFirst().orElse(null);
		if (option == null || option.state() != Enchanting.State.READY) {
			return false;
		}
		List<EnchantRecipes.Ing> cost = EnchantRecipes.cost(chosen, option.target());
		int[] found = match(cost, table);
		boolean free = player.hasInfiniteMaterials();
		if (!free && !paid(found)) {
			return false;
		}
		access.execute((level, pos) -> {
			if (!free) {
				for (int i = 0; i < cost.size(); i++) {
					ItemStack paidWith = table.getItem(found[i]);
					ItemStack left = EnchantRecipes.Ing.remainder(paidWith);
					paidWith.shrink(cost.get(i).count());
					if (paidWith.isEmpty() && !left.isEmpty()) {
						table.setItem(found[i], left);
					} else if (!left.isEmpty()) {
						player.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
					}
				}
			}
			ItemStack out = stack.is(Items.BOOK) ? stack.transmuteCopy(Items.ENCHANTED_BOOK) : stack;
			out.enchant(chosen, option.target());
			table.setItem(ITEM, out);
			player.onEnchantmentPerformed(out, 0);
			player.awardStat(Stats.ENCHANT_ITEM);
			if (player instanceof ServerPlayer server) {
				CriteriaTriggers.ENCHANTED_ITEM.trigger(server, out, option.target());
			}
			table.setChanged();
			level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		});
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, table));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < SLOTS) {
			if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!slots.get(ITEM).hasItem() && Enchanting.enchantable(stack)) {
			slots.get(ITEM).setByPlayer(stack.split(1));
		} else if (!moveItemStackTo(stack, 1, SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == copy.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.ENCHANTING_TABLE);
	}
}
