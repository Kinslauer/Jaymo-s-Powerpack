package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.Mending;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wood and flint tools aren't mended at the anvil ({@link Mending#unmendable}): a result that would mend one is taken away. */
@Mixin(AnvilMenu.class)
abstract class UnmendableAnvilMixin {
	@Shadow
	@Final
	private DataSlot cost;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void jaymospowers$noMending(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack input = menu.getSlot(0).getItem();
		ItemStack result = menu.getSlot(2).getItem();
		if (!result.isEmpty() && Mending.unmendable(input) && result.getDamageValue() < input.getDamageValue()) {
			menu.getSlot(2).set(ItemStack.EMPTY);
			cost.set(0);
		}
	}
}
