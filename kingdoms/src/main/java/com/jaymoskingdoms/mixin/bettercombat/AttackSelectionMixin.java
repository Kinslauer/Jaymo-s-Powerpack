package com.jaymoskingdoms.mixin.bettercombat;

import com.jaymoskingdoms.WeaponForms;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Arrays;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.30) Weapon Forms ({@link WeaponForms}): a player who hasn't learnt them picks from only the weapon's basic swings.
 * Better Combat makes this pick on both sides (the client's swing, the server's hit) from the same combo count.
 */
@Mixin(value = PlayerAttackHelper.class, remap = false)
abstract class AttackSelectionMixin {
	@ModifyExpressionValue(method = "selectAttack", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"))
	private static Object[] jaymoskingdoms$basicSwings(Object[] attacks, @Local(argsOnly = true) Player player) {
		if (player == null || WeaponForms.knows(player)) {
			return attacks;
		}
		return WeaponForms.basic(Arrays.asList((WeaponAttributes.Attack[]) attacks), WeaponAttributes.Attack::animation)
			.toArray(WeaponAttributes.Attack[]::new);
	}
}
