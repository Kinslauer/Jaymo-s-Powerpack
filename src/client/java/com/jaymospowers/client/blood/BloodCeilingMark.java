package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodBurn;
import com.jaymospowers.core.blood.BloodParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Blood sprayed up onto the underside of a block: a splat flat on the ceiling, drawn like a {@link BloodPuddle}
 * ({@link BloodDecals}), that drips back down for a while ({@link #DRIPS} drops, each a falling {@link BloodDrop} that
 * pools where it lands), then dries darker and fades. Fire beside it burns it away.
 */
public final class BloodCeilingMark extends Particle implements BloodDecals.Decal {
	/** How long it lies and how long of that it fades (ticks). */
	static final int LIFE = 500, FADE = 80;
	/** It drips every this many ticks (by chance), this many times at most, each this big. */
	static final int DRIP_EVERY = 16, DRIPS = 6;
	static final float DRIP_CHANCE = 0.55F, DRIP_SIZE = 0.35F;
	/** Its opacity, and how far under the ceiling it is drawn. */
	static final float ALPHA = 0.8F, LIFT = 0.004F;
	/** A splat this many times its drop's size. */
	static final float GROWTH = 2.4F;

	private final int color;
	private final float size;
	private final TextureAtlasSprite sprite;
	private final float yaw;
	private int drips = DRIPS;

	private BloodCeilingMark(ClientLevel level, double x, double y, double z, int color, float size, TextureAtlasSprite sprite) {
		super(level, x, y, z);
		this.color = color;
		this.size = size;
		this.sprite = sprite;
		this.yaw = this.random.nextInt(4) * Mth.HALF_PI;
		this.lifetime = LIFE + this.random.nextInt(200);
		this.gravity = 0.0F;
		this.hasPhysics = false;
	}

	/** A drop of {@code color}, {@code dropSize} across, flew up into the underside of a block at (x, ceiling, z): splats there (by the gore level's chance). */
	static void splat(ClientLevel level, double x, double ceiling, double z, int color, float dropSize) {
		SpriteSet sprites = BloodClient.puddleSprites();
		if (sprites == null || BloodSettings.off() || level.getRandom().nextFloat() >= BloodSettings.gore().share * 0.7F) {
			return;
		}
		float size = Mth.clamp(dropSize * GROWTH, 0.2F, 0.9F);
		Minecraft.getInstance().particleEngine.add(new BloodCeilingMark(level, x, ceiling, z, color, size, sprites.get(level.getRandom())));
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		if (this.age % BloodPuddle.RAIN_CHECK == 0 && this.lifetime - this.age > BloodPuddle.BURN_AWAY
			&& BloodBurn.near(this.level, BlockPos.containing(this.x, this.y - 0.5, this.z))) {
			this.lifetime = this.age + BloodPuddle.BURN_AWAY;
		}
		if (this.drips > 0 && this.age % DRIP_EVERY == 0 && this.random.nextFloat() < DRIP_CHANCE) {
			this.drips--;
			double dx = this.x + (this.random.nextDouble() - 0.5) * this.size * 0.6, dz = this.z + (this.random.nextDouble() - 0.5) * this.size * 0.6;
			BloodClient.drip(this.level, dx, this.y - 0.05, dz, this.color, DRIP_SIZE + this.size * 0.4F);
		}
	}

	@Override
	public ParticleRenderType getGroup() {
		return BloodDecals.TYPE;
	}

	@Override
	public boolean visible(Frustum frustum) {
		double r = this.size * 0.6;
		return frustum.isVisible(new AABB(this.x - r, this.y - 0.1, this.z - r, this.x + r, this.y + 0.1, this.z + r));
	}

	@Override
	public void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		if (BloodSettings.off()) {
			return;
		}
		float age = this.age + partialTick;
		float alpha = ALPHA;
		int fadeFrom = this.lifetime - FADE;
		if (age > fadeFrom) {
			alpha *= 1.0F - Mth.clamp((age - fadeFrom) / FADE, 0.0F, 1.0F);
		}
		if (alpha <= 0.01F) {
			return;
		}
		float grow = Mth.clamp(age / 2.0F, 0.5F, 1.0F);
		double h = this.size * 0.5 * grow;
		int rgb = this.color == BloodParticles.MAGMA ? BloodPuddle.shade(this.color, age, false) : BloodPuddle.shade(this.color, age * 2.0F, true);
		int light = this.getLightCoords(partialTick);
		int argb = ARGB.color(Math.round(alpha * 255.0F), rgb);
		float u0 = this.sprite.getU0(), u1 = this.sprite.getU1(), v0 = this.sprite.getV0(), v1 = this.sprite.getV1();
		Vec3 cam = camera.position();
		float cos = Mth.cos(this.yaw), sin = Mth.sin(this.yaw);
		float y = (float) (this.y - cam.y) - LIFT;
		double[][] corners = {{-h, -h, u0, v0}, {h, -h, u1, v0}, {h, h, u1, v1}, {-h, h, u0, v1}};
		for (double[] c : corners) {
			float lx = (float) c[0] * cos - (float) c[1] * sin, lz = (float) c[0] * sin + (float) c[1] * cos;
			decals.add(BloodDecals.DECAL_LAYER, (float) (this.x - cam.x) + lx, y, (float) (this.z - cam.z) + lz, (float) c[2], (float) c[3], argb, light);
		}
	}
}
