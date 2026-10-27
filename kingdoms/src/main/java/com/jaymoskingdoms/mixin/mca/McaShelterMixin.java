package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.entity.ai.brain.tasks.SeekIndoorShelterTask;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (lag fix) A homeless MCA villager seeking shelter paths to up to ten houses at once, and MCA tries again 5 s after
 * finding none. In a new capital that was a fifth of the server's tick; a failed search now waits about a minute.
 */
@Mixin(SeekIndoorShelterTask.class)
abstract class McaShelterMixin {
	/** Ticks a failed shelter search waits before the next, plus up to {@link #RETRY_SPREAD} so villagers don't search together. */
	@Unique
	private static final int RETRY_TICKS = 1200, RETRY_SPREAD = 400;

	@Shadow
	private long nextAttemptTime;

	@Inject(method = "backOffFailedSearch", at = @At("TAIL"))
	private void jaymoskingdoms$waitLonger(ServerLevel level, CallbackInfo ci) {
		this.nextAttemptTime = Math.max(this.nextAttemptTime, level.getGameTime() + RETRY_TICKS + level.getRandom().nextInt(RETRY_SPREAD));
	}
}
