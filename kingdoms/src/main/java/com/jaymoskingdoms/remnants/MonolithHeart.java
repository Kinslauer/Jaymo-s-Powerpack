package com.jaymoskingdoms.remnants;

import com.mojang.math.Transformation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.7.455) Andy: "I'd like the monoliths larger and animated more like the mod they came from". A monolith's heart is
 * no longer blocks but block displays the server moves: a glowing core of its element's gem with six gem shards on its
 * six sides, the whole cluster slowly tumbling while the shards breathe out and in, stretching as they go, and motes
 * drawn in to it off the stone round it. That is how Ancient Remnants' Elderheart behaves (studied from its public
 * source as behaviour only; nothing of its code, model or art is used). Only awake monoliths of ours have a living
 * heart; when the Giant falls the core goes out and the shards hang still and dark ({@link #darken}).
 */
public final class MonolithHeart {
	/** How often the heart is moved (ticks; each move is drawn smoothly over that long), and how near a player must be. */
	static final int MOVE_TICKS = 3;
	static final double ANIMATE_RANGE = 192.0;
	/** How far off the heart is drawn (a display's view range, in 64-block steps). */
	static final float VIEW_RANGE = 5.0F;
	/** The core: its size (blocks), how much it swells and shrinks, and how fast it turns on itself. */
	static final float CORE_SIZE = 3.0F, CORE_PULSE = 0.12F, CORE_SPIN = 0.045F;
	/** The shards: how big (blocks a side), how far from the centre at rest, how far they swing out and in. */
	static final float SHARD_SIZE = 1.7F, SHARD_REST = 4.2F, SHARD_SWING = 2.6F;
	/** How long the shards grow along their axis at rest, and how much more (or less) as they swing. */
	static final float SHARD_STRETCH = 1.4F, SHARD_STRETCH_SWING = 0.8F;
	/** How far the whole cluster tumbles round each axis (radians) and how fast (per tick). */
	static final float TUMBLE_X = 14.0F, TUMBLE_Y = 9.0F, TUMBLE_Z = 5.0F;
	static final float TUMBLE_X_RATE = 0.011F, TUMBLE_Y_RATE = 0.019F, TUMBLE_Z_RATE = 0.027F;
	/** The shards' breath: its quick beat and the slow swell over it (per tick). */
	static final float BREATH_RATE = 0.1F, SWELL_RATE = 0.012F;
	/** Motes drawn in off the stone: how often (ticks), how far a ray looks for stone, how fast a mote flies in. */
	static final int RAY_TICKS = 2;
	static final double RAY_REACH = 14.0, RAY_SPEED = 0.6;

	static final String TAG = "jaymoskingdoms.monolith_heart";
	/** Each display's part: 0 the core, 1 to 6 the shards. */
	static final String PART = "jaymoskingdoms.heart_part_";
	static final int PARTS = 7;

	/** Each shard's axis: the turn that stands a shard (drawn along +Y) on its side of the core. */
	private static final Quaternionf[] AXES = {
		new Quaternionf(),
		new Quaternionf().rotateX(Mth.PI),
		new Quaternionf().rotateZ(-Mth.HALF_PI),
		new Quaternionf().rotateZ(Mth.HALF_PI),
		new Quaternionf().rotateX(Mth.HALF_PI),
		new Quaternionf().rotateX(-Mth.HALF_PI)
	};
	/** A cube stood on its corner, its long diagonal along +Y: a crystal. */
	private static final Quaternionf ON_CORNER = new Quaternionf().rotationTo(new Vector3f(1.0F, 1.0F, 1.0F).normalize(), new Vector3f(0.0F, 1.0F, 0.0F));
	private static final float HALF_DIAGONAL = (float) Math.sqrt(3.0) / 2.0F;

	private MonolithHeart() {
	}

	/** Where the centre of {@code m}'s heart floats. */
	static Vec3 centre(Monoliths.Monolith m) {
		return new Vec3(m.pos().getX() + 0.5, m.pos().getY() + m.heart() + 0.5, m.pos().getZ() + 0.5);
	}

	/** {@code m}'s heart displays, by part (a slot is null if that part is missing). */
	static Display.BlockDisplay[] find(ServerLevel level, Monoliths.Monolith m) {
		Display.BlockDisplay[] parts = new Display.BlockDisplay[PARTS];
		List<Display.BlockDisplay> found = level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(BlockPos.containing(centre(m))).inflate(2.0),
			d -> d.entityTags().contains(TAG));
		for (Display.BlockDisplay d : found) {
			for (int i = 0; i < PARTS; i++) {
				if (d.entityTags().contains(PART + i)) {
					if (parts[i] == null) {
						parts[i] = d;
					} else {
						d.discard();
					}
				}
			}
		}
		return parts;
	}

	/** Raises {@code m}'s heart afresh (any old pieces of it gone first). */
	static void raise(ServerLevel level, Monoliths.Monolith m) {
		for (Display.BlockDisplay d : find(level, m)) {
			if (d != null) {
				d.discard();
			}
		}
		Vec3 at = centre(m);
		BlockState gem = m.element().core().defaultBlockState();
		for (int i = 0; i < PARTS; i++) {
			Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			d.setBlockState(gem);
			d.setPos(at.x, at.y, at.z);
			d.setViewRange(VIEW_RANGE);
			d.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
			d.setWidth(CORE_SIZE + 2.0F * (SHARD_REST + SHARD_SWING));
			d.setHeight(CORE_SIZE + 2.0F * (SHARD_REST + SHARD_SWING));
			d.setTransformation(pose(i, level.getGameTime()));
			d.addTag(TAG);
			d.addTag(PART + i);
			level.addFreshEntity(d);
		}
	}

	/** Moves every awake heart of ours that someone is near enough to see. */
	static void tick(ServerLevel level, List<Monoliths.Monolith> all) {
		long now = level.getGameTime();
		if (now % MOVE_TICKS != 0 && now % RAY_TICKS != 0) {
			return;
		}
		for (Monoliths.Monolith m : all) {
			if (!m.ours() || m.state() != Monoliths.AWAKE || m.heart() < 0) {
				continue;
			}
			Vec3 at = centre(m);
			if (!level.isPositionEntityTicking(BlockPos.containing(at)) || level.getNearestPlayer(at.x, at.y, at.z, ANIMATE_RANGE, false) == null) {
				continue;
			}
			if (now % MOVE_TICKS == 0) {
				Display.BlockDisplay[] parts = find(level, m);
				boolean whole = true;
				for (Display.BlockDisplay d : parts) {
					whole &= d != null;
				}
				if (!whole) {
					raise(level, m);
					continue;
				}
				for (int i = 0; i < PARTS; i++) {
					parts[i].setTransformationInterpolationDuration(MOVE_TICKS);
					parts[i].setTransformationInterpolationDelay(0);
					parts[i].setTransformation(pose(i, now + MOVE_TICKS));
				}
			}
			if (now % RAY_TICKS == 0) {
				ray(level, m, at);
			}
		}
	}

	/** Part {@code part}'s pose at game time {@code t}, about the heart's centre. */
	static Transformation pose(int part, long t) {
		float time = (float) (t % 2_400_000L);
		Quaternionf tumble = new Quaternionf()
			.rotateX(TUMBLE_X * Mth.cos(time * TUMBLE_X_RATE))
			.rotateY(TUMBLE_Y * Mth.cos(time * TUMBLE_Y_RATE))
			.rotateZ(TUMBLE_Z * Mth.cos(time * TUMBLE_Z_RATE));
		if (part == 0) {
			// the core: turning on itself inside the tumble, swelling a little with the shards' breath
			float size = CORE_SIZE * (1.0F + CORE_PULSE * breath(time));
			Quaternionf turn = tumble.mul(new Quaternionf().rotateY(time * CORE_SPIN), new Quaternionf());
			Vector3f shift = turn.transform(new Vector3f(-size / 2.0F));
			return new Transformation(shift, turn, new Vector3f(size), null);
		}
		// a shard: out along its axis, stretched along it as it swings out, a crystal stood on its corner
		float breath = breath(time);
		float out = SHARD_REST + SHARD_SWING * breath, stretch = SHARD_STRETCH + SHARD_STRETCH_SWING * breath;
		Quaternionf facing = tumble.mul(AXES[part - 1], new Quaternionf());
		// the cube's centre sits on the axis, `out` from the heart's centre: its corner is half a diagonal (stretched) below
		Vector3f shift = facing.transform(new Vector3f(0.0F, out - SHARD_SIZE * HALF_DIAGONAL * stretch, 0.0F));
		return new Transformation(shift, facing, new Vector3f(SHARD_SIZE, SHARD_SIZE * stretch, SHARD_SIZE), new Quaternionf(ON_CORNER));
	}

	/** The shards' breath at {@code time}: -1 (drawn in) to 1 (flung out), a quick beat under a slow swell. */
	private static float breath(float time) {
		return Mth.sin(time * BREATH_RATE) * Mth.cos(time * SWELL_RATE);
	}

	/** A mote drawn in to the heart off the stone round it (as the Elderheart pulls light off the blocks about it). */
	private static void ray(ServerLevel level, Monoliths.Monolith m, Vec3 at) {
		RandomSource random = level.getRandom();
		Vec3 dir;
		do {
			dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
		} while (dir.lengthSqr() < 1.0E-6);
		dir = dir.normalize();
		// start outside the heart's own reach so the ray finds the crown, not the air round the core
		Vec3 from = at.add(dir.scale(CORE_SIZE));
		BlockHitResult hit = level.clip(new ClipContext(from, at.add(dir.scale(RAY_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		Vec3 p = hit.getLocation();
		Vec3 in = at.subtract(p).normalize();
		level.sendParticles(m.element().mote, p.x, p.y, p.z, 0, in.x, in.y, in.z, RAY_SPEED);
		level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, in.x, in.y, in.z, RAY_SPEED * 0.5);
	}

	/**
	 * Its Giant fallen, {@code m}'s heart goes out: the core is gone and the shards hang where they were, dark and still.
	 * Returns whether there was a heart of displays to put out.
	 */
	static boolean darken(ServerLevel level, Monoliths.Monolith m) {
		boolean any = false;
		for (Display.BlockDisplay d : find(level, m)) {
			if (d == null) {
				continue;
			}
			any = true;
			if (d.entityTags().contains(PART + 0)) {
				d.discard();
			} else {
				d.setBrightnessOverride(null);
				d.setBlockState(m.element().band().defaultBlockState());
			}
		}
		return any;
	}

	/**
	 * (0.7.455) A monolith raised before its heart moved: finds its old block core over the pillar, and with it takes
	 * out the core and the four still shards about it so a living heart can float there. Returns how high over the foot
	 * the heart's centre is, or -1 if no core was found.
	 */
	static int fromBlocks(ServerLevel level, Monoliths.Monolith m) {
		int scale = Monoliths.SCALE, half = scale / 2;
		BlockPos foot = m.pos();
		int lowest = Integer.MAX_VALUE, count = 0;
		for (BlockPos p : BlockPos.betweenClosed(foot.offset(-half, 1, -half), foot.offset(half, Monoliths.PART_HEIGHT, half))) {
			BlockState state = level.getBlockState(p);
			if (state.is(m.element().core()) || state.is(m.element().vanilla[Element.CORE])) {
				lowest = Math.min(lowest, p.getY());
				count++;
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		if (count == 0) {
			return -1;
		}
		int bottom = lowest - foot.getY();
		if (count < scale * scale * scale) {
			// one raised before 0.7.263: a single core block, left shards and all
			return bottom;
		}
		// the shards stood one cube lower, level and one higher, two cubes out on the diagonals (Monoliths.raise before 0.7.455)
		int[][] shards = {{2, -1, 2}, {-2, 0, 2}, {2, 1, -2}, {-2, -1, -2}};
		for (int[] s : shards) {
			BlockPos corner = foot.offset(s[0] * scale - half, bottom + s[1] * scale, s[2] * scale - half);
			for (BlockPos p : BlockPos.betweenClosed(corner, corner.offset(scale - 1, scale - 1, scale - 1))) {
				BlockState state = level.getBlockState(p);
				if (state.is(m.element().band()) || state.is(m.element().vanilla[Element.BAND])) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		return bottom + half;
	}
}
