package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Visitors;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Residency;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.299) A visitor for the day ({@link Visitors}) never joins the town they're visiting, nor claims a home in it. */
@Mixin(Residency.class)
abstract class McaVisitorResidencyMixin {
	@Shadow
	@Final
	private VillagerEntityMCA entity;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$visiting(CallbackInfo ci) {
		if (Visitors.is(this.entity)) {
			ci.cancel();
		}
	}
}
