package com.jaymospowers.power.wind.mixin;

import com.jaymospowers.power.wind.WindHeavy;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A Wind user's melee hit with an axe or mace is lighter (the main hit and the sweep), see {@link WindHeavy}. */
@Mixin(Player.class)
abstract class WindPlayerAttackMixin {
	@ModifyArg(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
	private float jaymospowers$windHeavyHit(float damage) {
		return WindHeavy.scale((Player)(Object)this, damage);
	}

	@ModifyArg(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V"),
		index = 1)
	private float jaymospowers$windHeavySweep(float damage) {
		return WindHeavy.scale((Player)(Object)this, damage);
	}
}
