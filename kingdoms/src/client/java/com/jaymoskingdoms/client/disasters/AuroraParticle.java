package com.jaymoskingdoms.client.disasters;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/** One soft patch of an aurora's curtain ({@link SkiesClient}): a storm cloud's puff, lit from within, swaying and breathing. */
final class AuroraParticle extends SingleQuadParticle {
	private static final int FADE = 50;

	private final float baseAlpha, phase;
	private final double homeX;

	AuroraParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float size, int life, float r, float g, float b,
		float alpha) {
		super(level, x, y, z, sprite);
		this.quadSize = size;
		this.lifetime = life;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.friction = 1.0F;
		this.baseAlpha = alpha;
		this.phase = this.random.nextFloat() * Mth.TWO_PI;
		this.homeX = x;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.setColor(r, g, b);
		this.setAlpha(0.0F);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		this.x = this.homeX + Mth.sin(this.age * 0.02F + this.phase) * 6.0;
		float fade = Math.min(1.0F, Math.min(this.age / (float)FADE, (this.lifetime - this.age) / (float)FADE));
		float breath = 0.75F + 0.25F * Mth.sin(this.age * 0.05F + this.phase);
		this.alpha = this.baseAlpha * Mth.clamp(fade, 0.0F, 1.0F) * breath;
	}

	@Override
	public int getLightCoords(float partial) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
