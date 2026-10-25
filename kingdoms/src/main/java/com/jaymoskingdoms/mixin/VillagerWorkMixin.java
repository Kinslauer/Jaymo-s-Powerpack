package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Doorways;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.Workers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.21) A villager at work for a settlement (with MCA) works instead of minding its brain ({@link Workers#step}). */
@Mixin(Villager.class)
abstract class VillagerWorkMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$work(ServerLevel level, CallbackInfo ci) {
		if ((Object)this instanceof Worker w) {
			boolean working = Workers.step(level, w);
			// (0.7.198) their brain opens doors and gates on their way; while it waits, we do ({@link Doorways})
			Doorways.tick(level, w.mob(), working);
			if (working) {
				ci.cancel();
			}
		}
	}
}
