package com.jaymoskingdoms.client.disasters;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * An ember thrown up off a fire ({@link SkiesClient}): a glowing speck that rides the hot air up, slowing, wanders a
 * little, is taken by the wind as it climbs, winks as it burns and cools from yellow-orange to a dull red before it goes
 * out. It lights itself. Made on this side only, round the camera.
 */
final class EmberParticle extends SingleQuadParticle {
	/** Its colour hot and cooled; how hard it winks; how much the wind takes it (share of the wind) by the top of its climb. */
	static final float[] HOT = {1.0F, 0.78F, 0.32F}, COOL = {0.75F, 0.16F, 0.05F};
	static final float WINK = 0.35F, WIND = 0.8F;
	private final double windX, windZ;
	private final float baseSize, phase;

	EmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite, float size, int life,
		double windX, double windZ) {
		super(level, x, y, z, sprite);
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.windX = windX;
		this.windZ = windZ;
		this.quadSize = this.baseSize = size;
		this.lifetime = life;
		this.friction = 0.96F;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.phase = this.random.nextFloat() * Mth.TWO_PI;
		this.setColor(HOT[0], HOT[1], HOT[2]);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.isAlive()) {
			return;
		}
		float t = this.age / (float)this.lifetime;
		// the hot air carries it up, the wind takes it the higher it gets, and it wanders a little
		this.yd += 0.004 * (1.0F - t);
		this.xd += (this.windX * WIND * t - this.xd) * 0.05 + (this.random.nextFloat() - 0.5F) * 0.006;
		this.zd += (this.windZ * WIND * t - this.zd) * 0.05 + (this.random.nextFloat() - 0.5F) * 0.006;
		float cool = Mth.clamp(t * 1.3F, 0.0F, 1.0F);
		this.setColor(Mth.lerp(cool, HOT[0], COOL[0]), Mth.lerp(cool, HOT[1], COOL[1]), Mth.lerp(cool, HOT[2], COOL[2]));
		float wink = 1.0F - WINK * (0.5F + 0.5F * Mth.sin(this.age * 0.9F + this.phase));
		this.alpha = wink * (1.0F - Mth.clamp((t - 0.75F) * 4.0F, 0.0F, 1.0F));
		this.quadSize = this.baseSize * (1.0F - 0.4F * t);
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
