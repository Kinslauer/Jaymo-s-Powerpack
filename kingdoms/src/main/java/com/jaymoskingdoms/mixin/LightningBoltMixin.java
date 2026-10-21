package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.disasters.Storms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.120) A storm cell's bolts ({@link Storms#BOLT_TAG}) light a fire only now and then ({@link
 * Storms#STRIKE_FIRE_CHANCE}), and then only where they struck: vanilla lights it and up to four around it, and again on
 * each after-flash. Other lightning (vanilla's, tridents, powers) is left alone.
 */
@Mixin(LightningBolt.class)
abstract class LightningBoltMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;spawnFire(I)V"))
	private void jaymoskingdoms$stormFire(LightningBolt bolt, int additionalSources, Operation<Void> original) {
		if (!bolt.entityTags().contains(Storms.BOLT_TAG)) {
			original.call(bolt, additionalSources);
		} else if (additionalSources > 0 && bolt.getRandom().nextFloat() < Storms.STRIKE_FIRE_CHANCE) {
			// the first strike (Normal and Hard only, as vanilla), never the after-flashes
			original.call(bolt, 0);
		}
	}
}
