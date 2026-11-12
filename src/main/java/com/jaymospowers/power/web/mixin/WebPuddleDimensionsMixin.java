package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebPuddle;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A host melted into a puddle ({@link WebPuddle}) has a flat box in every pose; both sides read the synced flag, so they agree. */
@Mixin(Avatar.class)
abstract class WebPuddleDimensionsMixin {
	@Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$puddle(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof Player player && WebPuddle.puddled(player)) {
			cir.setReturnValue(WebPuddle.DIMENSIONS);
		}
	}
}
