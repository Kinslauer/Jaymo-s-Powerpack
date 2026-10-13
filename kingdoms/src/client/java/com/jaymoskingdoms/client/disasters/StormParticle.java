package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Storm;
import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.disasters.Tornado;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * (0.6.9) A puff of a storm: a storm cloud drifting with its cell, a wisp of a tornado's funnel whirling up round it,
 * or the dust and debris churned up at its foot. Spawned by the client alone ({@link StormsClient}) from its copy of
 * the storms; it follows its storm or tornado as that moves, and fades away if it's gone.
 */
final class StormParticle extends SingleQuadParticle {
	static final int CLOUD = 0, FUNNEL = 1, DEBRIS = 2;
	/** How many ticks it takes to fade in and out. */
	private static final int FADE = 25;

	private final int kind;
	/** The storm or tornado it belongs to. */
	private final int owner;
	private final float baseAlpha;
	/** A funnel wisp: its height above the ground, its angle round the funnel, how far out (share of the radius), how fast it climbs. */
	private float h, angle, out, climb;
	/** A cloud: where it sits relative to its storm. */
	private double offX, offZ;

	StormParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int kind, int owner, float size, int life,
		float r, float g, float b, float alpha) {
		super(level, x, y, z, sprite);
		this.kind = kind;
		this.owner = owner;
		this.quadSize = size;
		this.lifetime = life;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.friction = 1.0F;
		this.baseAlpha = alpha;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.setColor(r, g, b);
		this.setAlpha(0.0F);
	}

	/** A cloud puff sits at (offX, offZ) from its storm's middle. */
	StormParticle cloud(double offX, double offZ) {
		this.offX = offX;
		this.offZ = offZ;
		return this;
	}

	/** A funnel wisp starts {@code h} up, at {@code angle}, {@code out} of the radius, climbing {@code climb} a tick. */
	StormParticle funnel(float h, float angle, float out, float climb) {
		this.h = h;
		this.angle = angle;
		this.out = out;
		this.climb = climb;
		return this;
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
		boolean alive = true;
		if (this.kind == CLOUD) {
			Storm s = storm(this.owner);
			if (s == null) {
				alive = false;
				this.x += this.xd;
				this.z += this.zd;
			} else {
				this.xd = s.vx;
				this.zd = s.vz;
				this.x = s.x + this.offX;
				this.z = s.z + this.offZ;
			}
			this.roll += 0.001F;
		} else {
			Tornado t = tornado(this.owner);
			if (t == null) {
				alive = false;
				this.y += 0.1;
			} else {
				float r = Math.max(0.6F, t.radiusAt(this.h) * this.out);
				// faster nearer the middle
				float spin = (this.kind == FUNNEL ? 2.4F : 1.4F) / Math.max(1.5F, r) * (0.6F + 0.4F * t.formed());
				this.angle += spin;
				this.h += this.climb;
				if (this.kind == DEBRIS) {
					this.out += 0.012F;
				}
				this.x = t.x + Mth.cos(this.angle) * r;
				this.z = t.z + Mth.sin(this.angle) * r;
				this.y = t.y + this.h;
				if (this.h > t.height()) {
					this.age = Math.max(this.age, this.lifetime - FADE);
				}
			}
			this.roll += 0.05F;
		}
		float fade = Math.min(1.0F, Math.min(this.age / (float)FADE, (this.lifetime - this.age) / (float)FADE));
		if (!alive) {
			this.age = Math.max(this.age, this.lifetime - FADE);
		}
		this.alpha = this.baseAlpha * Mth.clamp(fade, 0.0F, 1.0F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	static Storm storm(int id) {
		for (Storm s : Storms.CLIENT) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	static Tornado tornado(int id) {
		for (Tornado t : Storms.CLIENT_TORNADOES) {
			if (t.id == id) {
				return t;
			}
		}
		return null;
	}
}
