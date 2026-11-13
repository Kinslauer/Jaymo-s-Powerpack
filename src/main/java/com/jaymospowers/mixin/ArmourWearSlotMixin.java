package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No armour repairs mid-fight at the grindstone or in the crafting grid either (two worn pieces put together), as at the
 * anvil ({@link ArmourWearRepairMixin}, {@link ArmourWear#refuseRepair}): the mended piece can't be taken out of the
 * result slot till the fight is over.
 */
@Mixin(Slot.class)
abstract class ArmourWearSlotMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract ItemStack getItem();

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noRepairInAFight(Player player, CallbackInfoReturnable<Boolean> cir) {
		ItemStack out = this.getItem();
		if (!ArmourWear.armour(out)) {
			return;
		}
		Container inputs = null;
		if (player.containerMenu instanceof GrindstoneMenu grindstone && this.container == ((GrindstoneMenuAccessor) grindstone).jaymospowers$resultSlots()) {
			inputs = ((GrindstoneMenuAccessor) grindstone).jaymospowers$repairSlots();
		} else if ((Object) this instanceof ResultSlot result) {
			inputs = ((ResultSlotAccessor) result).jaymospowers$craftSlots();
		}
		if (inputs == null) {
			return;
		}
		// the most worn piece of the same armour that went in: the result mends it
		ItemStack before = ItemStack.EMPTY;
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack in = inputs.getItem(i);
			if (in.is(out.getItem()) && in.getDamageValue() > before.getDamageValue()) {
				before = in;
			}
		}
		if (!before.isEmpty() && ArmourWear.refuseRepair(player, before, out)) {
			cir.setReturnValue(false);
		}
	}
}
