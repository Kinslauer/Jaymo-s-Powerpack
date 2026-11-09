package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Riders;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A Rider breaks a wild horse (or llama) in far fewer tries: its taming roll counts more temper ({@link Riders#temper}). */
@Mixin(RunAroundLikeCrazyGoal.class)
abstract class RunAroundLikeCrazyGoalMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/equine/AbstractHorse;getTemper()I"))
	private int jaymoskingdoms$riderTemper(AbstractHorse horse) {
		return Riders.temper(horse, horse.getTemper());
	}
}
