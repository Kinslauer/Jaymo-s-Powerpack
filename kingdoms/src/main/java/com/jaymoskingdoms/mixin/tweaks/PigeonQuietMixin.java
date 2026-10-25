package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.Pigeon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.200) A messenger pigeon ({@link Pigeon}) is a parrot underneath, but it doesn't mimic the mobs about it. */
@Mixin(Parrot.class)
abstract class PigeonQuietMixin {
	@Inject(method = "imitateNearbyMobs", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$noMimicry(Level level, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Pigeon) {
			cir.setReturnValue(false);
		}
	}
}
