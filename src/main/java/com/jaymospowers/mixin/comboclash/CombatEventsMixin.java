package com.jaymospowers.mixin.comboclash;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Combo Clash's server drops every plain attack by a player in its combat mode, a backstop for its client, which takes
 * the attack click itself. Its combat mode is on for everyone now ({@code ComboClashClient}), and its client lets a click
 * through only where it's ours (a takedown, a finisher, a whip, a dig; {@code ClientCombatMixin}), so a plain attack
 * that reaches the server is one of those and lands. Combo Clash is "Combo Clash" by HanHoYoung, shipped unmodified;
 * nothing of it is copied. Without it this applies to nothing.
 */
@Pseudo
@Mixin(targets = "com.comboclash.combat.CombatEvents", remap = false)
abstract class CombatEventsMixin {
	@Inject(method = "dropVanillaAttack(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$letThrough(Player player, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
