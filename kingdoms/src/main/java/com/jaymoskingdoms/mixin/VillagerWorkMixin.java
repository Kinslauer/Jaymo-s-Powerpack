package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.Workers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.13) A villager at work for a settlement (with MCA) works instead of minding its brain ({@link Workers#step}). */
@Mixin(Villager.class)
abstract class VillagerWorkMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$work(ServerLevel level, CallbackInfo ci) {
		if ((Object)this instanceof Worker w && Workers.step(level, w)) {
			ci.cancel();
		}
	}
}
