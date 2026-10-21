package com.jaymospowers.mixin;

import com.jaymospowers.core.FistGuard;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link FistGuard}: mid-fight, the free slot a pickup gets is never your empty selected one. */
@Mixin(Inventory.class)
abstract class FistGuardInventoryMixin {
	@Shadow
	@Final
	private NonNullList<ItemStack> items;

	@Shadow
	@Final
	public Player player;

	@Shadow
	public abstract int getSelectedSlot();

	@Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$keepFistFree(CallbackInfoReturnable<Integer> cir) {
		int slot = cir.getReturnValueI();
		if (slot < 0 || slot != this.getSelectedSlot() || !FistGuard.guarding(this.player)) {
			return;
		}
		// getFreeSlot returns the first empty slot, so the next one is further on
		for (int i = slot + 1; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}
}
