package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.sailing.Ship;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.589) Sharks in a ship's wake. Andy (2026-11-05), on a seabed packed with sharks: "i kind of like the idea of a
 * feeding frenzy like that ... maybe add an event or a group that often follows ships".
 *
 * <p>Out on open ocean (an ocean biome, at least {@link #MIN_DEPTH} blocks of water under her), a vessel with a player
 * aboard (a {@link Ship}, a boat, anything they ride on the water) is now and then picked up by a pack of great white
 * sharks (Naturalist's; without it, none come): every {@link #ROLL_EVERY} ticks under way there's a {@link #PACK_CHANCE}
 * chance {@link #PACK_MIN}-{@link #PACK_MAX} of them come up out of sight astern and trail her, fins weaving
 * {@link #TRAIL} blocks behind, for about {@link #PACK_LIFE} ticks, then peel away.
 *
 * <p>Blood in the water sets them off: anything hurt or killed in the water near the pack ({@link #FRENZY_RANGE}) starts a
 * feeding frenzy. For {@link #FRENZY_TICKS} the whole pack goes for whatever's bleeding, then for anything else swimming
 * near (a man overboard included), the water churns red, and more sharks come to it, up to {@link #FRENZY_MOST}.
 */
public final class SharkWake {
	/** How often (ticks) the packs are steered, and how often a vessel under way rolls for one; the chance it comes. */
	static final int STEER_EVERY = 20, ROLL_EVERY = 600;
	static final float PACK_CHANCE = 0.3F;
	/** How many in a pack; how long (ticks) it trails her; how long it waits for her once she's out of the open sea. */
	static final int PACK_MIN = 3, PACK_MAX = 5;
	static final long PACK_LIFE = 6000L, LOST_AFTER = 600L;
	/** Open sea: this many blocks of water under her at least (counted down from sea level, at most {@link #DEPTH_LOOK}). */
	static final int MIN_DEPTH = 8, DEPTH_LOOK = 16;
	/** Under way: faster than this (blocks a tick) to roll for a pack. */
	static final double UNDER_WAY = 0.05;
	/** Where they come up (blocks astern), how far behind her they trail, how far apart they swim, how deep, how fast. */
	static final double ARRIVE_MIN = 28.0, ARRIVE_MAX = 36.0, TRAIL = 10.0, SPREAD = 3.5, DEEP = 3.0, SWIM = 1.4, CHASE = 1.8;
	/** Blood this near a pack's shark sets it off; how long (ticks) it lasts; how many more come to it, and at most. */
	static final double FRENZY_RANGE = 24.0, PREY_RANGE = 16.0;
	static final int FRENZY_TICKS = 300, FRENZY_MORE = 3, FRENZY_MOST = 10;
	/** Sharks out of a player's sight this far when the pack breaks up are taken back into the deep. */
	static final double GONE_RANGE = 48.0;

	/** Each vessel's pack (vessel UUID → pack). */
	private static final Map<UUID, Pack> PACKS = new HashMap<>();
	/** Vessels that already rolled this round and missed (vessel UUID → when they may roll again). */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();

	private static final class Pack {
		final ServerLevel level;
		final List<UUID> sharks = new ArrayList<>();
		final long formed;
		long seen, frenzyUntil = Long.MIN_VALUE;
		@Nullable UUID prey;

		Pack(ServerLevel level, long now) {
			this.level = level;
			this.formed = now;
			this.seen = now;
		}
	}

	private SharkWake() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SharkWake::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F) {
				blood(entity, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(SharkWake::blood);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PACKS.clear();
			ROLLED.clear();
		});
	}

	private static @Nullable EntityType<?> shark() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Sharks.SHARK).orElse(null);
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % STEER_EVERY != 0) {
			return;
		}
		EntityType<?> type = shark();
		if (type == null) {
			return;
		}
		// (the vessels out on the open sea with a player aboard)
		Map<UUID, Entity> vessels = new HashMap<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			Entity vessel = vessel(level, player);
			if (vessel != null && openSea(level, vessel)) {
				vessels.put(vessel.getUUID(), vessel);
			}
		}
		for (Entity vessel : vessels.values()) {
			Pack pack = PACKS.get(vessel.getUUID());
			if (pack != null) {
				pack.seen = now;
				continue;
			}
			if (vessel.getDeltaMovement().horizontalDistance() < UNDER_WAY || ROLLED.getOrDefault(vessel.getUUID(), Long.MIN_VALUE) > now) {
				continue;
			}
			ROLLED.put(vessel.getUUID(), now + ROLL_EVERY);
			RandomSource random = level.getRandom();
			if (random.nextFloat() < PACK_CHANCE) {
				pack = new Pack(level, now);
				int n = Mth.nextInt(random, PACK_MIN, PACK_MAX);
				for (int i = 0; i < n; i++) {
					Entity s = arrive(level, type, vessel.position(), astern(vessel), random);
					if (s != null) {
						pack.sharks.add(s.getUUID());
					}
				}
				if (!pack.sharks.isEmpty()) {
					PACKS.put(vessel.getUUID(), pack);
				}
			}
		}
		ROLLED.values().removeIf(t -> t < now);
		for (Iterator<Map.Entry<UUID, Pack>> it = PACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pack> e = it.next();
			Pack pack = e.getValue();
			if (pack.level != level) {
				continue;
			}
			List<Mob> sharks = members(pack);
			boolean frenzy = pack.frenzyUntil > now;
			if (sharks.isEmpty() || !frenzy && (now - pack.seen > LOST_AFTER || now - pack.formed > PACK_LIFE)) {
				breakUp(level, sharks);
				it.remove();
				continue;
			}
			if (frenzy) {
				frenzy(level, pack, sharks);
				continue;
			}
			Entity vessel = vessels.get(e.getKey());
			if (vessel != null) {
				trail(level, vessel, sharks);
			}
		}
	}

	/** What {@code player} is out on the water in: what they ride, or a ship they stand on (null: neither). */
	private static @Nullable Entity vessel(ServerLevel level, ServerPlayer player) {
		Entity root = player.getRootVehicle();
		if (root != player) {
			return root;
		}
		for (Ship ship : level.getEntities(Ship.TYPE, player.getBoundingBox().inflate(24.0), s -> true)) {
			if (ship.getBoundingBox().inflate(1.0).contains(player.position()) && ship.aboard(ship.toLocal(player.position()), 1.0)) {
				return ship;
			}
		}
		return null;
	}

	/** {@code vessel} is on the open ocean: an ocean biome, deep water under her. */
	private static boolean openSea(ServerLevel level, Entity vessel) {
		BlockPos at = BlockPos.containing(vessel.getX(), level.getSeaLevel() - 1, vessel.getZ());
		if (!level.getBiome(at).is(BiomeTags.IS_OCEAN)) {
			return false;
		}
		int depth = 0;
		for (BlockPos.MutableBlockPos p = at.mutable(); depth < DEPTH_LOOK && level.getFluidState(p).is(FluidTags.WATER); p.move(0, -1, 0)) {
			depth++;
		}
		return depth >= MIN_DEPTH;
	}

	/** Which way is astern of her: back along her wake, or behind where she faces if she's still. */
	private static Vec3 astern(Entity vessel) {
		Vec3 v = vessel.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		if (v.lengthSqr() < 1.0E-4) {
			v = Vec3.directionFromRotation(0.0F, vessel.getYRot());
		}
		return v.normalize().scale(-1.0);
	}

	/** A shark come up out of the deep {@link #ARRIVE_MIN}-{@link #ARRIVE_MAX} blocks off {@code from}, towards {@code dir} (null: no water deep enough). */
	private static @Nullable Entity arrive(ServerLevel level, EntityType<?> type, Vec3 from, Vec3 dir, RandomSource random) {
		for (int tries = 0; tries < 6; tries++) {
			double d = Mth.nextDouble(random, ARRIVE_MIN, ARRIVE_MAX);
			double side = Mth.nextDouble(random, -12.0, 12.0);
			BlockPos pos = BlockPos.containing(from.x + dir.x * d - dir.z * side, level.getSeaLevel() - 4, from.z + dir.z * d + dir.x * side);
			if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER)
				&& level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				return type.spawn(level, pos, EntitySpawnReason.EVENT);
			}
		}
		return null;
	}

	private static List<Mob> members(Pack pack) {
		List<Mob> out = new ArrayList<>();
		for (Iterator<UUID> it = pack.sharks.iterator(); it.hasNext(); ) {
			if (pack.level.getEntity(it.next()) instanceof Mob m && m.isAlive()) {
				out.add(m);
			} else {
				it.remove();
			}
		}
		return out;
	}

	/** Fins weaving along in her wake, spread out across it. */
	private static void trail(ServerLevel level, Entity vessel, List<Mob> sharks) {
		Vec3 back = astern(vessel);
		long now = level.getGameTime();
		for (int i = 0; i < sharks.size(); i++) {
			Mob s = sharks.get(i);
			double side = (i - (sharks.size() - 1) * 0.5) * SPREAD + Mth.sin((now + i * 37) * 0.05F) * 1.5;
			double behind = TRAIL + (i % 2) * 4.0;
			double x = vessel.getX() + back.x * behind - back.z * side, z = vessel.getZ() + back.z * behind + back.x * side;
			double far = s.distanceToSqr(vessel);
			s.getNavigation().moveTo(x, level.getSeaLevel() - DEEP, z, far > 24.0 * 24.0 ? CHASE : SWIM);
		}
	}

	/** Something bled in the water: any pack near enough goes into a frenzy over it. */
	private static void blood(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !victim.isInWater() || PACKS.isEmpty()) {
			return;
		}
		EntityType<?> type = shark();
		if (type == null || victim.getType() == type) {
			return;
		}
		long now = level.getGameTime();
		for (Pack pack : PACKS.values()) {
			if (pack.level != level) {
				continue;
			}
			List<Mob> sharks = members(pack);
			boolean near = false;
			for (Mob s : sharks) {
				near |= s.distanceToSqr(victim) <= FRENZY_RANGE * FRENZY_RANGE;
			}
			if (!near) {
				continue;
			}
			boolean fresh = pack.frenzyUntil <= now;
			pack.frenzyUntil = now + FRENZY_TICKS;
			pack.prey = victim.isAlive() ? victim.getUUID() : null;
			level.sendParticles(com.jaymoskingdoms.PowersCompat.blood(0x8A1010, 2.0F), victim.getX(), victim.getY() + 0.5, victim.getZ(), 24, 0.8, 0.5, 0.8, 0.0);
			if (fresh) {
				// (more come to the blood)
				RandomSource random = level.getRandom();
				int more = Math.min(FRENZY_MORE, FRENZY_MOST - pack.sharks.size());
				for (int i = 0; i < more; i++) {
					float a = random.nextFloat() * Mth.TWO_PI;
					Entity s = arrive(level, type, victim.position(), new Vec3(Mth.cos(a), 0.0, Mth.sin(a)), random);
					if (s != null) {
						pack.sharks.add(s.getUUID());
					}
				}
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5F, 0.7F);
			}
		}
	}

	/** The pack tears at whatever's bleeding, then anything else swimming near; the water churns. */
	private static void frenzy(ServerLevel level, Pack pack, List<Mob> sharks) {
		EntityType<?> type = sharks.get(0).getType();
		LivingEntity prey = pack.prey == null ? null : level.getEntity(pack.prey) instanceof LivingEntity l && l.isAlive() && l.isInWater() ? l : null;
		for (Mob s : sharks) {
			LivingEntity target = prey;
			if (target == null) {
				List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(PREY_RANGE),
					e -> e.isAlive() && e.isInWater() && e.getType() != type && !(e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())));
				double best = Double.MAX_VALUE;
				for (LivingEntity e : near) {
					double d = e.distanceToSqr(s);
					if (d < best) {
						best = d;
						target = e;
					}
				}
			}
			if (target != null) {
				s.setTarget(target);
				s.getNavigation().moveTo(target, CHASE);
			}
			if (s.getY() > level.getSeaLevel() - 3.0) {
				level.sendParticles(ParticleTypes.SPLASH, s.getX(), level.getSeaLevel(), s.getZ(), 10, 0.8, 0.1, 0.8, 0.2);
			}
		}
		if (prey == null) {
			pack.prey = null;
		}
	}

	/** The pack peels away: those out of sight go back into the deep, the rest swim off on their own. */
	private static void breakUp(ServerLevel level, List<Mob> sharks) {
		for (Mob s : sharks) {
			s.setTarget(null);
			if (level.getNearestPlayer(s, GONE_RANGE) == null) {
				s.discard();
			}
		}
	}
}
