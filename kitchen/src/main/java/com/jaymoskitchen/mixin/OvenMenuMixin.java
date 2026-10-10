package com.jaymoskitchen.mixin;

import com.jaymoskitchen.kitchen.HobToolSlot;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.blay09.mods.cookingforblockheads.menu.OvenMenu;
import net.blay09.mods.cookingforblockheads.menu.slot.SlotOvenTool;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the oven screen's tool slots which oven they're for, so they can lock cookware in use (SlotOvenToolMixin). */
@Mixin(OvenMenu.class)
abstract class OvenMenuMixin {
	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;)V",
		at = @At("TAIL"))
	private void jaymoskitchen$toolSlots(int containerId, Inventory inventory, OvenBlockEntity oven, CallbackInfo ci) {
		for (Slot slot : ((AbstractContainerMenu) (Object) this).slots) {
			if (slot instanceof SlotOvenTool) {
				((HobToolSlot) slot).jaymoskitchen$setOven(oven);
			}
		}
	}
}
