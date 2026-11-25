package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.MobShields;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (fighting AI patch 3, sh-busy) An MCA villager's bow or crossbow shot (MCA's bow task, its crossbow task) never goes with the
 * shield up: held up for a shot on its way, the shot waits; up for anything else, it comes down to fire
 * ({@link MobShields#holdsFire}).
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaShieldFireMixin {
	@Inject(method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shieldDown(LivingEntity target, float power, CallbackInfo ci) {
		if (MobShields.holdsFire((VillagerEntityMCA) (Object) this, false)) {
			ci.cancel();
		}
	}
}
