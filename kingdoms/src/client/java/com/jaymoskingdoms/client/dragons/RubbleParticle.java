package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonSounds;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * {@link DragonSounds#RUBBLE}, the stone tremor's flying grit: Dragonforge's earth breath frames (RochStudio) played over
 * its short life, flung as it is sent and falling back to the ground.
 */
final class RubbleParticle extends SingleQuadParticle {
	static final float SIZE = 2.4F, GRAVITY = 0.6F, FRICTION = 0.9F;
	static final int LIFE = 10, LIFE_SPREAD = 8;

	private final SpriteSet sprites;

	private RubbleParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xd;
		this.yd = yd + 0.15;
		this.zd = zd;
		this.gravity = GRAVITY;
		this.friction = FRICTION;
		this.quadSize *= SIZE;
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD + 1);
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.setSpriteFromAge(sprites);
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(DragonSounds.RUBBLE,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new RubbleParticle(level, x, y, z, xd, yd, zd, sprites));
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
