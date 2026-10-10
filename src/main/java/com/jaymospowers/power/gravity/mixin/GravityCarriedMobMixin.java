package com.jaymospowers.power.gravity.mixin;

import com.jaymospowers.core.Carry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried mob's mind is on hold (any carry, though only Gravity lifts the dangerous ones): no goals, brain,
 * pathing, targeting or attacks, and a creeper stops swelling. It all comes back the moment it's let go.
 */
@Mixin(Mob.class)
abstract class GravityCarriedMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carriedNoAi(CallbackInfo ci) {
		Mob mob = (Mob)(Object)this;
		if (!Carry.isCarried(mob)) {
			return;
		}
		mob.getNavigation().stop();
		if (mob.getTarget() != null) {
			mob.setTarget(null);
		}
		if (mob.isAggressive()) {
			mob.setAggressive(false);
		}
		mob.setXxa(0.0F);
		mob.setYya(0.0F);
		mob.setZza(0.0F);
		mob.setJumping(false);
		if (mob instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		ci.cancel();
	}
}
