package com.jaymospowers.mixin;

import com.jaymospowers.combat.Blows;
import com.jaymospowers.stamina.Exertion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's swing at something, for as long as it lasts: what it strikes answers to the weapon's distance
 * ({@link Blows#begin}), and it tires them in armour ({@link Exertion#swung}). Here rather than in Fabric's attack
 * callback so Better Combat's swings, which call this directly for each thing struck, count too.
 */
@Mixin(Player.class)
abstract class BlowsPlayerMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void jaymospowers$swingBegins(Entity target, CallbackInfo ci) {
		Blows.begin((Player) (Object) this);
		if ((Object) this instanceof ServerPlayer player) {
			Exertion.swung(player, false);
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void jaymospowers$swingEnds(Entity target, CallbackInfo ci) {
		Blows.end((Player) (Object) this);
	}
}
