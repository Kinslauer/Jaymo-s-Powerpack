package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktidePower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Veil: mobs only notice a veiled Darktide user within the veil's range (their follow range is scaled down to it). */
@Mixin(LivingEntity.class)
abstract class DarktideVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$darktideVeil(ServerLevel level, Entity targeting, CallbackInfoReturnable<Double> cir) {
		if (!((Object) this instanceof Player player) || !(targeting instanceof LivingEntity looker)) {
			return;
		}
		double range = DarktidePower.veilRange(player);
		if (range < 0.0) {
			return;
		}
		double follow = looker.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? looker.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
		if (follow > range) {
			cir.setReturnValue(cir.getReturnValue() * range / follow);
		}
	}
}
