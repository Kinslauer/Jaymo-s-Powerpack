package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.31) Natural disasters, stage 2: the land's own. Every few days ({@link Disasters#DISASTER_DAYS}, as often as
 * stage 1's supercells) the land under someone brews what suits it:
 * <ul>
 * <li>a <b>sandstorm</b> on sand: a thick orange fog that pushes you with the wind and piles up sand layers;</li>
 * <li>a <b>blizzard</b> in snow: a white-out that piles snow and freezes anyone out in it (leather keeps it off, and a
 * fire close by);</li>
 * <li>a <b>wildfire</b> in dry land (savanna, badlands) after dry lightning: a fire line that runs downwind, with tall
 * smoke. Settlements throw everyone they've got at it ({@code Firefighting}), and it's no crime;</li>
 * <li>anywhere else, a thunderstorm drifting in that drops <b>hail</b>: it stings anyone bareheaded and breaks glass
 * and crops.</li>
 * </ul>
 * Thunderstorms now and then bring hail of their own too. And after long rain, a <b>flood</b>: a river or lake near
 * someone rises a few blocks for a while, then drains (every water block it put down is taken back out, even when the
 * server stops).
 *
 * <p>Kept cheap: sandstorms, blizzards and hail are a handful of numbers sent to the clients, which do the fog and the
 * blowing; the server only works in the few blocks round each player under one.
 */
public final class Calamities {
	/** How often we think about a new one (ticks). */
	static final int ROLL_TICKS = 200;
	/** How long a sandstorm, blizzard or hail shower takes to build (and to die down) (ticks). */
	static final int GATHER_TICKS = 600;
	/** Sandstorms' and blizzards' radii, lifetimes (ticks) and drift (share of the wind). */
	static final float LAND_RADIUS_MIN = 150.0F, LAND_RADIUS_MAX = 230.0F;
	static final int LAND_LIFE_MIN = 6000, LAND_LIFE_MAX = 9600;
	static final float LAND_DRIFT = 0.5F;
	/** Hail: the odds a thunderstorm (a supercell) brings some once it's over someone; how long it lasts; its share of
	 * the storm's radius. */
	static final float HAIL_THUNDER = 0.12F, HAIL_SUPERCELL = 0.4F, HAIL_SHARE = 0.6F;
	static final int HAIL_LIFE_MIN = 1800, HAIL_LIFE_MAX = 3600;
	/** Hail stings a bareheaded player under it this often (chance per half second, at full strength), this hard. */
	static final float HAIL_STING = 0.3F, HAIL_DAMAGE = 1.0F;
	/** Hail breaks glass and crops in the open: tries a tick per player, and the odds a glass block or a crop goes. */
	static final float HAIL_GLASS = 0.03F, HAIL_CROP = 0.12F;
	/** Sand and snow pile up round each player under a storm: tries a tick (the odds), how far round, how deep. */
	static final float PILE_CHANCE = 0.5F;
	static final int PILE_RANGE = 24, SAND_DEEPEST = 3, SNOW_DEEPEST = 4;
	/** A sandstorm's push (blocks/tick², at full strength) on players in the open. */
	static final double SAND_PUSH = 0.012;
	/** A blizzard freezes players in the open: frozen ticks added a tick (the game takes 2 back a tick); a fire this
	 * near keeps you warm. */
	static final int FREEZE_ADD = 2, WARM_RANGE = 4;
	/** Wildfires: how long they burn (ticks), how often the front spreads, how many of its fires spread each time, how
	 * far it runs from where it began, how many burning spots the front keeps. */
	static final int FIRE_LIFE_MIN = 2400, FIRE_LIFE_MAX = 4800, FIRE_SPREAD_EVERY = 8, FIRE_SPREAD = 5, FIRE_FRONT = 48;
	static final double FIRE_REACH = 90.0;
	/** Where dry lightning starts one: this far from the one it's for. */
	static final double FIRE_MIN = 45.0, FIRE_MAX = 75.0;
	/** Settlements this near a wildfire fight it all-out. */
	static final double FIRE_ALARM = 120.0;
	/** Floods: after this much rain on someone (ticks), the odds each roll of one; how far round we look for water;
	 * how many blocks the water rises, how far it spreads, the most blocks it puts down, how many a tick, how long it
	 * stands before draining. */
	static final int RAIN_BEFORE_FLOOD = 9000;
	static final float FLOOD_CHANCE = 0.012F;
	static final int FLOOD_LOOK = 40, FLOOD_RISE = 3, FLOOD_RADIUS = 44, FLOOD_MOST = 3000, FLOOD_PER_TICK = 24, FLOOD_HOLD = 3600;
	/** At least this many water columns found to flood (a river or lake, not a puddle). */
	static final int FLOOD_WATER = 8;
	/** Players hear of one within this distance. */
	static final double NEWS_RANGE = 300.0;
	/** How many sandstorms, blizzards and hail showers at once; floods and wildfires. */
	static final int MOST = 4, MOST_FLOODS = 2, MOST_FIRES = 2;

	static final ResourceKey<DamageType> HAIL = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("hail"));

	/** (0.7.200) Body heat a full blizzard takes off, and a full sandstorm adds. */
	static final double BODY_SNOW = 45.0, BODY_SAND = 25.0;
	static final List<Calamity> SERVER = new ArrayList<>();
	/** The client's copy (set by the client from {@link Sync}, moved on each tick). */
	public static volatile List<Calamity> CLIENT = List.of();
	private static final List<Wildfire> FIRES = new ArrayList<>();
	private static final List<Flood> FLOODS = new ArrayList<>();
	/** Thunderstorms already rolled for hail (or sent in to bring it, true). */
	private static final Map<Integer, Boolean> HAIL_ROLLED = new HashMap<>();
	/** How long it's been raining on each player (ticks, roughly). */
	private static final Map<UUID, Integer> RAINED = new HashMap<>();
	/** Players by a fire (kept warm), checked now and then. */
	private static final Set<UUID> WARM = new HashSet<>();
	private static boolean sentEmpty = true;

	private Calamities() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
	}

	/** Every sandstorm, blizzard and hail shower ends; floods drain at once. */
	static void clear(@Nullable ServerLevel level) {
		SERVER.clear();
		FORECASTS.clear();
		FIRES.clear();
		HAIL_ROLLED.clear();
		RAINED.clear();
		WARM.clear();
		if (level != null) {
			for (Flood f : FLOODS) {
				f.drainAll(level);
			}
		}
		FLOODS.clear();
	}

	// ---- both sides -----------------------------------------------------------------------------------------------

	/** How much of a {@code kind} lies over (x, z), 0..1 (on the client, its copy). */
	public static float cover(Level level, int kind, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		float best = 0.0F;
		for (Calamity c : level.isClientSide() ? CLIENT : SERVER) {
			if (c.kind == kind) {
				best = Math.max(best, c.over(x, z));
			}
		}
		return best;
	}

	/**
	 * (0.7.200) What the calamity over {@code p} does to their body heat (Kitchen's {@code BodyHeat}, by reflection): a
	 * blizzard freezes, a sandstorm bakes, out in the open; under a roof a little of it.
	 */
	public static double bodyHeat(ServerPlayer p) {
		double x = p.getX(), z = p.getZ();
		float snow = cover(p.level(), Calamity.BLIZZARD, x, z), sand = cover(p.level(), Calamity.SANDSTORM, x, z);
		if (snow <= 0.05F && sand <= 0.05F) {
			return 0.0;
		}
		double open = p.level().canSeeSky(BlockPos.containing(p.getEyePosition())) ? 1.0 : 0.3;
		return (sand * BODY_SAND - snow * BODY_SNOW) * open;
	}

	/** Whether a wildfire burns near {@code pos}: the settlements there fight it all-out ({@code Firefighting}). */
	public static boolean wildfireNear(BlockPos pos) {
		for (Wildfire f : FIRES) {
			if (f.near(pos, FIRE_ALARM)) {
				return true;
			}
		}
		return false;
	}

	// ---- the server ------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		if (now % ROLL_TICKS == 0) {
			roll(level, random);
		}
		hailFromStorms(level, random);
		for (Calamity c : SERVER) {
			if (c.kind == Calamity.HAIL) {
				Storm s = storm(c.storm);
				if (s != null) {
					c.x = s.x;
					c.z = s.z;
					c.vx = s.vx;
					c.vz = s.vz;
				} else {
					c.life = Math.min(c.life, c.age + GATHER_TICKS / 4);
				}
			}
			c.move();
		}
		SERVER.removeIf(c -> c.age >= c.life);
		boolean damage = level.getGameRules().get(Disasters.DISASTER_DAMAGE);
		for (Calamity c : SERVER) {
			if (c.kind == Calamity.QUAKE) {
				Earthquakes.tick(level, c, random, now, damage);
			}
		}
		forecasts(level, now);
		if (!SERVER.isEmpty()) {
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator()) {
					weather(level, p, random, now, damage);
				}
			}
		}
		FIRES.removeIf(f -> !f.tick(level, random, now, damage));
		FLOODS.removeIf(f -> !f.tick(level));
		if (now % 10 == 0 && (!SERVER.isEmpty() || !sentEmpty)) {
			Sync sync = new Sync(List.copyOf(SERVER));
			for (ServerPlayer p : level.players()) {
				ServerPlayNetworking.send(p, sync);
			}
			sentEmpty = SERVER.isEmpty();
		}
	}

	/** Maybe the land brews something: about as often as a supercell, and what suits the ground under someone. */
	private static void roll(ServerLevel level, RandomSource random) {
		rain(level, random);
		int days = level.getGameRules().get(Disasters.DISASTER_DAYS);
		// (stage 3) a Harvest Moon never brings one; a Blood Moon twice as often
		if (days <= 0 || harvestMoon(level) || random.nextDouble() >= ROLL_TICKS / (days * 24000.0) * (bloodMoon(level) ? 2.0 : 1.0)) {
			return;
		}
		ServerPlayer target = someone(level, random);
		if (target != null && FORECASTS.size() < MOST_FORECASTS) {
			// (stage 3) it's foretold, and comes about half a day later
			BlockPos at = target.blockPosition();
			String kind = random.nextFloat() < (Earthquakes.mountainous(level, at) ? Earthquakes.MOUNTAINS : Earthquakes.ELSEWHERE)
				? "quake" : suits(level, at, random);
			FORECASTS.add(new Forecast(kind, at, level.getGameTime() + FORECAST_TICKS));
		}
	}

	/** (Stage 3) A disaster foretold: what, where, and when it comes. Court wizards, the almanac and the Traveller's
	 * Journal tell of it. */
	record Forecast(String kind, BlockPos at, long when) {
	}

	/** (Stage 3) How long before a disaster it's foretold (ticks); how many can be foretold at once; it comes about
	 * whoever is within this distance of where it was foretold then (or not at all). */
	static final long FORECAST_TICKS = 12000L;
	static final int MOST_FORECASTS = 3;
	static final double FORECAST_REACH = 400.0;
	/** (Stage 3) The almanac and the wizards foretell disasters within this distance. */
	public static final double FORETOLD_RANGE = 1200.0;
	private static final List<Forecast> FORECASTS = new ArrayList<>();

	/** (Testing) Something suited to where {@code p} stands is foretold there, coming in a minute. */
	static void foretellNow(ServerLevel level, ServerPlayer p) {
		BlockPos at = p.blockPosition();
		String kind = Earthquakes.mountainous(level, at) && level.getRandom().nextBoolean() ? "quake" : suits(level, at, level.getRandom());
		FORECASTS.add(new Forecast(kind, at, level.getGameTime() + 1200L));
	}

	/** Foretold disasters whose time has come. */
	private static void forecasts(ServerLevel level, long now) {
		for (java.util.Iterator<Forecast> it = FORECASTS.iterator(); it.hasNext(); ) {
			Forecast f = it.next();
			if (now < f.when()) {
				continue;
			}
			it.remove();
			if (harvestMoon(level)) {
				continue;
			}
			ServerPlayer near = null;
			double best = FORECAST_REACH * FORECAST_REACH;
			for (ServerPlayer p : level.players()) {
				double d = p.blockPosition().distSqr(f.at());
				if (!p.isSpectator() && d < best) {
					best = d;
					near = p;
				}
			}
			if (near != null) {
				start(level, near, f.kind(), level.getRandom());
			}
		}
	}

	/** (Stage 3) What's foretold within {@link #FORETOLD_RANGE} of {@code pos}, in words (empty: nothing). */
	public static List<Component> foretold(ServerLevel level, BlockPos pos) {
		List<Component> lines = new ArrayList<>();
		long now = level.getGameTime();
		for (Forecast f : FORECASTS) {
			if (f.at().distSqr(pos) > FORETOLD_RANGE * FORETOLD_RANGE) {
				continue;
			}
			long hours = Math.max(1L, Math.round((f.when() - now) / 1000.0));
			int blocks = (int)Math.round(Math.sqrt(f.at().distSqr(pos)));
			Component where = blocks < 64 ? Component.translatable("disaster.jaymoskingdoms.forecast.here")
				: Component.translatable("disaster.jaymoskingdoms.forecast.there", Storms.direction(pos.getX(), pos.getZ(), f.at().getX(), f.at().getZ()), blocks);
			lines.add(Component.translatable("disaster.jaymoskingdoms.forecast." + f.kind(), where, hours));
		}
		return lines;
	}

	/** (Stage 3) Tonight's moon. */
	static boolean bloodMoon(ServerLevel level) {
		com.jaymoskingdoms.Moons.Moon moon = com.jaymoskingdoms.Moons.now(level.getServer());
		return moon == com.jaymoskingdoms.Moons.Moon.BLOOD || moon == com.jaymoskingdoms.Moons.Moon.SUPER_BLOOD;
	}

	/** (Stage 3) A Harvest Moon is up: no disaster comes (stage 1's supercells wait too). */
	public static boolean harvestMoon(ServerLevel level) {
		return com.jaymoskingdoms.Moons.now(level.getServer()) == com.jaymoskingdoms.Moons.Moon.HARVEST;
	}

	/** What the land at {@code pos} brews: a sandstorm on sand, a blizzard in snow, a wildfire where it's dry, else hail. */
	static String suits(ServerLevel level, BlockPos pos, RandomSource random) {
		Biome biome = level.getBiome(pos).value();
		if (sandy(level, pos, random)) {
			return "sandstorm";
		}
		if (biome.hasPrecipitation() && biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
			return "blizzard";
		}
		if (!biome.hasPrecipitation()) {
			return "wildfire";
		}
		return "hail";
	}

	/** Starts a {@code kind} ({@code sandstorm}, {@code blizzard}, {@code wildfire}, {@code flood}, {@code hail}) by
	 * {@code target}. Whether it could (a flood needs water near, a wildfire needs block damage on). */
	static boolean start(ServerLevel level, ServerPlayer target, String kind, RandomSource random) {
		switch (kind) {
			case "quake" -> {
				Calamity c = Earthquakes.start(level, target.position(), 0, random);
				if (c != null) {
					com.jaymoskingdoms.DisasterRelief.struck(level, c.x, c.z, c.radius, "quake", Earthquakes.magnitude(c));
				}
				return c != null;
			}
			case "sandstorm" -> {
				return land(level, target.position(), Calamity.SANDSTORM, random) != null;
			}
			case "blizzard" -> {
				return land(level, target.position(), Calamity.BLIZZARD, random) != null;
			}
			case "wildfire" -> {
				return wildfire(level, target.position(), random);
			}
			case "flood" -> {
				return flood(level, target.blockPosition(), random);
			}
			default -> {
				// a thunderstorm comes in upwind, carrying hail
				Storm s = Storms.form(level, target.position(), Storm.THUNDER, random, true);
				HAIL_ROLLED.put(s.id, true);
				return true;
			}
		}
	}

	private static @Nullable ServerPlayer someone(ServerLevel level, RandomSource random) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
	}

	/** Most of the ground round {@code pos} is sand. */
	private static boolean sandy(ServerLevel level, BlockPos pos, RandomSource random) {
		int sand = 0;
		for (int i = 0; i < 7; i++) {
			BlockPos at = i == 0 ? pos : pos.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
			if (level.getBlockState(top).is(BlockTags.SAND)) {
				sand++;
			}
		}
		return sand >= 4;
	}

	/** A sandstorm or blizzard rolls in over {@code near} from upwind. */
	private static @Nullable Calamity land(ServerLevel level, Vec3 near, int kind, RandomSource random) {
		if (SERVER.size() >= MOST) {
			return null;
		}
		double wx = Math.cos(Storms.windAngle), wz = Math.sin(Storms.windAngle);
		float radius = Mth.lerp(random.nextFloat(), LAND_RADIUS_MIN, LAND_RADIUS_MAX);
		double back = radius * 0.5;
		float speed = Storms.windSpeed * LAND_DRIFT;
		Calamity c = new Calamity(Storms.nextId(), kind, near.x - wx * back, near.z - wz * back, wx * speed, wz * speed, radius, 0,
			Mth.nextInt(random, LAND_LIFE_MIN, LAND_LIFE_MAX), -1);
		SERVER.add(c);
		news(level, c.x, c.z, "disaster.jaymoskingdoms." + Calamity.NAMES[kind]);
		com.jaymoskingdoms.DisasterRelief.struck(level, near.x, near.z, radius, Calamity.NAMES[kind], 1);
		return c;
	}

	/** Players near (x, z) hear of it ({@code key} takes the direction it lies in). */
	private static void news(ServerLevel level, double x, double z, String key) {
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(x, p.getY(), z) < NEWS_RANGE * NEWS_RANGE) {
				p.sendSystemMessage(Component.translatable(key, Storms.direction(p.getX(), p.getZ(), x, z)));
			}
		}
	}

	private static @Nullable Storm storm(int id) {
		for (Storm s : Storms.SERVER) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	/** A thunderstorm over someone, built up, may let down hail (once); one sent in for hail always does. */
	private static void hailFromStorms(ServerLevel level, RandomSource random) {
		HAIL_ROLLED.keySet().removeIf(id -> storm(id) == null);
		for (Storm s : Storms.SERVER) {
			if (s.kind == Storm.RAIN || s.strength() < 0.8F || Boolean.FALSE.equals(HAIL_ROLLED.get(s.id))) {
				continue;
			}
			boolean over = false;
			for (ServerPlayer p : level.players()) {
				if (s.over(p.getX(), p.getZ()) > 0.5F) {
					over = true;
					break;
				}
			}
			if (!over) {
				continue;
			}
			boolean sent = Boolean.TRUE.equals(HAIL_ROLLED.get(s.id));
			HAIL_ROLLED.put(s.id, false);
			float odds = s.kind == Storm.SUPERCELL ? HAIL_SUPERCELL : HAIL_THUNDER;
			if ((sent || random.nextFloat() < odds) && SERVER.size() < MOST) {
				SERVER.add(new Calamity(Storms.nextId(), Calamity.HAIL, s.x, s.z, s.vx, s.vz, s.radius * HAIL_SHARE, 0,
					Mth.nextInt(random, HAIL_LIFE_MIN, HAIL_LIFE_MAX), s.id));
				news(level, s.x, s.z, "disaster.jaymoskingdoms.hail");
				com.jaymoskingdoms.DisasterRelief.struck(level, s.x, s.z, s.radius, "hail", 1);
			}
		}
	}

	/** What a sandstorm, blizzard or hail does to {@code p} and the ground round them. */
	private static void weather(ServerLevel level, ServerPlayer p, RandomSource random, long now, boolean damage) {
		double x = p.getX(), z = p.getZ();
		float sand = cover(level, Calamity.SANDSTORM, x, z), snow = cover(level, Calamity.BLIZZARD, x, z), hail = cover(level, Calamity.HAIL, x, z);
		if (sand <= 0.05F && snow <= 0.05F && hail <= 0.05F) {
			return;
		}
		boolean open = level.canSeeSky(BlockPos.containing(p.getEyePosition())) && !p.isCreative();
		if (sand > 0.3F && open && now % 2 == 0 && !p.getAbilities().flying) {
			double push = SAND_PUSH * 2.0 * sand;
			p.push(Math.cos(Storms.windAngle) * push, 0.0, Math.sin(Storms.windAngle) * push);
			p.syncVelocity = true;
		}
		if (snow > 0.3F) {
			if (now % 20 == 0) {
				warmth(level, p);
			}
			if (open && p.canFreeze() && !WARM.contains(p.getUUID())) {
				int add = FREEZE_ADD + (now % 2 == 0 ? 1 : 0);
				p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze(), p.getTicksFrozen() + add));
			}
		}
		if (hail > 0.4F && open && now % 10 == 0 && random.nextFloat() < HAIL_STING * hail) {
			if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HAIL));
				p.hurtServer(level, source, HAIL_DAMAGE);
			} else {
				level.playSound(null, p.blockPosition().above(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.WEATHER, 0.6F, 1.6F);
			}
		}
		if (!damage) {
			return;
		}
		if (sand > 0.3F && random.nextFloat() < PILE_CHANCE * sand) {
			pile(level, p.blockPosition(), random, Calamity.SANDSTORM);
		}
		if (snow > 0.3F && random.nextFloat() < PILE_CHANCE * snow) {
			pile(level, p.blockPosition(), random, Calamity.BLIZZARD);
		}
		if (hail > 0.4F) {
			batter(level, p.blockPosition(), random);
		}
	}

	/** Whether a fire, campfire or lava is close by {@code p}. */
	private static void warmth(ServerLevel level, ServerPlayer p) {
		BlockPos at = p.blockPosition();
		boolean warm = BlockPos.betweenClosedStream(at.offset(-WARM_RANGE, -2, -WARM_RANGE), at.offset(WARM_RANGE, 2, WARM_RANGE))
			.anyMatch(q -> {
				BlockState s = level.getBlockState(q);
				return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.LAVA);
			});
		if (warm) {
			WARM.add(p.getUUID());
		} else {
			WARM.remove(p.getUUID());
		}
	}

	/** A layer of sand (on sand-coloured ground, red sand on red) or snow settles somewhere near {@code near}. */
	private static void pile(ServerLevel level, BlockPos near, RandomSource random, int kind) {
		int x = near.getX() + random.nextInt(PILE_RANGE * 2 + 1) - PILE_RANGE, z = near.getZ() + random.nextInt(PILE_RANGE * 2 + 1) - PILE_RANGE;
		BlockPos column = new BlockPos(x, near.getY(), z);
		if (!level.isLoaded(column) || cover(level, kind, x + 0.5, z + 0.5) < 0.3F) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		BlockState here = level.getBlockState(top);
		BlockState under = level.getBlockState(top.below());
		Block layer;
		int deepest;
		if (kind == Calamity.SANDSTORM) {
			layer = under.is(Blocks.RED_SAND) || under.is(Disasters.RED_SAND_LAYER) || under.is(Blocks.TERRACOTTA)
				|| under.is(BlockTags.TERRACOTTA) ? Disasters.RED_SAND_LAYER : Disasters.SAND_LAYER;
			deepest = SAND_DEEPEST;
		} else {
			if (!level.getBiome(top).value().coldEnoughToSnow(top, level.getSeaLevel())) {
				return;
			}
			layer = Blocks.SNOW;
			deepest = SNOW_DEEPEST;
		}
		if (here.is(layer)) {
			int layers = here.getValue(SnowLayerBlock.LAYERS);
			if (layers < deepest) {
				level.setBlock(top, here.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
			}
			return;
		}
		// on top of the layer below, if it's a part-filled one of these
		if (under.is(layer)) {
			int layers = under.getValue(SnowLayerBlock.LAYERS);
			if (layers < deepest) {
				level.setBlock(top.below(), under.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
			}
			return;
		}
		BlockState fresh = layer.defaultBlockState();
		if ((here.isAir() || here.is(BlockTags.REPLACEABLE) && !here.is(Blocks.WATER)) && here.getFluidState().isEmpty()
			&& fresh.canSurvive(level, top)) {
			level.setBlock(top, fresh, Block.UPDATE_ALL);
		}
	}

	/** Hail on the open ground somewhere near {@code near}: glass may crack and break, crops get beaten down. */
	private static void batter(ServerLevel level, BlockPos near, RandomSource random) {
		int x = near.getX() + random.nextInt(65) - 32, z = near.getZ() + random.nextInt(65) - 32;
		BlockPos column = new BlockPos(x, near.getY(), z);
		if (!level.isLoaded(column) || cover(level, Calamity.HAIL, x + 0.5, z + 0.5) < 0.4F) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		BlockState plant = level.getBlockState(top);
		if (plant.is(BlockTags.CROPS) && random.nextFloat() < HAIL_CROP) {
			level.destroyBlock(top, true);
			return;
		}
		BlockState roof = level.getBlockState(top.below());
		if ((roof.is(ConventionalBlockTags.GLASS_BLOCKS) || roof.is(ConventionalBlockTags.GLASS_PANES)) && random.nextFloat() < HAIL_GLASS) {
			level.destroyBlock(top.below(), false);
		}
	}

	// ---- wildfires --------------------------------------------------------------------------------------------------

	/** Dry lightning strikes upwind of {@code near}, and a wildfire starts there. */
	private static boolean wildfire(ServerLevel level, Vec3 near, RandomSource random) {
		if (FIRES.size() >= MOST_FIRES || !level.getGameRules().get(Disasters.DISASTER_DAMAGE)) {
			return false;
		}
		double a = Storms.windAngle + Math.PI + (random.nextDouble() - 0.5) * 1.6;
		double d = Mth.lerp(random.nextDouble(), FIRE_MIN, FIRE_MAX);
		BlockPos column = BlockPos.containing(near.x + Math.cos(a) * d, near.y, near.z + Math.sin(a) * d);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.snapTo(Vec3.atBottomCenterOf(top));
			level.addFreshEntity(bolt);
		}
		Wildfire fire = new Wildfire(top.getX() + 0.5, top.getZ() + 0.5, Mth.nextInt(level.getRandom(), FIRE_LIFE_MIN, FIRE_LIFE_MAX));
		// it catches where the bolt struck, or on the dry grass close by
		for (int i = 0; i < 12 && fire.front.isEmpty(); i++) {
			BlockPos at = i == 0 ? top : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				top.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			fire.ignite(level, at);
		}
		if (fire.front.isEmpty()) {
			return false;
		}
		FIRES.add(fire);
		news(level, fire.x, fire.z, "disaster.jaymoskingdoms.wildfire");
		com.jaymoskingdoms.DisasterRelief.struck(level, fire.x, fire.z, FIRE_REACH, "wildfire", 1);
		return true;
	}

	/** A wildfire: a front of burning spots that keeps creeping downwind, lighting the dry grass ahead of it. */
	private static final class Wildfire {
		final double x, z;
		final int life;
		int age;
		final ArrayDeque<BlockPos> front = new ArrayDeque<>();

		Wildfire(double x, double z, int life) {
			this.x = x;
			this.z = z;
			this.life = life;
		}

		boolean near(BlockPos pos, double range) {
			for (BlockPos f : front) {
				if (f.closerThan(pos, range)) {
					return true;
				}
			}
			return false;
		}

		/** Sets fire at {@code pos} if it can burn there (grass, plants, wood underfoot). */
		boolean ignite(ServerLevel level, BlockPos pos) {
			BlockState here = level.getBlockState(pos);
			if (!(here.isAir() || here.is(BlockTags.REPLACEABLE)) || !here.getFluidState().isEmpty() || level.isRainingAt(pos)) {
				return false;
			}
			BlockState under = level.getBlockState(pos.below());
			if (!under.is(BlockTags.DIRT) && !under.ignitedByLava()) {
				return false;
			}
			BlockState fire = BaseFireBlock.getState(level, pos);
			if (!fire.canSurvive(level, pos)) {
				return false;
			}
			level.setBlock(pos, fire, Block.UPDATE_ALL);
			front.addLast(pos.immutable());
			while (front.size() > FIRE_FRONT) {
				front.removeFirst();
			}
			return true;
		}

		/** One tick; false when it's burnt out. */
		boolean tick(ServerLevel level, RandomSource random, long now, boolean damage) {
			if (++age >= life || front.isEmpty() || !damage) {
				return false;
			}
			if (now % FIRE_SPREAD_EVERY == 0) {
				List<BlockPos> from = new ArrayList<>(front);
				double wx = Math.cos(Storms.windAngle), wz = Math.sin(Storms.windAngle);
				for (int i = 0; i < FIRE_SPREAD; i++) {
					BlockPos f = from.get(random.nextInt(from.size()));
					double ahead = 1.0 + random.nextDouble() * 3.0, side = (random.nextDouble() - 0.5) * 5.0;
					double nx = f.getX() + 0.5 + wx * ahead - wz * side, nz = f.getZ() + 0.5 + wz * ahead + wx * side;
					if ((nx - x) * (nx - x) + (nz - z) * (nz - z) > FIRE_REACH * FIRE_REACH) {
						continue;
					}
					BlockPos column = BlockPos.containing(nx, f.getY(), nz);
					if (level.isLoaded(column)) {
						ignite(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
					}
				}
			}
			if (now % 20 == 0) {
				// tall smoke over it, seen from far off
				int puffs = 0;
				for (BlockPos f : front) {
					if (puffs >= 6) {
						break;
					}
					if (random.nextInt(4) == 0) {
						level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, f.getX() + 0.5, f.getY() + 0.5, f.getZ() + 0.5, 2, 0.6, 0.3, 0.6, 0.01);
						puffs++;
					}
				}
				front.removeIf(f -> level.isLoaded(f) && !level.getBlockState(f).is(BlockTags.FIRE) && random.nextInt(3) == 0);
			}
			return true;
		}
	}

	// ---- floods -----------------------------------------------------------------------------------------------------

	/** Tallies the rain on each player; after long enough, a flood near one may rise. */
	private static void rain(ServerLevel level, RandomSource random) {
		Set<UUID> here = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			here.add(p.getUUID());
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p.blockPosition());
			int rained = RAINED.getOrDefault(p.getUUID(), 0);
			rained = level.isRainingAt(top) ? rained + ROLL_TICKS : Math.max(0, rained - ROLL_TICKS * 2);
			if (rained >= RAIN_BEFORE_FLOOD && random.nextFloat() < FLOOD_CHANCE && level.getGameRules().get(Disasters.DISASTER_DAYS) > 0
				&& flood(level, p.blockPosition(), random)) {
				rained = 0;
			}
			RAINED.put(p.getUUID(), rained);
		}
		RAINED.keySet().retainAll(here);
	}

	/** The river or lake nearest {@code near} rises. Whether there was one. */
	private static boolean flood(ServerLevel level, BlockPos near, RandomSource random) {
		if (FLOODS.size() >= MOST_FLOODS) {
			return false;
		}
		for (Flood f : FLOODS) {
			if (f.center.closerThan(near, FLOOD_RADIUS * 2.0)) {
				return false;
			}
		}
		// the water's surface: the most common height among the water columns round about
		Map<Integer, List<BlockPos>> byHeight = new HashMap<>();
		for (int i = 0; i < 500; i++) {
			BlockPos column = near.offset(random.nextInt(FLOOD_LOOK * 2 + 1) - FLOOD_LOOK, 0, random.nextInt(FLOOD_LOOK * 2 + 1) - FLOOD_LOOK);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
			if (level.getFluidState(top).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)
				&& top.getY() >= level.getSeaLevel() - 3 && top.getY() <= level.getSeaLevel() + 48) {
				byHeight.computeIfAbsent(top.getY(), y -> new ArrayList<>()).add(top);
			}
		}
		List<BlockPos> best = null;
		for (List<BlockPos> l : byHeight.values()) {
			if (best == null || l.size() > best.size()) {
				best = l;
			}
		}
		if (best == null || best.size() < FLOOD_WATER) {
			return false;
		}
		BlockPos center = best.get(0);
		double bestD = Double.MAX_VALUE;
		for (BlockPos b : best) {
			double d = b.distSqr(near);
			if (d < bestD) {
				bestD = d;
				center = b;
			}
		}
		Flood flood = new Flood(center, level.getGameRules().get(Disasters.DISASTER_DAMAGE));
		FLOODS.add(flood);
		news(level, center.getX(), center.getZ(), "disaster.jaymoskingdoms.flood");
		com.jaymoskingdoms.DisasterRelief.struck(level, center.getX(), center.getZ(), FLOOD_RADIUS + 20.0, "flood", 1);
		return true;
	}

	/**
	 * A flood: the water at {@link #center}'s height rises {@link #FLOOD_RISE} blocks, a layer at a time, over the low
	 * ground connected to it, stands a while, then drains away (every block it put down taken back out, last first).
	 */
	private static final class Flood {
		final BlockPos center;
		final boolean damage;
		/** The blocks it has put down (in order), and the next layer's still to put down. */
		final List<BlockPos> placed = new ArrayList<>();
		final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		/** The layer it's filling (1..{@link #FLOOD_RISE}), and the last layer's blocks (where the next one starts). */
		int layer;
		List<BlockPos> last = new ArrayList<>();
		int hold = FLOOD_HOLD;
		boolean draining;

		Flood(BlockPos center, boolean damage) {
			this.center = center.immutable();
			this.damage = damage;
		}

		/** One tick; false when it has drained. */
		boolean tick(ServerLevel level) {
			if (draining) {
				for (int i = 0; i < FLOOD_PER_TICK && !placed.isEmpty(); i++) {
					drain(level, placed.remove(placed.size() - 1));
				}
				return !placed.isEmpty();
			}
			if (pending.isEmpty()) {
				if (layer < FLOOD_RISE && placed.size() < FLOOD_MOST) {
					nextLayer(level);
				}
				if (pending.isEmpty() && --hold <= 0) {
					draining = true;
				}
				return true;
			}
			for (int i = 0; i < FLOOD_PER_TICK && !pending.isEmpty(); i++) {
				BlockPos pos = pending.removeFirst();
				if (!level.isLoaded(pos) || !floodable(level, pos)) {
					continue;
				}
				if (!level.getBlockState(pos).isAir()) {
					level.destroyBlock(pos, true);
				}
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				placed.add(pos);
				if (i == 0 && level.getRandom().nextInt(6) == 0) {
					level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.8F);
				}
			}
			return true;
		}

		/** The next layer: every open spot one up from the water, spreading over the flat ground beside it. */
		private void nextLayer(ServerLevel level) {
			layer++;
			int y = center.getY() + layer;
			ArrayDeque<BlockPos> open = new ArrayDeque<>();
			Set<BlockPos> seen = new HashSet<>();
			List<BlockPos> seeds = new ArrayList<>();
			if (layer == 1) {
				// the water's own surface, round about
				for (int dx = -FLOOD_RADIUS; dx <= FLOOD_RADIUS; dx += 1) {
					for (int dz = -FLOOD_RADIUS; dz <= FLOOD_RADIUS; dz += 1) {
						if (dx * dx + dz * dz > FLOOD_RADIUS * FLOOD_RADIUS) {
							continue;
						}
						BlockPos below = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
						if (level.isLoaded(below) && level.getFluidState(below).is(FluidTags.WATER)) {
							seeds.add(below.above());
						}
					}
				}
			} else {
				for (BlockPos p : last) {
					seeds.add(p.above());
				}
			}
			for (BlockPos s : seeds) {
				if (seen.add(s)) {
					open.add(s);
				}
			}
			List<BlockPos> fill = new ArrayList<>();
			while (!open.isEmpty() && placed.size() + fill.size() < FLOOD_MOST) {
				BlockPos p = open.removeFirst();
				if (!level.isLoaded(p) || !floodable(level, p) || !held(level, p.below())) {
					continue;
				}
				fill.add(p);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = p.relative(d);
					int dx = n.getX() - center.getX(), dz = n.getZ() - center.getZ();
					if (dx * dx + dz * dz <= FLOOD_RADIUS * FLOOD_RADIUS && n.getY() == y && seen.add(n)) {
						open.add(n);
					}
				}
			}
			pending.addAll(fill);
			last = fill;
		}

		/** Water may go here: air, or (if disasters may break things) grass, flowers and crops. */
		private boolean floodable(ServerLevel level, BlockPos pos) {
			BlockState s = level.getBlockState(pos);
			if (s.isAir()) {
				return true;
			}
			return damage && s.getFluidState().isEmpty() && (s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.CROPS)
				|| s.is(BlockTags.SMALL_FLOWERS)) && !s.is(BlockTags.FIRE);
		}

		/** What's under {@code pos} holds water up: water, or a solid top. */
		private static boolean held(ServerLevel level, BlockPos pos) {
			return level.getFluidState(pos).is(FluidTags.WATER) || level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
		}

		private static void drain(ServerLevel level, BlockPos pos) {
			if (level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}

		/** Everything it put down goes, at once (the server's stopping, or an op cleared the skies). */
		void drainAll(ServerLevel level) {
			pending.clear();
			for (int i = placed.size() - 1; i >= 0; i--) {
				drain(level, placed.get(i));
			}
			placed.clear();
		}
	}

	// ---- the network ------------------------------------------------------------------------------------------------

	/** Server → client: every sandstorm, blizzard and hail shower in the Overworld, a few times a second. */
	public record Sync(List<Calamity> calamities) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("calamities"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.calamities.size());
				for (Calamity c : p.calamities) {
					c.write(buf);
				}
			},
			buf -> {
				int n = buf.readVarInt();
				List<Calamity> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					list.add(Calamity.read(buf));
				}
				return new Sync(list);
			});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}
}
