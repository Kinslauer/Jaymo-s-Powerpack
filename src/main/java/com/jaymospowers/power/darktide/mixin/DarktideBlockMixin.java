package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktideGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Darktide guard takes its share of a hit where a shield would (so vanilla treats a parry as a full block: no
 * knockback, no hurt flash). Anything the guard has nothing to do with falls through to vanilla.
 */
@Mixin(LivingEntity.class)
abstract class DarktideBlockMixin {
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideGuard(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float blocked = DarktideGuard.blocked((LivingEntity) (Object) this, level, source, damage);
		if (blocked >= 0.0F) {
			cir.setReturnValue(blocked);
		}
	}
}
