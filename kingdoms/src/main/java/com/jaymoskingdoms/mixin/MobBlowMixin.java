package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Blows;
import com.jaymoskingdoms.DualBlades;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A mob's melee blow, as it lands ({@code Mob.doHurtTarget}): noted for spacing, and a heavy weapon's blow falling from a jump
 * for it is a critical ({@link Blows#dealt}); a two-bladed fighter's off-hand turn hits with its off-hand blade ({@link DualBlades#dealt}).
 */
@Mixin(Mob.class)
abstract class MobBlowMixin {
	@ModifyArg(method = "doHurtTarget",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
		index = 2)
	private float jaymoskingdoms$falling(float damage) {
		return DualBlades.dealt((Mob) (Object) this, Blows.dealt((Mob) (Object) this, damage));
	}
}
