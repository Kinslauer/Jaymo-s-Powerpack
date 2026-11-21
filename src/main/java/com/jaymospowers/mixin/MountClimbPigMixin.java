package com.jaymospowers.mixin;

import com.jaymospowers.core.MountClimb;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player steps over onto a saddled pig before riding it ({@link MountClimb}). */
@Mixin(Pig.class)
abstract class MountClimbPigMixin {
	@WrapOperation(method = "mobInteract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;startRiding(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean jaymospowers$climbOn(Player player, Entity pig, Operation<Boolean> original) {
		return MountClimb.begin(player, pig) || original.call(player, pig);
	}
}
