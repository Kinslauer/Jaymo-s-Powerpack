package com.jaymoskingdoms.mixin.sleep;

import com.jaymoskingdoms.Timelapse;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** (0.7.5) The night runs by as a timelapse ({@link Timelapse}) rather than skipping straight to morning. */
@Mixin(ServerLevel.class)
abstract class ServerLevelTimelapseMixin {
	@Shadow
	@Final
	private List<ServerPlayer> players;

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
	private boolean jaymoskingdoms$timelapse(SleepStatus status, int percentage) {
		return Timelapse.enoughSleeping((ServerLevel) (Object) this, status, percentage, this.players);
	}
}
