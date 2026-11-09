package com.jaymospowers.mixin;

import com.jaymospowers.core.Perch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A small animal perched on a player ({@link Perch}): the server lets it ride a player at all (vanilla won't let
 * anything ride what isn't saved with the world, and players aren't; a perch is let down when they leave), and on both
 * sides it sits on the shoulder, the head or draped across the back, facing the player's way.
 */
@Mixin(Entity.class)
abstract class PerchRidingMixin {
	@WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
	private boolean jaymospowers$perchOnPlayer(EntityType<?> type, Operation<Boolean> original) {
		// (the perch is marked just before it climbs on: only a perch rides a player this way)
		return original.call(type) || ((Entity)(Object)this).getAttached(Perch.PERCHED) != null;
	}

	@Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$perchSpot(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
		if (!((Object)this instanceof Player player)) {
			return;
		}
		int spot = Perch.spot(passenger);
		if (spot < 0) {
			return;
		}
		Vec3 at = Perch.ridingPosition(player, passenger, spot);
		move.accept(passenger, at.x, at.y, at.z);
		Perch.face(player, passenger, spot);
		ci.cancel();
	}
}
