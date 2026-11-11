package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.core.sun.SunExposure;
import com.jaymospowers.power.chrono.future.Future;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Burn in daylight only when the sky is truly open to the Sun: a ray from the mob's head toward the Sun's real
 * position must pass unobstructed. This replaces {@code Mob}'s {@code canSeeSky} check in {@code isSunBurnTick},
 * so tree shade no longer counts as cover. FutureSunMixin still owns the future (nothing burns under the ashen sun)
 * - this mixin bails out for {@code Future.FUTURE} and lets it set the result whichever of the two runs first.
 */
@Mixin(Mob.class)
public abstract class SunBurnMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void jaymospowers$sunRay(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob) (Object) this;
        Level level = mob.level();
        if (level.dimension() == Future.FUTURE) {
            return; // FutureSunMixin: nothing burns under the future's ashen sun
        }
        if (!level.isClientSide() && level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, mob.position())) {
            float br = mob.getLightLevelDependentMagicValue();
            if (br > 0.5F && mob.getRandom().nextFloat() * 30.0F < (br - 0.4F) * 2.0F && !mob.isInWaterOrRain() && !mob.isInPowderSnow && !mob.wasInPowderSnow) {
                cir.setReturnValue(SunExposure.exposedToSun(level, mob.getEyePosition()));
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
