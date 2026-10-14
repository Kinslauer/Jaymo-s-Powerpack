package com.jaymospowers.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The scale attribute stops at 16 (under 29 blocks for a player): the Great Ape needs more, up to {@link ZFighterLineage#MAX_SCALE}. */
@Mixin(RangedAttribute.class)
abstract class ZFighterScaleMixin {
	@Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$apeScale(double value, CallbackInfoReturnable<Double> cir) {
		if ("attribute.name.scale".equals(((Attribute) (Object) this).getDescriptionId())) {
			cir.setReturnValue(Double.isNaN(value) ? 0.0625 : Mth.clamp(value, 0.0625, ZFighterLineage.MAX_SCALE));
		}
	}
}
