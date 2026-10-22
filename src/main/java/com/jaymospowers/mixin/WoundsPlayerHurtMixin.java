package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Wounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.15.94) {@link WoundsHurtMixin} for players, whose {@code actuallyHurt} is their own. */
@Mixin(Player.class)
abstract class WoundsPlayerHurtMixin {
	@WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
	private void jaymospowers$woundsFirst(Player self, float health, Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
		original.call(self, Wounds.takeHit(self, source, self.getHealth() - health, health));
	}
}
