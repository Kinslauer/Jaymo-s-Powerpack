package com.jaymospowers.mixin;

import com.jaymospowers.combat.LockedSwings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link LockedSwings}: locked on, a swing at anyone but the locked foe never happens (Better Combat calls this for each
 * thing in its arc). Ahead of every other hook on the attack (a lower priority goes first), so a skipped swing isn't
 * counted, tired from or charged by them.
 */
@Mixin(value = Player.class, priority = 500)
abstract class LockedSwingMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$onlyTheLocked(Entity target, CallbackInfo ci) {
		if (!LockedSwings.strikes((Player) (Object) this, target)) {
			ci.cancel();
		}
	}
}
