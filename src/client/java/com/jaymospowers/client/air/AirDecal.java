package com.jaymospowers.client.air;

import com.jaymospowers.client.blood.BloodDecals;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A flat mark laid on a surface (a floor's top, a wall's face, water): one sprite stretched over a square or strip
 * given by its middle and two half-sides, fading in and out. Drawn as ready-made quads in the blood decals' group
 * ({@link BloodDecals}), through its depth-biased layer, so it lies flush on what it's laid on.
 */
class AirDecal extends Particle implements BloodDecals.Decal {
	/** How far off the surface it's drawn (blocks). */
	static final float LIFT = 0.003F;

	protected Vec3 u, v;
	protected final TextureAtlasSprite sprite;
	protected int rgb;
	protected final float alphaMax;
	protected final boolean bright;
	protected final int fadeIn, fadeOut;

	AirDecal(ClientLevel level, double x, double y, double z, Vec3 u, Vec3 v, TextureAtlasSprite sprite, int rgb, float alpha, int life,
		int fadeIn, int fadeOut, boolean bright) {
		super(level, x, y, z);
		this.u = u;
		this.v = v;
		this.sprite = sprite;
		this.rgb = rgb;
		this.alphaMax = alpha;
		this.lifetime = life;
		this.fadeIn = Math.max(1, fadeIn);
		this.fadeOut = Math.max(1, fadeOut);
		this.bright = bright;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.xd = this.yd = this.zd = 0.0;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
		}
	}

	/** Fades out over its last {@link #fadeOut} ticks from now (or sooner if it had less left). */
	void fadeNow() {
		this.lifetime = Math.min(this.lifetime, this.age + this.fadeOut);
	}

	@Override
	public ParticleRenderType getGroup() {
		return BloodDecals.TYPE;
	}

	@Override
	public boolean visible(Frustum frustum) {
		double r = this.u.length() + this.v.length();
		return frustum.isVisible(new AABB(this.x - r, this.y - r, this.z - r, this.x + r, this.y + r, this.z + r));
	}

	/** Its opacity now (0..1). */
	protected float alpha(float partialTick) {
		float age = this.age + partialTick;
		return this.alphaMax * Mth.clamp(Math.min(age / this.fadeIn, (this.lifetime - age) / this.fadeOut), 0.0F, 1.0F);
	}

	@Override
	public void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		float alpha = this.alpha(partialTick);
		if (alpha <= 0.01F) {
			return;
		}
		Vec3 cam = camera.position();
		double cx = Mth.lerp(partialTick, this.xo, this.x) - cam.x, cy = Mth.lerp(partialTick, this.yo, this.y) - cam.y,
			cz = Mth.lerp(partialTick, this.zo, this.z) - cam.z;
		int light = this.bright ? LightCoordsUtil.FULL_BRIGHT : this.getLightCoords(partialTick);
		int color = ARGB.color(Math.round(alpha * 255.0F), this.rgb);
		float u0 = this.sprite.getU0(), u1 = this.sprite.getU1(), v0 = this.sprite.getV0(), v1 = this.sprite.getV1();
		Vec3 u = this.u, v = this.v;
		float[][] corners = {{-1, -1, u0, v0}, {-1, 1, u0, v1}, {1, 1, u1, v1}, {1, -1, u1, v0}};
		// both windings, so it shows from whichever side it's seen
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < 4; i++) {
				float[] c = corners[pass == 0 ? i : 3 - i];
				decals.add(BloodDecals.DECAL_LAYER, (float) (cx + u.x * c[0] + v.x * c[1]), (float) (cy + u.y * c[0] + v.y * c[1]),
					(float) (cz + u.z * c[0] + v.z * c[1]), c[2], c[3], color, light);
			}
		}
	}
}
