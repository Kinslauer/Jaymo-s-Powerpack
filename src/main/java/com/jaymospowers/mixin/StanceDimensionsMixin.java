package com.jaymospowers.mixin;

import com.jaymospowers.core.Stance;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.57) A player in the low crouch is a shorter box than a sneak (both sides read the synced flag, so they agree). */
@Mixin(Avatar.class)
abstract class StanceDimensionsMixin {
	@Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$lowCrouch(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof Player player && pose == Pose.CROUCHING) {
			cir.setReturnValue(Stance.dimensions(player, pose, cir.getReturnValue()));
		}
	}
}
