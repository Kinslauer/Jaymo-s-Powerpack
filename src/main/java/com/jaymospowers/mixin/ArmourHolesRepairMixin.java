package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourHoles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any repair that restores durability (an anvil, Mending, a mob's own mending) wipes the holes worn into the piece ({@link ArmourHoles}). */
@Mixin(ItemStack.class)
abstract class ArmourHolesRepairMixin {
	@Inject(method = "setDamageValue", at = @At("HEAD"))
	private void jaymospowers$repairClosesHoles(int damage, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (damage < self.getDamageValue() && self.has(ArmourHoles.HOLES)) {
			ArmourHoles.clear(self);
		}
	}
}
