package com.jaymoskingdoms.mixin.provenance;

import com.jaymoskingdoms.Provenance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An item's origin ({@link Provenance}) is no part of what it is: two stacks that differ only in their stamps are the same
 * for stacking and hashing (the merged stack keeps the first one's stamp). And what is made at a bench, a furnace or a
 * smithing table is stamped as made, by whoever takes it out.
 */
@Mixin(ItemStack.class)
abstract class ItemStackOriginMixin {
	@Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$sameBesideOrigin(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
		ItemStack bareA = Provenance.bare(a), bareB = Provenance.bare(b);
		if (bareA != a || bareB != b) {
			cir.setReturnValue(ItemStack.isSameItemSameComponents(bareA, bareB));
		}
	}

	@Inject(method = "hashItemAndComponents", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$hashBesideOrigin(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		ItemStack bare = Provenance.bare(stack);
		if (bare != stack) {
			cir.setReturnValue(ItemStack.hashItemAndComponents(bare));
		}
	}

	@Inject(method = "onCraftedBy", at = @At("HEAD"))
	private void jaymoskingdoms$made(Player player, int count, CallbackInfo ci) {
		Provenance.made((ItemStack)(Object)this, player);
	}
}
