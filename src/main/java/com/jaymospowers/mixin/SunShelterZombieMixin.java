package com.jaymospowers.mixin;

import com.jaymospowers.core.sun.SeekShelterGoal;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A zombie whose skin the Sun reaches now looks for real shelter (a cave, somewhere underground, under a solid roof)
 * instead of just burning where it stands.
 */
@Mixin(Zombie.class)
public abstract class SunShelterZombieMixin {
    @Inject(method = "registerGoals", at = @At("HEAD"))
    private void jaymospowers$addShelterGoal(CallbackInfo ci) {
        Zombie mob = (Zombie) (Object) this;
        if (mob instanceof Husk || mob instanceof ZombifiedPiglin) {
            return; // they don't burn in the sun
        }
        MobGoalsAccessor goals = (MobGoalsAccessor) mob;
        goals.jaymospowers$goals().addGoal(5, new SeekShelterGoal(mob, false));
    }
}
