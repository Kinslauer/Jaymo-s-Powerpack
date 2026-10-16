package com.jaymospowers.mixin;

import com.jaymospowers.JaymosPowers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.13.2) Vanilla logs "Detected unsafe terrain read during worldgen" on every single read past a generating chunk's
 * edge, with no stack trace: one chunk once logged 57,000 of them in two minutes, and nothing said which mod was reading.
 * Now each chunk logs it once, with the stack trace that shows the reader, and the repeats are dropped.
 */
@Mixin(WorldGenRegion.class)
abstract class WorldGenReadMixin {
	/** Chunks already reported (as packed chunk positions); kept small, it only fills when something misbehaves. */
	@Unique
	private static final Set<Long> jaymospowers$reported = ConcurrentHashMap.newKeySet();

	@Shadow
	@Final
	private int centerChunkX;
	@Shadow
	@Final
	private int centerChunkZ;

	@WrapOperation(method = "warnIfReadOutsideWriteZone", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;logAndPauseIfInIde(Ljava/lang/String;)V"))
	private void jaymospowers$onceWithTrace(String message, Operation<Void> original) {
		if (jaymospowers$reported.size() > 4096) {
			jaymospowers$reported.clear();
		}
		long chunk = (long) this.centerChunkX & 0xFFFFFFFFL | ((long) this.centerChunkZ & 0xFFFFFFFFL) << 32;
		if (jaymospowers$reported.add(chunk)) {
			JaymosPowers.LOGGER.warn("{} (further reads for this chunk are not logged)", message, new Throwable("the read"));
		}
	}
}
