package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.Webbed;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Webbed up ({@link Webbed}): while any layer holds, no walking (the walking input is dropped, as Derelict's Cobwebbed
 * does, mim1q, MIT) and no sprint, so a jump is only a hop in place; and adding the effect from anywhere wraps that many
 * layers on.
 */
@Mixin(LivingEntity.class)
abstract class WebbedLivingEntityMixin {
	@ModifyVariable(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
	private Vec3 jaymospowers$webbedStill(Vec3 input) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!Webbed.held(self)) {
			return input;
		}
		if (self.isSprinting()) {
			self.setSprinting(false);
		}
		return new Vec3(0.0, Math.min(0.0, input.y), 0.0);
	}

	@Inject(method = "jumpFromGround", at = @At("HEAD"))
	private void jaymospowers$webbedHop(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isSprinting() && Webbed.held(self)) {
			self.setSprinting(false);
		}
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$webbedLayers(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (Webbed.EFFECT != null && effect.is(Webbed.EFFECT)) {
			cir.setReturnValue(Webbed.wrap((LivingEntity) (Object) this, effect.getDuration(), effect.getAmplifier() + 1));
		}
	}
}
