package com.jaymoskingdoms.disasters;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.12) Storm fronts. Now and then a storm cell forms a long way off, upwind of someone, and drifts across the land
 * with the wind: rain and thunder fall only under it (a mixin on {@link Level}'s weather asks {@link #cover}), and its
 * lightning strikes only there. Every few days ({@link Disasters#DISASTER_DAYS}) the cell is a supercell, and as it
 * nears people it can put down a {@link Tornado}. In a dry land it's a dust devil instead. Vanilla's own weather is
 * left as it is; these come on top of it.
 *
 * <p>The cells live only in memory: after a restart the sky is clear of them.
 */
public final class Storms {
	/** How often we think about forming a cell (ticks). */
	static final int ROLL_TICKS = 200;
	/** On average, an ordinary storm cell this often (days), near someone. */
	static final double RAIN_CELL_DAYS = 1.5;
	/** Where a cell forms: this far upwind of the one it's for, give or take {@link #SPAWN_JITTER} across the wind. */
	static final double SPAWN_DISTANCE = 300.0, SPAWN_JITTER = 70.0;
	/** A dust devil forms this far from the one it's for. */
	static final double DEVIL_MIN = 40.0, DEVIL_MAX = 70.0;
	/** Cells' radii (rain, thunder, supercell). */
	static final float[] RADIUS_MIN = {70.0F, 80.0F, 110.0F}, RADIUS_MAX = {110.0F, 130.0F, 170.0F};
	/** How long a cell takes to gather (and to break up) (ticks). */
	static final int GATHER_TICKS = 1200;
	/** Rain fades out over this many blocks past a cell's edge. */
	static final double EDGE = 40.0;
	/** Lightning: one strike per this many ticks under a thunderstorm, and under a supercell. */
	static final int STRIKE_THUNDER = 140, STRIKE_SUPERCELL = 60;
	/** A supercell puts down its tornado once it's this close to someone (and gathered); the chance each roll. */
	static final double TOUCHDOWN_NEAR = 170.0;
	static final float TOUCHDOWN_CHANCE = 0.3F;
	/** (0.7.2) Only this share of the supercells that come on their own can put down a tornado at all (the rest just
	 * storm): about one tornado every 60 days at the default {@link Disasters#DISASTER_DAYS}. */
	static final float TORNADO_SUPERCELLS = 0.1F;
	/** The odds a supercell's tornado is a monster. */
	static final float MONSTER_CHANCE = 0.2F;
	/** The wind's drift speed (blocks/tick) range, and how far its heading wanders per tick (radians). */
	static final float WIND_MIN = 0.045F, WIND_MAX = 0.08F, WIND_WANDER = 0.002F;
	/** Players hear about a forming supercell within this distance. */
	static final double NEWS_RANGE = 450.0;
	/** How many cells can be about at once. */
	static final int MOST = 4;

	static final List<Storm> SERVER = new ArrayList<>();
	/** The client's copy (set by the client from {@link StormNet.Sync}, moved on each tick). */
	public static volatile List<Storm> CLIENT = List.of();
	public static volatile List<Tornado> CLIENT_TORNADOES = List.of();
	public static volatile float clientWindAngle, clientWindSpeed;
	/** The client's rain and thunder where its camera is (eased), which the client's sky and rain use. */
	public static volatile float clientRain, clientThunder;
	/** Rain falls on ground this much under a cell's cover. */
	public static final float RAINS = 0.3F;

	static float windAngle = 0.0F, windSpeed = 0.06F;

	/** (0.7.12) The server's wind: the way it blows toward (radians, toward (cos, sin) in x, z), and how hard, from none
	 * (its least) to 1 (its most). Ships' sails ask this. */
	public static float windAngle() {
		return windAngle;
	}

	public static float windStrength() {
		return (windSpeed - WIND_MIN) / (WIND_MAX - WIND_MIN);
	}
	private static int nextId = 1;

	private Storms() {
	}

	static int nextId() {
		return nextId++;
	}

	static void clear() {
		SERVER.clear();
		Tornadoes.clear();
	}

	// ---- both sides -----------------------------------------------------------------------------------------------

	/** How much of a storm cell lies over (x, z), 0..1 (on the client, its copy). The Overworld only. */
	public static float cover(Level level, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		List<Storm> storms = level.isClientSide() ? CLIENT : SERVER;
		float best = 0.0F;
		for (Storm s : storms) {
			best = Math.max(best, s.over(x, z));
		}
		return best;
	}

	/** How much thunder there is over (x, z), 0..1 (a thunderstorm's or supercell's cover). */
	public static float thunder(Level level, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		List<Storm> storms = level.isClientSide() ? CLIENT : SERVER;
		float best = 0.0F;
		for (Storm s : storms) {
			if (s.kind != Storm.RAIN) {
				best = Math.max(best, s.over(x, z));
			}
		}
		return best;
	}

	/** Whether a thunderstorm is right overhead at (x, z). Jaymo's Powers' Wind asks this (by reflection). */
	public static boolean thunderAt(Level level, double x, double z) {
		return thunder(level, x, z) > 0.6F;
	}

	// ---- the server ------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		windAngle += (random.nextFloat() - 0.5F) * 2.0F * WIND_WANDER;
		windSpeed = Mth.clamp(windSpeed + (random.nextFloat() - 0.5F) * 0.0004F, WIND_MIN, WIND_MAX);
		if (now % ROLL_TICKS == 0) {
			roll(level, random);
		}
		for (Storm s : SERVER) {
			s.move();
			strike(level, s, random);
			if (s.kind == Storm.SUPERCELL && !s.spawned && now % 100 == 0 && s.strength() > 0.8F) {
				touchdown(level, s, random);
			}
		}
		SERVER.removeIf(s -> s.age >= s.life);
		Tornadoes.tick(level);
		if (now % 10 == 0) {
			StormNet.Sync sync = new StormNet.Sync(windAngle, windSpeed, List.copyOf(SERVER), List.copyOf(Tornadoes.SERVER));
			for (ServerPlayer player : level.players()) {
				ServerPlayNetworking.send(player, sync);
			}
		}
		if (now % 20 == 0) {
			Instruments.readBarometers(level);
		}
	}

	/** Maybe a cell forms: a supercell (or dust devil) every few days, an ordinary one more often. */
	private static void roll(ServerLevel level, RandomSource random) {
		if (SERVER.size() >= MOST || level.players().isEmpty()) {
			return;
		}
		int days = level.getGameRules().get(Disasters.DISASTER_DAYS);
		if (days > 0 && random.nextDouble() < ROLL_TICKS / (days * 24000.0)) {
			ServerPlayer target = someone(level, random);
			if (target != null) {
				if (dry(level, target.blockPosition())) {
					dustDevil(level, target.position(), random);
				} else {
					Storm cell = form(level, target.position(), Storm.SUPERCELL, random, true);
					// (0.7.2) most never put one down
					cell.spawned = random.nextFloat() >= TORNADO_SUPERCELLS;
				}
			}
			return;
		}
		if (random.nextDouble() < ROLL_TICKS / (RAIN_CELL_DAYS * 24000.0)) {
			ServerPlayer target = someone(level, random);
			if (target != null && !dry(level, target.blockPosition())) {
				form(level, target.position(), random.nextFloat() < 0.6F ? Storm.THUNDER : Storm.RAIN, random, false);
			}
		}
	}

	private static @Nullable ServerPlayer someone(ServerLevel level, RandomSource random) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
	}

	/** Whether no rain falls here (a desert, a savanna, badlands): dust devils, not storms. */
	static boolean dry(ServerLevel level, BlockPos pos) {
		return !level.getBiome(pos).value().hasPrecipitation();
	}

	/**
	 * A cell of {@code kind} forms {@link #SPAWN_DISTANCE} upwind of {@code near} (or right over it, for a test) and
	 * drifts toward it with the wind.
	 */
	static Storm form(ServerLevel level, Vec3 near, int kind, RandomSource random, boolean upwind) {
		double wx = Math.cos(windAngle), wz = Math.sin(windAngle);
		double across = (random.nextDouble() - 0.5) * 2.0 * SPAWN_JITTER;
		double back = upwind ? SPAWN_DISTANCE : 0.0;
		double x = near.x - wx * back - wz * across, z = near.z - wz * back + wx * across;
		float speed = windSpeed * (0.85F + random.nextFloat() * 0.3F);
		float radius = Mth.lerp(random.nextFloat(), RADIUS_MIN[kind], RADIUS_MAX[kind]);
		// long enough to cross the land it was made for and on past it
		int life = (int)((back * 2.0 + radius * 2.0) / speed) + GATHER_TICKS;
		Storm s = new Storm(nextId(), kind, x, z, wx * speed, wz * speed, radius, upwind ? 0 : GATHER_TICKS, life);
		SERVER.add(s);
		if (kind == Storm.SUPERCELL) {
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceToSqr(x, p.getY(), z) < NEWS_RANGE * NEWS_RANGE) {
					p.sendSystemMessage(Component.translatable("disaster.jaymoskingdoms.supercell", direction(p.getX(), p.getZ(), x, z)));
				}
			}
		}
		return s;
	}

	/** A dust devil forms a little way off from {@code near}, crossing the land with the wind. */
	static Tornado dustDevil(ServerLevel level, Vec3 near, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double d = Mth.lerp(random.nextDouble(), DEVIL_MIN, DEVIL_MAX);
		return Tornadoes.spawn(level, Tornado.DUST_DEVIL, null, near.x + Math.cos(angle) * d, near.z + Math.sin(angle) * d, random);
	}

	/** The supercell, near enough to someone, may put down its tornado. */
	private static void touchdown(ServerLevel level, Storm s, RandomSource random) {
		boolean near = false;
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(s.x, p.getY(), s.z) < TOUCHDOWN_NEAR * TOUCHDOWN_NEAR) {
				near = true;
				break;
			}
		}
		if (!near || random.nextFloat() > TOUCHDOWN_CHANCE) {
			return;
		}
		s.spawned = true;
		int size = random.nextFloat() < MONSTER_CHANCE ? Tornado.MONSTER : Tornado.TORNADO;
		// down on the storm's leading side
		double ahead = s.radius * 0.3, speed = Math.max(1.0E-3, Math.sqrt(s.vx * s.vx + s.vz * s.vz));
		Tornadoes.spawn(level, size, s, s.x + s.vx / speed * ahead, s.z + s.vz / speed * ahead, random);
	}

	/** Lightning under a thunderstorm, now and then, where the land is loaded. */
	private static void strike(ServerLevel level, Storm s, RandomSource random) {
		if (s.kind == Storm.RAIN) {
			return;
		}
		int every = s.kind == Storm.SUPERCELL ? STRIKE_SUPERCELL : STRIKE_THUNDER;
		if (random.nextInt(every) != 0 || random.nextFloat() > s.strength()) {
			return;
		}
		double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * s.radius * 0.85;
		int x = Mth.floor(s.x + Math.cos(a) * r), z = Mth.floor(s.z + Math.sin(a) * r);
		BlockPos column = new BlockPos(x, level.getMinY(), z);
		if (!level.isLoaded(column)) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		if (level.getBiome(top).value().getPrecipitationAt(top, level.getSeaLevel()) != Biome.Precipitation.RAIN) {
			return;
		}
		// a lightning rod nearby takes it
		BlockPos rod = BlockPos.betweenClosedStream(top.offset(-8, -6, -8), top.offset(8, 10, 8))
			.filter(p -> level.getBlockState(p).is(BlockTags.LIGHTNING_RODS)).findFirst().map(BlockPos::immutable).orElse(null);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.snapTo(Vec3.atBottomCenterOf(rod != null ? rod.above() : top));
			level.addFreshEntity(bolt);
		}
	}

	/** Which way (x, z) lies from (fromX, fromZ), as a compass word for messages. */
	public static Component direction(double fromX, double fromZ, double x, double z) {
		double angle = Math.toDegrees(Math.atan2(z - fromZ, x - fromX));
		// east is 0°, south 90° (z grows southward)
		String[] names = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
		int i = Math.floorMod(Math.round((float)(angle / 45.0)), 8);
		return Component.translatable("disaster.jaymoskingdoms.dir." + names[i]);
	}
}
