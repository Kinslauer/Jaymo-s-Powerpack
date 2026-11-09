package com.jaymospowers.mixin;

import com.jaymospowers.core.Perch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Riding a player. Vanilla won't let anything ride what isn't saved with the world (a rider is saved inside its
 * vehicle), and players aren't, so on a server nothing could mount a player at all: not a perch ({@link Perch}), nor an
 * aerbunny, nor a Xenoform's riders. Here anything may, and while it rides a player it's saved on its own in its chunk,
 * where it is (an autosave or a crash keeps it; on reload it's simply off). A player who logs out drops their riders
 * first (vanilla). A perched animal sits on the shoulder, the head or draped across the back, facing the player's way.
 */
@Mixin(Entity.class)
abstract class PerchRidingMixin {
	@WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
	private boolean jaymospowers$rideAPlayer(EntityType<?> type, Operation<Boolean> original, @Local(argsOnly = true) Entity entityToRide) {
		return original.call(type) || entityToRide instanceof Player;
	}

	/** Riding a player, it's saved on its own (vanilla saves riders only inside their vehicle, and a player isn't saved there). */
	@Inject(method = "shouldBeSaved", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$savedOffPlayer(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity)(Object)this;
		if (!cir.getReturnValueZ() && self.getVehicle() instanceof Player && (self.getRemovalReason() == null || self.getRemovalReason().shouldSave())
			&& !(self instanceof Player)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$saveOffPlayer(ValueOutput output, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity)(Object)this;
		if (self.getVehicle() instanceof Player && !(self instanceof Player)) {
			cir.setReturnValue(self.saveAsPassenger(output));
		}
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
