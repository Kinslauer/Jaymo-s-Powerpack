package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking armour on doesn't work in Vapour Form: there's nothing to strap it to. */
@Mixin(Equippable.class)
abstract class SmokeEquipMixin {
	@Shadow
	@Final
	private EquipmentSlot slot;

	@Inject(method = "swapWithEquipmentSlot", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noArmourInVapour(ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
		if (this.slot.isArmor() && SmokeVapour.active(player)) {
			SmokeVapour.refuseArmour(player);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
