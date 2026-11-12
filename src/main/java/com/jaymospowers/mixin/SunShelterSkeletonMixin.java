package com.jaymospowers.mixin;

import com.jaymospowers.core.sun.SeekShelterGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skeleton or stray whose skin the Sun reaches now looks for real shelter (a cave, somewhere underground, under a
 * solid roof) instead of fleeing to the first patch of leaf shade it finds. Registered before {@code FleeSunGoal} so
 * real shelter wins; random shade remains the fallback when nothing real is in reach.
 */
@Mixin(AbstractSkeleton.class)
public abstract class SunShelterSkeletonMixin {
    @Inject(method = "registerGoals", at = @At("HEAD"))
    private void jaymospowers$addShelterGoal(CallbackInfo ci) {
        AbstractSkeleton mob = (AbstractSkeleton) (Object) this;
        MobGoalsAccessor goals = (MobGoalsAccessor) mob;
        goals.jaymospowers$goals().addGoal(3, new SeekShelterGoal(mob, true));
    }
}
