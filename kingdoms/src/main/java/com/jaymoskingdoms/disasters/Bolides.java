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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Falling stones out of the sky the disasters bring down themselves: the Last Bell's ringing over open land, and the
 * world's ending, whose stones punch through to the bedrock ({@link Doomsday}). Each is seen coming (a blazing trail
 * across the sky, heard far off), lands with a blast, a short quake and fires round it, and leaves a crater lined with
 * scorched stone (the world's ending's, a shaft below it down to the bedrock with lava at its foot).
 * <p>
 * (nd-meteor, nd-chains) Jaymo's Powers' great falling stars land here too ({@link #struck}, by reflection): what they
 * set off in the land (quakes, wildfires) and the dim cold spell after one ({@link #veil}).
 */
public final class Bolides {
	/** It comes down from this high and this far off its mark (blocks), over this long (ticks); seen this far off. */
	static final double HEIGHT = 150.0, SLANT = 70.0;
	static final int FALL_TICKS = 90;
	static final double SEEN_FROM = 500.0;
	/** Its crater: radius (base plus per magnitude), depth (a share of the radius). */
	static final int CRATER = 2, CRATER_PER_MAGNITUDE = 2;
	static final float DEPTH = 0.6F;
	/** A bore's shaft radius, and how far over the world's floor it stops (the bedrock). */
	static final int SHAFT = 2, FLOOR_ABOVE = 5;
	/** The blast on landing (hurts and flings; breaks nothing itself: the crater is carved), plus per magnitude. */
	static final float BLAST = 2.0F, BLAST_PER_MAGNITUDE = 0.8F;
	/** Fires lit round the rim (plus per magnitude). */
	static final int FIRES = 6, FIRES_PER_MAGNITUDE = 4;
	/** (nd-meteor) A star this strong leaves the sky dim and the air cold for this long after (ticks: a day). */
	static final int VEIL_FROM = Magnitude.GREAT, VEIL_TICKS = 24000;
	/** At most this many in the air or being dug at once. */
	static final int MOST = 6;

	/** When the dust of the last great star clears (game time). */
	static final AttachmentType<Long> VEIL_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("star_dust_until"), b -> b.persistent(Codec.LONG));

	private static final class Bolide {
		final Vec3 from, to;
		final int magnitude;
		final boolean bore;
		int age;
		boolean landed;
		final ArrayDeque<BlockPos> dig = new ArrayDeque<>();
		final java.util.Set<BlockPos> lining = new java.util.HashSet<>(), lava = new java.util.HashSet<>();

		Bolide(Vec3 from, Vec3 to, int magnitude, boolean bore) {
			this.from = from;
			this.to = to;
			this.magnitude = magnitude;
			this.bore = bore;
		}
	}

	private static final List<Bolide> FALLING = new ArrayList<>();

	private Bolides() {
	}

	static void clear() {
		FALLING.clear();
	}

	/** A stone of {@code m} comes down at {@code ground}'s column ({@code bore}: on through to the bedrock). */
	static boolean fall(ServerLevel level, Vec3 ground, int m, boolean bore) {
		if (FALLING.size() >= MOST || !Loaded.made(level, Mth.floor(ground.x), Mth.floor(ground.z))) {
			return false;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(ground.x), Mth.floor(ground.z));
		Vec3 to = new Vec3(Mth.floor(ground.x) + 0.5, y, Mth.floor(ground.z) + 0.5);
		double a = level.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 from = to.add(Math.cos(a) * SLANT, HEIGHT, Math.sin(a) * SLANT);
		from = new Vec3(from.x, Math.min(from.y, level.getMaxY() - 2), from.z);
		FALLING.add(new Bolide(from, to, Magnitude.clamp(m), bore));
		level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 12.0F, 0.4F);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (FALLING.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		boolean damage = level.getGameRules().get(Disasters.DISASTER_DAMAGE);
		FALLING.removeIf(b -> !step(level, b, damage));
	}

	/** One tick of a stone: falling, then dug in; false when it's done. */
	private static boolean step(ServerLevel level, Bolide b, boolean damage) {
		if (!b.landed) {
			b.age++;
			float p = Mth.clamp(b.age / (float)FALL_TICKS, 0.0F, 1.0F);
			Vec3 at = b.from.lerp(b.to, p * p);
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) < SEEN_FROM * SEEN_FROM) {
					level.sendParticles(player, ParticleTypes.FLAME, true, true, at.x, at.y, at.z, 10 + b.magnitude * 2, 0.8, 0.8, 0.8, 0.05);
					level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 6, 0.8, 0.8, 0.8, 0.02);
					level.sendParticles(player, ParticleTypes.LAVA, true, true, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
				}
			}
			if (b.age % 10 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 5.0F, 0.4F + p * 0.4F);
			}
			if (b.age >= FALL_TICKS) {
				b.landed = true;
				land(level, b, damage);
			}
			return true;
		}
		if (!damage) {
			return false;
		}
		int share = BlockBudget.share();
		for (int i = 0; i < share && !b.dig.isEmpty(); i++) {
			BlockPos p = b.dig.removeFirst();
			if (!Loaded.made(level, p)) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.BEDROCK) || state.hasBlockEntity() || state.is(BlockTags.FEATURES_CANNOT_REPLACE) && !state.isAir()) {
				continue;
			}
			BlockState laid = b.lava.contains(p) ? Blocks.LAVA.defaultBlockState()
				: b.lining.contains(p) ? lining(level.getRandom(), b.bore && p.getY() < b.to.y - 4) : Blocks.AIR.defaultBlockState();
			if (state == laid || state.isAir() && laid.isAir()) {
				continue;
			}
			if (!BlockBudget.set(level, p, laid)) {
				b.dig.addFirst(p);
				break;
			}
		}
		return !b.dig.isEmpty();
	}

	private static BlockState lining(RandomSource random, boolean deep) {
		int r = random.nextInt(10);
		if (deep) {
			return r < 2 ? Blocks.MAGMA_BLOCK.defaultBlockState() : r < 4 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
		}
		return r < 3 ? Blocks.BLACKSTONE.defaultBlockState() : r < 5 ? Blocks.COARSE_DIRT.defaultBlockState() : r < 6 ? Blocks.MAGMA_BLOCK.defaultBlockState()
			: r < 7 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BASALT.defaultBlockState();
	}

	/** It lands: the blast, a quake, fires; the crater (and shaft) laid out to dig. */
	private static void land(ServerLevel level, Bolide b, boolean damage) {
		Vec3 to = b.to;
		RandomSource random = level.getRandom();
		level.explode(null, to.x, to.y, to.z, BLAST + BLAST_PER_MAGNITUDE * b.magnitude, Level.ExplosionInteraction.NONE);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 10.0F, 0.5F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 10.0F, 0.7F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y + 1.0, to.z, 2, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(ParticleTypes.LAVA, to.x, to.y + 1.0, to.z, 30, 2.0, 0.5, 2.0, 0.0);
		int radius = CRATER + CRATER_PER_MAGNITUDE * b.magnitude;
		struck(level, to.x, to.y, to.z, b.magnitude, radius, false);
		if (!damage) {
			return;
		}
		int depth = Math.max(2, Math.round(radius * DEPTH));
		int cx = Mth.floor(to.x), cz = Mth.floor(to.z), top = Mth.floor(to.y);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > radius + 0.3) {
					continue;
				}
				int down = (int)Math.round(depth * (1.0 - (r / (radius + 1.0)) * (r / (radius + 1.0))));
				int x = cx + dx, z = cz + dz;
				int ground = Loaded.made(level, x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : top;
				for (int y = ground; y >= top - down; y--) {
					b.dig.add(new BlockPos(x, y, z));
				}
				BlockPos floor = new BlockPos(x, top - down - 1, z);
				b.dig.add(floor);
				b.lining.add(floor);
			}
		}
		if (b.bore) {
			// on down through the world to the bedrock
			int bottom = level.getMinY() + FLOOR_ABOVE;
			for (int y = top - depth - 1; y >= bottom; y--) {
				for (int dx = -SHAFT - 1; dx <= SHAFT + 1; dx++) {
					for (int dz = -SHAFT - 1; dz <= SHAFT + 1; dz++) {
						double r = Math.sqrt(dx * dx + dz * dz);
						if (r > SHAFT + 1.3) {
							continue;
						}
						BlockPos p = new BlockPos(cx + dx, y, cz + dz);
						b.dig.add(p);
						if (r > SHAFT + 0.3 || y == bottom) {
							b.lining.add(p);
						}
					}
				}
			}
			// a pool of lava at its foot
			for (int dx = -SHAFT; dx <= SHAFT; dx++) {
				for (int dz = -SHAFT; dz <= SHAFT; dz++) {
					BlockPos p = new BlockPos(cx + dx, bottom + 1, cz + dz);
					b.lining.remove(p);
					b.lava.add(p);
				}
			}
		}
		// fires round the rim
		int fires = FIRES + FIRES_PER_MAGNITUDE * b.magnitude;
		for (int i = 0; i < fires; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, d = radius + 1.0 + random.nextDouble() * radius;
			int x = Mth.floor(to.x + Math.cos(a) * d), z = Mth.floor(to.z + Math.sin(a) * d);
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			BlockState fire = BaseFireBlock.getState(level, at);
			if (level.getBlockState(at).isAir() && fire.canSurvive(level, at)) {
				BlockBudget.set(level, at, fire);
			}
		}
	}

	/**
	 * (nd-chains, nd-meteor) A falling stone of {@code m} has landed at (x, y, z), its crater {@code radius} across: the ground
	 * shakes, a wildfire may catch round it, a great one leaves the sky dim and the air cold for a day, and the settlements
	 * near are struck. Jaymo's Powers' great falling stars call this by reflection ({@code powers} is {@code true} then).
	 */
	public static void struck(ServerLevel level, double x, double y, double z, int m, int radius, boolean powers) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		m = Magnitude.clamp(m);
		RandomSource random = level.getRandom();
		Earthquakes.start(level, new Vec3(x, y, z), Math.max(1, m - 2), random, true);
		if (m >= Magnitude.SEVERE) {
			Calamities.wildfireAt(level, BlockPos.containing(x + radius + 2, y, z), m - 1, random);
		}
		if (m >= VEIL_FROM) {
			level.setAttached(VEIL_UNTIL, level.getGameTime() + VEIL_TICKS);
		}
		com.jaymoskingdoms.DisasterRelief.struck(level, x, z, radius * 4.0, "meteor", m);
	}

	/** Reflection's way in for Jaymo's Powers (its great falling stars). */
	public static void powersStar(ServerLevel level, double x, double y, double z, int m, int radius) {
		struck(level, x, y, z, m, radius, true);
	}

	/** How much of a great star's dust still hangs in the sky, 0..1 (thinning over its last quarter). */
	public static float veil(ServerLevel level) {
		Long until = level.getAttached(VEIL_UNTIL);
		if (until == null) {
			return 0.0F;
		}
		long left = until - level.getGameTime();
		if (left <= 0L) {
			return 0.0F;
		}
		return Mth.clamp(left / (VEIL_TICKS * 0.25F), 0.0F, 1.0F) * Mth.clamp((VEIL_TICKS - left) / 400.0F, 0.0F, 1.0F);
	}

	static boolean falling() {
		return !FALLING.isEmpty();
	}
}
