package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.Workstations;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking what a station made sounds like the work (see Workstations). */
@Mixin(AbstractContainerScreen.class)
abstract class WorkstationSlotMixin {
	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"))
	private void jaymospowers$craftSound(Slot slot, int slotId, int buttonNum, ContainerInput input, CallbackInfo ci) {
		Workstations.clicked((AbstractContainerScreen<?>)(Object)this, slot, input);
	}
}
