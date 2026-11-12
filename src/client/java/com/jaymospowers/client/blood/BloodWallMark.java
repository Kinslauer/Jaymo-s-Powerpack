package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.blood.BloodTrail;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.41) Andy liked Hematic's blood that "pours over block edges" (ideas only). Blood on the side of a block, drawn
 * flat on the face like a {@link BloodPuddle} is on the ground ({@link BloodDecals}):
 * <ul>
 * <li>a drop that flies into a wall splats on it ({@link #splat}), a bigger one with a run trickling down from it;</li>
 * <li>a pool that spreads over the edge of a drop-off pours over it ({@link #edges}): a run down the side, growing as it
 * runs, as far as the wall goes (a block or two at most), dripping off the bottom for a while.</li>
 * </ul>
 * It lies as long as the pool that fed it (a lasting stain's runs as long as the stain), darkens as it dries, and fades.
 * Magma blood glows and cools here as it does on the ground.
 *
 * <p>Each mark keeps the block it lies on ({@link #host}). Andy: "the blood decals that splashed on the door did not
 * travel with the door as it opened, so they floated in midair". When that block changes ({@link #follow}), a mark on a
 * door swings with the door, about its hinge, to wherever the door now stands; one whose wall is gone (the block broken,
 * a fence gate or trapdoor opened, a trapdoor closed) is gone with it.
 */
public final class BloodWallMark extends Particle implements BloodDecals.Decal {
	/** Its opacity; how long a run takes to run its length (ticks); how far it can run (blocks). */
	static final float ALPHA = 0.75F;
	/** (0.21.x) Wall marks at the edge of view distance fade out over these last blocks instead of popping at it. */
	static final float DRAW_DISTANCE_FADE = 8.0F;
	static final int RUN_TICKS = 50;
	static final float MOST_RUN = 2.0F;
	/** How long a passing mark lies (ticks), the last of them fading. */
	static final int LIFE = 1400, FADE = 150;
	/** A run drips off its end: once every this many ticks (by chance), this many times at most. */
	static final int DRIP_EVERY = 14, DRIPS = 4;
	/** A drop splats on a wall this many times its size; a splat this big or more starts a run (by chance). */
	static final float SPLAT_GROWTH = 2.6F, RUNS_FROM = 0.3F;
	/** How far off the face it's drawn (against flicker). */
	static final float LIFT = 0.015F;

	/** The face it lies on; changes when the door it's on swings ({@link #follow}). */
	private Direction face;
	private final float splat;
	private final float run;
	private final float runWidth;
	private final int color;
	private final boolean stain;
	private final boolean magma;
	private final @Nullable TextureAtlasSprite splatSprite;
	private final @Nullable TextureAtlasSprite runSprite;
	/** Where it starts on the face (its top: the splat's middle, or the edge a run pours over). */
	private double ax, ay, az;
	/** The block it lies on, and how that block stood when last looked at ({@link #follow}). */
	private final BlockPos host;
	private BlockState hostState;
	private int drips;
	/** (0.20.54) When the rain started washing it off (it fades from then), or -1. */
	private int washFrom = -1;

	private BloodWallMark(ClientLevel level, BlockPos host, double x, double y, double z, Direction face, float splat, float run,
		float runWidth, int color, int age, int life, boolean stain) {
		// its light comes from just off the face
		super(level, x + face.getStepX() * 0.1, y, z + face.getStepZ() * 0.1);
		this.ax = x;
		this.ay = y;
		this.az = z;
		this.host = host.immutable();
		this.hostState = level.getBlockState(this.host);
		this.face = face;
		this.splat = splat;
		this.run = run;
		this.runWidth = runWidth;
		this.color = color;
		this.stain = stain;
		this.magma = color == BloodParticles.MAGMA;
		this.age = age;
		this.lifetime = age + life;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		SpriteSet puddles = BloodClient.puddleSprites(), runs = BloodClient.runSprites();
		this.splatSprite = splat > 0.0F && puddles != null ? puddles.get(this.random) : null;
		this.runSprite = run > 0.0F && runs != null ? runs.get(this.random) : null;
		this.drips = run > 0.0F ? DRIPS : 0;
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
		if (!this.follow()) {
			this.remove();
			return;
		}
		// (0.20.54) rain running down the wall washes it away (stain's runs too, as the server washes the stain)
		if (this.age % BloodPuddle.RAIN_CHECK == 0 && this.lifetime - this.age > BloodPuddle.RAIN_WASH
			&& BloodPuddle.rainedOn(this.level, this.x, this.y, this.z)) {
			this.lifetime = this.age + BloodPuddle.RAIN_WASH;
			this.washFrom = this.age;
		}
		// the run, grown to its length, drips off its end for a while
		if (this.drips > 0 && this.age >= RUN_TICKS && this.age < RUN_TICKS + DRIP_EVERY * DRIPS * 2 && this.age % DRIP_EVERY == 0
			&& this.random.nextFloat() < 0.6F) {
			this.drips--;
			Vec3 end = this.point(0.0, -this.run, 0.03);
			BloodClient.drip(this.level, end.x, end.y - 0.05, end.z, this.color, 0.3F + this.runWidth);
		}
		if (this.magma && this.age < BloodParticles.MAGMA_COOLS / 2 && this.random.nextFloat() < 0.02F) {
			Vec3 at = this.point(0.0, -this.run * 0.5, 0.05);
			this.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.02, 0.0);
		}
	}

	/**
	 * Keeps it on the block it lies on: when that block has changed, a door that swung takes it with it; otherwise it
	 * stays only if the wall it's on is still there. False when it has nothing left to lie on.
	 */
	private boolean follow() {
		if (!this.level.hasChunkAt(this.host)) {
			// not loaded here (at the edge of view): nothing to go by, so leave it as it was
			return true;
		}
		BlockState now = this.level.getBlockState(this.host);
		if (now == this.hostState) {
			return true;
		}
		BlockState was = this.hostState;
		this.hostState = now;
		if (now.getBlock() == was.getBlock() && !this.swing(was, now)) {
			return false;
		}
		return this.onWall(now);
	}

	/**
	 * A panel (a door, an open trapdoor) that swung from lying against one side of its block to another: turns the mark
	 * the quarter turn the panel took, about its hinge (the corner the two sides share). Vanilla's door is a board
	 * {@code t} thick against side {@code d1} closed and against side {@code d2} open, so the swing is the quarter turn
	 * about the vertical line {@code t/2} in from both sides that takes {@code d2} to {@code d1} (what faced out now faces
	 * into the block, as a real door's does). False if it swung some way a wall mark can't follow (a trapdoor's flip).
	 */
	private boolean swing(BlockState was, BlockState now) {
		AABB before = bounds(was), after = bounds(now);
		Direction d1 = before == null ? null : againstSide(before), d2 = after == null ? null : againstSide(after);
		if (d1 == null || d2 == null) {
			// not a panel before or after: nothing swung; whether it still has a wall is up to the caller
			return true;
		}
		if (d1 == d2) {
			return true;
		}
		if (d1.getAxis() == Direction.Axis.Y || d2.getAxis() == Direction.Axis.Y || d1.getAxis() == d2.getAxis()) {
			return false;
		}
		double t = Math.min(before.getXsize(), before.getZsize());
		if (Math.abs(t - Math.min(after.getXsize(), after.getZsize())) > 1.0E-3) {
			return false;
		}
		// the hinge line, in the block
		double inset = 0.5 - t * 0.5;
		double px = this.host.getX() + 0.5 + (d1.getStepX() + d2.getStepX()) * inset;
		double pz = this.host.getZ() + 0.5 + (d1.getStepZ() + d2.getStepZ()) * inset;
		// the quarter turn about the vertical axis k = d2 x d1 (k.y = d2.z*d1.x - d2.x*d1.z) is v -> k x v: (x, z) -> (k.y*z, -k.y*x)
		int ky = d2.getStepZ() * d1.getStepX() - d2.getStepX() * d1.getStepZ();
		double rx = this.ax - px, rz = this.az - pz;
		this.ax = px + ky * rz;
		this.az = pz - ky * rx;
		// the same turn on the face it lies on: (x, z) -> (z, -x) is a quarter turn counter-clockwise seen from above
		Direction turned = ky > 0 ? this.face.getCounterClockWise() : this.face.getClockWise();
		this.face = turned;
		// its light comes from just off the face, as when it was made
		this.setPos(this.ax + turned.getStepX() * 0.1, this.ay, this.az + turned.getStepZ() * 0.1);
		this.xo = this.x;
		this.zo = this.z;
		return true;
	}

	/** Whether the block it lies on still has a surface where it starts (just behind it, on the face it lies on). */
	private boolean onWall(BlockState state) {
		VoxelShape shape = state.getCollisionShape(this.level, this.host);
		if (shape.isEmpty()) {
			return false;
		}
		double x = this.ax - this.face.getStepX() * 0.02 - this.host.getX(), y = this.ay - this.host.getY();
		double z = this.az - this.face.getStepZ() * 0.02 - this.host.getZ();
		for (AABB box : shape.toAabbs()) {
			if (box.inflate(0.01).contains(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	private @Nullable AABB bounds(BlockState state) {
		VoxelShape shape = state.getShape(this.level, this.host, CollisionContext.empty());
		return shape.isEmpty() ? null : shape.bounds();
	}

	/** The side of its block a thin board ({@code box}, block-local) lies flat against, or null if it isn't one. */
	private static @Nullable Direction againstSide(AABB box) {
		for (Direction.Axis axis : Direction.Axis.values()) {
			double min = box.min(axis), max = box.max(axis);
			if (max - min >= 0.5) {
				continue;
			}
			// thin this way and whole the other two
			for (Direction.Axis other : Direction.Axis.values()) {
				if (other != axis && (box.min(other) > 1.0E-3 || box.max(other) < 1.0 - 1.0E-3)) {
					return null;
				}
			}
			if (min < 1.0E-3) {
				return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
			}
			if (max > 1.0 - 1.0E-3) {
				return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
			}
			return null;
		}
		return null;
	}

	/** A point on the face: {@code across} along it, {@code down} from the top (negative is down), {@code out} off it. */
	private Vec3 point(double across, double down, double out) {
		// across the face, left to right seen from outside
		Direction side = this.face.getCounterClockWise();
		return new Vec3(this.ax + side.getStepX() * across + this.face.getStepX() * out, this.ay + down,
			this.az + side.getStepZ() * across + this.face.getStepZ() * out);
	}

	@Override
	public ParticleRenderType getGroup() {
		return BloodDecals.TYPE;
	}

	@Override
	public boolean visible(Frustum frustum) {
		return frustum.pointInFrustum(this.ax, this.ay, this.az) || frustum.pointInFrustum(this.ax, this.ay - this.run, this.az);
	}

	@Override
	public void emit(BloodDecals.State decals, Camera camera, float partialTick) {
		float age = this.age + partialTick;
		float alpha = ALPHA;
		int fadeFrom = this.washFrom >= 0 ? this.washFrom : this.lifetime - (this.stain ? BloodTrail.FADE : FADE);
		if (age > fadeFrom) {
			alpha *= 1.0F - Mth.clamp((age - fadeFrom) / (this.lifetime - fadeFrom), 0.0F, 1.0F);
		}
		// (0.21.x) at the edge of view distance fade out over the last blocks instead of popping at it
		if (alpha > 0.0F) {
			float drawDistance = Minecraft.getInstance().options.renderDistance().get() * 16.0F;
			Vec3 at = new Vec3(this.ax, this.ay, this.az);
			double dx = camera.position().x - at.x, dy = camera.position().y - at.y, dz = camera.position().z - at.z;
			float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float fadeStart = drawDistance - DRAW_DISTANCE_FADE;
			if (dist > fadeStart) {
				alpha *= 1.0F - Mth.clamp((dist - fadeStart) / DRAW_DISTANCE_FADE, 0.0F, 1.0F);
			}
		}
		if (alpha <= 0.01F) {
			return;
		}
		int rgb = BloodPuddle.shade(this.color, age, this.stain);
		int light = this.getLightCoords(partialTick);
		if (this.magma) {
			light = BloodPuddle.glow(light, age);
		}
		int argb = ARGB.color(Math.round(alpha * 255.0F), rgb);
		Vec3 cam = camera.position();
		if (this.splatSprite != null) {
			float grow = Mth.clamp(age / 2.0F, 0.5F, 1.0F);
			double h = this.splat * 0.5 * grow;
			this.quad(decals, cam, this.splatSprite, -h, h, h, -h, argb, light, false);
		}
		if (this.runSprite != null) {
			float length = this.run * Mth.clamp(age / RUN_TICKS, 0.0F, 1.0F);
			if (length > 0.01F) {
				double w = this.runWidth * 0.5;
				this.quad(decals, cam, this.runSprite, -w, w, this.splat * 0.15, -length, argb, light, true);
			}
		}
	}

	/** One quad on the face from {@code a0} to {@code a1} across and {@code top} to {@code bottom} (down is negative). */
	private void quad(BloodDecals.State decals, Vec3 cam, TextureAtlasSprite sprite, double a0, double a1, double top, double bottom, int argb,
		int light, boolean run) {
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		// corners: top left, bottom left, bottom right, top right (seen from outside)
		Vec3 tl = this.point(a0, top, LIFT), bl = this.point(a0, bottom, LIFT), br = this.point(a1, bottom, LIFT), tr = this.point(a1, top, LIFT);
		decals.add(BloodDecals.DECAL_LAYER, (float) (tl.x - cam.x), (float) (tl.y - cam.y), (float) (tl.z - cam.z), u0, v0, argb, light);
		decals.add(BloodDecals.DECAL_LAYER, (float) (bl.x - cam.x), (float) (bl.y - cam.y), (float) (bl.z - cam.z), u0, v1, argb, light);
		decals.add(BloodDecals.DECAL_LAYER, (float) (br.x - cam.x), (float) (br.y - cam.y), (float) (br.z - cam.z), u1, v1, argb, light);
		decals.add(BloodDecals.DECAL_LAYER, (float) (tr.x - cam.x), (float) (tr.y - cam.y), (float) (tr.z - cam.z), u1, v0, argb, light);
	}

	// ---- where they come from ------------------------------------------------------------------------------------------

	/**
	 * A drop of {@code color}, {@code size} across, flew into the {@code face} of a wall at {@code at}: a splat there,
	 * and from a big one (by chance) a run down the wall below it.
	 */
	static void splat(ClientLevel level, BlockPos wall, Vec3 at, Direction face, int color, float size) {
		if (face.getAxis() == Direction.Axis.Y || BloodClient.puddleSprites() == null) {
			return;
		}
		float splat = Mth.clamp(size * SPLAT_GROWTH, 0.15F, 0.9F);
		float run = 0.0F;
		if (splat >= RUNS_FROM && level.getRandom().nextFloat() < 0.6F) {
			run = Math.min(reach(level, wall, face, at.y), splat * (1.0F + level.getRandom().nextFloat() * 1.5F));
		}
		add(new BloodWallMark(level, wall, at.x, at.y, at.z, face, splat, run, splat * 0.35F, color, 0, LIFE + level.getRandom().nextInt(600), false));
	}

	/**
	 * A pool of {@code color} lying at ({@code x}, {@code y}, {@code z}), {@code size} blocks across: wherever it spreads
	 * over the edge of the block it lies on and nothing is beyond that edge, it pours down the side. At most two runs.
	 */
	static void edges(ClientLevel level, double x, double y, double z, float size, int color, int age, int life, boolean stain) {
		if (BloodClient.runSprites() == null || size < 0.5F) {
			return;
		}
		BlockPos under = BlockPos.containing(x, y - 0.02, z);
		BlockState ground = level.getBlockState(under);
		// on the top of a whole block (not a slab or a step, whose sides are short)
		if (Math.abs(y - (under.getY() + 1.0)) > 0.02 || !ground.isCollisionShapeFullBlock(level, under)) {
			return;
		}
		double half = size * 0.5;
		int made = 0;
		for (Direction face : Direction.Plane.HORIZONTAL) {
			if (made >= 2) {
				break;
			}
			// how far the pool reaches past the edge on this side
			double edge = switch (face) {
				case EAST -> under.getX() + 1.0 - x;
				case WEST -> x - under.getX();
				case SOUTH -> under.getZ() + 1.0 - z;
				default -> z - under.getZ();
			};
			if (edge > half * 0.85) {
				continue;
			}
			BlockPos beyond = under.relative(face);
			if (!open(level, beyond) || !open(level, beyond.above())) {
				continue;
			}
			double over = half - edge;
			// where along the edge: the pool's own middle, kept off the corners
			double fx = face.getAxis() == Direction.Axis.X ? under.getX() + (face == Direction.EAST ? 1.0 : 0.0) : Mth.clamp(x, under.getX() + 0.15,
				under.getX() + 0.85);
			double fz = face.getAxis() == Direction.Axis.Z ? under.getZ() + (face == Direction.SOUTH ? 1.0 : 0.0) : Mth.clamp(z, under.getZ() + 0.15,
				under.getZ() + 0.85);
			float run = (float) Math.min(reach(level, under, face, y), 0.3 + over * 1.6 + level.getRandom().nextDouble() * 0.5);
			float width = (float) Mth.clamp(over * 0.6, 0.12, 0.4);
			add(new BloodWallMark(level, under, fx, y, fz, face, 0.0F, run, width, color, age, Math.max(1, life), stain));
			made++;
		}
	}

	/** How far down from {@code fromY} a run can go on {@code face} of {@code block} and the whole blocks under it. */
	private static float reach(ClientLevel level, BlockPos block, Direction face, double fromY) {
		double bottom = block.getY();
		BlockPos below = block.below();
		while (fromY - bottom < MOST_RUN && level.getBlockState(below).isFaceSturdy(level, below, face) && open(level, below.relative(face))) {
			bottom = below.getY();
			below = below.below();
		}
		return (float) Math.min(MOST_RUN, fromY - bottom - 0.05);
	}

	private static boolean open(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	private static void add(Particle particle) {
		Minecraft.getInstance().particleEngine.add(particle);
	}
}
