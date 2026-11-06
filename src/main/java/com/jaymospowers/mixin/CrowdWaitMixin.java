package com.jaymospowers.mixin;

import com.jaymospowers.combat.Crowds;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.21.20) A goal-driven foe waiting its turn (see Crowds) doesn't close in or strike: Crowds walks it round the ring. */
@Mixin(MeleeAttackGoal.class)
abstract class CrowdWaitMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$waitTurn(CallbackInfo ci) {
		if (Crowds.waiting(this.mob)) {
			ci.cancel();
		}
	}
}
