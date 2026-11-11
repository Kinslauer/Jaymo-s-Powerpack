package com.jaymospowers.client.power.blindness.mixin;

import com.jaymospowers.client.power.blindness.BlindnessDevoutSight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The devout's sight: living things near them glow through walls, on their screen only. */
@Mixin(Minecraft.class)
abstract class BlindnessSightGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$devoutSight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && BlindnessDevoutSight.color(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
