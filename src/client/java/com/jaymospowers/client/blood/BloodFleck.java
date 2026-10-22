package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * (0.15.109) A {@link BloodParticles.Fleck}: one of the blood drop sprites, its colour held steady (redstone dust
 * shimmers through its frames and shades; Andy: blood should stay still), drifting down a little and fading at the end.
 */
final class BloodFleck extends SingleQuadParticle {
	/** Size against vanilla dust of the same scale. */
	static final float SIZE = 1.2F;
	/** Life (ticks: at least this, up to this many more), the last {@link #FADE} fading out. */
	static final int LIFE = 20, LIFE_SPREAD = 16, FADE = 8;
	/** How it falls, and the speed it keeps each tick. */
	static final float GRAVITY = 0.35F, FRICTION = 0.96F;

	BloodFleck(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, int color, float scale) {
		super(level, x, y, z, xd, yd, zd, sprites.get(level.getRandom()));
		this.xd *= 0.1;
		this.yd *= 0.1;
		this.zd *= 0.1;
		this.friction = FRICTION;
		this.gravity = GRAVITY;
		this.quadSize *= 0.75F * scale * SIZE;
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD + 1);
		this.setColor(BloodPuddle.red(color), BloodPuddle.green(color), BloodPuddle.blue(color));
		this.alpha = 1.0F;
	}

	@Override
	public void tick() {
		super.tick();
		this.alpha = Mth.clamp((this.lifetime - this.age) / (float) FADE, 0.0F, 1.0F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
