package com.jaymospowers.client.combat;

import com.jaymospowers.combat.AttackTells;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * (0.20.4) {@link AttackTells#GLINT}: an attack tell's star. One of our four-point glint sprites, tinted the tell's
 * colour (white: parry it, red: dodge or block it), full bright so it reads in daylight and at night. It pops past full
 * size in its first two ticks, settles, turns a little, holds through the wind-up and then shrinks away.
 */
public final class TellGlintParticle extends SingleQuadParticle {
	/** Quad size for a glint of scale 1 (vanilla's quadSize is half the width, so about a block across). */
	static final float SIZE = 0.5F;
	/** Life in ticks (a little past the wind-up), the pop's length and how far past full it overshoots. */
	static final int LIFE = AttackTells.WINDUP + 3, POP = 2;
	static final float OVERSHOOT = 1.25F;
	/** Turn per tick (radians). */
	static final float SPIN = 0.12F;

	private final float fullSize;

	private TellGlintParticle(ClientLevel level, double x, double y, double z, int color, float scale, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.fullSize = SIZE * scale;
		this.quadSize = this.fullSize;
		this.lifetime = LIFE;
		this.setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
		this.alpha = 1.0F;
		this.roll = 0.0F; // the star and its heart turn together
		this.oRoll = this.roll;
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(AttackTells.GLINT,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new TellGlintParticle(level, x, y, z, options.color(), options.scale(), sprites));
		ParticleProviderRegistry.getInstance().register(AttackTells.GLINT_CORE,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new TellGlintParticle(level, x, y, z, options.color(), options.scale(), sprites));
	}

	@Override
	public void tick() {
		super.tick();
		this.oRoll = this.roll;
		this.roll += SPIN;
		int fadeFrom = this.lifetime - 2;
		this.alpha = this.age < fadeFrom ? 1.0F : Mth.clamp(1.0F - (this.age - fadeFrom) / 2.0F, 0.0F, 1.0F);
	}

	@Override
	public float getQuadSize(float partial) {
		float t = this.age + partial;
		if (t < POP) {
			// pop: from a speck to past full
			return this.fullSize * OVERSHOOT * Mth.clamp(0.25F + 0.75F * t / POP, 0.0F, 1.0F);
		}
		float shrinkFrom = this.lifetime - 3;
		if (t < shrinkFrom) {
			// settle back to full
			return this.fullSize * Mth.lerp(Mth.clamp((t - POP) / 2.0F, 0.0F, 1.0F), OVERSHOOT, 1.0F);
		}
		return this.fullSize * Mth.clamp(1.0F - (t - shrinkFrom) / 3.0F * 0.8F, 0.0F, 1.0F);
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
