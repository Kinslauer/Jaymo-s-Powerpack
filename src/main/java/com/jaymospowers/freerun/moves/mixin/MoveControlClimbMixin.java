package com.jaymospowers.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.NpcClimb;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where a mob would jump at the way's next step, a climber pulls itself up over it instead when it's higher than a jump
 * reaches ({@link NpcClimb#tryStart}); mid-climb it doesn't jump at all.
 */
@Mixin(MoveControl.class)
abstract class MoveControlClimbMixin {
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected double wantedX;
	@Shadow
	protected double wantedY;
	@Shadow
	protected double wantedZ;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/JumpControl;jump()V"))
	private void jaymospowers$pullUp(JumpControl control, Operation<Void> original) {
		if (NpcClimb.climbing(this.mob) || NpcClimb.tryStart(this.mob, this.wantedX, this.wantedY, this.wantedZ)) {
			return;
		}
		original.call(control);
	}
}
