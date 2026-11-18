package com.jaymospowers.client.bits.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Your held item stays in view in first person while riding. First-person Model (optional, nothing of it copied) draws the item
 * on your own body's hand, which sits out of the view once you're in a saddle, boat or cart, and unless the setting says
 * otherwise doesn't draw vanilla's hand and item over it. While you are a passenger holding something, it answers that vanilla's
 * hands should show.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.firstperson.LogicHandler", remap = false)
abstract class FirstPersonModelMountedHandsMixin {
	@ModifyReturnValue(method = "showVanillaHands(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), remap = false)
	private boolean jaymospowers$mounted(boolean original, ItemStack main, ItemStack off) {
		LocalPlayer player = Minecraft.getInstance().player;
		return original || player != null && player.isPassenger() && (!main.isEmpty() || !off.isEmpty());
	}
}
