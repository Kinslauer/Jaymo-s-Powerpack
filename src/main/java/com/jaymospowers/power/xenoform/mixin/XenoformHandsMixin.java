package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fists only: while a player is an alien, both hands read as empty to everything that asks the entity (attacking,
 * using, mining, rendering, equipment sync), so items can't be used and punches land for the alien's strength. The
 * inventory itself is untouched: the hotbar and the inventory screen still show and move the items.
 */
@Mixin(LivingEntity.class)
abstract class XenoformHandsMixin {
	@Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$fistsOnly(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
		if (slot.getType() == EquipmentSlot.Type.HAND && (Object)this instanceof Player player && XenoformState.transformed(player)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
