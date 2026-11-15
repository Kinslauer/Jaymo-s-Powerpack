package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Formations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A soldier's shot passes over their own side: the archers behind a shield wall don't hit its front rank ({@link Formations#spares}). */
@Mixin(Projectile.class)
abstract class ProjectileAlliesMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$overTheWall(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Projectile self = (Projectile) (Object) this;
		if (!self.level().isClientSide() && Formations.spares(self.getOwner(), entity)) {
			cir.setReturnValue(false);
		}
	}
}
