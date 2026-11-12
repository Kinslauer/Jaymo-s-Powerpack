package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Deafened;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A thunderbolt striking close by rings the ears of the players near it ({@link Deafened#BOLT}). */
@Mixin(LightningBolt.class)
abstract class BoltRingMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymoskingdoms$ring(CallbackInfo ci) {
		LightningBolt bolt = (LightningBolt) (Object) this;
		if (bolt.tickCount == 0 && bolt.level() instanceof ServerLevel level) {
			Deafened.blast(level, bolt.position(), Deafened.BOLT);
		}
	}
}
