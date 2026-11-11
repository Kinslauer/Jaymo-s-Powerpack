package com.jaymospowers.rig.mixin;

import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Rig on a quiet frame ({@link RigFrame#detect}) is noticed late: the share of a mob's sight range the wearer is
 * seen at is scaled down, in proportion to how many pieces are worn, and further still while sneaking.
 */
@Mixin(LivingEntity.class)
abstract class RigVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$rigQuiet(ServerLevel level, Entity targeting, CallbackInfoReturnable<Double> cir) {
		if (!((Object)this instanceof Player player) || !Rig.wearsAny(player)) {
			return;
		}
		RigFrame frame = Rig.frame(player);
		if (frame.detect >= 1.0) {
			return;
		}
		double share = frame.detect * (player.isCrouching() ? RigFrame.SNEAK_SHARE : 1.0);
		cir.setReturnValue(cir.getReturnValue() * share);
	}
}
