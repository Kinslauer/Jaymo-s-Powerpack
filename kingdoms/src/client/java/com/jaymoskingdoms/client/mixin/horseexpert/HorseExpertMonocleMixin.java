package com.jaymoskingdoms.client.mixin.horseexpert;

import com.jaymoskingdoms.TreeLocks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Andy (Vanilla+, Horse Expert row): "add to the tree", the K skill tree. Horse Expert shows a mount's speed, jump,
 * health and storage while you wear its Monocle; Horseman rank 1 (the horse whistle's rank, free to a Rider) reads them
 * without one. Its check is "is the monocle slot empty": with rank 1 it answers no. The monocle still works for anyone.
 * Its sneak and tamed-only settings still apply. Skipped when Horse Expert isn't there.
 */
@Pseudo
@Mixin(targets = "fuzs.horseexpert.common.client.handler.AttributeOverlayHandler", remap = false)
abstract class HorseExpertMonocleMixin {
	@ModifyExpressionValue(method = "getInspectableEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"),
		require = 0)
	private static boolean jaymoskingdoms$horsemanReads(boolean noMonocle) {
		Minecraft minecraft = Minecraft.getInstance();
		return noMonocle && (minecraft.player == null || !TreeLocks.has(minecraft.player, TreeLocks.Lock.WHISTLE));
	}
}
