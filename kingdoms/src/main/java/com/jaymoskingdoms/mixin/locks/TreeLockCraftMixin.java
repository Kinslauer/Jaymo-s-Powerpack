package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.TreeLocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A trade's stations come out of a crafting grid only for those who learned the trade, and a saw cuts only for a sawyer
 * ({@link TreeLocks#mayCraft}): the result stays in its slot, with a line on the action bar.
 */
@Mixin(ResultSlot.class)
abstract class TreeLockCraftMixin extends Slot {
	@Shadow
	@Final
	private CraftingContainer craftSlots;

	TreeLockCraftMixin(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPickup(Player player) {
		return super.mayPickup(player) && (!this.hasItem() || TreeLocks.mayCraft(player, this.getItem(), this.craftSlots.getItems()));
	}
}
