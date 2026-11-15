package com.jaymospowers.power.xenoform;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.power.web.WebCraterFeature;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.tinkering.Tinkering;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Falling stars: now and then, on a clear night, a glowing rock comes down out of the sky near someone standing under
 * it, the way a Gravity meteor does, and blows a crater. Unlike a meteor it doesn't break up: a
 * {@link FallenStarBlock fallen star} is left in the crater's heart until someone mines it. Nothing to do with any
 * power until then; it's how the Xeno-Band gets into the world (and, more rarely, a symbiote).
 */
public final class FallingStars {
	// ---- tuning ----
	/** Each player under the open night sky is rolled this often (ticks), with this chance of a star. */
	public static final int CHECK_EVERY = 200;
	public static final float STAR_CHANCE = 0.002F;
	/** No two stars in one world closer in time than this (ticks: a day). */
	public static final int GAP_TICKS = 24000;
	/** It lands this far from the player (blocks), from this high, over this many ticks. */
	public static final double RANGE_MIN = 40.0, RANGE_MAX = 100.0;
	public static final double HEIGHT = 110.0;
	public static final int FALL_TICKS = 140;
	/** The blast on landing (hurts and flings; breaks nothing itself: the crater is carved). */
	public static final float BLAST = 2.0F;
	/** The landing shakes every screen this far off, this hard (degrees) close by. */
	public static final double LANDING_SHAKE_RANGE = 64.0;
	public static final float LANDING_SHAKE = 2.5F;
	/** Its trail is sent to everyone in the world this close (blocks), so a star is seen coming down from far off. */
	public static final double SEEN_FROM = 400.0;
	/** How big the rock is drawn while it falls (it glows through hills too). */
	public static final float ROCK_SCALE = 2.5F;
	/** After it lands, a tall smoke plume with sparks marks the crater for this long (ticks: 3 minutes). */
	public static final int PLUME_TICKS = 3600;

	/** (medieval picks, nd-meteor) A great star: far rarer (the chance a roll, and the least gap: five days)... */
	public static final float GREAT_CHANCE = 0.00015F;
	public static final int GREAT_GAP_TICKS = 5 * 24000;
	/** ...seen growing in the sky this long before it falls (ticks: two minutes), then falling this long, from this high. */
	public static final int GROW_TICKS = 2400, GREAT_FALL_TICKS = 220;
	public static final double GREAT_HEIGHT = 170.0, GREAT_RANGE_MIN = 70.0, GREAT_RANGE_MAX = 150.0;
	public static final float GREAT_ROCK_SCALE = 7.0F;
	/** Its landing: the blast, the shake (range, strength), and the shockwave (range, most hurt, fling). */
	public static final float GREAT_BLAST = 4.0F, GREAT_SHAKE = 6.0F, SHOCK_DAMAGE = 12.0F;
	public static final double GREAT_SHAKE_RANGE = 220.0, SHOCK_RANGE = 28.0, SHOCK_FLING = 2.2;
	/** Its crater: radius and depth (blocks), the raised lip's width, earth thrown out, fires lit round the rim. */
	public static final int CRATER_RADIUS = 13, CRATER_DEPTH = 8, LIP = 3, THROWN = 28, RIM_FIRES = 18;
	/** The crater is carved this many blocks a tick at most, on loaded land only. */
	public static final int CARVE_PER_TICK = 300;
	/** Its strength for Jaymo's Kingdoms' disasters (the news, the quake, the dust in the sky). */
	public static final int GREAT_MAGNITUDE = 5;

	/** What the star looks like coming down. */
	private static final List<BlockState> ROCK = List.of(Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
		Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());

	private record Star(ResourceKey<Level> dimension, int rock, Vec3 from, Vec3 to, long start) {
	}

	private record Plume(ResourceKey<Level> dimension, Vec3 at, long start) {
	}

	/** A great star: growing in the sky over {@code to} from {@code start}; its rock, once it falls (-1 till then). */
	private static final class GreatStar {
		final ResourceKey<Level> dimension;
		final Vec3 to, from;
		final long start;
		int rock = -1;

		GreatStar(ResourceKey<Level> dimension, Vec3 to, Vec3 from, long start) {
			this.dimension = dimension;
			this.to = to;
			this.from = from;
			this.start = start;
		}
	}

	/**
	 * A crater being carved: its bowl's columns (each dug out top down when its land is loaded, its height read only
	 * then), then the lip's; columns out of loaded land wait and are tried again. The column being dug, block by block.
	 */
	private static final class Carve {
		final ResourceKey<Level> dimension;
		final BlockPos centre, heart;
		final ArrayDeque<int[]> bowl = new ArrayDeque<>(), lip = new ArrayDeque<>(), waiting = new ArrayDeque<>();
		final ArrayDeque<BlockPos> dig = new ArrayDeque<>();
		@org.jspecify.annotations.Nullable BlockPos floor;

		Carve(ResourceKey<Level> dimension, BlockPos centre, BlockPos heart) {
			this.dimension = dimension;
			this.centre = centre;
			this.heart = heart;
		}

		boolean done() {
			return bowl.isEmpty() && lip.isEmpty() && waiting.isEmpty() && dig.isEmpty() && floor == null;
		}
	}

	/** Columns of a crater out of loaded land are tried again this often (ticks). */
	public static final int CARVE_RETRY = 100;

	private static final List<GreatStar> GREAT = new ArrayList<>();
	private static final List<Carve> CARVES = new ArrayList<>();
	private static final Map<ResourceKey<Level>, Long> LAST_GREAT = new HashMap<>();
	private static final List<Star> STARS = new ArrayList<>();
	private static final List<Plume> PLUMES = new ArrayList<>();
	private static final Map<ResourceKey<Level>, Long> LAST = new HashMap<>();

	private FallingStars() {
	}

	static void init() {
	}

	static void clear() {
		STARS.clear();
		PLUMES.clear();
		LAST.clear();
		GREAT.clear();
		CARVES.clear();
		LAST_GREAT.clear();
	}

	static void tick(MinecraftServer server) {
		fly(server);
		smoke(server);
		great(server);
		carve(server);
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		rollGreat(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || level.isRaining()) {
				continue;
			}
			long now = level.getGameTime();
			Long last = LAST.get(level.dimension());
			if (last != null && now - last < GAP_TICKS) {
				continue;
			}
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || !level.canSeeSky(player.blockPosition()) || random.nextFloat() >= STAR_CHANCE) {
					continue;
				}
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = Mth.lerp(random.nextDouble(), RANGE_MIN, RANGE_MAX);
				Vec3 near = player.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
				if (drop(level, near)) {
					break;
				}
			}
		}
	}

	/**
	 * Starts a star falling onto the ground at {@code near}'s column (a roll, or the debug command). False if there's no
	 * good ground there (water, the void).
	 */
	public static boolean drop(ServerLevel level, Vec3 near) {
		int x = Mth.floor(near.x);
		int z = Mth.floor(near.z);
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos ground = new BlockPos(x, groundY, z);
		if (groundY <= level.getMinY() + 5 || !level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
			return false;
		}
		Vec3 to = Vec3.atBottomCenterOf(ground.above());
		RandomSource random = level.getRandom();
		double slant = random.nextDouble() * Math.PI * 2.0;
		Vec3 from = to.add(Math.cos(slant) * HEIGHT * 0.5, HEIGHT, Math.sin(slant) * HEIGHT * 0.5);
		from = new Vec3(from.x, Math.min(from.y, level.getMaxY() - 2), from.z);
		BlockCluster rock = BlockCluster.made(level, ROCK, from);
		rock.script(true, false);
		com.jaymospowers.core.Oddities.mark(rock, com.jaymospowers.core.Oddities.STAR);
		rock.setAttached(BlockCluster.SCALE, ROCK_SCALE);
		rock.setGlowingTag(true);
		STARS.add(new Star(level.dimension(), rock.getId(), from, to, level.getGameTime()));
		LAST.put(level.dimension(), level.getGameTime());
		// a long whistle, and no word: whoever looks up has to spot it and find where it lands
		level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 12.0F, 0.5F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 12.0F, 0.6F);
		return true;
	}

	private static void fly(MinecraftServer server) {
		for (Iterator<Star> it = STARS.iterator(); it.hasNext(); ) {
			Star star = it.next();
			ServerLevel level = server.getLevel(star.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			Entity rock = level.getEntity(star.rock());
			long t = level.getGameTime() - star.start();
			float p = Mth.clamp(t / (float)FALL_TICKS, 0.0F, 1.0F);
			Vec3 at = star.from().lerp(star.to(), p * p);
			if (rock != null) {
				Vec3 was = rock.position();
				rock.setPos(at);
				rock.setDeltaMovement(at.subtract(was));
				rock.syncVelocity = true;
			}
			// a bright tail, and smoke, seen from far off (forced past the usual 32-block particle range)
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) > SEEN_FROM * SEEN_FROM) {
					continue;
				}
				level.sendParticles(player, MagicFx.LIGHT, true, true, at.x, at.y + 0.8, at.z, 14, 1.0, 1.0, 1.0, 0.03);
				level.sendParticles(player, ParticleTypes.FLAME, true, true, at.x, at.y + 0.8, at.z, 8, 1.0, 1.0, 1.0, 0.05);
				level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y + 0.8, at.z, 6, 0.8, 0.8, 0.8, 0.02);
				level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, at.x, at.y + 0.8, at.z, 3, 0.6, 0.6, 0.6, 0.01);
			}
			if (t % 10 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 4.0F, 0.5F + p * 0.4F);
			}
			if (t < FALL_TICKS) {
				continue;
			}
			it.remove();
			if (rock != null) {
				rock.discard();
			}
			land(level, star.to());
			PLUMES.add(new Plume(level.dimension(), star.to(), level.getGameTime()));
		}
	}

	/** The plume over a fresh crater: signal smoke climbing high, and sparks, so whoever saw it fall can find it. */
	private static void smoke(MinecraftServer server) {
		for (Iterator<Plume> it = PLUMES.iterator(); it.hasNext(); ) {
			Plume plume = it.next();
			ServerLevel level = server.getLevel(plume.dimension());
			long t = level == null ? PLUME_TICKS : level.getGameTime() - plume.start();
			if (t >= PLUME_TICKS) {
				it.remove();
				continue;
			}
			if (t % 4 != 0) {
				continue;
			}
			Vec3 at = plume.at();
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) > SEEN_FROM * SEEN_FROM) {
					continue;
				}
				level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, at.x, at.y + 0.5, at.z, 2, 0.6, 0.3, 0.6, 0.01);
				level.sendParticles(player, MagicFx.LIGHT, true, true, at.x, at.y + 1.0, at.z, 2, 0.5, 1.5, 0.5, 0.06);
			}
		}
	}

	// ---- great stars (medieval picks, nd-meteor) ----------------------------------------------------------------------

	private static void rollGreat(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!level.isDarkOutside() || !GREAT.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_GREAT.get(level.dimension());
		if (last != null && now - last < GREAT_GAP_TICKS) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || random.nextFloat() >= GREAT_CHANCE) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = Mth.lerp(random.nextDouble(), GREAT_RANGE_MIN, GREAT_RANGE_MAX);
			if (dropGreat(level, player.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r))) {
				break;
			}
		}
	}

	/**
	 * A great star starts to grow in the sky over {@code near}'s column (a roll, or the debug command); in two minutes it
	 * falls. False if there's no good ground there.
	 */
	public static boolean dropGreat(ServerLevel level, Vec3 near) {
		int x = Mth.floor(near.x), z = Mth.floor(near.z);
		if (!level.isLoaded(new BlockPos(x, 0, z))) {
			return false;
		}
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos ground = new BlockPos(x, groundY, z);
		if (groundY <= level.getMinY() + CRATER_DEPTH + 4 || !level.getFluidState(ground.above()).isEmpty()) {
			return false;
		}
		Vec3 to = Vec3.atBottomCenterOf(ground.above());
		double slant = level.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 from = to.add(Math.cos(slant) * GREAT_HEIGHT * 0.6, GREAT_HEIGHT, Math.sin(slant) * GREAT_HEIGHT * 0.6);
		from = new Vec3(from.x, Math.min(from.y, level.getMaxY() - 2), from.z);
		GREAT.add(new GreatStar(level.dimension(), to, from, level.getGameTime()));
		LAST_GREAT.put(level.dimension(), level.getGameTime());
		LAST.put(level.dimension(), level.getGameTime());
		return true;
	}

	/** Growing: a point of light high over where it'll land, brighter and wider every second; then the fall. */
	private static void great(MinecraftServer server) {
		for (Iterator<GreatStar> it = GREAT.iterator(); it.hasNext(); ) {
			GreatStar star = it.next();
			ServerLevel level = server.getLevel(star.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - star.start;
			if (t < GROW_TICKS) {
				if (t % 5 == 0) {
					float grown = t / (float)GROW_TICKS;
					int count = 1 + Math.round(grown * 18.0F);
					double spread = 0.3 + grown * 2.5;
					for (ServerPlayer player : level.players()) {
						if (player.distanceToSqr(star.from) <= SEEN_FROM * SEEN_FROM * 2.0) {
							level.sendParticles(player, MagicFx.LIGHT, true, true, star.from.x, star.from.y, star.from.z, count, spread, spread, spread, 0.01);
						}
					}
					if (t % 400 == 0) {
						level.playSound(null, star.from.x, star.to.y + 30.0, star.from.z, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 6.0F + 10.0F * grown, 0.5F);
					}
				}
				continue;
			}
			Entity rock = star.rock < 0 ? null : level.getEntity(star.rock);
			if (star.rock < 0) {
				BlockCluster made = BlockCluster.made(level, ROCK, star.from);
				made.script(true, false);
				com.jaymospowers.core.Oddities.mark(made, com.jaymospowers.core.Oddities.STAR);
				made.setAttached(BlockCluster.SCALE, GREAT_ROCK_SCALE);
				made.setGlowingTag(true);
				star.rock = made.getId();
				rock = made;
				level.playSound(null, star.to.x, star.to.y, star.to.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 16.0F, 0.3F);
				level.playSound(null, star.to.x, star.to.y, star.to.z, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 16.0F, 0.4F);
			}
			long f = t - GROW_TICKS;
			float p = Mth.clamp(f / (float)GREAT_FALL_TICKS, 0.0F, 1.0F);
			Vec3 at = star.from.lerp(star.to, p * p);
			if (rock != null) {
				Vec3 was = rock.position();
				rock.setPos(at);
				rock.setDeltaMovement(at.subtract(was));
				rock.syncVelocity = true;
			}
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) > SEEN_FROM * SEEN_FROM * 2.0) {
					continue;
				}
				level.sendParticles(player, MagicFx.LIGHT, true, true, at.x, at.y + 1.5, at.z, 30, 2.5, 2.5, 2.5, 0.05);
				level.sendParticles(player, ParticleTypes.FLAME, true, true, at.x, at.y + 1.5, at.z, 20, 2.0, 2.0, 2.0, 0.08);
				level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y + 1.5, at.z, 16, 2.0, 2.0, 2.0, 0.03);
			}
			if (f % 8 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 8.0F, 0.3F + p * 0.4F);
			}
			if (f < GREAT_FALL_TICKS) {
				continue;
			}
			it.remove();
			if (rock != null) {
				rock.discard();
			}
			landGreat(level, star.to);
			PLUMES.add(new Plume(level.dimension(), star.to, level.getGameTime()));
		}
	}

	/** It strikes: the blast and the shockwave, the crater (carved a little at a time), its iron heart, earth and fire. */
	private static void landGreat(ServerLevel level, Vec3 to) {
		RandomSource random = level.getRandom();
		// nothing on land that isn't loaded is touched: the crater waits for it (carve)
		boolean here = level.isLoaded(BlockPos.containing(to));
		if (here) {
			level.explode(null, to.x, to.y, to.z, GREAT_BLAST, Level.ExplosionInteraction.NONE);
		}
		com.jaymospowers.core.Shakes.around(level, to, GREAT_SHAKE_RANGE, GREAT_SHAKE);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 16.0F, 0.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 16.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y + 1.0, to.z, 8, CRATER_RADIUS * 0.4, 1.5, CRATER_RADIUS * 0.4, 0.0);
		level.sendParticles(MagicFx.LIGHT, to.x, to.y + 1.0, to.z, 160, 3.0, 2.0, 3.0, 0.4);
		level.sendParticles(ParticleTypes.LAVA, to.x, to.y + 1.0, to.z, 60, 3.0, 1.0, 3.0, 0.0);
		// the shockwave: everyone near is thrown back and hurt, the less the further off
		for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
			new net.minecraft.world.phys.AABB(to, to).inflate(SHOCK_RANGE))) {
			Vec3 away = e.position().subtract(to);
			double d = away.length();
			if (d > SHOCK_RANGE) {
				continue;
			}
			float near = (float)(1.0 - d / SHOCK_RANGE);
			Vec3 dir = d < 0.5 ? new Vec3(0.0, 1.0, 0.0) : away.scale(1.0 / d);
			e.hurtServer(level, level.damageSources().explosion(null, null), SHOCK_DAMAGE * near);
			e.push(dir.x * SHOCK_FLING * near, 0.6 * near + 0.2, dir.z * SHOCK_FLING * near);
			e.syncVelocity = true;
		}
		BlockPos centre = BlockPos.containing(to).below();
		Carve carve = new Carve(level.dimension(), centre, centre.below(CRATER_DEPTH - 1));
		// the bowl's columns, the middle first (its heart); the lip a ring round it (heights are read as each is carved)
		carve.bowl.add(new int[] {0, 0});
		for (int dx = -CRATER_RADIUS - LIP; dx <= CRATER_RADIUS + LIP; dx++) {
			for (int dz = -CRATER_RADIUS - LIP; dz <= CRATER_RADIUS + LIP; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r <= CRATER_RADIUS && (dx != 0 || dz != 0)) {
					carve.bowl.add(new int[] {dx, dz});
				} else if (r > CRATER_RADIUS && r <= CRATER_RADIUS + LIP && random.nextFloat() < 0.8F) {
					carve.lip.add(new int[] {dx, dz});
				}
			}
		}
		CARVES.add(carve);
		// earth thrown out of it, landing round about
		for (int i = 0; here && i < THROWN; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			BlockPos at = BlockPos.containing(to.x + Math.cos(a) * 2.0, to.y + 2.0 + i % 4, to.z + Math.sin(a) * 2.0);
			if (!level.getBlockState(at).isAir()) {
				continue;
			}
			BlockState earth = switch (random.nextInt(4)) {
				case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
				case 1 -> Blocks.COBBLESTONE.defaultBlockState();
				case 2 -> Blocks.GRAVEL.defaultBlockState();
				default -> Blocks.DIRT.defaultBlockState();
			};
			level.setBlock(at, earth, Block.UPDATE_CLIENTS);
			net.minecraft.world.entity.item.FallingBlockEntity thrown = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, at, earth);
			double out = 0.6 + random.nextDouble() * 0.9;
			thrown.setDeltaMovement(Math.cos(a) * out, 0.7 + random.nextDouble() * 0.6, Math.sin(a) * out);
			thrown.setHurtsEntities(1.5F, 12);
		}
		com.jaymospowers.compat.KingdomsCompat.greatStar(level, to.x, to.y, to.z, GREAT_MAGNITUDE, CRATER_RADIUS);
	}

	/**
	 * Each tick, {@link #CARVE_PER_TICK} blocks at most across every crater: the bowl dug out column by column (its floor
	 * scorched, the heart set in the middle one), then the lip piled up and fires lit. Only on loaded land: a column whose
	 * land isn't loaded waits, and nothing is read or changed there till it is.
	 */
	private static void carve(MinecraftServer server) {
		int budget = CARVE_PER_TICK;
		boolean retry = server.getTickCount() % CARVE_RETRY == 0;
		for (Iterator<Carve> it = CARVES.iterator(); it.hasNext(); ) {
			Carve c = it.next();
			ServerLevel level = server.getLevel(c.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			RandomSource random = level.getRandom();
			if (retry) {
				c.bowl.addAll(c.waiting.stream().filter(col -> col.length == 2).toList());
				c.lip.addAll(c.waiting.stream().filter(col -> col.length == 3).map(col -> new int[] {col[0], col[1]}).toList());
				c.waiting.clear();
			}
			while (budget > 0) {
				if (!c.dig.isEmpty()) {
					BlockPos p = c.dig.poll();
					budget--;
					if (!p.equals(c.heart) && level.isLoaded(p) && breakable(level, p)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					continue;
				}
				if (c.floor != null) {
					// the floor left behind, scorched (and the heart in the middle)
					BlockPos p = c.floor;
					c.floor = null;
					budget--;
					if (level.isLoaded(p)) {
						if (p.getX() == c.centre.getX() && p.getZ() == c.centre.getZ()) {
							level.setBlock(p, scorched(random), Block.UPDATE_CLIENTS);
							level.setBlock(c.heart, Tinkering.METEORIC_IRON_HEART.defaultBlockState(), Block.UPDATE_ALL);
						} else if (breakable(level, p)) {
							level.setBlock(p, scorched(random), Block.UPDATE_CLIENTS);
						}
					}
					continue;
				}
				if (!c.bowl.isEmpty()) {
					int[] col = c.bowl.poll();
					int x = c.centre.getX() + col[0], z = c.centre.getZ() + col[1];
					if (!level.isLoaded(new BlockPos(x, 0, z))) {
						c.waiting.add(col);
						continue;
					}
					double k = Math.sqrt(col[0] * col[0] + col[1] * col[1]) / CRATER_RADIUS;
					int bottom = c.centre.getY() - (int)Math.round(CRATER_DEPTH * (1.0 - k * k));
					int top = Math.max(c.centre.getY() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
					for (int y = top; y > bottom; y--) {
						c.dig.add(new BlockPos(x, y, z));
					}
					c.floor = new BlockPos(x, bottom, z);
					continue;
				}
				if (c.lip.isEmpty()) {
					break;
				}
				int[] col = c.lip.poll();
				int x = c.centre.getX() + col[0], z = c.centre.getZ() + col[1];
				if (!level.isLoaded(new BlockPos(x, 0, z))) {
					c.waiting.add(new int[] {col[0], col[1], 1});
					continue;
				}
				budget -= 3;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int high = 1 + random.nextInt(2);
				for (int i = 0; i < high; i++) {
					BlockPos at = new BlockPos(x, y + i, z);
					if (level.getBlockState(at).canBeReplaced()) {
						level.setBlock(at, random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				if (random.nextInt(Math.max(1, (CRATER_RADIUS + LIP) * 8 / RIM_FIRES)) == 0) {
					BlockPos fire = new BlockPos(x, y + high, z);
					if (level.getBlockState(fire).isAir()) {
						level.setBlock(fire, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
			if (c.done()) {
				it.remove();
			}
			if (budget <= 0) {
				break;
			}
		}
	}

	private static boolean breakable(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return !s.isAir() && s.getDestroySpeed(level, p) >= 0.0F && s.getBlock() != Tinkering.METEORIC_IRON_HEART
			&& !(s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock);
	}

	private static BlockState scorched(RandomSource random) {
		return switch (random.nextInt(5)) {
			case 0 -> Blocks.MAGMA_BLOCK.defaultBlockState();
			case 1 -> Blocks.BLACKSTONE.defaultBlockState();
			case 2 -> Blocks.BASALT.defaultBlockState();
			default -> Blocks.COARSE_DIRT.defaultBlockState();
		};
	}

	/** The crater, and the star in its heart. */
	private static void land(ServerLevel level, Vec3 to) {
		level.explode(null, to.x, to.y, to.z, BLAST, Level.ExplosionInteraction.NONE);
		com.jaymospowers.core.Shakes.around(level, to, LANDING_SHAKE_RANGE, LANDING_SHAKE);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 8.0F, 0.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 10.0F, 0.9F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y + 1.0, to.z, 2, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(MagicFx.LIGHT, to.x, to.y + 1.0, to.z, 60, 1.5, 1.0, 1.5, 0.25);
		level.sendParticles(ParticleTypes.LAVA, to.x, to.y + 1.0, to.z, 20, 1.0, 0.5, 1.0, 0.0);
		BlockPos ground = BlockPos.containing(to).below();
		BlockPos seat = ground.below(WebCraterFeature.DEPTH - 1);
		boolean carved = WebCraterFeature.carve(level, level.getRandom(), ground, false);
		if (carved && WebPower.GOO != null && level.getBlockState(seat).is(WebPower.GOO)) {
			// the crater's heart is where the goo would sit: the star instead
			starAt(level, seat);
			return;
		}
		// odd ground: just set it down on top
		BlockPos.MutableBlockPos pos = ground.above().mutable();
		for (int i = 0; i < 8 && pos.getY() > level.getMinY(); i++, pos.move(0, -1, 0)) {
			if (level.getBlockState(pos.below()).isSolid() && (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())) {
				starAt(level, pos);
				return;
			}
		}
		starAt(level, ground);
	}

	/** (0.18.122) The star set in its crater; one in {@link XenoFinds#WRECK_ONE_IN} came down a wreck. */
	private static void starAt(ServerLevel level, BlockPos pos) {
		BlockPos at = pos.immutable();
		level.setBlock(at, FallenStarBlock.FALLEN_STAR.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getRandom().nextInt(XenoFinds.WRECK_ONE_IN) == 0) {
			StarWreckBlock.wreck(level, at);
		}
		// (0.18.122) and one in StarReaders.ONE_IN draws a star-reader, a day or so later
		StarReaders.crater(level, at);
	}
}
