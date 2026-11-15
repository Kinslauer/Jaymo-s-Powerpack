package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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

	/**
	 * A block column the wave flooded, or one beside it (water flowing over can turn to new sources there): {@code pos}
	 * is the column at the height its ground (and any water it already had) reached before the wave came, so anything
	 * wet from there up is the wave's. Looked at again after it's drained ({@code pass}), in case new sources formed.
	 */
	private record Drain(long pos, long at, int pass) {
	}

	/** How far beside a flooded column its footprint reaches (blocks), and how many times a column is drained. */
	static final int FOOTPRINT = 2, PASSES = 4;
	/** How often columns that were out of loaded land are looked at again (ticks). */
	static final int LATER_EVERY = 200;

	/** The wave's footprint still to drain, saved with the world so a stop or restart never leaves the water standing. */
	static final AttachmentType<List<Long>> UNDRAINED = AttachmentRegistry.create(JaymosKingdoms.id("tsunami_undrained"),
		b -> b.persistent(Codec.LONG.listOf()));

	private static final class Wave {
		final Vec3 origin, dir, perp;
		final int width, magnitude;
		final float height;
		double front;
		int row = -1, wavesLeft, waitUntil;
		final boolean[] dead;
		/** By lane: the row where it first came on land (-1: not yet). */
		final int[] shore;
		/** The block columns of the row being worked, and how far through them it is. */
		long @Nullable [] cells;
		int cell;
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
			this.shore = new int[width];
			java.util.Arrays.fill(this.shore, -1);
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
	private static final ArrayDeque<Drain> FLOODED = new ArrayDeque<>();
	/** Footprint columns that were out of loaded land when their turn came. */
	private static final ArrayDeque<Drain> LATER = new ArrayDeque<>();
	/** The columns already in the footprint (x, z packed), so each is taken once. */
	private static final java.util.Set<Long> SEEN = new java.util.HashSet<>();
	private static int ticks;
	private static boolean loaded;

	private Tsunamis() {
	}

	/** An op cleared the skies: every wave ends, and its water drains from now on (a share a tick, as ever). */
	static void clear(@Nullable ServerLevel level) {
		WAVES.clear();
		List<Drain> all = new ArrayList<>(FLOODED);
		all.addAll(LATER);
		FLOODED.clear();
		LATER.clear();
		for (Drain d : all) {
			FLOODED.addLast(new Drain(d.pos(), Long.MIN_VALUE / 2, d.pass()));
		}
	}

	/** The server is stopping: the waves end, and what's left to drain is saved to drain after it starts again. */
	static void stop(ServerLevel level) {
		WAVES.clear();
		List<Long> left = new ArrayList<>();
		FLOODED.forEach(d -> left.add(d.pos()));
		LATER.forEach(d -> left.add(d.pos()));
		level.setAttached(UNDRAINED, left);
		FLOODED.clear();
		LATER.clear();
		SEEN.clear();
		loaded = false;
	}

	private static void load(ServerLevel level) {
		if (loaded) {
			return;
		}
		loaded = true;
		for (long pos : level.getAttachedOrElse(UNDRAINED, List.<Long>of())) {
			FLOODED.addLast(new Drain(pos, Long.MIN_VALUE / 2, 0));
			SEEN.add(column(BlockPos.getX(pos), BlockPos.getZ(pos)));
		}
		level.removeAttached(UNDRAINED);
	}

	private static long column(int x, int z) {
		return (long)x << 32 | z & 0xFFFFFFFFL;
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
		load(level);
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
		// work every row it has reached, a whole column at a time while the budget lasts (else it waits there)
		while (true) {
			if (w.cells == null) {
				if (w.row >= targetRow) {
					break;
				}
				w.row++;
				w.cells = cells(w, w.row);
				w.cell = 0;
			}
			while (w.cell < w.cells.length) {
				if (damage && BlockBudget.share(BlockBudget.Of.TSUNAMI) < CREST * 2) {
					break;
				}
				column(level, w, w.row, w.cells[w.cell++], sea, damage);
			}
			if (w.cell < w.cells.length) {
				break;
			}
			w.cells = null;
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

	/**
	 * Every block column whose middle lies in the front's row {@code r} (the strip from {@code r} to {@code r + 1} along
	 * the wave, its width across), each once: worked row by row, they cover the wave's whole path with no gaps however it's
	 * angled.
	 */
	private static long[] cells(Wave w, int r) {
		double half = w.width / 2.0;
		Vec3[] corners = {w.at(r, -half), w.at(r, half), w.at(r + 1, -half), w.at(r + 1, half)};
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Vec3 c : corners) {
			minX = Math.min(minX, c.x);
			maxX = Math.max(maxX, c.x);
			minZ = Math.min(minZ, c.z);
			maxZ = Math.max(maxZ, c.z);
		}
		java.util.List<Long> out = new ArrayList<>();
		for (int x = Mth.floor(minX) - 1; x <= Mth.floor(maxX) + 1; x++) {
			for (int z = Mth.floor(minZ) - 1; z <= Mth.floor(maxZ) + 1; z++) {
				double rx = x + 0.5 - w.origin.x, rz = z + 0.5 - w.origin.z;
				if (Mth.floor(rx * w.dir.x + rz * w.dir.z) != r || Math.abs(rx * w.perp.x + rz * w.perp.z) > half) {
					continue;
				}
				out.add(column(x, z));
			}
		}
		long[] cells = new long[out.size()];
		for (int i = 0; i < cells.length; i++) {
			cells[i] = out.get(i);
		}
		return cells;
	}

	/** The front reaches the block column {@code cell} in row {@code r}: on land it floods, or the wave dies there. */
	private static void column(ServerLevel level, Wave w, int r, long cell, int sea, boolean damage) {
		int x = (int)(cell >> 32), z = (int)cell;
		double rx = x + 0.5 - w.origin.x, rz = z + 0.5 - w.origin.z;
		int i = Mth.clamp(Mth.floor(rx * w.perp.x + rz * w.perp.z + w.width / 2.0), 0, w.width - 1);
		if (w.dead[i]) {
			return;
		}
		if (!Loaded.made(level, x, z)) {
			w.dead[i] = true;
			return;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos below = new BlockPos(x, ground - 1, z);
		if (ground <= sea + 1 && level.getFluidState(below).is(FluidTags.WATER)) {
			// still the sea: it passes as a swell (and spray where it rises)
			if (w.shore[i] >= 0 || level.getRandom().nextInt(40) == 0) {
				level.sendParticles(ParticleTypes.SPLASH, x + 0.5, sea + 1.0, z + 0.5, 4, 0.5, 0.3, 0.5, 0.2);
			}
			return;
		}
		if (w.shore[i] < 0) {
			w.shore[i] = r;
		}
		int inland = r - w.shore[i] + 1;
		float left = w.height - inland * DECAY;
		int line = Mth.floor(sea + left);
		if (ground >= line || inland > RUN_MOST) {
			w.dead[i] = true;
			return;
		}
		if (!w.landed) {
			w.landed = true;
			com.jaymoskingdoms.DisasterRelief.struck(level, x, z, w.width, "tsunami", w.magnitude);
		}
		if (!damage) {
			return;
		}
		// before the water comes: this column and those beside it, as they were, to drain back to
		footprint(level, x, z);
		float smash = SMASH[w.magnitude];
		int top = Math.min(line, ground + CREST);
		for (int y = ground; y < top; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && !weak(level, p, state, smash)) {
				continue;
			}
			if (!state.isAir() && !BlockBudget.destroy(BlockBudget.Of.TSUNAMI, level, p, true)) {
				return;
			}
			if (!BlockBudget.set(BlockBudget.Of.TSUNAMI, level, p, Blocks.WATER.defaultBlockState())) {
				return;
			}
		}
		// the white water at its crest
		if (level.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.SPLASH, x + 0.5, top + 0.5, z + 0.5, 6, 0.5, 0.4, 0.5, 0.3);
			level.sendParticles(ParticleTypes.CLOUD, x + 0.5, top + 0.5, z + 0.5, 1, 0.4, 0.2, 0.4, 0.02);
		}
	}

	/** The columns round (x, z) join the footprint, each at the height its ground and old water had before the wave. */
	private static void footprint(ServerLevel level, int x, int z) {
		for (int dx = -FOOTPRINT; dx <= FOOTPRINT; dx++) {
			for (int dz = -FOOTPRINT; dz <= FOOTPRINT; dz++) {
				int cx = x + dx, cz = z + dz;
				if (!Loaded.made(level, cx, cz) || !SEEN.add(column(cx, cz))) {
					continue;
				}
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
				FLOODED.addLast(new Drain(BlockPos.asLong(cx, ground, cz), ticks, 0));
			}
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

	/**
	 * The water behind the waves drains away, oldest first: every block of water in a footprint column above where its
	 * ground and old water stood, sources the flowing water made included. Each column is looked at again a few times
	 * after, until no water has come back into it; one out of loaded land waits until it's loaded again.
	 */
	private static void drain(ServerLevel level) {
		int n = 0;
		while (!FLOODED.isEmpty() && n < DRAIN_PER_TICK && ticks - FLOODED.peekFirst().at() >= HOLD) {
			if (BlockBudget.share(BlockBudget.Of.TSUNAMI) < CREST + 3) {
				break;
			}
			Drain d = FLOODED.removeFirst();
			int x = BlockPos.getX(d.pos()), y0 = BlockPos.getY(d.pos()), z = BlockPos.getZ(d.pos());
			if (!Loaded.made(level, x, z)) {
				LATER.addLast(d);
				continue;
			}
			boolean wet = false;
			for (int y = y0; y <= y0 + CREST + 2; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p).is(Blocks.WATER) && BlockBudget.set(BlockBudget.Of.TSUNAMI, level, p, Blocks.AIR.defaultBlockState())) {
					wet = true;
					n++;
				}
			}
			if (wet && d.pass() + 1 < PASSES) {
				FLOODED.addLast(new Drain(d.pos(), ticks, d.pass() + 1));
			} else {
				SEEN.remove(column(x, z));
			}
		}
		if (ticks % LATER_EVERY == 0) {
			for (int i = LATER.size(); i > 0; i--) {
				Drain d = LATER.removeFirst();
				if (Loaded.made(level, BlockPos.getX(d.pos()), BlockPos.getZ(d.pos()))) {
					FLOODED.addLast(new Drain(d.pos(), Long.MIN_VALUE / 2, d.pass()));
				} else {
					LATER.addLast(d);
				}
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
