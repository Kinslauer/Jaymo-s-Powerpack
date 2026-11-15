package com.jaymoskingdoms.mixin.heritage;

import com.jaymoskingdoms.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Agility is traversal speed, not only walking speed ({@link Stats#agility}, Andy's skill-tree locks): a player's swim
 * strokes push this much harder, and they go up ladders, vines and scaffolding this much faster. Runs where the movement
 * is worked out (the player's own client). Walking, balancing and crawling already take it from the walking speed.
 */
@Mixin(LivingEntity.class)
abstract class AgilityTravelMixin {
	@ModifyArg(method = "travelInWater", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"), index = 0)
	private float jaymoskingdoms$agileSwim(float speed) {
		return (Object) this instanceof Player player ? speed * (float) (1.0 + Stats.agility(player)) : speed;
	}

	@ModifyConstant(method = "handleRelativeFrictionAndCalculateMovement", constant = @Constant(doubleValue = 0.2))
	private double jaymoskingdoms$agileClimb(double up) {
		return (Object) this instanceof Player player ? up * (1.0 + Stats.agility(player)) : up;
	}
}
