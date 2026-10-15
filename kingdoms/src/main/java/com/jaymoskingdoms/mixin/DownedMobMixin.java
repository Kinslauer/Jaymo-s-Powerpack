package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Downed;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.70) A downed guard ({@link Downed}) has no AI (goals, brain, pathing): it bleeds out, and maybe crawls. */
@Mixin(Mob.class)
abstract class DownedMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$downed(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (Downed.isDowned(self)) {
			Downed.step(self);
			ci.cancel();
		}
	}
}
