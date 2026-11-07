package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.20.68) No armour repairs at an anvil in the middle of a fight ({@link ArmourWear#refuseRepair}). */
@Mixin(AnvilMenu.class)
abstract class ArmourWearRepairMixin {
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$notInAFight(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		if (ArmourWear.refuseRepair(player, self.getSlot(0).getItem(), self.getSlot(2).getItem())) {
			cir.setReturnValue(false);
		}
	}
}
