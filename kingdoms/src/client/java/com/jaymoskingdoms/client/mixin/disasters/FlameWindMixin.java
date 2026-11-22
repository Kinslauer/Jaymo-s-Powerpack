package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.WindSigns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire in the wind (Andy's survival picks, "wind sway"): the flames off fires, campfires, torches and candles (soul
 * fire too) lean along the wind under open sky, a share of the smoke's drift ({@code WindDriftMixin}). Flames use the
 * base particle's tick, so this sits there and asks the particle what it is.
 */
@Mixin(Particle.class)
abstract class FlameWindMixin {
	@Shadow
	@Final
	protected ClientLevel level;
	@Shadow
	protected boolean removed;
	@Shadow
	protected double x;
	@Shadow
	protected double y;
	@Shadow
	protected double z;

	@Shadow
	public abstract void move(double xa, double ya, double za);

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$flameInTheWind(CallbackInfo ci) {
		if (!((Object) this instanceof FlameParticle)) {
			return;
		}
		double px = WindSigns.pushX, pz = WindSigns.pushZ;
		if (this.removed || px == 0.0 && pz == 0.0 || !this.level.canSeeSky(BlockPos.containing(this.x, this.y, this.z))) {
			return;
		}
		this.move(px * WindSigns.FLAME_SHARE, 0.0, pz * WindSigns.FLAME_SHARE);
	}
}
