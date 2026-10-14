package com.jaymoskingdoms.mixin.sleep;

import com.jaymoskingdoms.BedRest;
import com.jaymoskingdoms.Timelapse;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.4) A player resting in bed ({@link BedRest}) isn't turned out of it by daylight, and is never "asleep long enough",
 * so they can't bring on the morning (or anything else that waits on a full sleep). Sleepers aren't turned out by the
 * dawn while the night runs by ({@link Timelapse}): it wakes them itself on reaching the morning.
 */
@Mixin(Player.class)
abstract class PlayerRestMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
	private boolean jaymoskingdoms$restByDay(BedRule rule, Level level) {
		return BedRest.resting((Player) (Object) this) || Timelapse.running() || rule.canSleep(level);
	}

	@Inject(method = "isSleepingLongEnough", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$restIsNotSleep(CallbackInfoReturnable<Boolean> cir) {
		if (BedRest.resting((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
