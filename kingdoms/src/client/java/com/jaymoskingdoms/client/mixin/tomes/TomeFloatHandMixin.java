package com.jaymoskingdoms.client.mixin.tomes;

import com.jaymoskingdoms.Tomes;
import com.jaymoskingdoms.client.TomeFloat;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.7.390) Your own tome afloat ({@code TomeFloat}): in first person the hand that held it is drawn empty (0.7.398: the off hand too). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class TomeFloatHandMixin {
	@ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack jaymoskingdoms$bookAfloat(ItemStack stack, @Local(argsOnly = true) InteractionHand hand) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Tomes.isTome(stack) && TomeFloat.afloat(player, hand == InteractionHand.OFF_HAND) ? ItemStack.EMPTY : stack;
	}
}
