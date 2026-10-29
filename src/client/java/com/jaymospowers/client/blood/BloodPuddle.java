package com.jaymospowers.client.blood;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.jaymospowers.core.blood.BloodTrail;
import java.util.List;
import java.util.TreeSet;

/**
 * (0.15.93) A pool of blood on the ground (Iron's Simple Blood's {@code BloodGroundParticle}, ported): a square of one
 * of its puddle sprites, turned a random quarter, laid flat over the tops of the blocks under it (slabs, stairs and
 * steps too, each top its own piece, fainter the further down it falls), up to {@link #MAX_DROP} below where it landed.
 * It splats in, stays, then shrinks and fades. Drawn by {@link BloodDecals}, not as a camera-facing quad.
 *
 * <p>(0.17.182) One of the server's lasting stains ({@link BloodTrail}) lies as long as the server says, darkening to a
 * dried brown as it ages (so a trail reads fresh to old), and fades over {@link BloodTrail#FADE}.
 */
final class BloodPuddle extends Particle {
	/** Its starting opacity; how long it takes to splat in (ticks); how far down it can lie (blocks). */
	static final float ALPHA = 0.7F, SPLAT_IN = 1.5F, MAX_DROP = 2.0F;
	/** How long it lasts (ticks: at least this, up to this many more), the last {@link #FADE} of them fading. */
	static final int LIFE = 1200, LIFE_SPREAD = 600, FADE = 150;
	/** (0.17.182) A stain is fully dried this far through its life, and dried blood is this dark. */
	static final float DRIED_AT = 0.5F, DRIED = 0.45F;
	/** A puddle is this many blocks across at scale 1. */
	static final float SIZE = 1.5F;
	private static final double SAME_HEIGHT = 1.0E-4;

	private float baseSize;
	/** (0.17.182) Whether it's one of the server's lasting stains (darkens as it dries, fades slowly). */
	private final boolean stain;
	private final float yaw;
	private final float r, g, b;
	private final float zFight;
	private final TextureAtlasSprite sprite;
	private float alpha = ALPHA;

	BloodPuddle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int color, float scale, int age, int life) {
		super(level, x, y, z, 0.0, 0.0, 0.0);
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.baseSize = SIZE * scale;
		this.yaw = this.random.nextInt(4) * Mth.HALF_PI;
		this.stain = life > 0;
		if (this.stain) {
			this.age = age;
			this.lifetime = age + life;
		} else {
			this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD);
		}
		this.gravity = 1.0F;
		this.sprite = sprites.get(this.random);
		this.r = red(color);
		this.g = green(color);
		this.b = blue(color);
		this.zFight = this.random.nextFloat();
	}

	/** (0.17.182) The server grew this stain (more blood fell on it): its new size, age and the ticks it has left. */
	void restain(float scale, int age, int life) {
		this.baseSize = SIZE * scale;
		this.age = Math.max(this.age, age);
		this.lifetime = this.age + life;
	}

	static float red(int color) {
		return (color >> 16 & 0xFF) / 255.0F;
	}

	static float green(int color) {
		return (color >> 8 & 0xFF) / 255.0F;
	}

	static float blue(int color) {
		return (color & 0xFF) / 255.0F;
	}

	@Override
	public ParticleRenderType getGroup() {
		return BloodDecals.TYPE;
	}

	/** Whether the camera could see where it lies. */
	boolean visible(net.minecraft.client.renderer.culling.Frustum frustum) {
		return frustum.pointInFrustum(this.x, this.y, this.z);
	}

	/** Lays its pieces into {@code decals} for this frame. */
	void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		float size = this.baseSize;
		float age = this.age + partialTick;
		if (age <= SPLAT_IN) {
			size *= age / (SPLAT_IN * 2.0F) + 0.5F;
		}
		int fade = this.stain ? BloodTrail.FADE : FADE;
		int fadeFrom = this.lifetime - fade;
		if (age > fadeFrom) {
			size *= Mth.smoothstep(1.0F - Math.max(age - fadeFrom - 60.0F, 0.0F) / fade);
			this.alpha = 1.0F - Mth.clamp((age - fadeFrom) / fade, 1.0F - ALPHA, 1.0F);
		}
		double cx = Mth.lerp(partialTick, this.xo, this.x);
		double cy = Mth.lerp(partialTick, this.yo, this.y);
		double cz = Mth.lerp(partialTick, this.zo, this.z);
		float half = size * 0.5F;
		double minX = cx - half, maxX = cx + half, minZ = cz - half, maxZ = cz + half;
		int light = this.getLightCoords(partialTick);
		int top = Mth.floor(cy + 1.0), bottom = Mth.floor(cy - MAX_DROP);
		for (int bx = Mth.floor(minX); bx <= Mth.floor(maxX); bx++) {
			for (int bz = Mth.floor(minZ); bz <= Mth.floor(maxZ); bz++) {
				this.column(decals, camera, bx, bz, top, bottom, cx, cy, cz, minX, maxX, minZ, maxZ, size, light);
			}
		}
	}

	/** Lays it on the highest block top in this column at or under it. */
	private void column(BloodDecals.State decals, Camera camera, int bx, int bz, int top, int bottom, double cx, double cy, double cz,
		double minX, double maxX, double minZ, double maxZ, float size, int light) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom; y--) {
			pos.set(bx, y - 1, bz);
			BlockState state = this.level.getBlockState(pos);
			if (state.getRenderShape() == RenderShape.INVISIBLE || state.getCollisionShape(this.level, pos).isEmpty()) {
				continue;
			}
			VoxelShape shape = state.getShape(this.level, pos, CollisionContext.empty());
			if (!shape.isEmpty() && this.onBlock(decals, camera, pos, shape, cx, cy, cz, minX, maxX, minZ, maxZ, size, light)) {
				return;
			}
		}
	}

	private boolean onBlock(BloodDecals.State decals, Camera camera, BlockPos pos, VoxelShape shape, double cx, double cy, double cz,
		double minX, double maxX, double minZ, double maxZ, float size, int light) {
		AABB bounds = shape.bounds();
		double x0 = Math.max(minX, pos.getX() + bounds.minX), x1 = Math.min(maxX, pos.getX() + bounds.maxX);
		double z0 = Math.max(minZ, pos.getZ() + bounds.minZ), z1 = Math.min(maxZ, pos.getZ() + bounds.maxZ);
		if (x0 >= x1 || z0 >= z1) {
			return false;
		}
		List<AABB> boxes = shape.toAabbs();
		TreeSet<Double> tops = new TreeSet<>();
		for (AABB box : boxes) {
			tops.add(box.maxY);
		}
		boolean laid = false;
		for (double localTop : tops) {
			double topY = pos.getY() + localTop;
			if (topY > cy + 0.25) {
				continue;
			}
			float fade = Mth.lerp(Mth.clamp((float) (cy - topY) / MAX_DROP, 0.0F, 1.0F), 1.0F, 0.25F);
			for (AABB box : boxes) {
				if (Math.abs(box.maxY - localTop) > SAME_HEIGHT) {
					continue;
				}
				double px0 = Math.max(x0, pos.getX() + box.minX), px1 = Math.min(x1, pos.getX() + box.maxX);
				double pz0 = Math.max(z0, pos.getZ() + box.minZ), pz1 = Math.min(z1, pos.getZ() + box.maxZ);
				if (px0 >= px1 || pz0 >= pz1) {
					continue;
				}
				this.plane(decals, camera, px0, px1, pz0, pz1, topY, cx, cz, size, light, fade);
				laid = true;
			}
		}
		return laid;
	}

	/** One flat piece of the puddle, its sprite cut to fit. */
	private void plane(BloodDecals.State decals, Camera camera, double x0, double x1, double z0, double z1, double y, double cx, double cz,
		float size, int light, float fade) {
		float u0 = this.sprite.getU0(), u1 = this.sprite.getU1(), v0 = this.sprite.getV0(), v1 = this.sprite.getV1();
		Vec3 cam = camera.position();
		float cos = Mth.cos(this.yaw), sin = Mth.sin(this.yaw);
		// (a hair above the block, a little different for each puddle so overlapping ones don't flicker)
		float lift = (this.zFight + 0.08F) * Math.max(0.05F, this.alpha - 0.3F) * 0.05F;
		float dried = this.stain ? Mth.lerp(Mth.clamp(this.age / (BloodTrail.LIFE * DRIED_AT), 0.0F, 1.0F), 1.0F, DRIED) : 1.0F;
		int color = ARGB.colorFromFloat(this.alpha * fade, this.r * dried, this.g * dried, this.b * dried);
		double[][] corners = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
		for (double[] corner : corners) {
			float dx = (float) (corner[0] - cx), dz = (float) (corner[1] - cz);
			float lx = dx * cos - dz * sin, lz = dx * sin + dz * cos;
			float u = (lx / size + 0.5F) * (u1 - u0) + u0;
			float v = (lz / size + 0.5F) * (v1 - v0) + v0;
			decals.add(SingleQuadParticle.Layer.TRANSLUCENT, (float) (corner[0] - cam.x), (float) (y - cam.y) + lift,
				(float) (corner[1] - cam.z), u, v, color, light);
		}
	}
}
