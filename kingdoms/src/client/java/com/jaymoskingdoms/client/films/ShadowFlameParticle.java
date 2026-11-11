package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.films.LoreFilms;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.RisingParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * {@link LoreFilms#SHADOWFLAME}, the sorcerer's fire. Andy: "The shadowflame should be soulfire, but black". It is the
 * game's soul fire flame (its own sprite) drawn black with the faintest blue left at its heart, bigger than a torch's,
 * rising as it is sent and shrinking as it burns out. The blue wisps, the black motes and the smoke that go with it are
 * the game's own particles, sent beside it (LoreFilmClient.shadowFlame).
 */
final class ShadowFlameParticle extends RisingParticle {
	/** How much bigger than a torch's flame, how long it burns (ticks, and up to as much again), and what is left of the soul fire's blue. */
	static final float SIZE = 2.2F, RED = 0.06F, GREEN = 0.07F, BLUE = 0.14F;
	static final int LIFE = 14;

	private ShadowFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
		super(level, x, y, z, xd, yd, zd, sprite);
		this.quadSize *= SIZE;
		this.lifetime = LIFE + this.random.nextInt(LIFE + 1);
		this.setColor(RED, GREEN, BLUE);
	}

	static void register() {
		ParticleProviderRegistry.getInstance().register(LoreFilms.SHADOWFLAME,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new ShadowFlameParticle(level, x, y, z, xd, yd, zd, sprites.get(random)));
	}

	@Override
	public float getQuadSize(float partial) {
		float life = Mth.clamp((this.age + partial) / this.lifetime, 0.0F, 1.0F);
		return this.quadSize * (1.0F - life * life * 0.5F);
	}

	@Override
	public int getLightCoords(float partial) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}
}
