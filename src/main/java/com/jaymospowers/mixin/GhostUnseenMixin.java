package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Limbo;
import com.jaymospowers.core.wounds.OutOfBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-14): as a ghost, "mobs could still see and aggro/ try to attack me". A player out of their body
 * ({@link OutOfBody}) or waiting in {@link Limbo} is no one a mob can see: no mob takes them for a target, keeps them as one
 * ({@code Mob#getTarget} asks the same), or turns to look at them. What lies where they fell is still a body: scavengers
 * and the like find it by their own looking.
 */
@Mixin(LivingEntity.class)
abstract class GhostUnseenMixin {
	@Inject(method = "canBeSeenByAnyone", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ghostUnseen(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && (OutOfBody.out(player.getUUID()) || Limbo.in(player))) {
			cir.setReturnValue(false);
		}
	}
}
