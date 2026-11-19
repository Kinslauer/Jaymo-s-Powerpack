package com.jaymospowers.mixin;

import com.jaymospowers.fighting.SnowballSting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A snowball's hit hurts a little and chills (see SnowballSting) instead of dealing nothing. */
@Mixin(Snowball.class)
abstract class SnowballStingMixin {
	@Redirect(method = "onHitEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
	private void jaymospowers$sting(Entity target, DamageSource source, float damage) {
		Snowball ball = (Snowball) (Object) this;
		target.hurt(source, ball.level() instanceof ServerLevel ? SnowballSting.hit(ball, target, damage) : damage);
	}
}
