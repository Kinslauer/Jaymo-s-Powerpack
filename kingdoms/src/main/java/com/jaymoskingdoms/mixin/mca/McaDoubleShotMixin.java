package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.BandStyles;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (fighting AI patch 7, st-archers) An MCA villager's bow shot has gone (the method's last return is the bow's; its crossbow's
 * returns earlier): a skilled archer's second arrow follows, spent from its quiver ({@link BandStyles#loosed}).
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaDoubleShotMixin {
	@Inject(method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("TAIL"))
	private void jaymoskingdoms$secondArrow(LivingEntity target, float power, CallbackInfo ci) {
		BandStyles.loosed((VillagerEntityMCA) (Object) this, target);
	}
}
