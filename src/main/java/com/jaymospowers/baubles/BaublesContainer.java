package com.jaymospowers.baubles;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A player's seven bauble slots while their screen is open: read from what they wear, written back on every change. */
final class BaublesContainer extends SimpleContainer {
	private final ServerPlayer player;

	BaublesContainer(ServerPlayer player) {
		super(Baubles.SIZE);
		this.player = player;
		for (int i = 0; i < Baubles.SIZE; i++) {
			this.getItems().set(i, Baubles.worn(player).get(i));
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		Baubles.setWorn(this.player, this.getItems());
	}

	@Override
	public boolean stillValid(Player player) {
		return player == this.player && player.isAlive();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return Baubles.fits(slot, stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void stopOpen(ContainerUser user) {
		super.stopOpen(user);
		Baubles.setWorn(this.player, this.getItems());
	}
}
