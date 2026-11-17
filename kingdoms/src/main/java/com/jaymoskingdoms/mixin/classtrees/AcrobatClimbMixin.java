package com.jaymoskingdoms.mixin.classtrees;

import com.jaymoskingdoms.ClassForks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An Acrobat Thief climbs faster ({@link ClassForks#ACROBAT_CLIMB}): the climb up a ladder, vine or wall that vanilla
 * sets at 0.2 a tick is quicker for them. Runs on both sides, so the client's own movement climbs too.
 */
@Mixin(LivingEntity.class)
abstract class AcrobatClimbMixin {
	@Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$acrobatClimb(Vec3 input, float friction, CallbackInfoReturnable<Vec3> cir) {
		Vec3 movement = cir.getReturnValue();
		if ((Object) this instanceof Player player && movement.y == 0.2 && player.onClimbable() && ClassForks.acrobat(player)) {
			cir.setReturnValue(new Vec3(movement.x, 0.2 * ClassForks.ACROBAT_CLIMB, movement.z));
		}
	}
}
