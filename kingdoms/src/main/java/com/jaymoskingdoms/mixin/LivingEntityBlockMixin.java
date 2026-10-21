package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobShields;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.105) A blow taken on a mob's raised shield: an axe knocks it aside, as vanilla does only to a player's
 * ({@link MobShields#blocked}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityBlockMixin {
	@Inject(method = "blockUsingItem", at = @At("TAIL"))
	private void jaymoskingdoms$knockAside(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked,
		CallbackInfo ci) {
		if ((Object) this instanceof Mob mob) {
			MobShields.blocked(level, mob, attacker);
		}
	}
}
