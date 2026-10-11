package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktideGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A guarding Darktide blade sends projectiles back at their shooter (see DarktideGuard#deflection). */
@Mixin(Entity.class)
abstract class DarktideDeflectMixin {
	@Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideReflect(Projectile projectile, CallbackInfoReturnable<ProjectileDeflection> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		ProjectileDeflection deflection = DarktideGuard.deflection(self, projectile);
		if (deflection != null) {
			cir.setReturnValue(deflection);
		}
	}
}
