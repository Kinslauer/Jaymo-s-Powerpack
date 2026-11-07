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
import com.jaymospowers.core.blood.BloodParticles;
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
 *
 * <p>(0.20.41, Hematic's ideas) A pool that spreads over the edge of a drop-off pours down the side
 * ({@link BloodWallMark#edges}). A bloody footprint ({@link BloodParticles.Puddle#print}) is one sole, turned the way its
 * walker went and laid flat where it fell. Magma blood ({@link BloodParticles#MAGMA}) glows bright and cools to a dark crust
 * ({@link #shade}, {@link #glow}), smoking while it's hot.
 */
public final class BloodPuddle extends Particle implements BloodDecals.Decal {
	/** Its starting opacity; how long it takes to splat in (ticks); how far down it can lie (blocks). */
	static final float ALPHA = 0.7F, SPLAT_IN = 1.5F, MAX_DROP = 2.0F;
	/** How long it lasts (ticks: at least this, up to this many more), the last {@link #FADE} of them fading. */
	static final int LIFE = 1200, LIFE_SPREAD = 600, FADE = 150;
	/** (0.17.182) A stain is fully dried this far through its life, and dried blood is this dark. */
	static final float DRIED_AT = 0.5F, DRIED = 0.45F;
	/** A puddle is this many blocks across at scale 1. */
	static final float SIZE = 1.5F;
	/** (0.19.55) A smear ({@code BloodTrail.smear}) is this many times as long as a puddle across, and this many times as narrow. */
	static final float SMEAR_LONG = 1.9F, SMEAR_NARROW = 0.5F;
	/** (0.19.62) A stain drawn smaller by blood magic eases down this share of its size a tick; one restained with fewer than this many ticks left (drawn dry) fades over them. */
	static final float DRAWN_EASE = 0.12F;
	static final int QUICK_FADE_UNDER = 100;
	private static final double SAME_HEIGHT = 1.0E-4;
	/**
	 * How far a decal sits above the surface it's drawn on, plus a per-decal jitter so overlapping decals don't
	 * flicker in step. Kept to a few thousandths of a block: enough to clear coplanar z-fighting with the block, small
	 * enough that a puddle hanging over a slab edge or lying on a carpet or snow layer stays flush instead of floating.
	 * Coplanar flicker is handled by drawing through the decal render state (BloodDecals.TYPE) - 26.3's RenderType and
	 * RenderSetup carry no polygon or depth offset, so the lift cannot be relied on alone.
	 */
	static final float DECAL_LIFT = 0.002F, DECAL_LIFT_JITTER = 0.002F;
	/** (0.19.63) A water puddle's opacity, its colour, how long it takes to dry up (ticks), and the biggest it grows (scale). */
	static final float WET_ALPHA = 0.5F;
	static final int WATER = 0x8EA4B6;
	static final int WET_FADE = 400;
	static final float WET_MAX_SCALE = 1.6F;

	private float baseSize;
	/** (0.19.62) The size it's easing to (blood magic drawing it down), and how long its last fade takes once drawn dry (ticks; 0: the usual). */
	private float targetSize;
	private int quickFade;
	/** (0.17.182) Whether it's one of the server's lasting stains (darkens as it dries, fades slowly). */
	private final boolean stain;
	/** (0.19.63) Whether it's water (rain or drips: never darkens, dries up slowly). */
	private final boolean wet;
	private final float yaw;
	/** (0.19.55) Its stretch east-west and north-south (1 and 1 round; a smear long one way and narrow the other). */
	private final float stretchX, stretchZ;
	private final float r, g, b;
	private final float zFight;
	private final TextureAtlasSprite sprite;
	private float alpha = ALPHA;
	/** (0.20.41) A footprint: its turn (radians, as yaw) and whether it's a right foot (its sole mirrored); its colour as it is. */
	private final boolean print;
	private final float printYaw;
	private final boolean rightFoot;
	private final int color;
	private final boolean magma;

	BloodPuddle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int color, float scale, int age, int life) {
		this(level, x, y, z, sprites, color, scale, age, life, BloodParticles.Puddle.ROUND);
	}

	BloodPuddle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int color, float scale, int age, int life, int shape) {
		this(level, x, y, z, sprites, color, scale, age, life, shape, false);
	}

	/**
	 * (0.19.63) A puddle of water (rain pooling, drips off a wet body) this big, lying {@code life} ticks before it starts to
	 * dry up; {@link #feed} keeps it (and grows it) while more water falls on it.
	 */
	public static BloodPuddle water(ClientLevel level, double x, double y, double z, SpriteSet sprites, float scale, int life) {
		return new BloodPuddle(level, x, y, z, sprites, WATER, scale, 0, life, BloodParticles.Puddle.ROUND, true);
	}

	private BloodPuddle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int color, float scale, int age, int life, int shape,
		boolean wet) {
		super(level, x, y, z, 0.0, 0.0, 0.0);
		this.wet = wet;
		this.color = color;
		this.magma = color == BloodParticles.MAGMA;
		this.print = BloodParticles.Puddle.print(shape);
		int printDir = this.print ? (shape - BloodParticles.Puddle.PRINT) % BloodParticles.Puddle.PRINT_RIGHT : 0;
		this.rightFoot = this.print && shape - BloodParticles.Puddle.PRINT >= BloodParticles.Puddle.PRINT_RIGHT;
		this.printYaw = printDir * Mth.PI / 8.0F;
		if (this.print) {
			shape = BloodParticles.Puddle.ROUND;
		}
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.baseSize = SIZE * scale;
		this.targetSize = this.baseSize;
		boolean round = shape == BloodParticles.Puddle.ROUND;
		// (0.19.55) a smear is only ever turned end for end, so its sprite stays drawn out along its length
		this.yaw = (round ? this.random.nextInt(4) : this.random.nextInt(2) * 2) * Mth.HALF_PI;
		this.stretchX = round ? 1.0F : shape == BloodParticles.Puddle.SMEAR_X ? SMEAR_LONG : SMEAR_NARROW;
		this.stretchZ = round ? 1.0F : shape == BloodParticles.Puddle.SMEAR_Z ? SMEAR_LONG : SMEAR_NARROW;
		this.stain = !wet && life > 0;
		if (wet) {
			this.lifetime = life + WET_FADE;
			this.alpha = WET_ALPHA;
		} else if (this.stain) {
			this.age = age;
			this.lifetime = age + life;
		} else {
			this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD);
		}
		this.gravity = 1.0F;
		SpriteSet prints = BloodClient.printSprites();
		this.sprite = this.print && prints != null ? prints.get(0, 1) : sprites.get(this.random);
		this.r = red(color);
		this.g = green(color);
		this.b = blue(color);
		this.zFight = this.random.nextFloat();
		if (!wet && !this.print && round) {
			// (0.20.41) a pool spread over a ledge pours down its side
			BloodWallMark.edges(level, x, y, z, this.baseSize, color, this.age, this.lifetime - this.age, this.stain);
		}
		if (this.magma && this.age < 40) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y + 0.05, z, 0.0, 0.04, 0.0);
			level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x, y + 0.05, z, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * (0.20.41) The colour blood of {@code color} shows {@code age} ticks after it was spilt: a lasting stain
	 * ({@code stain}) darkens to dried brown; magma glows its own orange, then cools to a crust.
	 */
	static int shade(int color, float age, boolean stain) {
		if (color == BloodParticles.MAGMA) {
			float cool = Mth.clamp(age / BloodParticles.MAGMA_COOLS, 0.0F, 1.0F);
			return ARGB.srgbLerp(cool * cool, BloodParticles.MAGMA, BloodParticles.MAGMA_CRUST) & 0xFFFFFF;
		}
		if (!stain) {
			return color & 0xFFFFFF;
		}
		float dried = Mth.lerp(Mth.clamp(age / (BloodTrail.LIFE * DRIED_AT), 0.0F, 1.0F), 1.0F, DRIED);
		return ARGB.color(0, Math.round(red(color) * dried * 255.0F), Math.round(green(color) * dried * 255.0F), Math.round(blue(color) * dried * 255.0F))
			& 0xFFFFFF;
	}

	/** (0.20.41) Hot magma blood lights itself: {@code light} with its block light raised by how hot it still is. */
	static int glow(int light, float age) {
		float heat = 1.0F - Mth.clamp(age / BloodParticles.MAGMA_COOLS, 0.0F, 1.0F);
		int block = Math.max(light >> 4 & 0xF, Math.round(heat * 15.0F));
		return light & 0xFFFF0000 | block << 4;
	}

	/** (0.17.182) The server grew this stain (more blood fell on it): its new size, age and the ticks it has left. */
	void restain(float scale, int age, int life) {
		this.targetSize = SIZE * scale;
		if (this.targetSize > this.baseSize) {
			// (grown: at once; drawn smaller by blood magic: it eases down, tick)
			this.baseSize = this.targetSize;
		}
		this.age = Math.max(this.age, age);
		this.lifetime = this.age + life;
		// (0.19.62) drawn dry: it fades over the little time it has left, not all at once (fresh blood on it: as before)
		this.quickFade = life < QUICK_FADE_UNDER ? Math.max(1, life) : 0;
	}

	/** (0.20.54) How often a passing pool checks for rain on it (ticks), and how long it then takes to wash away. */
	static final int RAIN_CHECK = 20, RAIN_WASH = 100;

	/** (0.20.54) Whether rain falls on this spot (a client-side check, for the blood that's only for show). */
	static boolean rainedOn(ClientLevel level, double x, double y, double z) {
		if (!level.isRaining()) {
			return false;
		}
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
		return level.hasChunkAt(pos) && level.isRainingAt(pos);
	}

	@Override
	public void tick() {
		super.tick();
		// (0.20.54) rain washes a passing pool away (a lasting stain is washed by the server, BloodTrail.wash)
		if (!this.wet && !this.stain && this.age % RAIN_CHECK == 0 && this.lifetime - this.age > RAIN_WASH
			&& rainedOn(this.level, this.x, this.y + 0.5, this.z)) {
			this.lifetime = this.age + RAIN_WASH;
			this.quickFade = RAIN_WASH;
		}
		if (this.magma && this.age < BloodParticles.MAGMA_COOLS / 2 && this.random.nextFloat() < 0.03F) {
			this.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, this.x + (this.random.nextFloat() - 0.5F) * this.baseSize * 0.6,
				this.y + 0.05, this.z + (this.random.nextFloat() - 0.5F) * this.baseSize * 0.6, 0.0, 0.03, 0.0);
		}
		if (this.baseSize > this.targetSize) {
			this.baseSize = Math.max(this.targetSize, this.baseSize - Math.max(DRAWN_EASE * this.baseSize, 0.01F));
		}
	}

	/** (0.19.63) More water fell on this puddle: it lasts at least {@code life} more ticks and grows by {@code grow} (scale). */
	public void feed(int life, float grow) {
		if (!this.wet) {
			return;
		}
		this.lifetime = Math.max(this.lifetime, this.age + life + WET_FADE);
		this.baseSize = Math.min(SIZE * WET_MAX_SCALE, this.baseSize + SIZE * grow);
	}

	/** (0.19.63) Where it lies. */
	public Vec3 position() {
		return new Vec3(this.x, this.y, this.z);
	}

	/** (0.19.63) Its size now (blocks across). */
	public float size() {
		return this.baseSize;
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
	@Override
	public boolean visible(net.minecraft.client.renderer.culling.Frustum frustum) {
		// culled by the quad's own bounds, not just its centre point: a big puddle near the screen edge
		// stays drawn until the whole quad is out of view instead of popping out
		float r = this.baseSize * 0.6F;
		return frustum.isVisible(new net.minecraft.world.phys.AABB(this.x - r, this.y - 0.1F, this.z - r,
				this.x + r, this.y + 0.1F, this.z + r));
	}

	/** Lays its pieces into {@code decals} for this frame. */
	@Override
	public void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		float size = this.baseSize;
		float age = this.age + partialTick;
		if (age <= SPLAT_IN) {
			size *= age / (SPLAT_IN * 2.0F) + 0.5F;
		}
		int fade = this.quickFade > 0 ? this.quickFade : this.wet ? WET_FADE : this.stain ? BloodTrail.FADE : FADE;
		int fadeFrom = this.lifetime - fade;
		if (age > fadeFrom) {
			size *= Mth.smoothstep(1.0F - Math.max(age - fadeFrom - 60.0F, 0.0F) / fade);
			float start = this.wet ? WET_ALPHA : ALPHA;
			this.alpha = 1.0F - Mth.clamp((age - fadeFrom) / fade, 1.0F - start, 1.0F);
		}
		double cx = Mth.lerp(partialTick, this.xo, this.x);
		double cy = Mth.lerp(partialTick, this.yo, this.y);
		double cz = Mth.lerp(partialTick, this.zo, this.z);
		double halfX = size * this.stretchX * 0.5, halfZ = size * this.stretchZ * 0.5;
		double minX = cx - halfX, maxX = cx + halfX, minZ = cz - halfZ, maxZ = cz + halfZ;
		int light = this.getLightCoords(partialTick);
		if (this.magma) {
			light = glow(light, age);
		}
		this.shown = shade(this.color, age, this.stain);
		if (this.print) {
			this.printQuad(decals, camera, cx, cy, cz, size, light);
			return;
		}
		int top = Mth.floor(cy + 1.0), bottom = Mth.floor(cy - MAX_DROP);
		for (int bx = Mth.floor(minX); bx <= Mth.floor(maxX); bx++) {
			for (int bz = Mth.floor(minZ); bz <= Mth.floor(maxZ); bz++) {
				this.column(decals, camera, bx, bz, top, bottom, cx, cy, cz, minX, maxX, minZ, maxZ, size, light);
			}
		}
	}

	/** (0.20.41) This frame's colour (RGB): {@link #shade}. */
	private int shown;

	/** (0.20.41) A footprint: one sole lying flat where it fell, toes the way its walker went. */
	private void printQuad(BloodDecals.State decals, Camera camera, double cx, double cy, double cz, float size, int light) {
		float u0 = this.sprite.getU0(), u1 = this.sprite.getU1(), v0 = this.sprite.getV0(), v1 = this.sprite.getV1();
		if (this.rightFoot) {
			float swap = u0;
			u0 = u1;
			u1 = swap;
		}
		Vec3 cam = camera.position();
		// forward (the toes) and across, from its yaw (0 = south)
		float fx = -Mth.sin(this.printYaw), fz = Mth.cos(this.printYaw);
		float rx = Mth.cos(this.printYaw), rz = Mth.sin(this.printYaw);
		float h = size * 0.5F;
		float lift = DECAL_LIFT + this.zFight * DECAL_LIFT_JITTER;
		int color = ARGB.color(Math.round(this.alpha * 0.85F * 255.0F), this.shown);
		float[][] corners = {{-h, -h, u0, v1}, {-h, h, u0, v0}, {h, h, u1, v0}, {h, -h, u1, v1}};
		for (float[] c : corners) {
			double x = cx + rx * c[0] + fx * c[1], z = cz + rz * c[0] + fz * c[1];
			decals.add(SingleQuadParticle.Layer.TRANSLUCENT, (float) (x - cam.x), (float) (cy - cam.y) + lift, (float) (z - cam.z), c[2], c[3], color,
				light);
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
		// above the block top, with a per-puddle jitter so overlapping decals don't lock in step and flicker
		float lift = DECAL_LIFT + this.zFight * DECAL_LIFT_JITTER;
		// (0.20.41) drying darker, or magma cooling: shade()
		int color = this.wet ? ARGB.colorFromFloat(this.alpha * fade, this.r, this.g, this.b)
			: ARGB.color(Math.round(this.alpha * fade * 255.0F), this.shown);
		double[][] corners = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
		for (double[] corner : corners) {
			float dx = (float) (corner[0] - cx), dz = (float) (corner[1] - cz);
			float lx = dx * cos - dz * sin, lz = dx * sin + dz * cos;
			// (the yaw is a quarter turn: a smear's is half turns, so its local axes stay its world ones)
			float u = (lx / (size * this.stretchX) + 0.5F) * (u1 - u0) + u0;
			float v = (lz / (size * this.stretchZ) + 0.5F) * (v1 - v0) + v0;
			decals.add(SingleQuadParticle.Layer.TRANSLUCENT, (float) (corner[0] - cam.x), (float) (y - cam.y) + lift,
				(float) (corner[1] - cam.z), u, v, color, light);
		}
	}
}
