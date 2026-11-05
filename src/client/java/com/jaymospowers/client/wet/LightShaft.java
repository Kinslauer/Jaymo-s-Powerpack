package com.jaymospowers.client.wet;

import com.jaymospowers.client.blood.BloodDecals;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.19.63) Andy: "subsurface scattering rays underwater". One shaft of sunlight through the water: a tall, soft beam
 * hanging from the surface, slanting with the sun, turned to face the camera about its own length, fading in, swaying a
 * little, then fading out. Drawn as ready-made quads in the blood decals' group ({@link BloodDecals}), at full light.
 */
final class LightShaft extends Particle implements BloodDecals.Decal {
	/** How long it takes to fade in and out (ticks), and how far it sways (blocks). */
	static final float FADE = 30.0F, SWAY = 0.15F;
	/** Its colour (a pale sea green-blue). */
	static final int COLOR = 0xCFEFFF;

	private final TextureAtlasSprite sprite;
	/** Where its top meets the surface, how long and wide it is, how far its foot slants off (blocks), and how bright at most. */
	private final double length;
	private final float width, slantX, slantZ, brightness, phase;

	LightShaft(ClientLevel level, double x, double surface, double z, SpriteSet sprites, double length, float width, float slantX, float slantZ,
		float brightness, int life) {
		super(level, x, surface, z);
		this.sprite = sprites.get(this.random);
		this.length = length;
		this.width = width;
		this.slantX = slantX;
		this.slantZ = slantZ;
		this.brightness = brightness;
		this.lifetime = life;
		this.phase = this.random.nextFloat() * Mth.TWO_PI;
		this.gravity = 0.0F;
		this.hasPhysics = false;
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

	@Override
	public ParticleRenderType getGroup() {
		return BloodDecals.TYPE;
	}

	@Override
	public boolean visible(Frustum frustum) {
		return frustum.isVisible(new AABB(this.x - this.width, this.y - this.length, this.z - this.width, this.x + this.width, this.y + 0.1,
			this.z + this.width).expandTowards(this.slantX, 0.0, this.slantZ));
	}

	@Override
	public void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		float age = this.age + partialTick;
		float fade = Mth.clamp(Math.min(age, this.lifetime - age) / FADE, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			return;
		}
		float alpha = this.brightness * Mth.smoothstep(fade);
		float sway = Mth.sin(age * 0.05F + this.phase) * SWAY;
		Vec3 cam = camera.position();
		double topX = this.x, topY = this.y, topZ = this.z;
		double footX = this.x + this.slantX + sway, footY = this.y - this.length, footZ = this.z + this.slantZ;
		// turned to face the camera about its own length: across = (along x to-camera), flattened
		double midX = (topX + footX) * 0.5 - cam.x, midZ = (topZ + footZ) * 0.5 - cam.z;
		double across = Math.hypot(midX, midZ);
		if (across < 1.0E-3) {
			return;
		}
		float half = this.width * 0.5F;
		float ax = (float) (-midZ / across) * half, az = (float) (midX / across) * half;
		int light = 0xF000F0;
		int top = ARGB.colorFromFloat(alpha, ARGB.redFloat(COLOR), ARGB.greenFloat(COLOR), ARGB.blueFloat(COLOR));
		float u0 = this.sprite.getU0(), u1 = this.sprite.getU1(), v0 = this.sprite.getV0(), v1 = this.sprite.getV1();
		float tx = (float) (topX - cam.x), ty = (float) (topY - cam.y), tz = (float) (topZ - cam.z);
		float fx = (float) (footX - cam.x), fy = (float) (footY - cam.y), fz = (float) (footZ - cam.z);
		// both windings, so it shows whichever way round the quad faces
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, tx - ax, ty, tz - az, u0, v0, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, fx - ax, fy, fz - az, u0, v1, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, fx + ax, fy, fz + az, u1, v1, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, tx + ax, ty, tz + az, u1, v0, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, tx + ax, ty, tz + az, u1, v0, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, fx + ax, fy, fz + az, u1, v1, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, fx - ax, fy, fz - az, u0, v1, top, light);
		decals.add(SingleQuadParticle.Layer.TRANSLUCENT, tx - ax, ty, tz - az, u0, v0, top, light);
	}
}
