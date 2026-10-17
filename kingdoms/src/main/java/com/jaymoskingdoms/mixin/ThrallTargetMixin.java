package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.NightbornBlood;
import com.jaymoskingdoms.Thralls;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.37) A necromancer's thralls never take their own side for a foe, nor their side's folk them ({@link Thralls#refuses}).
 * Every mob's targeting (goals, brains, {@code setTarget}) asks {@code canAttack}. (0.7.88) Nor do the dead risen under a
 * Blood Moon go for a Nightborn vampire ({@link NightbornBlood#spares}).
 */
@Mixin(LivingEntity.class)
public abstract class ThrallTargetMixin {
	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$spareOwnSide(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (Thralls.refuses((LivingEntity)(Object)this, target) || NightbornBlood.spares((LivingEntity)(Object)this, target)) {
			cir.setReturnValue(false);
		}
	}
}
