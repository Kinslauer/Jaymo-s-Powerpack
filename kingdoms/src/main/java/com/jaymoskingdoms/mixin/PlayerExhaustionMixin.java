package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.5.51) Survival practice and Packhorse ({@link Skills}): hunger drains slower. */
@Mixin(Player.class)
abstract class PlayerExhaustionMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$skillExhaustion(float exhaustion) {
		return (Object) this instanceof ServerPlayer player ? exhaustion * Skills.exhaustion(player) : exhaustion;
	}
}
