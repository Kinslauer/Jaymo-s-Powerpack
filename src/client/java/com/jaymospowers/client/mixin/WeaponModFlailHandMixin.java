package com.jaymospowers.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.16.8) A flail in a player's hand is drawn as Balkon's bare handle (its "thrown" model): the chain and ball hanging
 * off it are LashRenderer's, physical, so they swing from the handle instead of being painted on. Icons keep the ball.
 */
@Mixin(targets = "ckathode.weaponmod.item.WMItemProperties$FlailThrown")
abstract class WeaponModFlailHandMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$bareHandle(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context,
		CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player && (context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
			cir.setReturnValue(true);
		}
	}
}
