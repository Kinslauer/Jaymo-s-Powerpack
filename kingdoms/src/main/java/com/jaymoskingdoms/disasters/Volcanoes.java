package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Volcanoes that wake (medieval picks, nd-volcano). There is no volcano in the world's making: a tall peak in the hot
 * lands (desert, savanna, badlands, any land too hot for rain) or in Nether-touched ground (netherrack and magma by a
 * ruined portal) is the mountain that wakes. Its course:
 * <ol>
 * <li><b>Swelling</b>, for days by its strength: smoke climbs from its summit, magma breaks through the rock up there,
 * and small quakes run through the ground round it (no word: the ground speaks for itself). Before it goes, the signs
 * ({@link Signs}).</li>
 * <li><b>Eruption</b>: thrown rocks (real falling stones of magma, blackstone and basalt, landing where they land), lava
 * welling over the summit and running down, ash falling over the land round it in layers ({@link Calamity#ASH}: the
 * clients' grey fog and falling flakes), and a burning cloud racing down its slopes that sets everything in its path
 * alight. The ground shakes with it ({@link Earthquakes}).</li>
 * <li><b>The caldera</b>: spent, its summit falls in, a pit with a lava lake in it.</li>
 * <li><b>Cooling</b>: the running lava sets to basalt and the standing lava to obsidian.</li>
 * </ol>
 * Kept cheap: everything waits for the summit's land to be loaded with someone near it, every block it changes goes
 * through the shared {@link BlockBudget}, and the volcano itself is a handful of numbers saved with the world.
 */
public final class Volcanoes {
	/** A peak counts at this height over the sea (blocks); looked for this far round someone (blocks), this many tries. */
	static final int PEAK_HEIGHT = 40, PEAK_LOOK = 160, PEAK_TRIES = 48;
	/** A hot land: this warm at least (the biome's base temperature), or rainless. */
	static final float HOT = 1.0F;
	/** How long it swells, per magnitude (ticks: half a day each), and the quick swell of one called down (ticks). */
	static final int SWELL_PER_MAGNITUDE = 12000, QUICK_SWELL = 1200;
	/** While it swells: a tremor this often (ticks, with these odds), and magma broken through near the summit (at most). */
	static final int TREMOR_EVERY = 1800, SWELL_MAGMA = 24;
	static final float TREMOR_CHANCE = 0.6F;
	/** The eruption: how long (ticks, times its {@link Magnitude#length}), lava welled up at the summit (a source this
	 * often, at most this many a magnitude), rocks thrown (the odds a tick a magnitude, at most this many a magnitude). */
	static final int ERUPT_TICKS = 1600, LAVA_EVERY = 12, LAVA_PER_MAGNITUDE = 10, ROCKS_PER_MAGNITUDE = 24;
	static final float ROCK_CHANCE = 0.06F;
	/** A thrown rock's speed out and up, and how much it hurts where it lands (per block fallen, at most). */
	static final double ROCK_OUT_MIN = 0.4, ROCK_OUT_MAX = 1.1, ROCK_UP_MIN = 0.9, ROCK_UP_MAX = 1.6;
	static final float ROCK_HURT = 2.0F;
	static final int ROCK_HURT_MOST = 20;
	/** The ash cloud's radius (times its {@link Magnitude#size}), and how often ash settles round someone under it. */
	static final float ASH_RADIUS = 110.0F, ASH_PILE = 0.35F;
	static final int ASH_RANGE = 24, ASH_DEEPEST = 4;
	/** The burning cloud: how many tongues of it run down the slopes (plus one a magnitude), how far (steps, plus per
	 * magnitude), one step this often (ticks), how wide it burns (blocks), how hard. */
	static final int FLOW_TONGUES = 2, FLOW_STEPS = 30, FLOW_STEPS_PER_MAGNITUDE = 14, FLOW_EVERY = 2;
	static final double FLOW_WIDTH = 3.5;
	static final float FLOW_DAMAGE = 3.0F;
	static final int FLOW_FIRE_TICKS = 160;
	/** The caldera: its radius and depth (plus a magnitude each). */
	static final int CALDERA_RADIUS = 4, CALDERA_DEPTH = 3;
	/** Cooling: how long (ticks), columns looked at a tick, how far round the summit (plus per magnitude). */
	static final int COOL_TICKS = 1600, COOL_COLUMNS = 250, COOL_RADIUS = 40, COOL_RADIUS_PER_MAGNITUDE = 15;
	/** Players this near the summit let it go on (and see its smoke); no two wake this near each other; at most this many. */
	static final double NEAR = 400.0, APART = 300.0;
	static final int MOST = 2;

	static final int SWELL = 0, ERUPT = 1, CALDERA = 2, COOL = 3;

	/** A volcano as saved: where its summit is, its strength, what it's doing and since when, and how long it swells. */
	record Saved(BlockPos summit, int magnitude, int phase, long since, int swell) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("summit").forGetter(Saved::summit),
			Codec.INT.fieldOf("magnitude").forGetter(Saved::magnitude),
			Codec.INT.fieldOf("phase").forGetter(Saved::phase),
			Codec.LONG.fieldOf("since").forGetter(Saved::since),
			Codec.INT.fieldOf("swell").forGetter(Saved::swell)).apply(i, Saved::new));
	}

	static final AttachmentType<List<Saved>> SAVED = AttachmentRegistry.create(JaymosKingdoms.id("volcanoes"), b -> b.persistent(Saved.CODEC.listOf()));

	/** One awake. Only its {@link Saved} part is kept over a restart; the rest starts again. */
	private static final class Volcano {
		final BlockPos summit;
		final int magnitude;
		int phase;
		long since;
		final int swell;
		int magma, lava, rocks, signed;
		@Nullable Calamity ash;
		final List<Flow> flows = new ArrayList<>();
		final ArrayDeque<BlockPos> caldera = new ArrayDeque<>();
		final java.util.Set<BlockPos> lake = new java.util.HashSet<>();
		boolean calderaLaid;

		Volcano(Saved s) {
			this.summit = s.summit();
			this.magnitude = Magnitude.clamp(s.magnitude());
			this.phase = s.phase();
			this.since = s.since();
			this.swell = s.swell();
		}

		Saved saved() {
			return new Saved(summit, magnitude, phase, since, swell);
		}
	}

	/** A tongue of the burning cloud running downhill. */
	private static final class Flow {
		double x, z, heading;
		int steps, flat;
		final int most;

		Flow(double x, double z, double heading, int most) {
			this.x = x;
			this.z = z;
			this.heading = heading;
			this.most = most;
		}
	}

	private static final List<Volcano> AWAKE = new ArrayList<>();
	private static boolean loaded;

	private Volcanoes() {
	}

	/** The server stopped: forget what's awake (it's saved). */
	static void clear() {
		AWAKE.clear();
		loaded = false;
	}

	/** Everything stops at once (an op cleared the skies): the eruptions end where they stand. */
	static void calm(ServerLevel level) {
		AWAKE.clear();
		save(level);
	}

	private static void save(ServerLevel level) {
		level.setAttached(SAVED, AWAKE.stream().map(Volcano::saved).toList());
	}

	// ---- finding a peak ---------------------------------------------------------------------------------------------

	/** Whether the land at {@code pos} is hot (or touched by the Nether). */
	static boolean hot(ServerLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		if (biome.getBaseTemperature() >= HOT || !biome.hasPrecipitation()) {
			return true;
		}
		BlockState top = level.getBlockState(pos.below());
		return top.is(BlockTags.INFINIBURN_OVERWORLD) || top.is(Blocks.BLACKSTONE) || top.is(Blocks.BASALT);
	}

	/** A tall peak in hot or Nether-touched land near {@code near} (its summit's top), or null. */
	static @Nullable BlockPos peakNear(ServerLevel level, BlockPos near) {
		return peakNear(level, near, true);
	}

	/** A tall peak near {@code near}, hot land or not when {@code hotOnly} is false (the world's ending opens any). */
	private static @Nullable BlockPos peakNear(ServerLevel level, BlockPos near, boolean hotOnly) {
		RandomSource random = level.getRandom();
		BlockPos best = null;
		int floor = level.getSeaLevel() + PEAK_HEIGHT;
		for (int i = 0; i < PEAK_TRIES; i++) {
			int x = near.getX() + random.nextInt(PEAK_LOOK * 2 + 1) - PEAK_LOOK, z = near.getZ() + random.nextInt(PEAK_LOOK * 2 + 1) - PEAK_LOOK;
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y >= floor && (best == null || y > best.getY())) {
				BlockPos at = new BlockPos(x, y, z);
				if (!hotOnly || hot(level, at)) {
					best = at;
				}
			}
		}
		return best == null ? null : climb(level, best);
	}

	/** Up the slope from {@code from} to the top of the peak. */
	private static BlockPos climb(ServerLevel level, BlockPos from) {
		BlockPos at = from;
		for (int step = 0; step < 12; step++) {
			BlockPos higher = at;
			for (int dx = -6; dx <= 6; dx += 3) {
				for (int dz = -6; dz <= 6; dz += 3) {
					int x = at.getX() + dx, z = at.getZ() + dz;
					if (!Loaded.made(level, x, z)) {
						continue;
					}
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					if (y > higher.getY()) {
						higher = new BlockPos(x, y, z);
					}
				}
			}
			if (higher.equals(at)) {
				break;
			}
			at = higher;
		}
		return at;
	}

	/**
	 * A peak near {@code near} wakes, as strong as {@code m} (0: by chance, at least moderate); {@code quick}: it swells
	 * only a minute (one called down, the world's ending). Whether one did.
	 */
	static boolean wakeNear(ServerLevel level, BlockPos near, int m, boolean quick) {
		load(level);
		BlockPos summit = peakNear(level, near);
		return summit != null && wake(level, summit, m, quick);
	}

	/** (The world's ending) Any tall peak near {@code near} opens, quickly. Whether one did. */
	static boolean wakeAnyNear(ServerLevel level, BlockPos near, int m) {
		load(level);
		BlockPos summit = peakNear(level, near, false);
		return summit != null && wake(level, summit, m, true);
	}

	/** The peak at {@code summit} wakes. */
	static boolean wake(ServerLevel level, BlockPos summit, int m, boolean quick) {
		load(level);
		if (AWAKE.size() >= MOST) {
			return false;
		}
		for (Volcano v : AWAKE) {
			if (v.summit.closerThan(summit, APART)) {
				return false;
			}
		}
		if (m <= 0) {
			m = Magnitude.roll(level, level.getRandom(), Magnitude.MODERATE);
		}
		int swell = quick ? QUICK_SWELL : SWELL_PER_MAGNITUDE * m;
		AWAKE.add(new Volcano(new Saved(summit.immutable(), m, SWELL, level.getGameTime(), swell)));
		save(level);
		return true;
	}

	private static void load(ServerLevel level) {
		if (loaded) {
			return;
		}
		loaded = true;
		for (Saved s : level.getAttachedOrElse(SAVED, List.<Saved>of())) {
			AWAKE.add(new Volcano(s));
		}
	}

	// ---- the server -------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		load(level);
		if (AWAKE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		boolean damage = level.getGameRules().get(Disasters.DISASTER_DAMAGE);
		boolean changed = false;
		for (java.util.Iterator<Volcano> it = AWAKE.iterator(); it.hasNext(); ) {
			Volcano v = it.next();
			boolean near = someoneNear(level, v.summit) && Loaded.made(level, v.summit);
			long age = now - v.since;
			switch (v.phase) {
				case SWELL -> {
					if (near) {
						swell(level, v, random, now, damage);
					}
					if (age >= v.swell - Signs.LEAD && v.signed == 0) {
						v.signed = 1;
						Signs.omen(level, Vec3.atCenterOf(v.summit), Signs.GREAT_RADIUS, Signs.LEAD + Signs.GATHER, v.magnitude);
					}
					// it goes only with someone there to see it (else it waits, smoking)
					if (age >= v.swell && near) {
						erupt(level, v, random);
						changed = true;
					}
				}
				case ERUPT -> {
					if (near) {
						eruption(level, v, random, now, damage);
					}
					if (age >= Math.round(ERUPT_TICKS * Magnitude.length(v.magnitude))) {
						v.phase = CALDERA;
						v.since = now;
						changed = true;
					}
				}
				case CALDERA -> {
					if (near && (!damage || caldera(level, v))) {
						v.phase = COOL;
						v.since = now;
						changed = true;
					}
				}
				default -> {
					if (near && damage) {
						cool(level, v, random);
					}
					if (age >= COOL_TICKS) {
						if (v.ash != null) {
							v.ash.life = Math.min(v.ash.life, v.ash.age + Calamities.GATHER_TICKS);
						}
						it.remove();
						changed = true;
					}
				}
			}
		}
		if (changed) {
			save(level);
		}
	}

	private static boolean someoneNear(ServerLevel level, BlockPos at) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(at, NEAR)) {
				return true;
			}
		}
		return false;
	}

	/** Smoke over the summit, seen from far off (forced past the usual particle range). */
	private static void smoke(ServerLevel level, BlockPos summit, int puffs, boolean fire) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(summit, NEAR)) {
				level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, summit.getX() + 0.5, summit.getY() + 1.0, summit.getZ() + 0.5,
					puffs, 1.5, 0.5, 1.5, 0.02);
				if (fire) {
					level.sendParticles(p, ParticleTypes.LAVA, true, true, summit.getX() + 0.5, summit.getY() + 1.0, summit.getZ() + 0.5,
						puffs, 1.5, 0.5, 1.5, 0.0);
					level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, true, summit.getX() + 0.5, summit.getY() + 3.0, summit.getZ() + 0.5,
						puffs * 2, 3.0, 2.0, 3.0, 0.08);
				}
			}
		}
	}

	/** Swelling: smoke, magma breaking through, tremors. */
	private static void swell(ServerLevel level, Volcano v, RandomSource random, long now, boolean damage) {
		if (now % 10 == 0) {
			smoke(level, v.summit, 2 + v.magnitude, false);
		}
		if (damage && v.magma < SWELL_MAGMA && now % 200 == 0) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				v.summit.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4)).below();
			BlockState was = level.getBlockState(at);
			if ((was.is(BlockTags.BASE_STONE_OVERWORLD) || was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(BlockTags.TERRACOTTA))
				&& BlockBudget.set(level, at, Blocks.MAGMA_BLOCK.defaultBlockState())) {
				v.magma++;
				level.playSound(null, at, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 0.6F);
			}
		}
		if (now % TREMOR_EVERY == 0 && random.nextFloat() < TREMOR_CHANCE) {
			Earthquakes.start(level, Vec3.atCenterOf(v.summit), Math.max(1, v.magnitude - 2), random, true);
		}
	}

	/** It goes: the ground heaves, the ash cloud rises, the burning cloud starts down. */
	private static void erupt(ServerLevel level, Volcano v, RandomSource random) {
		v.phase = ERUPT;
		v.since = level.getGameTime();
		Vec3 top = Vec3.atCenterOf(v.summit);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16.0F, 0.3F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 16.0F, 0.4F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, top.x, top.y + 2.0, top.z, 3, 2.0, 1.0, 2.0, 0.0);
		Earthquakes.start(level, top, Math.max(1, v.magnitude - 1), random, true);
		float radius = ASH_RADIUS * Magnitude.size(v.magnitude);
		int life = Math.round(ERUPT_TICKS * Magnitude.length(v.magnitude)) + COOL_TICKS + Calamities.GATHER_TICKS;
		v.ash = new Calamity(Storms.nextId(), Calamity.ASH, top.x, top.z, Math.cos(Storms.windAngle) * 0.01, Math.sin(Storms.windAngle) * 0.01,
			radius, 0, life, -1).magnitude(v.magnitude);
		Calamities.SERVER.add(v.ash);
		burningCloud(v, random);
		com.jaymoskingdoms.DisasterRelief.struck(level, top.x, top.z, radius, "volcano", v.magnitude);
	}

	/** Tongues of the burning cloud start down the slopes. */
	private static void burningCloud(Volcano v, RandomSource random) {
		int tongues = FLOW_TONGUES + v.magnitude;
		for (int i = 0; i < tongues; i++) {
			v.flows.add(new Flow(v.summit.getX() + 0.5, v.summit.getZ() + 0.5, random.nextDouble() * Math.PI * 2.0,
				FLOW_STEPS + FLOW_STEPS_PER_MAGNITUDE * v.magnitude));
		}
	}

	/** A tick of the eruption. */
	private static void eruption(ServerLevel level, Volcano v, RandomSource random, long now, boolean damage) {
		if (now % 4 == 0) {
			smoke(level, v.summit, 3 + v.magnitude, true);
		}
		if (now % 50 == 0) {
			level.playSound(null, v.summit, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10.0F, 0.3F + random.nextFloat() * 0.2F);
		}
		if (damage && now % LAVA_EVERY == 0 && v.lava < LAVA_PER_MAGNITUDE * v.magnitude) {
			// lava wells over the summit and runs down
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				v.summit.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
			if (level.getBlockState(at).canBeReplaced() && BlockBudget.set(level, at, Blocks.LAVA.defaultBlockState())) {
				v.lava++;
			}
		}
		if (v.rocks < ROCKS_PER_MAGNITUDE * v.magnitude && random.nextFloat() < ROCK_CHANCE * v.magnitude) {
			throwRock(level, v, random);
		}
		// a second burning cloud halfway through a strong one
		long age = now - v.since;
		if (v.magnitude >= Magnitude.GREAT && age == Math.round(ERUPT_TICKS * Magnitude.length(v.magnitude)) / 2) {
			burningCloud(v, random);
		}
		if (now % FLOW_EVERY == 0) {
			v.flows.removeIf(f -> !flow(level, v, f, random, damage));
		}
		if (damage && v.ash != null) {
			for (ServerPlayer p : level.players()) {
				float under = v.ash.over(p.getX(), p.getZ());
				if (under > 0.3F && random.nextFloat() < ASH_PILE * under) {
					ash(level, p.blockPosition(), random, v.ash);
				}
			}
		}
	}

	/** A rock thrown out of the summit: a real falling stone that lands where it lands. */
	private static void throwRock(ServerLevel level, Volcano v, RandomSource random) {
		BlockPos at = v.summit.above(3);
		if (!level.getBlockState(at).isAir() || !BlockBudget.spend()) {
			return;
		}
		BlockState rock = switch (random.nextInt(3)) {
			case 0 -> Blocks.MAGMA_BLOCK.defaultBlockState();
			case 1 -> Blocks.BLACKSTONE.defaultBlockState();
			default -> Blocks.BASALT.defaultBlockState();
		};
		level.setBlock(at, rock, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		FallingBlockEntity stone = FallingBlockEntity.fall(level, at, rock);
		double a = random.nextDouble() * Math.PI * 2.0;
		double out = Mth.lerp(random.nextDouble(), ROCK_OUT_MIN, ROCK_OUT_MAX) * Magnitude.speed(v.magnitude);
		stone.setDeltaMovement(Math.cos(a) * out, Mth.lerp(random.nextDouble(), ROCK_UP_MIN, ROCK_UP_MAX), Math.sin(a) * out);
		stone.setHurtsEntities(ROCK_HURT, ROCK_HURT_MOST);
		stone.igniteForTicks(200);
		v.rocks++;
	}

	/** One step of a tongue of the burning cloud: downhill, burning what it passes; false when it has spent itself. */
	private static boolean flow(ServerLevel level, Volcano v, Flow f, RandomSource random, boolean damage) {
		if (++f.steps > f.most || f.flat > 6) {
			return false;
		}
		int x = Mth.floor(f.x), z = Mth.floor(f.z);
		if (!Loaded.made(level, x, z)) {
			return false;
		}
		int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// the lowest way ahead (within an eighth of a turn either side)
		double bestHeading = f.heading;
		int lowest = Integer.MAX_VALUE;
		for (int k = -2; k <= 2; k++) {
			double h = f.heading + k * Math.PI / 8.0;
			int nx = Mth.floor(f.x + Math.cos(h) * 2.0), nz = Mth.floor(f.z + Math.sin(h) * 2.0);
			if (!Loaded.made(level, nx, nz)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
			if (y < lowest) {
				lowest = y;
				bestHeading = h;
			}
		}
		if (lowest == Integer.MAX_VALUE) {
			return false;
		}
		f.flat = lowest >= here ? f.flat + 1 : 0;
		f.heading = bestHeading + (random.nextDouble() - 0.5) * 0.3;
		f.x += Math.cos(f.heading);
		f.z += Math.sin(f.heading);
		Vec3 at = new Vec3(f.x, here + 1.0, f.z);
		if (level.getFluidState(BlockPos.containing(f.x, here - 1, f.z)).is(net.minecraft.tags.FluidTags.WATER)) {
			// it boils off over water
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 1.5, 0.5, 1.5, 0.05);
			return false;
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y + 1.0, at.z, 8, 2.0, 1.5, 2.0, 0.03);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, at.x, at.y + 2.0, at.z, 4, 2.5, 1.5, 2.5, 0.02);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 6, 1.5, 0.6, 1.5, 0.02);
		float hurt = FLOW_DAMAGE * Magnitude.harm(v.magnitude);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FLOW_WIDTH, 3.0, FLOW_WIDTH), LivingEntity::isAlive)) {
			e.igniteForTicks(FLOW_FIRE_TICKS);
			e.hurtServer(level, level.damageSources().inFire(), hurt);
		}
		if (damage && random.nextInt(3) == 0) {
			BlockPos spot = BlockPos.containing(f.x + random.nextGaussian() * 2.0, here, f.z + random.nextGaussian() * 2.0);
			if (Loaded.made(level, spot)) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
				BlockState was = level.getBlockState(top);
				if ((was.isAir() || was.is(BlockTags.REPLACEABLE)) && was.getFluidState().isEmpty()) {
					BlockState fire = BaseFireBlock.getState(level, top);
					if (fire.canSurvive(level, top)) {
						BlockBudget.set(level, top, fire);
					}
				}
				// leaves and grass it passes are scorched off
				BlockState under = level.getBlockState(top.below());
				if (under.is(Blocks.GRASS_BLOCK)) {
					BlockBudget.set(level, top.below(), Blocks.COARSE_DIRT.defaultBlockState());
				}
			}
		}
		return true;
	}

	/** Ash settles somewhere near {@code near}, in layers. */
	private static void ash(ServerLevel level, BlockPos near, RandomSource random, Calamity cloud) {
		int x = near.getX() + random.nextInt(ASH_RANGE * 2 + 1) - ASH_RANGE, z = near.getZ() + random.nextInt(ASH_RANGE * 2 + 1) - ASH_RANGE;
		if (!Loaded.made(level, x, z) || cloud.over(x + 0.5, z + 0.5) < 0.3F) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		BlockState here = level.getBlockState(top);
		BlockState under = level.getBlockState(top.below());
		int deepest = Math.min(8, ASH_DEEPEST + cloud.magnitude / 2);
		if (here.is(Disasters.ASH_LAYER)) {
			int layers = here.getValue(SnowLayerBlock.LAYERS);
			if (layers < deepest) {
				BlockBudget.set(level, top, here.setValue(SnowLayerBlock.LAYERS, layers + 1));
			}
			return;
		}
		if (under.is(Disasters.ASH_LAYER)) {
			int layers = under.getValue(SnowLayerBlock.LAYERS);
			if (layers < deepest) {
				BlockBudget.set(level, top.below(), under.setValue(SnowLayerBlock.LAYERS, layers + 1));
			}
			return;
		}
		BlockState fresh = Disasters.ASH_LAYER.defaultBlockState();
		if ((here.isAir() || here.is(BlockTags.REPLACEABLE) && !here.is(Blocks.WATER)) && here.getFluidState().isEmpty() && fresh.canSurvive(level, top)) {
			BlockBudget.set(level, top, fresh);
		}
	}

	/** The summit falls in: a pit, and a lava lake at its floor. True when it's done. */
	private static boolean caldera(ServerLevel level, Volcano v) {
		int radius = CALDERA_RADIUS + v.magnitude, depth = CALDERA_DEPTH + v.magnitude;
		if (!v.calderaLaid) {
			v.calderaLaid = true;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double r = Math.sqrt(dx * dx + dz * dz);
					if (r > radius + 0.3) {
						continue;
					}
					int down = (int)Math.round(depth * (1.0 - (r / (radius + 1.0)) * (r / (radius + 1.0))));
					int x = v.summit.getX() + dx, z = v.summit.getZ() + dz;
					if (!Loaded.made(level, x, z)) {
						continue;
					}
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
					int floor = v.summit.getY() - 1 - down;
					for (int y = top; y > floor; y--) {
						v.caldera.add(new BlockPos(x, y, z));
					}
					// the floor: a lava lake in the middle
					if (r < radius * 0.6) {
						BlockPos bottom = new BlockPos(x, floor, z);
						v.caldera.add(bottom);
						v.lake.add(bottom);
					}
				}
			}
			level.playSound(null, v.summit, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 12.0F, 0.25F);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, v.summit.getX() + 0.5, v.summit.getY(), v.summit.getZ() + 0.5, 2, radius, 1.0, radius, 0.0);
		}
		int share = BlockBudget.share();
		for (int i = 0; i < share && !v.caldera.isEmpty(); i++) {
			BlockPos p = v.caldera.removeFirst();
			BlockState state = level.getBlockState(p);
			if (state.isAir() || state.hasBlockEntity() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
				continue;
			}
			boolean lake = v.lake.contains(p) && !level.getBlockState(p.below()).isAir();
			BlockBudget.set(level, p, lake && level.getRandom().nextInt(3) > 0 ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		return v.caldera.isEmpty();
	}

	/** Cooling: running lava sets to basalt, standing lava to obsidian. */
	private static void cool(ServerLevel level, Volcano v, RandomSource random) {
		int radius = COOL_RADIUS + COOL_RADIUS_PER_MAGNITUDE * v.magnitude;
		for (int i = 0; i < COOL_COLUMNS && !BlockBudget.empty(); i++) {
			double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * radius;
			int x = v.summit.getX() + Mth.floor(Math.cos(a) * r), z = v.summit.getZ() + Mth.floor(Math.sin(a) * r);
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			for (int y = top; y > top - 4; y--) {
				BlockPos p = new BlockPos(x, y, z);
				net.minecraft.world.level.material.FluidState fluid = level.getFluidState(p);
				if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
					BlockBudget.set(level, p, fluid.isSourceOfType(Fluids.LAVA) ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BASALT.defaultBlockState());
					if (random.nextInt(8) == 0) {
						level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 1.0, z + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
						level.playSound(null, p, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.8F);
					}
				}
			}
		}
	}

	/** Whether a volcano is awake near (x, z) (the storm bell rings for one erupting). */
	static boolean erupting(double x, double z, double range) {
		for (Volcano v : AWAKE) {
			double dx = v.summit.getX() - x, dz = v.summit.getZ() - z;
			if (v.phase == ERUPT && dx * dx + dz * dz < range * range) {
				return true;
			}
		}
		return false;
	}

	/** Every awake volcano's summit (the almanac, the world's ending). */
	static List<BlockPos> summits() {
		return AWAKE.stream().map(v -> v.summit).toList();
	}

	static boolean any() {
		return !AWAKE.isEmpty();
	}
}
