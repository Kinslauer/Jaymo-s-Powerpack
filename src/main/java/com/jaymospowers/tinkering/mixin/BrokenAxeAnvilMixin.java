package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.ranged.Ranged;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A throwing axe that broke on a hit is mended at the anvil with three iron nuggets ({@link Ranged#mends}). */
@Mixin(AnvilMenu.class)
abstract class BrokenAxeAnvilMixin {
	@Shadow
	@Final
	private DataSlot cost;
	@Shadow
	private int repairItemCountCost;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void jaymospowers$mendAxe(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack axe = menu.getSlot(0).getItem();
		if (Ranged.mends(axe, menu.getSlot(1).getItem())) {
			menu.getSlot(2).set(Ranged.mended(axe));
			cost.set(1);
			repairItemCountCost = Ranged.MEND_NUGGETS;
		}
	}
}
