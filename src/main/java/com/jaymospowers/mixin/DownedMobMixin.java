package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.14.1, from Kingdoms' 0.7.73) A downed mob ({@link Downed}) has no AI (goals, brain, pathing): it gets up, bleeds out, or crawls. (0.19.20) Nor does
 * one executing a downed player ({@code MobExecutions}): it only walks and looks where that says. */
@Mixin(Mob.class)
abstract class DownedMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$downed(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (Downed.isDowned(self)) {
			Downed.step(self);
			ci.cancel();
		} else if (com.jaymospowers.core.wounds.MobExecutions.step(self)) {
			// (0.19.20) walking over to execute a downed player, or standing over them: its own AI set aside
			ci.cancel();
		}
	}
}
