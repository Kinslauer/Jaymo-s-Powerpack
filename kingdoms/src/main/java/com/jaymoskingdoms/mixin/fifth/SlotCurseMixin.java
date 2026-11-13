package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.ItemBonds;
import com.jaymoskingdoms.RelicCurses;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A thing under the clinging curse cannot be put down ({@link RelicCurses#clings}): it cannot be taken out of any slot by the
 * one it clings to, and cannot be put in a chest or any container that is not their own body (their inventory and the bauble
 * slots take it).
 */
@Mixin(Slot.class)
abstract class SlotCurseMixin {
	@Shadow
	public abstract ItemStack getItem();

	@Shadow
	@org.spongepowered.asm.mixin.Final
	public net.minecraft.world.Container container;

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$cling(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (RelicCurses.clings(player, this.getItem())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noChest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!(this.container instanceof Inventory) && !this.container.getClass().getSimpleName().equals("BaublesContainer")
			&& "clinging".equals(com.jaymoskingdoms.ItemCurses.tag(stack)) && ItemBonds.idOf(stack) != null) {
			cir.setReturnValue(false);
		}
	}
}
