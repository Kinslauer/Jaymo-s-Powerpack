package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Cover;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A shot about to hit someone behind cover may hit the cover instead, and stick in it ({@link Cover}). */
@Mixin(Projectile.class)
abstract class ProjectileCoverMixin {
	@Shadow
	protected abstract void onHitBlock(BlockHitResult hit);

	@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$cover(HitResult hit, CallbackInfo ci) {
		Projectile self = (Projectile) (Object) this;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && self.level() instanceof ServerLevel level) {
			BlockHitResult block = Cover.takes(level, self, target);
			if (block != null) {
				Cover.stuck(level, block, target);
				this.onHitBlock(block);
				ci.cancel();
			}
		}
	}
}
