package com.jaymoskingdoms.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Max health stops at 1024: a patron's champion in a big group at a high tier needs more, up to this. */
@Mixin(RangedAttribute.class)
abstract class MaxHealthCapMixin {
	/** The new top of the max health attribute. */
	private static final double MAX_HEALTH_CAP = 4096.0;

	@Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$healthCap(double value, CallbackInfoReturnable<Double> cir) {
		if ("attribute.name.max_health".equals(((Attribute) (Object) this).getDescriptionId())) {
			cir.setReturnValue(Double.isNaN(value) ? 1.0 : Mth.clamp(value, 1.0, MAX_HEALTH_CAP));
		}
	}
}
