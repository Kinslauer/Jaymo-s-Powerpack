package com.jaymoskingdoms.client.disasters;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * (0.7.31) A speck of a sandstorm or blizzard blown past on the wind, or a hailstone dropping hard and bouncing once.
 * Spawned by the client alone ({@link CalamitiesClient}), only round the camera.
 */
final class GustParticle extends SingleQuadParticle {
	static final int SAND = 0, SNOW = 1, HAIL = 2;
	private static final int FADE = 6;

	private final int kind;
	private final float baseAlpha;
	private boolean bounced;

	GustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite, int kind,
		float size, int life, float r, float g, float b, float alpha) {
		super(level, x, y, z, sprite);
		this.kind = kind;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.quadSize = size;
		this.lifetime = life;
		this.friction = 1.0F;
		this.gravity = kind == HAIL ? 1.2F : kind == SNOW ? 0.03F : 0.0F;
		this.hasPhysics = kind == HAIL;
		this.baseAlpha = alpha;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.setColor(r, g, b);
		this.setAlpha(0.0F);
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;
		super.tick();
		if (this.kind == HAIL) {
			if (this.onGround) {
				if (this.bounced) {
					this.age = Math.max(this.age, this.lifetime - FADE);
				} else {
					// one little hop off the ground
					this.bounced = true;
					this.yd = 0.12 + this.random.nextFloat() * 0.08;
					this.xd = (this.random.nextFloat() - 0.5) * 0.1;
					this.zd = (this.random.nextFloat() - 0.5) * 0.1;
				}
			}
		} else {
			// it swirls a little as it goes
			this.yd += (this.random.nextFloat() - 0.5F) * 0.02F;
			this.roll += 0.1F;
		}
		float fade = Math.min(1.0F, Math.min(this.age / (float)FADE, (this.lifetime - this.age) / (float)FADE));
		this.alpha = this.baseAlpha * Mth.clamp(fade, 0.0F, 1.0F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
