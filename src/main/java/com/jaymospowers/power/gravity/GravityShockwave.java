package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Gravity Well going off (G): a bubble of gravitic pressure swells out from the point to {@link #RADIUS} blocks
 * (16 across) over {@link #GROW_TICKS}, blasting the ground's debris up as it passes, purple streaks and dark shards
 * rising inside it. As the shell passes, lumps of the ground ({@link #RINGS}, made of what's there, never torn out of
 * the world) shove up out of the earth, leaning out from the middle. They lie where they rose, and Lift can take them
 * like any chunk, for {@link #DEBRIS_LINGER} ticks, then crumble away into rubble and are gone.
 */
final class GravityShockwave {
	/** How far the bubble swells (blocks) and how fast. */
	static final double RADIUS = 8.0;
	static final int GROW_TICKS = 12;
	/** The rings of risen debris: how far out (blocks) and how many lumps on each. */
	static final double[] RINGS = {3.0, 5.4, 7.6};
	static final int[] RING_LUMPS = {5, 8, 11};
	/** How long a lump takes to shove up out of the ground, from how deep (blocks); how long it lies (ticks). */
	static final int RISE_TICKS = 5;
	static final double RISE_DEPTH = 1.3;
	static final int DEBRIS_LINGER = 160;
	/** How far above and below the well's point the ground is looked for (blocks). */
	static final int GROUND_UP = 3, GROUND_DOWN = 6;
	/** How far each lump leans out (degrees). */
	static final float TILT_MIN = 20.0F, TILT_MAX = 60.0F;

	private static final DustParticleOptions BUBBLE = new DustParticleOptions(0xA070FF, 1.6F);
	private static final DustParticleOptions STREAK = new DustParticleOptions(0xC9A6FF, 1.1F);
	private static final DustParticleOptions SHARD = new DustParticleOptions(0x1A0F2E, 1.3F);
	/** The lumps' shapes (block offsets): a pair side by side, a pair stacked, an L, a standing plate. */
	private static final int[][][] SHAPES = {
		{{0, 0, 0}, {1, 0, 0}},
		{{0, 0, 0}, {0, 1, 0}},
		{{0, 0, 0}, {1, 0, 0}, {0, 0, 1}},
		{{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}},
		{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}},
	};

	private record Spot(double angle, double radius, int at) {
	}

	private static final class Wave {
		final ResourceKey<Level> dimension;
		final Vec3 at;
		final long start;
		final List<Spot> spots = new ArrayList<>();

		Wave(ResourceKey<Level> dimension, Vec3 at, long start) {
			this.dimension = dimension;
			this.at = at;
			this.start = start;
		}
	}

	/** A lump on its way up out of the ground: risen to {@code toY} by {@code end}. */
	private record Rising(ResourceKey<Level> dimension, int id, double toY, long end) {
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Rising> RISING = new ArrayList<>();

	private GravityShockwave() {
	}

	/** The well opens at {@code at}: the bubble starts to swell. */
	static void start(ServerLevel level, Vec3 at) {
		Wave wave = new Wave(level.dimension(), at, level.getGameTime());
		RandomSource random = level.getRandom();
		for (int ring = 0; ring < RINGS.length; ring++) {
			double offset = random.nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < RING_LUMPS[ring]; i++) {
				double angle = offset + (i + random.nextDouble() * 0.6) / RING_LUMPS[ring] * Math.PI * 2.0;
				double radius = RINGS[ring] + (random.nextDouble() - 0.5) * 1.2;
				wave.spots.add(new Spot(angle, radius, (int)Math.round(radius / RADIUS * GROW_TICKS)));
			}
		}
		WAVES.add(wave);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
		GravityPower.shake(level, at, RADIUS * 2.5, 0.8F);
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			ServerLevel level = server.getLevel(wave.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - wave.start;
			if (t > GROW_TICKS + 4) {
				it.remove();
				continue;
			}
			swell(level, wave, t);
		}
		for (Iterator<Rising> it = RISING.iterator(); it.hasNext(); ) {
			Rising rising = it.next();
			ServerLevel level = server.getLevel(rising.dimension());
			if (level == null || !(level.getEntity(rising.id()) instanceof BlockCluster lump)) {
				it.remove();
				continue;
			}
			long left = rising.end() - level.getGameTime();
			if (left <= 0) {
				// up: it lies where it rose, its own weight back on
				lump.setPos(lump.getX(), rising.toY(), lump.getZ());
				lump.script(false, false);
				lump.setDeltaMovement(0.0, -0.05, 0.0);
				it.remove();
				continue;
			}
			double y = Mth.lerp(1.0 / (left + 1), lump.getY(), rising.toY());
			Vec3 was = lump.position();
			lump.setPos(lump.getX(), y, lump.getZ());
			lump.setDeltaMovement(lump.position().subtract(was));
		}
	}

	/** One tick of the swell: the shell at its new size, the debris it blasts up, and the lumps it passes. */
	private static void swell(ServerLevel level, Wave wave, long t) {
		RandomSource random = level.getRandom();
		Vec3 at = wave.at;
		float p = Mth.clamp(t / (float)GROW_TICKS, 0.0F, 1.0F);
		// fast at first, slowing as it nears its size
		double r = RADIUS * (1.0 - (1.0 - p) * (1.0 - p));
		if (t <= GROW_TICKS) {
			int points = 24 + (int)(r * 8);
			for (int i = 0; i < points; i++) {
				// over the upper half, and a little below the ground line
				double a = random.nextDouble() * Math.PI * 2.0;
				double up = Math.asin(random.nextDouble() * 1.15 - 0.15);
				double c = Math.cos(up);
				level.sendParticles(BUBBLE, at.x + Math.cos(a) * c * r, at.y + Math.sin(up) * r, at.z + Math.sin(a) * c * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (t % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 20, r * 0.5, 0.6, r * 0.5, 0.15);
			}
			// the ground's debris blasted up where the shell meets it
			int bursts = 6 + (int)(r * 1.5);
			for (int i = 0; i < bursts; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
				BlockPos ground = ground(level, x, at.y, z);
				if (ground != null) {
					BlockState state = level.getBlockState(ground);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, ground.getY() + 1.1, z, 6, 0.3, 0.2, 0.3, 0.25);
				}
			}
		}
		// purple streaks and dark shards rising inside it
		for (int i = 0; i < 3; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double d = Math.sqrt(random.nextDouble()) * Math.max(1.0, r);
			double x = at.x + Math.cos(a) * d, z = at.z + Math.sin(a) * d;
			DustParticleOptions kind = i == 2 ? SHARD : STREAK;
			double tall = 1.2 + random.nextDouble() * 1.6;
			for (int k = 0; k < 6; k++) {
				level.sendParticles(kind, x, at.y + tall * k / 6.0, z, 1, 0.02, 0.0, 0.02, 0.0);
			}
		}
		// the lumps the shell has reached shove up out of the ground
		for (Iterator<Spot> it = wave.spots.iterator(); it.hasNext(); ) {
			Spot spot = it.next();
			if (spot.at() > t) {
				continue;
			}
			it.remove();
			raise(level, at, spot);
		}
	}

	/** A lump of the ground at {@code spot}, leaning out from the middle, starting its rise. */
	private static void raise(ServerLevel level, Vec3 center, Spot spot) {
		double x = center.x + Math.cos(spot.angle()) * spot.radius(), z = center.z + Math.sin(spot.angle()) * spot.radius();
		BlockPos ground = ground(level, x, center.y, z);
		if (ground == null) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockState top = level.getBlockState(ground);
		BlockState under = level.getBlockState(ground.below());
		if (!lumpable(under)) {
			under = top;
		}
		int[][] shape = SHAPES[random.nextInt(SHAPES.length)];
		List<BlockCluster.Piece> pieces = new ArrayList<>();
		for (int[] spotOff : shape) {
			// mostly the ground's own top, some of what lies under it
			BlockState state = random.nextFloat() < 0.6F ? top : under;
			pieces.add(new BlockCluster.Piece(Block.getId(state), spotOff[0], spotOff[1], spotOff[2]));
		}
		// (its box sits on the ground; it's drawn 0.7 up, so a leaning lump is half sunk in the earth)
		double toY = ground.getY() + 1.0;
		BlockCluster lump = new BlockCluster(BlockCluster.TYPE, level);
		lump.setPos(x, toY - RISE_DEPTH, z);
		lump.setAttached(BlockCluster.PIECES, pieces);
		// leaning out from the middle (the yaw that turns its lean outward), each its own way
		float yaw = (float)Math.toDegrees(-spot.angle()) + 90.0F + (random.nextFloat() - 0.5F) * 30.0F;
		float tilt = (random.nextBoolean() ? 1.0F : -1.0F) * Mth.lerp(random.nextFloat(), TILT_MIN, TILT_MAX);
		lump.setAttached(BlockCluster.REST, new BlockCluster.Rest(yaw, tilt));
		lump.linger(DEBRIS_LINGER, true);
		lump.script(true, false);
		level.addFreshEntity(lump);
		RISING.add(new Rising(level.dimension(), lump.getId(), toY, level.getGameTime() + RISE_TICKS));
		com.jaymospowers.core.Debris.puff(level, ground, top, 1.4F);
		if (random.nextInt(3) == 0) {
			level.playSound(null, x, toY, z, top.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.6F);
		}
	}

	/** A block a lump can look like: a plain full block. */
	private static boolean lumpable(BlockState state) {
		return !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	/** The top of the ground at ({@code x}, {@code z}) near height {@code y}: a full block with room over it, or null. */
	static @Nullable BlockPos ground(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
		for (int dy = GROUND_UP; dy >= -GROUND_DOWN; dy--) {
			pos.set(bx, by + dy, bz);
			BlockState state = level.getBlockState(pos);
			if (!state.isCollisionShapeFullBlock(level, pos) || !lumpable(state)) {
				continue;
			}
			BlockPos above = pos.above();
			if (!level.getBlockState(above).isCollisionShapeFullBlock(level, above)) {
				return pos.immutable();
			}
		}
		return null;
	}

	static void clear() {
		WAVES.clear();
		RISING.clear();
	}
}
