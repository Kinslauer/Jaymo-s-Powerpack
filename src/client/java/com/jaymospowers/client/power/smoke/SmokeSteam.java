package com.jaymospowers.client.power.smoke;

import com.jaymospowers.power.smoke.SmokeParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * {@link SmokeParticles#STEAM}: vanilla's geyser poof sprites (its 8 frames played over the puff's life), moving like
 * smoke instead of like a geyser. It keeps the velocity it's given (vanilla's poof is flung at random and always up),
 * slows gently, rises a little, and is a smoky grey that fades out at the end.
 */
final class SmokeSteam extends SingleQuadParticle {
	/** Size: vanilla's poof is about 0.23-0.47 blocks across. */
	static final float SIZE = 3.5F;
	/** Life in ticks: at least this, up to {@code LIFE + LIFE_SPREAD}. */
	static final int LIFE = 30, LIFE_SPREAD = 16;
	/** Speed kept each tick, and the lift (negative gravity). */
	static final float FRICTION = 0.92F, LIFT = -0.05F;
	/** The grey (0 black, 1 white) and how much each puff varies from it. */
	static final float GREY = 0.62F, GREY_SPREAD = 0.14F;
	static final float ALPHA = 0.9F;
	/** Ticks it grows in over, and fades out over. */
	static final int GROW = 3, FADE = 10;

	private final SpriteSet sprites;

	private SmokeSteam(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.friction = FRICTION;
		this.gravity = LIFT;
		this.quadSize *= 0.75F * SIZE;
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD + 1);
		float grey = GREY + (this.random.nextFloat() - 0.5F) * GREY_SPREAD;
		this.setColor(grey, grey, grey * 1.02F);
		this.alpha = ALPHA;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.setSpriteFromAge(sprites);
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(SmokeParticles.STEAM,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new SmokeSteam(level, x, y, z, xd, yd, zd, sprites));
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.alpha = ALPHA * Mth.clamp((this.lifetime - this.age) / (float)FADE, 0.0F, 1.0F);
	}

	@Override
	public float getQuadSize(float partial) {
		return this.quadSize * Mth.clamp((this.age + partial) / GROW, 0.0F, 1.0F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
