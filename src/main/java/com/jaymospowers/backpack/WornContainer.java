package com.jaymospowers.backpack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The worn backpack's contents while it is open: read from the backpack on the player's back, and written back to it
 * on every change. It closes if the backpack comes off.
 */
final class WornContainer extends SimpleContainer {
	private final ServerPlayer player;
	private final ItemStack backpack;

	WornContainer(ServerPlayer player) {
		super(Backpack.ROWS * 9);
		this.player = player;
		ItemStack worn = Backpack.worn(player);
		this.backpack = worn == null ? ItemStack.EMPTY : worn;
		this.backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
	}

	@Override
	public void setChanged() {
		super.setChanged();
		save();
	}

	@Override
	public boolean stillValid(Player player) {
		return player == this.player && player.isAlive() && !this.backpack.isEmpty() && Backpack.worn(this.player) == this.backpack;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return BackpackMenu.fits(stack);
	}

	@Override
	public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
		super.stopOpen(user);
		save();
	}

	private void save() {
		if (!this.backpack.isEmpty() && Backpack.worn(this.player) == this.backpack) {
			this.backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
		}
	}
}
