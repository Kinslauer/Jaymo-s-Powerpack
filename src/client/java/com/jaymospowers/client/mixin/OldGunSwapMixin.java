package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.OldGuns;
import com.jaymospowers.power.arsenal.Summoned;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Q with a black-powder gun in hand ({@link OldGuns}) doesn't drop it: you take up the next loaded one on the hotbar
 * instead. Andy, 2026-11-22: "pressing q with flintlocks just swaps to the other loaded one on the hotbar, like arsenal's
 * void flintlocks". With no other loaded one there, Q drops as before (and the reach for a spare, {@code FlintlockDraw},
 * still follows). A spent Void Armory flintlock is still dropped, since that's how it comes back loaded.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class OldGunSwapMixin {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$swapGun(LocalPlayer player, boolean all, CallbackInfo ci) {
		ItemStack held = player.getMainHandItem();
		if (!OldGuns.is(held) || ArsenalFlintlock.isFlintlock(held) && Summoned.is(held) && !OldGuns.loaded(held)) {
			return;
		}
		Inventory inventory = player.getInventory();
		int from = inventory.getSelectedSlot();
		int size = Inventory.getSelectionSize();
		for (int step = 1; step < size; step++) {
			int slot = (from + step) % size;
			ItemStack stack = inventory.getItem(slot);
			if (OldGuns.is(stack) && OldGuns.loaded(stack)) {
				inventory.setSelectedSlot(slot);
				ci.cancel();
				return;
			}
		}
	}
}
