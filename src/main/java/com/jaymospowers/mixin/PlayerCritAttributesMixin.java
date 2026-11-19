package com.jaymospowers.mixin;

import com.jaymospowers.combat.CombatAttrs;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players carry the four combat multipliers of {@link CombatAttrs}. */
@Mixin(Player.class)
abstract class PlayerCritAttributesMixin {
	@Inject(method = "createAttributes", at = @At("RETURN"))
	private static void jaymospowers$combatAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		cir.getReturnValue()
			.add(CombatAttrs.RIPOSTE_POWER, CombatAttrs.BASE)
			.add(CombatAttrs.SNEAK_ATTACK_POWER, CombatAttrs.BASE)
			.add(CombatAttrs.HEADSHOT_POWER, CombatAttrs.BASE)
			.add(CombatAttrs.PARRY_RECHARGE, CombatAttrs.BASE);
	}
}
