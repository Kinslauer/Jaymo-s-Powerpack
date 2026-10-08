package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.Summoned;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Summoned weapons stay on their holder: they can't go into chests, the Pocket Stash, crafting grids, anvils... */
@Mixin(Slot.class)
public abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$arsenalStayHeld(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!(this.container instanceof Inventory) && Summoned.is(stack)) {
			cir.setReturnValue(false);
		}
	}
}
