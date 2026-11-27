package com.jaymoskingdoms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A blast hurts a huge creature by how near it is to the nearest of it, not to its feet (after Dragonforge's giant
 * blasts, RochStudio): TNT under a dragon's wing or a creeper at a Giant's toe used to count from the middle of a body
 * many blocks across, and so did next to nothing. Anything at least {@link #BIG} wide is measured to its box.
 */
@Mixin(ServerExplosion.class)
abstract class BigBlastMixin {
	/** How wide (blocks) a creature must be to be measured to its box. */
	@org.spongepowered.asm.mixin.Unique
	private static final float BIG = 2.0F;

	@WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
	private double jaymoskingdoms$nearestOfIt(Entity entity, Vec3 center, Operation<Double> original) {
		double feet = original.call(entity, center);
		return entity.getBbWidth() >= BIG ? Math.min(feet, entity.getBoundingBox().distanceToSqr(center)) : feet;
	}
}
