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
 * A zombie whose skin the Sun reaches now runs for the nearest shade (a house, an overhang, a cave, a wall's shadow)
 * instead of just burning where it stands. It does so even mid-fight when shade is in reach, and goes straight back on
 * the attack once it is out of the Sun.
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
        goals.jaymospowers$goals().addGoal(1, new SeekShelterGoal(mob, false, true)); // ahead of the spear (2) and melee (3) attacks: the Sun beats the fight
    }
}
