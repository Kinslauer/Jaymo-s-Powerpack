package com.jaymoskingdoms.mixin.heritage;

import com.jaymoskingdoms.HeritageKits;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.490) Heavy in water (the Emberborn's flaw, {@link HeritageKits#heavyInWater}): a swim ignores speed attributes (and water
 * movement efficiency stops at 0), so after each tick's swim its sideways speed is cut and it's pulled down. Runs where the
 * movement is worked out: the player's own client, the server for a villager.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHeavyWaterMixin {
	@Inject(method = "travelInWater", at = @At("TAIL"))
	private void jaymoskingdoms$heavyInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (HeritageKits.heavyInWater(self)) {
			Vec3 v = self.getDeltaMovement();
			self.setDeltaMovement(v.x * HeritageKits.HEAVY_DRAG, v.y - HeritageKits.HEAVY_SINK, v.z * HeritageKits.HEAVY_DRAG);
		}
	}
}
