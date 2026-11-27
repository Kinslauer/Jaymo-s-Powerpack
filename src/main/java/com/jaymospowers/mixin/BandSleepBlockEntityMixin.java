package com.jaymospowers.mixin;

import com.jaymospowers.compat.BandSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses block entities in distant bands so their machines wait for a nearby player as an unloaded dimension would. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BandSleepBlockEntityMixin {
	@Shadow @Final private BlockEntity blockEntity;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$sleepBlockEntity(CallbackInfo ci) {
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			BlockPos pos = blockEntity.getBlockPos();
			if (BandSleep.asleep(level, pos.getX(), pos.getY(), pos.getZ())) {
				ci.cancel();
			}
		}
	}
}
