package com.jaymospowers.mixin;

import com.jaymospowers.combat.CritCue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's jumping crit on something living: the crit cue, and a heavy weapon's ring ({@link CritCue}). */
@Mixin(Player.class)
abstract class PlayerCritMixin {
	@Inject(method = "attackVisualEffects", at = @At("HEAD"))
	private void jaymospowers$critCue(Entity target, boolean criticalAttack, boolean sweepAttack, boolean fullStrengthAttack,
		boolean stabAttack, float magicBoost, CallbackInfo ci) {
		if (criticalAttack && (Object) this instanceof ServerPlayer player && target instanceof LivingEntity victim) {
			CritCue.crit(player, victim);
		}
	}
}
