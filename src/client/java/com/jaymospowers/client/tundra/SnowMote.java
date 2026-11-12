package com.jaymospowers.client.tundra;

import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.tundra.Tundra;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * {@link Tundra#SNOW_MOTE}: a fleck of drifting snow in the tundra's air (the biomes' ambient particle; Andy's plan: our
 * own motes, off in low-end mode). Ours, not the game's snowflake: one of four small sprites, tiny, sinking slowly while it
 * sways on a slow wave of its own, fading in and out, and gone where it lands. In low-end mode ({@link LowEndMode}) none
 * are made at all.
 */
public final class SnowMote extends SingleQuadParticle {
	// ---- tuning ----
	/** Size of a mote (the quad's half-width, in blocks): least, and how much more. */
	static final float SIZE = 0.035F, SIZE_SPREAD = 0.03F;
	/** Life in ticks: at least this, and up to this much more. */
	static final int LIFE = 110, LIFE_SPREAD = 90;
	/** How fast it sinks (blocks a tick), how far its sway pushes it, and how slow the sway is (radians a tick). */
	static final float SINK = -0.012F, SWAY = 0.008F, SWAY_RATE = 0.06F;
	/** Ticks it fades in over, and out over. */
	static final int FADE_IN = 12, FADE_OUT = 25;

	private final float phase, rate;

	private SnowMote(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.quadSize = SIZE + this.random.nextFloat() * SIZE_SPREAD;
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD + 1);
		this.gravity = 0.0F;
		this.friction = 1.0F;
		this.yd = SINK * (0.7F + this.random.nextFloat() * 0.6F);
		this.phase = this.random.nextFloat() * Mth.TWO_PI;
		this.rate = SWAY_RATE * (0.7F + this.random.nextFloat() * 0.6F);
		this.alpha = 0.0F;
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(Tundra.SNOW_MOTE,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> LowEndMode.on() ? null : new SnowMote(level, x, y, z, sprites));
	}

	@Override
	public void tick() {
		this.xd = Mth.sin(this.age * this.rate + this.phase) * SWAY;
		this.zd = Mth.cos(this.age * this.rate * 0.8F + this.phase) * SWAY;
		super.tick();
		if (this.onGround) {
			this.remove();
			return;
		}
		this.alpha = Math.min(Mth.clamp(this.age / (float)FADE_IN, 0.0F, 1.0F), Mth.clamp((this.lifetime - this.age) / (float)FADE_OUT, 0.0F, 1.0F));
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
