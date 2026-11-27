package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.bomb.BombStatus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An ender pearl thrown by one who is snuffed (or landing in a snuffed place) fizzles: it takes no one anywhere ({@link BombStatus}). */
@Mixin(ThrownEnderpearl.class)
abstract class BombStatusPearlMixin {
	@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$snuffedPearl(HitResult hit, CallbackInfo ci) {
		ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		if (self.getOwner() instanceof LivingEntity owner && BombStatus.snuffed(owner) || BombStatus.zone(self.level(), self.blockPosition())) {
			self.discard();
			ci.cancel();
		}
	}
}
