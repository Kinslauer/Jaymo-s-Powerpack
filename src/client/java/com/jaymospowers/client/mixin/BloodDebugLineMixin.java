package com.jaymospowers.client.mixin;

import com.jaymospowers.client.blood.BloodClient;
import net.minecraft.client.gui.components.debug.DebugEntryParticleRenderStats;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Under F3's particle count: the blood setting and how much blood is out now ({@link BloodClient#debugLine}). */
@Mixin(DebugEntryParticleRenderStats.class)
abstract class BloodDebugLineMixin {
	@Inject(method = "display", at = @At("TAIL"))
	private void jaymospowers$bloodLine(DebugScreenDisplayer displayer, Level level, LevelChunk client, LevelChunk server, CallbackInfo ci) {
		displayer.addLine(BloodClient.debugLine());
	}
}
