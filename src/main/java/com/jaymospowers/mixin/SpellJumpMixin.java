package com.jaymospowers.mixin;

import com.jaymospowers.core.MagicMoves;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A jump under the long jump spell ({@link MagicMoves}) goes higher and carries far on the way the jumper faces. */
@Mixin(LivingEntity.class)
abstract class SpellJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void jaymospowers$longJump(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!MagicMoves.longJump(self)) {
			return;
		}
		Vec3 motion = self.getDeltaMovement();
		Vec3 look = self.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 along = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(MagicMoves.JUMP_FORWARD);
		self.setDeltaMovement(motion.x + along.x, motion.y * MagicMoves.JUMP_HIGHER, motion.z + along.z);
	}
}
