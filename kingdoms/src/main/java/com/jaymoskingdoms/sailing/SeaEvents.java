package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.disasters.Tornado;
import com.jaymoskingdoms.disasters.Tornadoes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The open sea's own weather and wonders, for whoever is out on it (in an ocean, near the water), on top of the storm
 * cells and tornadoes of {@link Storms}:
 * <ul>
 * <li>Fog banks: a wall of grey rolls over the water and drifts with the wind; inside, you see a few dozen blocks.</li>
 * <li>Doldrums: a patch of dead calm. Sails hang slack; row out, or wait.</li>
 * <li>Squalls: a small, fast rain cell running before the wind, its gusts heeling ships and hurrying them on.</li>
 * <li>Whirlpools (deep water): the sea turns round an eye, pulling in boats, swimmers and ships, dragging swimmers
 * under and pouring into a hold that comes too near the middle.</li>
 * <li>A glowing tide (at night): the water lights up blue-green where anything moves through it.</li>
 * <li>Flotsam: wreckage drifting by, from some ship the sea took: planks, and what she carried.</li>
 * <li>St. Elmo's fire: under a thunderstorm, a blue flame crackles on a ship's mastheads.</li>
 * <li>Waterspouts: a tornado over the sea is one ({@link Tornadoes}); here they spin ships round, pour green water into
 * their holds and tear away sails, and on a warm still day the sea puts up small ones of its own.</li>
 * </ul>
 * Like the storms, they live only in memory: after a restart the sea is quiet.
 */
public final class SeaEvents {
	public enum Kind {
		FOG, CALM, SQUALL, WHIRLPOOL, GLOW;

		final String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** How often we think about the sea's weather (ticks), and on average how many days apart each comes to a sailor. */
	static final int ROLL_TICKS = 400;
	static final double FOG_DAYS = 1.2, CALM_DAYS = 2.0, SQUALL_DAYS = 1.0, WHIRLPOOL_DAYS = 3.0, GLOW_DAYS = 1.0, FLOTSAM_DAYS = 0.6,
		SPOUT_DAYS_WARM = 2.0, SPOUT_DAYS = 6.0;
	/** By kind: radius (least, most) and life (ticks, least, most). */
	static final float[] RADIUS_MIN = {90.0F, 150.0F, 35.0F, 12.0F, 60.0F}, RADIUS_MAX = {160.0F, 260.0F, 55.0F, 20.0F, 110.0F};
	static final int[] LIFE_MIN = {3000, 4000, 1800, 2400, 4000}, LIFE_MAX = {7000, 8000, 3000, 4800, 9000};
	/** How long each takes to come on and to pass (ticks). */
	static final int FADE = 600;
	/** At most this many about one sailor at once, and how far off they form (blocks). */
	static final int MOST_NEAR = 3;
	static final double NEAR = 500.0, FORM_MIN = 20.0, FORM_MAX = 140.0;
	/** Out at sea: in an ocean, this near the water's surface. */
	static final double SEA_HEIGHT = 12.0;
	/** Doldrums: what's left of the wind in the heart of one; a squall: how much harder it blows, and its gusts' shove. */
	static final float CALM_LEFT = 0.08F, SQUALL_EXTRA = 0.8F;
	static final double GUST = 0.18, GUST_PLAYER = 0.12;
	/** A whirlpool's pull and spin (blocks/tick at its eye), how near the eye swimmers are dragged under, water shipped there. */
	static final double WHIRL_IN = 0.025, WHIRL_SPIN = 0.07, WHIRL_DOWN = 0.07, WHIRL_EYE = 3.5;
	static final int WHIRL_FLOOD = 1;
	/** A waterspout on a ship: how hard its winds swing her (scaled by size), water shipped each second in its core, the
	 * chance each 5 seconds it takes a sail (a tornado or monster). */
	static final double SPOUT_TOW = 0.05;
	static final int[] SPOUT_FLOOD = {1, 3, 8};
	static final float SAIL_TEAR = 0.4F;
	/** St. Elmo's fire: this much thunder over her. */
	static final float ELMO_THUNDER = 0.5F;
	/** Flotsam: how far off upwind, how many planks, how much of a lost ship's stores. */
	static final double FLOTSAM_MIN = 24.0, FLOTSAM_MAX = 48.0;

	public static final class Sea {
		public final int id;
		public final Kind kind;
		public double x, z, vx, vz;
		/** The water's surface where it formed. */
		public final int y;
		public final float radius;
		public int age, life;

		public Sea(int id, Kind kind, double x, int y, double z, double vx, double vz, float radius, int age, int life) {
			this.id = id;
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vz = vz;
			this.radius = radius;
			this.age = age;
			this.life = life;
		}

		/** How strong it is at (x, z), 0..1: full inside, eased off over the outer quarter, coming on and passing. */
		public float at(double px, double pz) {
			double d = Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z));
			if (d >= radius) {
				return 0.0F;
			}
			float edge = (float)Math.min(1.0, (radius - d) / (radius * 0.25));
			return edge * this.formed();
		}

		public float formed() {
			return Mth.clamp(Math.min(age, life - age) / (float)FADE, 0.0F, 1.0F);
		}

		public void move() {
			x += vx;
			z += vz;
			age++;
		}

		void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeVarInt(kind.ordinal());
			buf.writeDouble(x);
			buf.writeVarInt(y);
			buf.writeDouble(z);
			buf.writeFloat((float)vx);
			buf.writeFloat((float)vz);
			buf.writeFloat(radius);
			buf.writeVarInt(age);
			buf.writeVarInt(life);
		}

		static Sea read(RegistryFriendlyByteBuf buf) {
			return new Sea(buf.readVarInt(), Kind.values()[Mth.clamp(buf.readVarInt(), 0, Kind.values().length - 1)], buf.readDouble(),
				buf.readVarInt(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
		}
	}

	/** Server → client: the sea's events, a couple of times a second. */
	public record Sync(List<Sea> seas) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("sea_events"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.seas.size());
			for (Sea s : p.seas) {
				s.write(buf);
			}
		}, buf -> {
			int n = buf.readVarInt();
			List<Sea> seas = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				seas.add(Sea.read(buf));
			}
			return new Sync(seas);
		});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	static final List<Sea> SERVER = new ArrayList<>();
	/** The client's copy (set from {@link Sync}, moved on each tick by the client). */
	public static volatile List<Sea> CLIENT = List.of();
	private static int nextId = 1;

	private SeaEvents() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(SeaEvents::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER.clear());
	}

	// ---- both sides ----------------------------------------------------------------------------------------------

	/** How much of the wind a ship's sails get at (x, z): nearly none in the doldrums, more in a squall. */
	public static float wind(Level level, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return 1.0F;
		}
		float calm = 0.0F, squall = 0.0F;
		for (Sea s : level.isClientSide() ? CLIENT : SERVER) {
			if (s.kind == Kind.CALM) {
				calm = Math.max(calm, s.at(x, z));
			} else if (s.kind == Kind.SQUALL) {
				squall = Math.max(squall, s.at(x, z));
			}
		}
		return Mth.lerp(calm, 1.0F, CALM_LEFT) + SQUALL_EXTRA * squall;
	}

	/** How thick the sea fog is at (x, z), 0..1 (the client asks, for its fog). */
	public static float fog(Level level, double x, double z) {
		return most(level, Kind.FOG, x, z);
	}

	/** How strong the strongest event of {@code kind} is at (x, z), 0..1. */
	public static float most(Level level, Kind kind, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		float best = 0.0F;
		for (Sea s : level.isClientSide() ? CLIENT : SERVER) {
			if (s.kind == kind) {
				best = Math.max(best, s.at(x, z));
			}
		}
		return best;
	}

	// ---- the server ----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (Sea s : SERVER) {
			s.move();
		}
		SERVER.removeIf(s -> s.age >= s.life);
		if (now % ROLL_TICKS == 0) {
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && atSea(level, p.position())) {
					roll(level, p, random);
				}
			}
		}
		if (!SERVER.isEmpty()) {
			whirl(level, now);
			if (now % 20 == 0) {
				gusts(level, random);
			}
		}
		spouts(level, now, random);
		if (now % 10 == 0) {
			elmo(level, random);
			Sync sync = new Sync(List.copyOf(SERVER));
			for (ServerPlayer p : level.players()) {
				ServerPlayNetworking.send(p, sync);
			}
		}
	}

	/** Whether {@code at} is out at sea: over an ocean, near the water's surface. */
	static boolean atSea(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		return Math.abs(at.y - level.getSeaLevel()) < SEA_HEIGHT && level.getBiome(pos).is(BiomeTags.IS_OCEAN);
	}

	private static void roll(ServerLevel level, ServerPlayer p, RandomSource random) {
		int near = 0;
		for (Sea s : SERVER) {
			if (p.position().distanceToSqr(s.x, p.getY(), s.z) < NEAR * NEAR) {
				near++;
			}
		}
		Holder<Biome> biome = level.getBiome(p.blockPosition());
		boolean night = !level.isBrightOutside();
		boolean deep = biome.is(BiomeTags.IS_DEEP_OCEAN);
		boolean warm = biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN);
		boolean stormy = Storms.cover(level, p.getX(), p.getZ()) > 0.2F;
		if (chance(random, FLOTSAM_DAYS)) {
			flotsam(level, p, random);
		}
		if (!stormy && chance(random, warm ? SPOUT_DAYS_WARM : SPOUT_DAYS)) {
			double a = random.nextDouble() * Math.PI * 2.0, d = Mth.lerp(random.nextDouble(), 50.0, 90.0);
			Tornadoes.waterspout(level, p.getX() + Math.cos(a) * d, p.getZ() + Math.sin(a) * d, random);
		}
		if (near >= MOST_NEAR) {
			return;
		}
		Kind kind = null;
		if (chance(random, FOG_DAYS * (night ? 0.6 : 1.4))) {
			kind = Kind.FOG;
		} else if (!stormy && chance(random, CALM_DAYS)) {
			kind = Kind.CALM;
		} else if (chance(random, SQUALL_DAYS)) {
			kind = Kind.SQUALL;
		} else if (deep && chance(random, WHIRLPOOL_DAYS)) {
			kind = Kind.WHIRLPOOL;
		} else if (night && chance(random, GLOW_DAYS * 0.5)) {
			kind = Kind.GLOW;
		}
		if (kind != null) {
			form(level, p.position(), kind, random);
		}
	}

	private static boolean chance(RandomSource random, double days) {
		return random.nextDouble() < ROLL_TICKS / (days * 24000.0);
	}

	/** An event of {@code kind} forms near {@code near}, out on the water. Null if there's no open sea there. */
	static @Nullable Sea form(ServerLevel level, Vec3 near, Kind kind, RandomSource random) {
		float radius = Mth.lerp(random.nextFloat(), RADIUS_MIN[kind.ordinal()], RADIUS_MAX[kind.ordinal()]);
		int life = Mth.nextInt(random, LIFE_MIN[kind.ordinal()], LIFE_MAX[kind.ordinal()]);
		float wind = Storms.windAngle();
		double wx = Math.cos(wind), wz = Math.sin(wind);
		double x, z;
		if (kind == Kind.FOG || kind == Kind.SQUALL) {
			// upwind, so it comes down on them
			double back = kind == Kind.FOG ? radius * 0.6 : FORM_MAX;
			x = near.x - wx * back;
			z = near.z - wz * back;
		} else {
			double a = random.nextDouble() * Math.PI * 2.0, d = Mth.lerp(random.nextDouble(), FORM_MIN, kind == Kind.WHIRLPOOL ? FORM_MAX : radius * 0.5);
			x = near.x + Math.cos(a) * d;
			z = near.z + Math.sin(a) * d;
		}
		BlockPos column = BlockPos.containing(x, level.getMinY(), z);
		if (!level.isLoaded(column) || !level.getBiome(column.atY(level.getSeaLevel())).is(BiomeTags.IS_OCEAN)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
		if (!level.getFluidState(column.atY(y - 1)).is(FluidTags.WATER)) {
			return null;
		}
		// a fog bank drifts slowly with the wind; the rest keep their place (a squall's set from its cell below)
		double drift = kind == Kind.FOG ? 0.5 : 0.0;
		float speed = Storms.windStrength() * 0.035F + 0.045F;
		Sea s = new Sea(nextId++, kind, x, y, z, wx * speed * drift, wz * speed * drift, radius, 0, life);
		if (kind == Kind.SQUALL) {
			com.jaymoskingdoms.disasters.Storm cell = Storms.squall(level, x, z, radius, life, random);
			if (cell == null) {
				return null;
			}
			s.vx = cell.vx;
			s.vz = cell.vz;
		}
		SERVER.add(s);
		return s;
	}

	/** A whirlpool turns everything afloat near it round its eye and draws it in; at the eye, swimmers go under. */
	private static void whirl(ServerLevel level, long now) {
		for (Sea s : SERVER) {
			if (s.kind != Kind.WHIRLPOOL) {
				continue;
			}
			float f = s.formed();
			AABB box = new AABB(s.x - s.radius, s.y - 6.0, s.z - s.radius, s.x + s.radius, s.y + 3.0, s.z + s.radius);
			for (Entity e : level.getEntities((Entity)null, box, e -> e.isAlive() && !e.isSpectator() && !e.isPassenger()
				&& !(e instanceof Ship) && !(e instanceof Player p && (p.isCreative() || p.getAbilities().flying)) && (e.isInWater()
					|| e instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat))) {
				double dx = e.getX() - s.x, dz = e.getZ() - s.z, d = Math.sqrt(dx * dx + dz * dz);
				if (d > s.radius || d < 1.0E-3) {
					continue;
				}
				double reach = (1.0 - d / s.radius) * f, nx = dx / d, nz = dz / d;
				Vec3 v = e.getDeltaMovement().add(-nx * WHIRL_IN * reach - nz * WHIRL_SPIN * reach, 0.0, -nz * WHIRL_IN * reach + nx * WHIRL_SPIN * reach);
				if (d < WHIRL_EYE && e.isInWater()) {
					v = v.add(0.0, -WHIRL_DOWN * f, 0.0);
				}
				e.setDeltaMovement(v);
				e.syncVelocity = true;
			}
			for (Ship ship : Ships.in(level)) {
				double dx = ship.getX() - s.x, dz = ship.getZ() - s.z, d = Math.sqrt(dx * dx + dz * dz);
				if (d > s.radius || d < 1.0E-3) {
					continue;
				}
				double reach = (1.0 - d / s.radius) * f, nx = dx / d, nz = dz / d;
				ship.tow(new Vec3(-nx * WHIRL_IN * reach - nz * WHIRL_SPIN * reach, 0.0, -nz * WHIRL_IN * reach + nx * WHIRL_SPIN * reach));
				if (d < WHIRL_EYE + 2.0 && now % 20 == 0) {
					ship.shipWater(WHIRL_FLOOD);
				}
			}
			if (now % 40 == s.id % 40) {
				level.playSound(null, s.x, s.y, s.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.WEATHER, 4.0F * f, 0.6F);
			}
		}
	}

	/** A squall's gusts: ships under it are shoved on before the wind, and those on their open decks feel it. */
	private static void gusts(ServerLevel level, RandomSource random) {
		float wind = Storms.windAngle();
		double wx = Math.cos(wind), wz = Math.sin(wind);
		for (Sea s : SERVER) {
			if (s.kind != Kind.SQUALL || random.nextInt(3) != 0) {
				continue;
			}
			for (Ship ship : Ships.in(level)) {
				float at = s.at(ship.getX(), ship.getZ());
				if (at <= 0.0F) {
					continue;
				}
				Hull h = ship.hull();
				double sails = h == null ? 0.0 : Math.sqrt(h.sails) * ship.sailSet();
				ship.tow(new Vec3(wx * GUST * at * (0.4 + sails), 0.0, wz * GUST * at * (0.4 + sails)));
				level.playSound(null, ship.getX(), ship.getY() + 4.0, ship.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.WEATHER,
					1.5F * at, 0.45F + random.nextFloat() * 0.1F);
			}
			for (ServerPlayer p : level.players()) {
				float at = s.at(p.getX(), p.getZ());
				if (at > 0.0F && level.canSeeSky(p.blockPosition().above()) && !p.isCreative()) {
					p.setDeltaMovement(p.getDeltaMovement().add(wx * GUST_PLAYER * at, 0.0, wz * GUST_PLAYER * at));
					p.syncVelocity = true;
				}
			}
		}
	}

	/** Waterspouts on ships: her bow swung round, green water poured in, and in a big one her sails torn away. */
	private static void spouts(ServerLevel level, long now, RandomSource random) {
		for (Tornado t : Tornadoes.active()) {
			if (!t.water) {
				continue;
			}
			float f = t.formed();
			for (Ship ship : Ships.in(level)) {
				double dx = ship.getX() - t.x, dz = ship.getZ() - t.z, d = Math.sqrt(dx * dx + dz * dz);
				double pull = t.pull() + (ship.hull() == null ? 0.0 : ship.hull().radius);
				if (d > pull || d < 1.0E-3) {
					continue;
				}
				double reach = (1.0 - d / pull) * f * (1.0 + t.size), nx = dx / d, nz = dz / d;
				ship.tow(new Vec3((-nx * 0.4 - nz) * SPOUT_TOW * reach, 0.0, (-nz * 0.4 + nx) * SPOUT_TOW * reach));
				if (d < t.core() * 1.5 + (ship.hull() == null ? 0.0 : ship.hull().radius * 0.5)) {
					if (now % 20 == 0) {
						ship.shipWater(SPOUT_FLOOD[t.size]);
					}
					if (t.size > Tornado.DUST_DEVIL && now % 100 == 0 && random.nextFloat() < SAIL_TEAR * f && ship.tearSail(level)) {
						level.playSound(null, ship.getX(), ship.getY() + 6.0, ship.getZ(), SoundEvents.WOOL_BREAK, SoundSource.WEATHER, 3.0F, 0.6F);
					}
				}
			}
		}
	}

	/** St. Elmo's fire: under a thunderstorm, blue flame crackling on each ship's mastheads (her highest blocks). */
	private static void elmo(ServerLevel level, RandomSource random) {
		Set<Ship> lit = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			for (Ship ship : Ships.in(level)) {
				if (lit.contains(ship) || ship.distanceToSqr(p) > 160.0 * 160.0 || Storms.thunder(level, ship.getX(), ship.getZ()) < ELMO_THUNDER) {
					continue;
				}
				lit.add(ship);
			}
		}
		for (Ship ship : lit) {
			Hull h = ship.hull();
			if (h == null || h.pieces.isEmpty()) {
				continue;
			}
			int top = Integer.MIN_VALUE;
			for (Ship.Piece piece : h.pieces) {
				top = Math.max(top, piece.y());
			}
			int shown = 0;
			for (Ship.Piece piece : h.pieces) {
				if (piece.y() >= top - 1 && shown < 4 && random.nextInt(2) == 0) {
					shown++;
					Vec3 at = ship.toWorld(piece.x(), piece.y() + 1.05, piece.z());
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.12, 0.05, 0.12, 0.004);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.15, 0.1, 0.15, 0.02);
				}
			}
			if (random.nextInt(6) == 0) {
				Vec3 at = ship.toWorld(0.0, top + 1.0, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 1.2F, 1.7F);
			}
		}
	}

	/** Wreckage drifts down on them from upwind: planks and spars, and what some lost ship carried. */
	private static void flotsam(ServerLevel level, ServerPlayer p, RandomSource random) {
		float wind = Storms.windAngle();
		double back = Mth.lerp(random.nextDouble(), FLOTSAM_MIN, FLOTSAM_MAX), across = (random.nextDouble() - 0.5) * 24.0;
		double wx = Math.cos(wind), wz = Math.sin(wind);
		double x = p.getX() - wx * back - wz * across, z = p.getZ() - wz * back + wx * across;
		BlockPos column = BlockPos.containing(x, level.getMinY(), z);
		if (!level.isLoaded(column)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
		if (!level.getFluidState(column.atY(y - 1)).is(FluidTags.WATER) || !level.getBiome(column.atY(y)).is(BiomeTags.IS_OCEAN)) {
			return;
		}
		List<ItemStack> wreck = new ArrayList<>();
		int planks = 2 + random.nextInt(5);
		wreck.add(new ItemStack(random.nextBoolean() ? Items.OAK_PLANKS : Items.SPRUCE_PLANKS, planks));
		if (random.nextInt(3) == 0) {
			wreck.add(new ItemStack(Items.STICK, 1 + random.nextInt(4)));
		}
		LootTable stores = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SHIPWRECK_SUPPLY);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, new Vec3(x, y, z)).create(LootContextParamSets.CHEST);
		List<ItemStack> rolled = new ArrayList<>(stores.getRandomItems(params));
		net.minecraft.util.Util.shuffle(rolled, random);
		int kept = 2 + random.nextInt(3);
		for (int i = 0; i < rolled.size() && i < kept; i++) {
			wreck.add(rolled.get(i));
		}
		for (ItemStack stack : wreck) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemEntity item = new ItemEntity(level, x + random.nextGaussian() * 2.0, y + 0.2, z + random.nextGaussian() * 2.0, stack);
			item.setDeltaMovement(wx * 0.02, 0.0, wz * 0.02);
			level.addFreshEntity(item);
		}
	}
}
