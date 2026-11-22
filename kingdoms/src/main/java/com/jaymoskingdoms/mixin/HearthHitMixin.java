package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.WorldTiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Andy (2026-11-19): in the Hearth tier no single hit on a player takes more than a fifth of their health
 * ({@link WorldTiers#hearthHit}). Here, where the hit is about to be taken off them (after every damage multiplier on the
 * way in, before armour), so nothing raises it past the cap afterwards.
 */
@Mixin(Player.class)
abstract class HearthHitMixin {
	@ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$hearthHit(float amount, ServerLevel level, DamageSource source) {
		return WorldTiers.hearthHit((Player) (Object) this, source, amount);
	}
}
