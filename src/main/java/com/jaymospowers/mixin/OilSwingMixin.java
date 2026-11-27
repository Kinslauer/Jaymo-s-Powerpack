package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.oil.Oils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A swing with an oiled blade uses the oil up ({@link Oils#swung}): once it has landed, so the last swing still carries its oil.
 * Here rather than in Fabric's attack callback so Better Combat's swings, which call this directly for each thing struck, count too
 * (a swing that strikes three things is still one swing). Empty swings are counted by {@link OilPunchMixin}.
 */
@Mixin(Player.class)
abstract class OilSwingMixin {
	@Inject(method = "attack", at = @At("RETURN"))
	private void jaymospowers$oilSwung(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			Oils.swung(player);
		}
	}
}
