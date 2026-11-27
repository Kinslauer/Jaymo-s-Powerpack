package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ThinkFirst;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every mob thinks a second before setting on a player it has just picked out ({@link ThinkFirst}): a combat sighting that
 * passed is held back until then. Every target goal and every brain's attack sensor asks here, so this one hook covers both.
 */
@Mixin(TargetingConditions.class)
abstract class TargetingThinkMixin {
	@Shadow
	@Final
	private boolean isCombat;

	@Inject(method = "test", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$thinkFirst(ServerLevel level, LivingEntity source, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (this.isCombat && cir.getReturnValueZ() && !ThinkFirst.ready(source, target)) {
			cir.setReturnValue(false);
		}
	}
}
