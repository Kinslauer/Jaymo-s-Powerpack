package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Storm;
import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.disasters.Tornado;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * (0.6.12) A puff of a storm: a storm cloud drifting with its cell, a wisp of a tornado's funnel whirling up round it,
 * or the dust and debris churned up at its foot. Spawned by the client alone ({@link StormsClient}) from its copy of
 * the storms; it follows its storm or tornado as that moves, and fades away if it's gone.
 */
final class StormParticle extends SingleQuadParticle {
	static final int CLOUD = 0, FUNNEL = 1, DEBRIS = 2;
	/** A bank of ground fog that belongs to no storm: it drifts the way it was sent and turns slowly. */
	static final int FOG = 3;
	/** Smoke off a big fire ({@link FireSmoke}): it rises, slowing, leans downwind as it climbs, swells, pales and thins. */
	static final int SMOKE = 4;
	/** A bank of dark smog hanging high over a burning forest ({@link FireSmoke}): it drifts downwind at its height,
	 * heaves slowly and swells, and lingers after the fire below has gone out. */
	static final int PALL = 5;
	/** How many ticks it takes to fade in and out (smog takes longer, so a bank gathers and clears slowly). */
	private static final int FADE = 25, PALL_FADE = 120;

	/** Ground fog is not drawn within FOG_CLEAR blocks of the camera, and thins in over the next FOG_THIN. */
	private static final double FOG_CLEAR = 5.0, FOG_THIN = 9.0;
	private float near = 1.0F;

	private final int kind;
	/** The storm or tornado it belongs to. */
	private final int owner;
	private final float baseAlpha;
	/** A funnel wisp: its height above the ground, its angle round the funnel, how far out (share of the radius), how fast it climbs. */
	private float h, angle, out, climb;
	/** A cloud: where it sits relative to its storm. */
	private double offX, offZ;
	/** Smoke: how fast it starts rising, the wind it leans into aloft, how much it swells, and its grey at the top. */
	private float rise, swell, greyTop, baseSize, greyFoot;
	private double windX, windZ;

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

	/** Smoke rises {@code rise} a tick at first, leans into (windX, windZ) aloft, swells by {@code swell} times and pales from {@code greyFoot} to {@code greyTop}. */
	StormParticle smoke(float rise, double windX, double windZ, float swell, float greyFoot, float greyTop) {
		this.rise = rise;
		this.windX = windX;
		this.windZ = windZ;
		this.swell = swell;
		this.greyTop = greyTop;
		this.baseSize = this.quadSize;
		this.greyFoot = greyFoot;
		return this;
	}

	/** Smog hangs where it is put, drifts with (windX, windZ), heaves up and down a little and swells by {@code swell} times over its life. */
	StormParticle pall(double windX, double windZ, float swell) {
		this.windX = windX;
		this.windZ = windZ;
		this.swell = swell;
		this.baseSize = this.quadSize;
		this.h = (float)y;
		this.angle = this.random.nextFloat() * Mth.TWO_PI;
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
		if (this.kind == PALL) {
			float t = this.age / (float)this.lifetime;
			this.xd = this.windX;
			this.zd = this.windZ;
			this.x += this.xd;
			this.z += this.zd;
			// (h is the height it was put at: it heaves a block or so round it, as a cloud does)
			this.angle += 0.01F;
			this.y = this.h + Mth.sin(this.angle) * 1.2F;
			this.roll += 0.0015F;
			this.quadSize = this.baseSize * (1.0F + this.swell * t);
			// (thin close to, so flying up through it is not a wall of grey)
			net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
			double away = Math.sqrt((this.x - eye.x) * (this.x - eye.x) + (this.y - eye.y) * (this.y - eye.y) + (this.z - eye.z) * (this.z - eye.z));
			this.near = Mth.clamp((float)((away - FOG_CLEAR * 2.0) / (FOG_THIN * 2.0)), 0.0F, 1.0F);
		} else if (this.kind == SMOKE) {
			float t = this.age / (float)this.lifetime;
			// rising fast off the flames and slowing as it cools; the wind takes it more the higher it gets
			this.xd = Mth.lerp(Math.min(1.0F, t * 2.5F), 0.0, this.windX);
			this.zd = Mth.lerp(Math.min(1.0F, t * 2.5F), 0.0, this.windZ);
			this.x += this.xd;
			this.y += this.rise * (1.0F - 0.6F * t);
			this.z += this.zd;
			this.roll += 0.004F;
			this.quadSize = this.baseSize * (1.0F + this.swell * t);
			float grey = Mth.lerp(Math.min(1.0F, t * 1.5F), this.greyFoot, this.greyTop);
			this.setColor(grey, grey * 0.97F, grey * 0.94F);
			net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
			double away = Math.sqrt((this.x - eye.x) * (this.x - eye.x) + (this.y - eye.y) * (this.y - eye.y) + (this.z - eye.z) * (this.z - eye.z));
			this.near = Mth.clamp((float)((away - FOG_CLEAR) / FOG_THIN), 0.0F, 1.0F) * (1.0F - 0.7F * t);
		} else if (this.kind == FOG) {
			this.x += this.xd;
			this.z += this.zd;
			this.roll += 0.002F;
			// (thin close to: a bank of it seen from inside is only a grey wall)
			net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
			double away = Math.sqrt((this.x - eye.x) * (this.x - eye.x) + (this.z - eye.z) * (this.z - eye.z));
			this.near = Mth.clamp((float)((away - FOG_CLEAR) / FOG_THIN), 0.0F, 1.0F);
		} else if (this.kind == CLOUD) {
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
		int fadeTicks = this.kind == PALL ? PALL_FADE : FADE;
		float fade = Math.min(1.0F, Math.min(this.age / (float)fadeTicks, (this.lifetime - this.age) / (float)fadeTicks));
		if (!alive) {
			this.age = Math.max(this.age, this.lifetime - FADE);
		}
		this.alpha = this.baseAlpha * Mth.clamp(fade, 0.0F, 1.0F) * this.near;
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
