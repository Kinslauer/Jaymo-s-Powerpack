package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.core.sun.SunExposure;
import com.jaymospowers.power.chrono.future.Future;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
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
                // Burn if any body point (feet, chest, head) is exposed to the sun
                Vec3 eye = mob.getEyePosition();
                // Feet: bottom of the entity's bounding box
                Vec3 feet = new Vec3(mob.getX(), mob.getY(), mob.getZ());
                // Head: top of the entity's bounding box
                Vec3 head = new Vec3(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
                Vec3 chest = new Vec3(mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ());
                boolean eyeExposed = SunExposure.exposedToSun(level, eye);
                boolean feetExposed = SunExposure.exposedToSun(level, feet);
                boolean headExposed = SunExposure.exposedToSun(level, head);
                boolean chestExposed = SunExposure.exposedToSun(level, chest);
                // Also check hitbox corners at feet and head level
                // Feet corners
                Vec3 feetCorner1 = new Vec3(mob.getBoundingBox().minX, mob.getY(), mob.getBoundingBox().minZ);
                Vec3 feetCorner2 = new Vec3(mob.getBoundingBox().maxX, mob.getY(), mob.getBoundingBox().minZ);
                Vec3 feetCorner3 = new Vec3(mob.getBoundingBox().minX, mob.getY(), mob.getBoundingBox().maxZ);
                Vec3 feetCorner4 = new Vec3(mob.getBoundingBox().maxX, mob.getY(), mob.getBoundingBox().maxZ);
                // Head corners
                Vec3 headCorner1 = new Vec3(mob.getBoundingBox().minX, mob.getY() + mob.getBbHeight(), mob.getBoundingBox().minZ);
                Vec3 headCorner2 = new Vec3(mob.getBoundingBox().maxX, mob.getY() + mob.getBbHeight(), mob.getBoundingBox().minZ);
                Vec3 headCorner3 = new Vec3(mob.getBoundingBox().minX, mob.getY() + mob.getBbHeight(), mob.getBoundingBox().maxZ);
                Vec3 headCorner4 = new Vec3(mob.getBoundingBox().maxX, mob.getY() + mob.getBbHeight(), mob.getBoundingBox().maxZ);
                boolean feetCornerExposed = SunExposure.exposedToSun(level, feetCorner1)
                    || SunExposure.exposedToSun(level, feetCorner2)
                    || SunExposure.exposedToSun(level, feetCorner3)
                    || SunExposure.exposedToSun(level, feetCorner4);
                boolean headCornerExposed = SunExposure.exposedToSun(level, headCorner1)
                    || SunExposure.exposedToSun(level, headCorner2)
                    || SunExposure.exposedToSun(level, headCorner3)
                    || SunExposure.exposedToSun(level, headCorner4);
                cir.setReturnValue(eyeExposed || feetExposed || headExposed || chestExposed || feetCornerExposed || headCornerExposed);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
