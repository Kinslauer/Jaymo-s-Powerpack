package com.jaymospowers.compat.mixin;

import com.jaymospowers.combat.RegionSwings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While locked on, a player's swing is picked from the attacks that suit the body region aimed at
 * ({@link RegionSwings#fit}). Better Combat makes this pick on both sides from the same combo count. Applied before
 * Kingdoms' Weapon Forms cut (lower priority), so that cut keeps the basic two of what fits.
 */
@Mixin(value = PlayerAttackHelper.class, remap = false, priority = 900)
abstract class RegionSwingMixin {
	@ModifyExpressionValue(method = "selectAttack", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"))
	private static Object[] jaymospowers$regionSwings(Object[] attacks, @Local(argsOnly = true) WeaponAttributes weapon,
		@Local(argsOnly = true) Player player, @Local(argsOnly = true) boolean offHand) {
		return RegionSwings.fit((WeaponAttributes.Attack[]) attacks, weapon, player, offHand);
	}
}
