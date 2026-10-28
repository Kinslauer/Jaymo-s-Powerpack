package com.jaymospowers.power.wind.mixin;

import com.jaymospowers.power.wind.SorasLineage;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.110) A Sora's Dagger worn out: its line is gone, and a new blade waits to be found (see SorasLineage). */
@Mixin(ItemStack.class)
abstract class SorasBreakMixin {
	@Inject(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private void jaymospowers$sorasBreaks(CallbackInfo ci) {
		SorasLineage.gone((ItemStack)(Object)this);
	}
}
