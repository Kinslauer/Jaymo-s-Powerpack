package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Climbing;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A walker follows (and plans) its path only on the ground or swimming; a fighter climbing a ladder ({@link Climbing}) is
 * neither, so its path would be dropped half way up. On a ladder it keeps following it.
 */
@Mixin(GroundPathNavigation.class)
abstract class GroundPathNavigationMixin extends PathNavigation {
	GroundPathNavigationMixin(Mob mob, Level level) {
		super(mob, level);
	}

	@Inject(method = "canUpdatePath", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$onLadder(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Climbing.follows(this.mob)) {
			cir.setReturnValue(true);
		}
	}
}
