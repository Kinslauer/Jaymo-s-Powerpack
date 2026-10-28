package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.future.MuzzleFlash;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * (0.17.135) {@link MuzzleFlash#FLASH}: a gun's muzzle burst. One of our star sprites at random, turned at random,
 * full bright (it lights itself, day or night), that pops to full size in its first tick and shrinks and fades over the
 * next few. A plume piece keeps the speed it was thrown with and slows hard.
 */
final class MuzzleFlashParticle extends SingleQuadParticle {
	/** Quad size for a flash of scale 1 (vanilla's quadSize is half the width, so about a block across). */
	static final float SIZE = 0.5F;
	/** Life in ticks: this, or one more. */
	static final int LIFE = 3;
	/** Speed kept each tick (a plume piece stops within its life). */
	static final float FRICTION = 0.55F;

	private MuzzleFlashParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float scale, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.friction = FRICTION;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.quadSize = SIZE * scale;
		this.lifetime = LIFE + this.random.nextInt(2);
		this.setColor(1.0F, 1.0F, 1.0F);
		this.alpha = 1.0F;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(MuzzleFlash.FLASH,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new MuzzleFlashParticle(level, x, y, z, xd, yd, zd, options.scale(), sprites));
	}

	@Override
	public void tick() {
		super.tick();
		this.alpha = Mth.clamp(1.0F - this.age / (float) this.lifetime, 0.0F, 1.0F);
	}

	@Override
	public float getQuadSize(float partial) {
		// full at once, then down to half
		return this.quadSize * (1.0F - 0.5F * Mth.clamp((this.age + partial) / this.lifetime, 0.0F, 1.0F));
	}

	@Override
	protected int getLightCoords(float partial) {
		return 0xF000F0;
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
