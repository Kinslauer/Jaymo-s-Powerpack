package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.ClassTrees;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Something taken out of a crafting grid: class experience for what a class makes ({@link ClassTrees#crafted}). */
@Mixin(ResultSlot.class)
abstract class ClassCraftMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void jaymoskingdoms$classMade(Player player, ItemStack carried, CallbackInfo ci) {
		ClassTrees.crafted(player, carried);
	}
}
