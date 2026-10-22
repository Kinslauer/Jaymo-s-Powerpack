package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.BladeClash;
import com.jaymoskingdoms.CombatArts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.6.5) How charged a swing was, before the swing resets it ({@link CombatArts#swing}). Here rather than in Fabric's
 * attack callback so Better Combat's swings, which call this directly, count too.
 */
@Mixin(Player.class)
abstract class PlayerAttackMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void jaymoskingdoms$swing(Entity target, CallbackInfo ci) {
		// (0.7.105) a full blow, read before the swing resets its charge, can clash (BladeClash)
		BladeClash.swung((Player) (Object) this);
		CombatArts.swing((Player) (Object) this);
	}
}
