package com.jaymoskitchen.mixin;

import com.jaymoskitchen.tanning.HideTraits;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Horse leather ({@link HideTraits}): sprinting costs a little less hunger. */
@Mixin(Player.class)
abstract class HideTraitsPlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float jaymoskitchen$supple(float exhaustion) {
		Player player = (Player) (Object) this;
		return player.isSprinting() ? HideTraits.sprintCost(player, exhaustion) : exhaustion;
	}
}
