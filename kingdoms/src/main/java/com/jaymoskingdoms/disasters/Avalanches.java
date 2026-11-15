package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Loaded;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Avalanches and landslides (medieval picks, nd-slides). Snow, or loose earth, breaks from a slope above someone and comes
 * down it, gathering speed: the top of the slope is stripped bare where it let go, and below, everything in its path is
 * swept along and buried under what it carried (snow, or dirt and gravel). Blizzards and quakes set them off
 * ({@link Calamities}, {@link Earthquakes}); mountain roads and villages under the slopes are where it hurts.
 * <p>
 * Kept cheap: one front a few blocks wide worked a row at a time, every block through the shared {@link BlockBudget}.
 */
public final class Avalanches {
	/** It breaks from a slope this much higher than whoever it's for, looked for this far round them, this many tries. */
	static final int ABOVE = 10, LOOK = 48, TRIES = 28;
	/** Its width and length by magnitude (blocks: base plus per magnitude). */
	static final int WIDTH = 5, WIDTH_PER_MAGNITUDE = 2, LENGTH = 26, LENGTH_PER_MAGNITUDE = 12;
	/** The share of its length where it lets go (the rest is where it comes to rest); how deep it strips, how deep it buries
	 * (plus one per two magnitudes). */
	static final float RELEASE = 0.35F;
	static final int STRIP = 2, BURY = 1;
	/** How fast it starts and how fast it gets (blocks a tick, times its {@link Magnitude#speed}). */
	static final double START_SPEED = 0.25, TOP_SPEED = 0.9, GATHER = 0.01;
	/** How hard it sweeps what's in its way, how much it hurts (once a second). */
	static final double SWEEP = 0.25;
	static final float HURT = 2.0F;
	/** Falling stones and snow tumbling at its front for looks (at most a tick). */
	static final int TUMBLE = 2;
	/** At most this many at once. */
	static final int MOST = 3;

	private static final class Slide {
		final Vec3 top, dir, perp;
		final int width, length, magnitude;
		final boolean snow;
		double front, speed;
		int row = -1, carried;
		long nextHurt;

		Slide(Vec3 top, Vec3 dir, int magnitude, boolean snow) {
			this.top = top;
			this.dir = dir;
			this.perp = new Vec3(-dir.z, 0.0, dir.x);
			this.magnitude = magnitude;
			this.width = WIDTH + WIDTH_PER_MAGNITUDE * magnitude;
			this.length = LENGTH + LENGTH_PER_MAGNITUDE * magnitude;
			this.snow = snow;
			this.speed = START_SPEED * Magnitude.speed(magnitude);
		}

		Vec3 at(double along, double across) {
			return top.add(dir.scale(along)).add(perp.scale(across));
		}
	}

	private static final List<Slide> SLIDES = new ArrayList<>();
	private static int ticks;

	private Avalanches() {
	}

	static void clear() {
		SLIDES.clear();
	}

	/** Whether {@code pos} is in snowy mountains (where avalanches come). */
	static boolean snowyHills(ServerLevel level, BlockPos pos) {
		return Earthquakes.mountainous(level, pos) && level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	private static boolean snowy(BlockState s) {
		return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW);
	}

	private static boolean loose(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SAND) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.CLAY);
	}

	/** A slope above {@code p} lets go, as strong as {@code m} (0: by chance). Whether there was one to. */
	static boolean near(ServerLevel level, ServerPlayer p, int m, RandomSource random) {
		return near(level, p.blockPosition(), m, random);
	}

	/** A slope above {@code p} (a spot, as the Last Bell picks one) lets go. */
	static boolean near(ServerLevel level, BlockPos p, int m, RandomSource random) {
		if (SLIDES.size() >= MOST || !level.getGameRules().get(Disasters.DISASTER_DAMAGE)) {
			return false;
		}
		BlockPos best = null;
		for (int i = 0; i < TRIES; i++) {
			int x = p.getX() + random.nextInt(LOOK * 2 + 1) - LOOK, z = p.getZ() + random.nextInt(LOOK * 2 + 1) - LOOK;
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y < p.getY() + ABOVE || best != null && y <= best.getY()) {
				continue;
			}
			BlockState surface = level.getBlockState(new BlockPos(x, y - 1, z));
			BlockState onTop = level.getBlockState(new BlockPos(x, y, z));
			if (snowy(surface) || snowy(onTop) || loose(surface)) {
				best = new BlockPos(x, y, z);
			}
		}
		if (best == null) {
			return false;
		}
		Vec3 dir = new Vec3(p.getX() + 0.5 - best.getX(), 0.0, p.getZ() + 0.5 - best.getZ());
		if (dir.lengthSqr() < 4.0) {
			return false;
		}
		if (m <= 0) {
			m = Magnitude.roll(level, random, Magnitude.MINOR);
		}
		m = Magnitude.clamp(m);
		BlockState surface = level.getBlockState(best.below());
		boolean snow = snowy(surface) || snowy(level.getBlockState(best));
		Slide s = new Slide(Vec3.atBottomCenterOf(best), dir.normalize(), m, snow);
		SLIDES.add(s);
		level.playSound(null, best, snow ? SoundEvents.POWDER_SNOW_BREAK : SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 6.0F, 0.4F);
		level.playSound(null, best, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.3F);
		Vec3 mid = s.at(s.length / 2.0, 0.0);
		com.jaymoskingdoms.DisasterRelief.struck(level, mid.x, mid.z, s.length, snow ? "avalanche" : "landslide", m);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (SLIDES.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		ticks++;
		RandomSource random = level.getRandom();
		SLIDES.removeIf(s -> !run(level, s, random));
	}

	/** One tick of a slide; false when it has come to rest. */
	private static boolean run(ServerLevel level, Slide s, RandomSource random) {
		s.speed = Math.min(TOP_SPEED * Magnitude.speed(s.magnitude), s.speed + GATHER);
		int target = Mth.floor(s.front + s.speed);
		while (s.row < target && !BlockBudget.empty(BlockBudget.Of.SLIDE)) {
			s.row++;
			row(level, s, s.row, random);
		}
		s.front = Math.min(s.front + s.speed, s.row + 0.999);
		sweep(level, s);
		if (ticks % 10 == 0) {
			Vec3 at = s.at(s.front, 0.0);
			level.playSound(null, at.x, at.y, at.z, s.snow ? SoundEvents.POWDER_SNOW_STEP : SoundEvents.GRAVEL_STEP, SoundSource.BLOCKS, 4.0F, 0.3F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 2.0F, 0.3F);
		}
		return s.row < s.length;
	}

	/** The front reaches row {@code r}: high up it strips the slope; lower down it buries what's there. */
	private static void row(ServerLevel level, Slide s, int r, RandomSource random) {
		boolean release = r < s.length * RELEASE;
		int bury = BURY + s.magnitude / 2;
		for (int i = 0; i < s.width; i++) {
			Vec3 at = s.at(r, i - s.width / 2.0);
			int x = Mth.floor(at.x), z = Mth.floor(at.z);
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (release) {
				for (int d = 0; d < STRIP + 1; d++) {
					BlockPos p = new BlockPos(x, ground - d, z);
					BlockState state = level.getBlockState(p);
					if (state.isAir() || state.is(BlockTags.REPLACEABLE) && !snowy(state)) {
						continue;
					}
					if (!(s.snow ? snowy(state) : loose(state)) || !BlockBudget.set(BlockBudget.Of.SLIDE, level, p, Blocks.AIR.defaultBlockState())) {
						break;
					}
					s.carried++;
				}
			} else if (s.carried > 0) {
				// it comes to rest here, burying what's under it
				int left = Math.max(1, Math.round(bury * (1.0F - (r - s.length * RELEASE) / (s.length * (1.0F - RELEASE)) * 0.6F)));
				for (int d = 0; d < left && s.carried > 0; d++) {
					BlockPos p = new BlockPos(x, ground + d, z);
					BlockState state = level.getBlockState(p);
					if (!state.isAir() && !state.is(BlockTags.REPLACEABLE) && !state.is(BlockTags.FLOWERS) && !state.is(BlockTags.CROPS)) {
						break;
					}
					if (!state.getFluidState().isEmpty()) {
						break;
					}
					BlockState laid = s.snow ? Blocks.SNOW_BLOCK.defaultBlockState()
						: random.nextInt(3) == 0 ? Blocks.GRAVEL.defaultBlockState() : random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
					if (!state.isAir() && !BlockBudget.destroy(BlockBudget.Of.SLIDE, level, p, true) || !BlockBudget.set(BlockBudget.Of.SLIDE, level, p, laid)) {
						return;
					}
					s.carried--;
				}
			}
			if (random.nextInt(3) == 0) {
				BlockState look = s.snow ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, look), x + 0.5, ground + 0.5, z + 0.5, 6, 0.6, 0.4, 0.6, 0.1);
				if (s.snow) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, x + 0.5, ground + 1.0, z + 0.5, 4, 0.8, 0.6, 0.8, 0.05);
				}
			}
		}
		// a few stones and clods tumbling at its front
		for (int t = 0; t < TUMBLE && s.carried > 0; t++) {
			Vec3 at = s.at(r, (random.nextDouble() - 0.5) * s.width);
			BlockPos p = BlockPos.containing(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z)) + 1, at.z);
			if (Loaded.made(level, p) && level.getBlockState(p).isAir() && BlockBudget.spend(BlockBudget.Of.SLIDE)) {
				BlockState look = s.snow ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
				level.setBlock(p, look, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				FallingBlockEntity tumble = FallingBlockEntity.fall(level, p, look);
				tumble.setDeltaMovement(s.dir.x * s.speed * 1.2, 0.15, s.dir.z * s.speed * 1.2);
				tumble.setHurtsEntities(1.0F, 8);
				s.carried--;
			}
		}
	}

	/** What's in its front is swept along and hurt. */
	private static void sweep(ServerLevel level, Slide s) {
		Vec3 mid = s.at(s.front, 0.0);
		boolean hurt = ticks >= s.nextHurt;
		if (hurt) {
			s.nextHurt = ticks + 20;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mid, mid).inflate(s.width / 2.0 + 2.0, 12.0, s.width / 2.0 + 2.0),
			LivingEntity::isAlive)) {
			Vec3 rel = e.position().subtract(s.top);
			double along = rel.dot(s.dir), across = rel.dot(s.perp);
			if (Math.abs(across) > s.width / 2.0 + 1.0 || Math.abs(along - s.front) > 3.0) {
				continue;
			}
			e.push(s.dir.x * SWEEP * s.speed * 3.0, 0.05, s.dir.z * SWEEP * s.speed * 3.0);
			e.syncVelocity = true;
			if (hurt) {
				e.hurtServer(level, level.damageSources().generic(), HURT * Magnitude.harm(s.magnitude));
			}
		}
	}
}
