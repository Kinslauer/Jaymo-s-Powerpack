package com.jaymoskingdoms.mixin.sleep;

import com.jaymoskingdoms.BedRest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** (0.7.4) Players resting in bed ({@link BedRest}) are awake: they don't count among the sleepers. */
@Mixin(SleepStatus.class)
abstract class SleepStatusMixin {
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z"))
	private boolean jaymoskingdoms$restIsAwake(ServerPlayer player) {
		return BedRest.countsAsSleeping(player);
	}
}
