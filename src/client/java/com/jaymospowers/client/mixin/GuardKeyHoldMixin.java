package com.jaymospowers.client.mixin;

import com.jaymospowers.combat.GuardKey;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla lets an item in use down whenever right click is up. A guard the guard key (F) raised stays up while F is held
 * ({@code GuardKeyClient}); anything else is let down as ever.
 */
@Mixin(Minecraft.class)
abstract class GuardKeyHoldMixin {
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
	private void jaymospowers$keepGuard(MultiPlayerGameMode gameMode, Player player, Operation<Void> original) {
		if (GuardKey.localHeld && GuardKey.blocks(player.getUseItem())) {
			return;
		}
		original.call(gameMode, player);
	}
}
