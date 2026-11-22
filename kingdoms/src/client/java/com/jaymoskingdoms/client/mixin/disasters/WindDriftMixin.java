package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.WindSigns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.FallingParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smoke (campfires, fires, torches' and chimneys' smoke, ash) and falling leaves drift along the wind under open sky
 * ({@link WindSigns}); out of the sky (indoors, under a roof or a canopy) they rise and fall as before.
 */
@Mixin({CampfireSmokeParticle.class, BaseAshSmokeParticle.class, FallingParticle.class})
abstract class WindDriftMixin extends Particle {
	private WindDriftMixin(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$driftOnTheWind(CallbackInfo ci) {
		double px = WindSigns.pushX, pz = WindSigns.pushZ;
		if (this.removed || px == 0.0 && pz == 0.0 || !this.level.canSeeSky(BlockPos.containing(this.x, this.y, this.z))) {
			return;
		}
		double share = (Object) this instanceof FallingParticle ? WindSigns.LEAF_SHARE : 1.0;
		this.move(px * share, 0.0, pz * share);
	}
}
