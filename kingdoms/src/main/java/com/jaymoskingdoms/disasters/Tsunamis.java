package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Loaded;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tsunamis (medieval picks, nd-tsunami). A quake under the sea ({@link Earthquakes}) sends a wave toward the nearest
 * shore with someone on it. Out at sea it passes as a swell; where it reaches land it rears up as a wall of real water a
 * few blocks deep that runs inland, losing strength as it climbs and the further it goes, smashing what is weak in its
 * way (glass, fences, crops, leaves, and at its worst timber walls) and carrying everyone off their feet, then drawing
 * back: every block of water it put down is taken back out behind it. The higher sea ({@code seas.HighSea}) makes every
 * coast matter more. The world's ending sends them in threes.
 * <p>
 * Kept cheap: the front is a line of columns worked one row at a time, every block through the shared
 * {@link BlockBudget} (the wave waits when it's spent), and only on loaded land.
 */
public final class Tsunamis {
	/** How far it looks for a shore (blocks), every so many blocks along each of this many ways. */
	static final int COAST_LOOK = 192, COAST_STEP = 8, COAST_WAYS = 16;
	/** It starts this far out at sea from the shore it's for (blocks). */
	static final double OFFSHORE = 70.0;
	/** Someone this near the quake is who the wave runs at. */
	static final double FOR_RANGE = 320.0;
	/** Its front's width, its height at the shore, by magnitude (blocks: base, plus a magnitude each). */
	static final int WIDTH = 50, WIDTH_PER_MAGNITUDE = 22;
	static final float HEIGHT = 2.0F, HEIGHT_PER_MAGNITUDE = 1.6F;
	/** How fast it runs (blocks a tick, times its {@link Magnitude#speed}); how much height it loses a block inland. */
	static final double SPEED = 0.35;
	static final float DECAY = 0.045F;
	/** The wall of water is at most this deep over the ground (blocks); the most it runs inland (blocks). */
	static final int CREST = 4, RUN_MOST = 260;
	/** By magnitude: the hardest block it smashes. */
	static final float[] SMASH = {0.0F, 0.6F, 1.0F, 1.5F, 2.0F, 2.5F};
	/** The water it put down stands this long before draining (ticks), and drains this many a tick at most. */
	static final int HOLD = 70, DRAIN_PER_TICK = 400;
	/** How hard it carries what it hits (blocks a tick, per magnitude), how much it hurts (once a second). */
	static final double CARRY = 0.18, DRAW_BACK = 0.06;
	static final float HURT = 2.0F;
	/** The wait between one wave and the next (ticks); each later wave is this much the size of the last. */
	static final int WAVE_GAP = 500;
	static final float NEXT_WAVE = 0.85F;
	/** At most this many at once. */
	static final int MOST = 3;

	/** A shore: a point of the sea, and the way from it to the land. */
	record Coast(Vec3 sea, Vec3 inland) {
	}

	private record Placed(BlockPos pos, long at) {
	}

	private static final class Wave {
		final Vec3 origin, dir, perp;
		final int width, magnitude;
		final float height;
		double front;
		int row = -1, wavesLeft, waitUntil;
		final boolean[] dead;
		final float[] inland;
		boolean landed;
		long nextHurt;

		Wave(Vec3 origin, Vec3 dir, int magnitude, float height, int waves) {
			this.origin = origin;
			this.dir = dir;
			this.perp = new Vec3(-dir.z, 0.0, dir.x);
			this.magnitude = magnitude;
			this.width = WIDTH + WIDTH_PER_MAGNITUDE * magnitude;
			this.height = height;
			this.wavesLeft = waves;
			this.dead = new boolean[width];
			this.inland = new float[width];
		}

		Vec3 at(double along, double across) {
			return origin.add(dir.scale(along)).add(perp.scale(across));
		}

		boolean spent() {
			for (boolean d : dead) {
				if (!d) {
					return false;
				}
			}
			return true;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static final ArrayDeque<Placed> PLACED = new ArrayDeque<>();
	private static int ticks;

	private Tsunamis() {
	}

	/** Every wave ends and its water drains at once (the server's stopping, or an op cleared the skies). */
	static void clear(@Nullable ServerLevel level) {
		WAVES.clear();
		if (level != null) {
			for (Placed p : PLACED) {
				if (Loaded.made(level, p.pos()) && level.getBlockState(p.pos()).is(Blocks.WATER)) {
					level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
			}
		}
		PLACED.clear();
	}

	/** Whether (x, z) is open sea. */
	static boolean atSea(ServerLevel level, double x, double z) {
		BlockPos at = BlockPos.containing(x, level.getSeaLevel() - 1, z);
		if (!level.getBiome(at).is(BiomeTags.IS_OCEAN)) {
			return false;
		}
		return !Loaded.made(level, at) || level.getFluidState(at).is(FluidTags.WATER);
	}

	/** The nearest shore to {@code near}, or null (no sea in reach). */
	static @Nullable Coast coastNear(ServerLevel level, BlockPos near) {
		Coast best = null;
		double bestD = Double.MAX_VALUE;
		for (int w = 0; w < COAST_WAYS; w++) {
			double a = w * Math.PI * 2.0 / COAST_WAYS;
			double dx = Math.cos(a), dz = Math.sin(a);
			for (int d = COAST_STEP; d <= COAST_LOOK; d += COAST_STEP) {
				double x = near.getX() + dx * d, z = near.getZ() + dz * d;
				if (!Loaded.made(level, Mth.floor(x), Mth.floor(z))) {
					break;
				}
				if (atSea(level, x, z)) {
					if (d < bestD) {
						bestD = d;
						best = new Coast(new Vec3(x, level.getSeaLevel(), z), new Vec3(-dx, 0.0, -dz));
					}
					break;
				}
			}
		}
		return best;
	}

	/** A wave of {@code m} (0: by chance) comes in on the shore nearest {@code near}, {@code waves} of them. */
	static boolean toward(ServerLevel level, BlockPos near, int m, int waves) {
		Coast coast = coastNear(level, near);
		if (coast == null) {
			return false;
		}
		if (m <= 0) {
			m = Magnitude.roll(level, level.getRandom(), Magnitude.MODERATE);
		}
		return launch(level, coast.sea().subtract(coast.inland().scale(OFFSHORE)), coast.inland(), m, waves);
	}

	/** (The Last Bell) A wave of {@code m} rises out at sea at {@code origin} and runs at the nearest land. */
	static boolean from(ServerLevel level, Vec3 origin, int m) {
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (int w = 0; w < COAST_WAYS; w++) {
			double a = w * Math.PI * 2.0 / COAST_WAYS;
			double dx = Math.cos(a), dz = Math.sin(a);
			for (int d = COAST_STEP; d <= COAST_LOOK; d += COAST_STEP) {
				double x = origin.x + dx * d, z = origin.z + dz * d;
				if (!Loaded.made(level, Mth.floor(x), Mth.floor(z))) {
					break;
				}
				if (!atSea(level, x, z)) {
					if (d < bestD) {
						bestD = d;
						best = new Vec3(dx, 0.0, dz);
					}
					break;
				}
			}
		}
		return best != null && launch(level, new Vec3(origin.x, level.getSeaLevel(), origin.z), best, m, 1);
	}

	/** (nd-chains) A quake under the sea at {@code origin}: a wave of {@code m} runs at whoever is nearest. */
	static boolean start(ServerLevel level, Vec3 origin, int m, int waves) {
		ServerPlayer nearest = null;
		double best = FOR_RANGE * FOR_RANGE;
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceToSqr(origin.x, p.getY(), origin.z);
			if (!p.isSpectator() && d < best) {
				best = d;
				nearest = p;
			}
		}
		if (nearest == null) {
			return false;
		}
		Vec3 dir = new Vec3(nearest.getX() - origin.x, 0.0, nearest.getZ() - origin.z);
		if (dir.lengthSqr() < 1.0) {
			return false;
		}
		return launch(level, origin, dir.normalize(), m, waves);
	}

	private static boolean launch(ServerLevel level, Vec3 origin, Vec3 dir, int m, int waves) {
		if (WAVES.size() >= MOST) {
			return false;
		}
		m = Magnitude.clamp(m);
		Wave w = new Wave(new Vec3(origin.x, level.getSeaLevel(), origin.z), dir, m, HEIGHT + HEIGHT_PER_MAGNITUDE * m, Math.max(1, waves));
		WAVES.add(w);
		// the signs: the sea draws back, the animals run, something roars out there
		Vec3 shore = origin.add(dir.scale(OFFSHORE));
		Signs.omen(level, shore, Signs.RADIUS, (int)Math.round(OFFSHORE * 2.0 / (SPEED * Magnitude.speed(m))) + Signs.GATHER, m);
		return true;
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ticks++;
		drain(level);
		if (WAVES.isEmpty()) {
			return;
		}
		boolean damage = level.getGameRules().get(Disasters.DISASTER_DAMAGE);
		List<Wave> next = new ArrayList<>();
		for (java.util.Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			if (ticks < w.waitUntil) {
				continue;
			}
			if (!run(level, w, damage)) {
				it.remove();
				if (w.wavesLeft > 1) {
					Wave again = new Wave(w.origin, w.dir, w.magnitude, w.height * NEXT_WAVE, w.wavesLeft - 1);
					again.waitUntil = ticks + WAVE_GAP;
					next.add(again);
				}
			}
		}
		WAVES.addAll(next);
	}

	/** One tick of a wave; false when it's spent. */
	private static boolean run(ServerLevel level, Wave w, boolean damage) {
		int sea = level.getSeaLevel();
		double speed = SPEED * Magnitude.speed(w.magnitude);
		int targetRow = Mth.floor(w.front + speed);
		// work every row it has reached, while the budget lasts (else it waits there)
		while (w.row < targetRow) {
			if (BlockBudget.empty()) {
				break;
			}
			w.row++;
			row(level, w, w.row, sea, damage);
		}
		w.front = Math.min(w.front + speed, w.row + 0.999);
		carry(level, w, sea);
		if (ticks % 30 == 0) {
			Vec3 mid = w.at(w.front, 0.0);
			level.playSound(null, mid.x, sea + 1.0, mid.z, SoundEvents.GENERIC_SPLASH, SoundSource.WEATHER, 8.0F, 0.35F);
			level.playSound(null, mid.x, sea + 1.0, mid.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 6.0F, 0.4F);
		}
		return !w.spent() && w.front < OFFSHORE + RUN_MOST + 40;
	}

	/** The front reaches row {@code r}: each column of it on land floods, or the wave dies there. */
	private static void row(ServerLevel level, Wave w, int r, int sea, boolean damage) {
		float smash = SMASH[w.magnitude];
		boolean struck = false;
		for (int i = 0; i < w.width; i++) {
			if (w.dead[i]) {
				continue;
			}
			Vec3 at = w.at(r, i - w.width / 2.0);
			int x = Mth.floor(at.x), z = Mth.floor(at.z);
			if (!Loaded.made(level, x, z)) {
				w.dead[i] = true;
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos below = new BlockPos(x, ground - 1, z);
			if (ground <= sea + 1 && level.getFluidState(below).is(FluidTags.WATER)) {
				// still the sea: it passes as a swell (and spray where it rises)
				if (w.inland[i] > 0.0F || level.getRandom().nextInt(40) == 0) {
					level.sendParticles(ParticleTypes.SPLASH, x + 0.5, sea + 1.0, z + 0.5, 4, 0.5, 0.3, 0.5, 0.2);
				}
				continue;
			}
			float left = w.height - w.inland[i] * DECAY;
			w.inland[i] += 1.0F;
			int line = Mth.floor(sea + left);
			if (ground >= line || w.inland[i] > RUN_MOST) {
				w.dead[i] = true;
				continue;
			}
			struck = true;
			if (!damage) {
				continue;
			}
			int top = Math.min(line, ground + CREST);
			for (int y = ground; y < top; y++) {
				BlockPos p = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(p);
				if (!state.isAir() && !weak(level, p, state, smash)) {
					continue;
				}
				if (!state.isAir() && !BlockBudget.destroy(level, p, true)) {
					return;
				}
				if (!BlockBudget.set(level, p, Blocks.WATER.defaultBlockState())) {
					return;
				}
				PLACED.addLast(new Placed(p, ticks));
			}
			// the white water at its crest
			if (level.getRandom().nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.SPLASH, x + 0.5, top + 0.5, z + 0.5, 6, 0.5, 0.4, 0.5, 0.3);
				level.sendParticles(ParticleTypes.CLOUD, x + 0.5, top + 0.5, z + 0.5, 1, 0.4, 0.2, 0.4, 0.02);
			}
		}
		if (struck && !w.landed) {
			w.landed = true;
			Vec3 mid = w.at(r, 0.0);
			com.jaymoskingdoms.DisasterRelief.struck(level, mid.x, mid.z, w.width, "tsunami", w.magnitude);
		}
	}

	/** What the wave breaks on its way: plants, glass, fences, leaves, crops, and (by its strength) timber. */
	private static boolean weak(ServerLevel level, BlockPos pos, BlockState state, float smash) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
			return false;
		}
		if (state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS)) {
			return true;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= smash;
	}

	/** Everything in the wave's front is carried inland; behind it, what's in the water is drawn back toward the sea. */
	private static void carry(ServerLevel level, Wave w, int sea) {
		Vec3 mid = w.at(w.front, 0.0);
		AABB box = new AABB(mid, mid).inflate(w.width / 2.0 + 4.0, w.height + 6.0, w.width / 2.0 + 4.0);
		boolean hurt = ticks >= w.nextHurt;
		if (hurt) {
			w.nextHurt = ticks + 20;
		}
		for (Entity e : level.getEntities((Entity)null, box, e -> e.isAlive() && !e.isSpectator())) {
			Vec3 rel = e.position().subtract(w.origin);
			double along = rel.dot(w.dir), across = rel.dot(w.perp);
			if (Math.abs(across) > w.width / 2.0 || e.getY() > sea + w.height + 2.0) {
				continue;
			}
			if (Math.abs(along - w.front) < 3.0 && w.landed) {
				double carry = CARRY * w.magnitude * 0.4;
				e.push(w.dir.x * carry, 0.06, w.dir.z * carry);
				e.syncVelocity = true;
				if (hurt && e instanceof LivingEntity living) {
					living.hurtServer(level, level.damageSources().drown(), HURT * Magnitude.harm(w.magnitude));
				}
			} else if (along < w.front - 8.0 && along > w.front - 60.0 && e.isInWater()) {
				e.push(-w.dir.x * DRAW_BACK, 0.0, -w.dir.z * DRAW_BACK);
				e.syncVelocity = true;
			}
		}
	}

	/** The water behind the waves drains away, oldest first. */
	private static void drain(ServerLevel level) {
		int n = 0;
		while (!PLACED.isEmpty() && n < DRAIN_PER_TICK && ticks - PLACED.peekFirst().at() >= HOLD) {
			Placed p = PLACED.removeFirst();
			if (Loaded.made(level, p.pos()) && level.getBlockState(p.pos()).is(Blocks.WATER) && BlockBudget.spend()) {
				level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				n++;
			} else if (BlockBudget.empty()) {
				PLACED.addFirst(p);
				break;
			}
		}
	}

	/** Whether a wave is on its way near (x, z) (the storm bell rings for one). */
	static boolean coming(double x, double z, double range) {
		for (Wave w : WAVES) {
			Vec3 mid = w.at(w.front, 0.0);
			if ((mid.x - x) * (mid.x - x) + (mid.z - z) * (mid.z - z) < range * range) {
				return true;
			}
		}
		return false;
	}
}
