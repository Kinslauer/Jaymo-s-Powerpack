package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Loaded;
import com.jaymoskingdoms.sailing.Ship;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hurricanes (medieval picks, nd-hurricane). A great storm forms far out at sea and comes in on a coast: a storm cell
 * hundreds of blocks across ({@link Storm}, a supercell to the clients, so its clouds, rain and thunder are the storms'
 * own) round a calm, clear eye ({@link Storm#eye}). In the wall round the eye the wind drags everything round with it and
 * lifts what's light, tears loose leaves, fences, plants and glass and flings them as wreckage, and lightning never stops
 * (seeking the high ground, {@link HighGround}). At sea, ships caught in it are battered and holed, and boats capsize. Once
 * it comes ashore it floods the coast ({@link Calamities#flood}), puts down tornadoes ({@link Tornadoes}), and weakens the
 * further it goes over land, until it breaks up.
 */
public final class Hurricanes {
	/** Its radius and eye (blocks, the radius times its {@link Magnitude#size}; the eye plus a magnitude each), the wall's
	 * width round the eye (plus a magnitude each). */
	static final float RADIUS = 170.0F, EYE = 18.0F, EYE_PER_MAGNITUDE = 4.0F, WALL = 36.0F, WALL_PER_MAGNITUDE = 10.0F;
	/** It forms this far out from the shore it's for (blocks), and comes in this fast (blocks a tick, times its speed). */
	static final double OUT = 420.0, SPEED = 0.07;
	/** Over land it weakens this much a tick (of its strength, 1 at sea), and lasts at most this long after landfall. */
	static final float WEAKEN = 1.0F / 4800.0F;
	static final int LAND_LIFE = 6000;
	/** The wind in the wall: how hard it drags round (blocks a tick², per magnitude), what share pulls inward, the odds a
	 * tick something light is lifted. */
	static final double DRAG = 0.012, INWARD = 0.25;
	static final float LIFT = 0.04F;
	/** Wreckage: tries a tick round each player in the wall, the hardest block it tears loose, how far round, how fast it flies. */
	static final float WRECK_TRIES = 0.35F, WRECK_HARDEST = 1.0F;
	static final int WRECK_RANGE = 20;
	static final double WRECK_SPEED = 0.9;
	/** Lightning in the wall: one this often near someone in it (ticks). */
	static final int BOLT_EVERY = 30;
	/** Tornadoes over land: looked for this often (ticks), with these odds, at most one plus one per two magnitudes. */
	static final int TORNADO_EVERY = 600;
	static final float TORNADO_CHANCE = 0.4F;
	/** At sea: the odds a second a ship in the wall is holed, and a boat capsizes. */
	static final float SHIP_HOLED = 0.08F, BOAT_CAPSIZE = 0.05F;
	/** At most this many at once. */
	static final int MOST = 1;

	private static final class Hurricane {
		final Storm storm;
		final int magnitude;
		final float wall;
		float power = 1.0F;
		boolean landfall;
		int tornadoes;

		Hurricane(Storm storm, int magnitude, float wall) {
			this.storm = storm;
			this.magnitude = magnitude;
			this.wall = wall;
		}

		/** How much of the wall's wind is at (x, z), 0..1. */
		float wind(double x, double z) {
			double d = Math.sqrt((x - storm.x) * (x - storm.x) + (z - storm.z) * (z - storm.z));
			if (d < storm.eye * 0.8 || d > storm.radius) {
				return 0.0F;
			}
			double middle = storm.eye + wall / 2.0;
			float inWall = (float)Mth.clamp(1.0 - Math.abs(d - middle) / (wall / 2.0 + 24.0), 0.0, 1.0);
			// the rest of the storm blows too, less
			return Math.max(inWall, 0.25F) * power * storm.strength();
		}
	}

	private static final List<Hurricane> ACTIVE = new ArrayList<>();

	private Hurricanes() {
	}

	static void clear() {
		ACTIVE.clear();
	}

	/** A hurricane of {@code m} (0: by chance, at least severe) forms out at sea off the coast nearest {@code near}. */
	static @Nullable Storm start(ServerLevel level, Vec3 near, int m) {
		if (ACTIVE.size() >= MOST) {
			return null;
		}
		Tsunamis.Coast coast = Tsunamis.coastNear(level, BlockPos.containing(near));
		if (coast == null) {
			return null;
		}
		if (m <= 0) {
			m = Magnitude.roll(level, level.getRandom(), Magnitude.SEVERE);
		}
		m = Magnitude.clamp(m);
		Vec3 from = coast.sea().subtract(coast.inland().scale(OUT));
		double speed = SPEED * Magnitude.speed(m);
		float radius = RADIUS * Magnitude.size(m);
		int life = (int)((OUT + radius) * 2.0 / speed) + Storms.GATHER_TICKS;
		Storm s = new Storm(Storms.nextId(), Storm.SUPERCELL, from.x, from.z, coast.inland().x * speed, coast.inland().z * speed, radius, 0, life);
		s.eye = EYE + EYE_PER_MAGNITUDE * m;
		// its own tornadoes, not a supercell's
		s.spawned = true;
		Storms.SERVER.add(s);
		ACTIVE.add(new Hurricane(s, m, WALL + WALL_PER_MAGNITUDE * m));
		return s;
	}

	static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		boolean damage = level.getGameRules().get(Disasters.DISASTER_DAMAGE);
		ACTIVE.removeIf(h -> !Storms.SERVER.contains(h.storm) || h.storm.age >= h.storm.life);
		for (Hurricane h : ACTIVE) {
			Storm s = h.storm;
			if (now % 40 == 0 && Loaded.made(level, Mth.floor(s.x), Mth.floor(s.z)) && !Tsunamis.atSea(level, s.x, s.z)) {
				if (!h.landfall) {
					landfall(level, h, random);
				}
			}
			if (h.landfall) {
				h.power = Math.max(0.0F, h.power - WEAKEN);
				if (h.power <= 0.05F) {
					s.life = Math.min(s.life, s.age + Storms.GATHER_TICKS);
				}
			}
			for (ServerPlayer p : level.players()) {
				float wind = h.wind(p.getX(), p.getZ());
				if (wind <= 0.05F || p.isSpectator()) {
					continue;
				}
				blow(level, h, p, wind, random);
				if (damage && random.nextFloat() < WRECK_TRIES * wind) {
					wreck(level, h, p, random);
				}
				if (now % BOLT_EVERY == 0 && wind > 0.4F) {
					bolt(level, h, p, random);
				}
				if (now % 20 == 0) {
					seas(level, h, p, wind, random);
				}
				if (h.landfall && now % TORNADO_EVERY == 0 && h.tornadoes < 1 + h.magnitude / 2 && random.nextFloat() < TORNADO_CHANCE * wind) {
					// (nd-chains) it puts down tornadoes once it's ashore
					double a = random.nextDouble() * Math.PI * 2.0, d = 30.0 + random.nextDouble() * 40.0;
					Tornadoes.spawn(level, h.magnitude >= Magnitude.GREAT ? Tornado.MONSTER : Tornado.TORNADO, s, p.getX() + Math.cos(a) * d,
						p.getZ() + Math.sin(a) * d, random);
					h.tornadoes++;
				}
			}
		}
	}

	/** It comes ashore: (nd-chains) the sea comes in over the coast, and the settlements there are struck. */
	private static void landfall(ServerLevel level, Hurricane h, RandomSource random) {
		h.landfall = true;
		Storm s = h.storm;
		s.life = Math.min(s.life, s.age + LAND_LIFE);
		ServerPlayer near = null;
		double best = (s.radius + 100.0) * (s.radius + 100.0);
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceToSqr(s.x, p.getY(), s.z);
			if (d < best) {
				best = d;
				near = p;
			}
		}
		if (near != null) {
			Calamities.flood(level, near.blockPosition(), random, h.magnitude);
		}
		com.jaymoskingdoms.DisasterRelief.struck(level, s.x, s.z, s.radius, "hurricane", h.magnitude);
	}

	/** The wind drags everything round the eye, and lifts what's light. */
	private static void blow(ServerLevel level, Hurricane h, ServerPlayer p, float wind, RandomSource random) {
		Storm s = h.storm;
		for (Entity e : level.getEntities((Entity)null, p.getBoundingBox().inflate(32.0, 16.0, 32.0), e -> e.isAlive() && !e.isSpectator()
			&& !(e instanceof FallingBlockEntity))) {
			if (e instanceof Player pl && (pl.isCreative() || pl.getAbilities().flying)) {
				continue;
			}
			if (!level.canSeeSky(e.blockPosition().above())) {
				continue;
			}
			double dx = e.getX() - s.x, dz = e.getZ() - s.z, d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
			float here = h.wind(e.getX(), e.getZ());
			double push = DRAG * h.magnitude * here;
			// round it (the way the world's storms turn), and in toward the eye
			double tx = -dz / d, tz = dx / d;
			e.push((tx - dx / d * INWARD) * push, 0.0, (tz - dz / d * INWARD) * push);
			if (here > 0.6F && random.nextFloat() < LIFT * here && e.getBbWidth() < 1.0F) {
				e.push(0.0, 0.25 + 0.1 * h.magnitude * here, 0.0);
			}
			e.syncVelocity = true;
		}
		if (random.nextInt(40) == 0) {
			level.playSound(null, p.getX(), p.getY() + 4.0, p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 1.5F * wind, 0.5F);
		}
	}

	/** Something light near {@code p} is torn loose and flung on the wind. */
	private static void wreck(ServerLevel level, Hurricane h, ServerPlayer p, RandomSource random) {
		int x = p.getBlockX() + random.nextInt(WRECK_RANGE * 2 + 1) - WRECK_RANGE, z = p.getBlockZ() + random.nextInt(WRECK_RANGE * 2 + 1) - WRECK_RANGE;
		if (!Loaded.made(level, x, z) || h.wind(x, z) < 0.5F) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
		BlockState state = level.getBlockState(top);
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
			return;
		}
		float hardness = state.getDestroySpeed(level, top);
		boolean light = state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.WOOL) || state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.CROPS) || hardness >= 0.0F && hardness <= WRECK_HARDEST;
		if (!light || !BlockBudget.spend()) {
			return;
		}
		if (!state.canSurvive(level, top) || state.is(BlockTags.REPLACEABLE)) {
			level.destroyBlock(top, true);
			return;
		}
		FallingBlockEntity piece = FallingBlockEntity.fall(level, top, state);
		Storm s = h.storm;
		double dx = x - s.x, dz = z - s.z, d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		piece.setDeltaMovement(-dz / d * WRECK_SPEED, 0.4 + random.nextDouble() * 0.4, dx / d * WRECK_SPEED);
		piece.setHurtsEntities(1.0F, 6);
		piece.disableDrop();
	}

	/** A bolt somewhere in the wall near {@code p}, seeking the high ground. */
	private static void bolt(ServerLevel level, Hurricane h, ServerPlayer p, RandomSource random) {
		double a = random.nextDouble() * Math.PI * 2.0, d = 10.0 + random.nextDouble() * 50.0;
		int x = Mth.floor(p.getX() + Math.cos(a) * d), z = Mth.floor(p.getZ() + Math.sin(a) * d);
		if (!Loaded.made(level, x, z) || com.jaymoskingdoms.Blessings.calmsSea(level, new BlockPos(x, level.getSeaLevel(), z))) {
			return;
		}
		BlockPos top = HighGround.seek(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)), random);
		BlockPos rod = HighGround.rod(level, top);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.addTag(Storms.BOLT_TAG);
			bolt.snapTo(Vec3.atBottomCenterOf(rod != null ? rod.above() : top));
			level.addFreshEntity(bolt);
		}
	}

	/** At sea: ships in the wall are holed, boats capsize. */
	private static void seas(ServerLevel level, Hurricane h, ServerPlayer p, float wind, RandomSource random) {
		if (wind < 0.4F) {
			return;
		}
		for (Entity e : level.getEntities((Entity)null, p.getBoundingBox().inflate(64.0, 16.0, 64.0), e -> e instanceof Ship || e instanceof AbstractBoat)) {
			if (!Tsunamis.atSea(level, e.getX(), e.getZ())) {
				continue;
			}
			float here = h.wind(e.getX(), e.getZ());
			if (e instanceof Ship ship && random.nextFloat() < SHIP_HOLED * here * h.magnitude / 3.0F) {
				AABB box = ship.getBoundingBox();
				Vec3 at = new Vec3(Mth.lerp(random.nextDouble(), box.minX, box.maxX), Mth.lerp(random.nextDouble(), box.minY, box.minY + 2.0),
					Mth.lerp(random.nextDouble(), box.minZ, box.maxZ));
				if (ship.struck(at, null)) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
				}
			} else if (e instanceof AbstractBoat boat && random.nextFloat() < BOAT_CAPSIZE * here * h.magnitude / 3.0F) {
				boat.ejectPassengers();
				boat.hurtServer(level, level.damageSources().generic(), 100.0F);
			}
		}
	}

	/** Whether a hurricane's wind is at (x, z) (the storm bell rings for one). */
	static boolean over(double x, double z) {
		for (Hurricane h : ACTIVE) {
			double dx = h.storm.x - x, dz = h.storm.z - z;
			if (dx * dx + dz * dz < (h.storm.radius + 120.0) * (h.storm.radius + 120.0)) {
				return true;
			}
		}
		return false;
	}
}
