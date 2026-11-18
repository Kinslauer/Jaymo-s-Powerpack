package com.jaymospowers.mixin;

import com.jaymospowers.mutation.ConjoinedTwins;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions a conjoined twin baby passenger on a zombie's shoulder. On {@link Entity}'s own positionRider, as
 * {@code PerchRidingMixin} is: Zombie doesn't override it in 26.3.
 */
@Mixin(Entity.class)
abstract class ConjoinedTwinZombieMixin {
	@Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$positionConjoinedTwin(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
		if (!((Object) this instanceof Zombie zombie)) {
			return;
		}
		Entity conjoinedTwin = ConjoinedTwins.getConjoinedTwin(zombie);
		if (conjoinedTwin == null || passenger != conjoinedTwin) {
			return;
		}
		// Position on left shoulder (zombies are slightly hunched, so adjust)
		double height = zombie.getBbHeight();
		float yaw = zombie.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));

		// Shoulder position: slightly to the left, up at ~70% height (zombies hunch), slightly forward
		double side = -0.3;
		double up = height * 0.7;
		double forward = 0.1;

		Vec3 pos = zombie.position().add(right.scale(side)).add(ahead.scale(forward)).add(0.0, up, 0.0);
		moveFunction.accept(passenger, pos.x, pos.y, pos.z);

		// Face the same direction as the zombie
		passenger.setYRot(zombie.yBodyRot);
		passenger.yRotO = zombie.yBodyRot;
		if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
			living.yBodyRot = zombie.yBodyRot;
			living.yBodyRotO = zombie.yBodyRot;
			living.setYHeadRot(zombie.yBodyRot);
		}
		ci.cancel();
	}
}
