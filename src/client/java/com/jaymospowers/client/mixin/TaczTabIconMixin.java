package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.TaczGuns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ's creative tabs show our flintlocks and musket balls instead of modern guns and ammo (see TaczGuns.tabIcon). */
@Mixin(CreativeModeTab.class)
abstract class TaczTabIconMixin {
	@Unique
	private ItemStack jaymospowers$icon;

	@Inject(method = "getIconItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$taczIcon(CallbackInfoReturnable<ItemStack> cir) {
		if (!TaczGuns.LOADED) {
			return;
		}
		if (jaymospowers$icon == null) {
			Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this);
			jaymospowers$icon = id == null ? ItemStack.EMPTY : TaczGuns.tabIcon(id);
		}
		if (!jaymospowers$icon.isEmpty()) {
			cir.setReturnValue(jaymospowers$icon);
		}
	}
}
