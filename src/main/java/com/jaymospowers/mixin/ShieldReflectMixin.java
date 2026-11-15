package com.jaymospowers.mixin;

import com.jaymospowers.combat.ShieldCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shot touching a player with a shield raised in its perfect parry is sent back before it can hurt (ShieldCraft#reflect). */
@Mixin(Entity.class)
abstract class ShieldReflectMixin {
	@Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$shieldReflect(Projectile projectile, CallbackInfoReturnable<ProjectileDeflection> cir) {
		if ((Object) this instanceof ServerPlayer player && ShieldCraft.reflect(player, projectile)) {
			cir.setReturnValue(ShieldCraft.REFLECTED);
		}
	}
}
