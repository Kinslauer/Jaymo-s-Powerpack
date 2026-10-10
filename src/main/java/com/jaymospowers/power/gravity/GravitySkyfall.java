package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collapsing Horizon's C, the ultimate: <b>Skyfall</b>. Gravity turns over around you ({@code RADIUS}): every
 * creature and a host of loose blocks lift off the ground and fall <i>up</i> into the sky for {@code RISE_TICKS},
 * tumbling and struggling against nothing. Then you close your fist and gravity comes back {@code SLAM}-fold. Creatures take what the fall gives them and then some ({@code EXTRA_PER_BLOCK} more per block
 * fallen, more the heavier they are, times a roll of {@code IMPACT_MIN}..{@code IMPACT_MAX}); the blocks shatter where they
 * land. Nothing comes down together: each creature and block drops at its own moment within {@code STAGGER_TICKS}, each
 * landing with its own thud and shudder, and the ground heaves when the last one lands. {@code COOLDOWN} after.
 * <p>
 * Weightless (V) on during the rise, you ride it up with everything else: never slammed, never hurt by it. You hang where
 * the rise left you ({@code riding}) for as long as Weightless lasts, then drift down at Weightless's weight, and land
 * softly ({@code DESCENT_MAX_TICKS} at most).
 */
final class GravitySkyfall {
	static final double RADIUS = 20.0;
	static final int RISE_TICKS = 60, COOLDOWN = 1800;
	/** Rising speed (blocks/tick) and how hard the struggle jostles; the fall's speed at the slam. */
	static final double RISE_SPEED = 0.55, JOSTLE = 0.08, SLAM = 3.5;
	/** How many loose blocks come up with them, and how hard a block may be. */
	static final int BLOCKS = 40;
	static final float BLOCK_HARDNESS = 3.0F;
	/** Extra damage per block fallen (on top of the fall's own), scaled by size. */
	static final float EXTRA_PER_BLOCK = 0.4F;
	/** After the rise, each thing drops at its own moment within this many ticks; each landing rolls this much of the damage. */
	static final int STAGGER_TICKS = 30;
	static final float IMPACT_MIN = 0.6F, IMPACT_MAX = 1.5F;
	/** Each landing's own shudder: how far it's felt, how hard (the last one shakes {@code RADIUS * 2} at 1.4). */
	static final double IMPACT_SHAKE_RANGE = 12.0;
	static final float IMPACT_SHAKE = 0.3F;
	/** After the last drop: how long to wait for stragglers still falling before it's over anyway. */
	static final int LANDING_TIMEOUT = 200;
	/** A rider drifting down once Weightless ends: its gravity (as Weightless's) and how long at most. */
	static final double DESCENT_GRAVITY = -0.7;
	static final int DESCENT_MAX_TICKS = 1200;
	/** A hanging rider who touches down this long after the rise (a ledge, a roof) is simply down. */
	static final int LAND_GRACE_TICKS = 5;

	private static final Identifier HOVER_ID = JaymosPowers.id("gravity_skyfall_hover");
	private static final Identifier DESCENT_ID = JaymosPowers.id("gravity_skyfall_descent");
	private static final Identifier DESCENT_FALL_ID = JaymosPowers.id("gravity_skyfall_descent_fall");

	private static final class Skyfall {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final Vec3 center;
		final long start;
		final List<Integer> lifted = new ArrayList<>();
		final List<Integer> blocks = new ArrayList<>();
		/** Each creature's height when it began to drop, by id (for the extra damage on landing). */
		final Map<Integer, Double> slamFrom = new HashMap<>();
		/** When each creature and block drops (ticks after the start), and each creature's damage roll. */
		final Map<Integer, Integer> dropAt = new HashMap<>();
		final Map<Integer, Float> impact = new HashMap<>();
		final Set<Integer> dropped = new HashSet<>();
		/** Each dropped block's last place (for its thud when it shatters). */
		final Map<Integer, Vec3> blockAt = new HashMap<>();
		boolean slammed;
		/** The caster rides the rise (Weightless). */
		boolean rider;

		Skyfall(ResourceKey<Level> dimension, UUID owner, Vec3 center, long start) {
			this.dimension = dimension;
			this.owner = owner;
			this.center = center;
			this.start = start;
		}
	}

	/** A caster left in the sky by their Skyfall: hanging (Weightless on) or drifting down (it ended). */
	private static final class Ride {
		final ResourceKey<Level> dimension;
		final long since;
		boolean descending;
		long descendFrom;

		Ride(ResourceKey<Level> dimension, long since) {
			this.dimension = dimension;
			this.since = since;
		}
	}

	private static final List<Skyfall> SKYFALLS = new ArrayList<>();
	private static final Map<UUID, Ride> RIDES = new HashMap<>();

	private GravitySkyfall() {
	}

	/** Something is up in a Skyfall right now (rising, hanging or falling; a creature, a block, or a riding caster). */
	public static boolean airborne(Entity entity) {
		if (entity instanceof ServerPlayer player && riding(player)) {
			return true;
		}
		int id = entity.getId();
		for (Skyfall skyfall : SKYFALLS) {
			if (skyfall.dimension == entity.level().dimension() && (skyfall.lifted.contains(id) || skyfall.blocks.contains(id))) {
				return true;
			}
		}
		return false;
	}

	/** The caster rides their own Skyfall: rising with it, hanging after it, or drifting down. */
	public static boolean riding(ServerPlayer player) {
		if (RIDES.containsKey(player.getUUID())) {
			return true;
		}
		for (Skyfall skyfall : SKYFALLS) {
			if (skyfall.rider && !skyfall.slammed && skyfall.owner.equals(player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** C, both ascended and corrupted: turn the sky over. */
	static boolean call(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position();
		Skyfall skyfall = new Skyfall(level.dimension(), player.getUUID(), center, level.getGameTime());
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(center) <= RADIUS * RADIUS
				&& !(e instanceof ServerPlayer p && p.isCreative()))) {
			skyfall.lifted.add(living.getId());
			living.setNoGravity(true);
			living.resetFallDistance();
			GravityPower.touch(living, player);
			if (living instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		// the ground comes up too: loose surface blocks, torn loose one by one
		List<BlockPos> surface = new ArrayList<>();
		int r = (int)RADIUS;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				int x = Mth.floor(center.x) + dx, z = Mth.floor(center.z) + dz;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
				if (Math.abs(top.getY() - center.y) < 12 && BlockCluster.takeable(level, top, BLOCK_HARDNESS, player)) {
					surface.add(top);
				}
			}
		}
		Collections.shuffle(surface, new java.util.Random(level.getRandom().nextLong()));
		for (BlockPos pos : surface.subList(0, Math.min(BLOCKS, surface.size()))) {
			BlockCluster block = BlockCluster.of(level, List.of(pos), new Vec3(0.0, 1.0, 0.0), player);
			if (block != null) {
				block.script(true, false);
				skyfall.blocks.add(block.getId());
			}
		}
		SKYFALLS.add(skyfall);
		// cast while weightless: you go up with it
		mount(player, skyfall);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 0.3F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0F, 0.5F);
		for (int i = 0; i < 48; i++) {
			double a = i / 48.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + Math.cos(a) * RADIUS * 0.8, center.y + 0.3, center.z + Math.sin(a) * RADIUS * 0.8,
				4, 0.2, 0.0, 0.2, 0.3);
		}
		GravityPower.shake(level, center, RADIUS * 1.5, 0.8F);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (!RIDES.isEmpty()) {
			tickRides(server);
		}
		for (Iterator<Skyfall> it = SKYFALLS.iterator(); it.hasNext(); ) {
			Skyfall skyfall = it.next();
			ServerLevel level = server.getLevel(skyfall.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - skyfall.start;
			ServerPlayer owner = server.getPlayerList().getPlayer(skyfall.owner);
			var random = level.getRandom();
			if (t < RISE_TICKS) {
				// Weightless switched on during the rise: up you go too
				if (owner != null && !skyfall.rider) {
					mount(owner, skyfall);
				}
				if (skyfall.rider && (owner == null || !owner.isAlive() || owner.level() != level)) {
					skyfall.rider = false;
					if (owner != null) {
						owner.setNoGravity(false);
					}
				}
				// falling up, struggling
				for (int id : skyfall.lifted) {
					Entity e = level.getEntity(id);
					if (e != null && e.isAlive()) {
						rise(level, e, t);
					}
				}
				if (skyfall.rider) {
					rise(level, owner, t);
				}
				for (int id : skyfall.blocks) {
					Entity block = level.getEntity(id);
					if (block != null) {
						Vec3 was = block.position();
						Vec3 to = was.add((random.nextDouble() - 0.5) * 0.1, RISE_SPEED * (0.8 + (id % 5) * 0.08), (random.nextDouble() - 0.5) * 0.1);
						block.setPos(to);
						block.setDeltaMovement(to.subtract(was));
					}
				}
				if (t % 20 == 0) {
					level.playSound(null, skyfall.center.x, skyfall.center.y + 10.0, skyfall.center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
						SoundSource.PLAYERS, 2.0F, 0.4F);
				}
				continue;
			}
			if (!skyfall.slammed) {
				// the fist closes: everything will come down, each in its own time
				skyfall.slammed = true;
				for (int id : skyfall.lifted) {
					skyfall.dropAt.put(id, RISE_TICKS + random.nextInt(STAGGER_TICKS + 1));
					skyfall.impact.put(id, Mth.lerp(random.nextFloat(), IMPACT_MIN, IMPACT_MAX));
				}
				for (int id : skyfall.blocks) {
					skyfall.dropAt.put(id, RISE_TICKS + random.nextInt(STAGGER_TICKS + 1));
				}
				if (skyfall.rider && owner != null) {
					// the rider isn't slammed: they hang where the rise left them
					skyfall.rider = false;
					hover(owner);
				}
				level.playSound(null, skyfall.center.x, skyfall.center.y + 10.0, skyfall.center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0F, 0.4F);
			}
			// creatures: hanging until their turn, then down
			for (Iterator<Integer> ids = skyfall.lifted.iterator(); ids.hasNext(); ) {
				int id = ids.next();
				Entity e = level.getEntity(id);
				if (!(e instanceof LivingEntity living) || !living.isAlive()) {
					if (e != null) {
						e.setNoGravity(false);
					}
					ids.remove();
					continue;
				}
				if (!skyfall.dropped.contains(id)) {
					if (t < skyfall.dropAt.getOrDefault(id, RISE_TICKS)) {
						hang(level, living);
						continue;
					}
					skyfall.dropped.add(id);
					living.setNoGravity(false);
					skyfall.slamFrom.put(id, living.getY());
					push(living, new Vec3(0.0, -SLAM, 0.0));
					continue;
				}
				// landing: the fall does its work, and the weight adds to it
				if (!living.onGround() && !living.isInWater()) {
					living.setDeltaMovement(living.getDeltaMovement().add(0.0, -0.3, 0.0));
					continue;
				}
				ids.remove();
				double fell = Math.max(0.0, skyfall.slamFrom.getOrDefault(id, living.getY()) - living.getY());
				float size = Mth.clamp(living.getBbWidth() * living.getBbHeight(), 0.5F, 3.0F);
				living.setInvulnerableTime(0);
				living.hurtServer(level, level.damageSources().fall(), (float)fell * EXTRA_PER_BLOCK * size * skyfall.impact.getOrDefault(id, 1.0F));
				com.jaymospowers.core.Debris.puff(level, living.blockPosition().below(), level.getBlockState(living.blockPosition().below()), 2.0F);
				thud(level, living.position(), 1.5F, 0.5F + random.nextFloat() * 0.3F);
			}
			// blocks: the same, and each lands where it shatters
			for (Iterator<Integer> ids = skyfall.blocks.iterator(); ids.hasNext(); ) {
				int id = ids.next();
				Entity block = level.getEntity(id);
				if (!skyfall.dropped.contains(id)) {
					if (!(block instanceof BlockCluster cluster)) {
						ids.remove();
						continue;
					}
					if (t < skyfall.dropAt.getOrDefault(id, RISE_TICKS)) {
						Vec3 was = block.position();
						Vec3 to = was.add((random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.06);
						block.setPos(to);
						block.setDeltaMovement(to.subtract(was));
						continue;
					}
					skyfall.dropped.add(id);
					cluster.script(false, false);
					cluster.setDeltaMovement(0.0, -SLAM, 0.0);
					cluster.launch(owner);
					skyfall.blockAt.put(id, cluster.position());
					continue;
				}
				if (block != null && !block.isRemoved()) {
					skyfall.blockAt.put(id, block.position());
					continue;
				}
				ids.remove();
				Vec3 at = skyfall.blockAt.remove(id);
				if (at != null) {
					thud(level, at, 1.0F, 0.6F + random.nextFloat() * 0.4F);
				}
			}
			boolean done = skyfall.lifted.isEmpty() && skyfall.blocks.isEmpty() && t >= RISE_TICKS + STAGGER_TICKS;
			if (done || t > RISE_TICKS + STAGGER_TICKS + LANDING_TIMEOUT) {
				// the last one down: the ground heaves
				for (int id : skyfall.lifted) {
					Entity e = level.getEntity(id);
					if (e != null) {
						e.setNoGravity(false);
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, skyfall.center.x, skyfall.center.y + 0.5, skyfall.center.z, 2, 4.0, 0.5, 4.0, 0.0);
				level.playSound(null, skyfall.center.x, skyfall.center.y, skyfall.center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.4F);
				GravityPower.shake(level, skyfall.center, RADIUS * 2.0, 1.4F);
				it.remove();
			}
		}
	}

	/** Falling up, struggling against nothing. */
	private static void rise(ServerLevel level, Entity e, long t) {
		var random = level.getRandom();
		push(e, new Vec3((random.nextDouble() - 0.5) * JOSTLE, RISE_SPEED * (1.0 - t / (double)RISE_TICKS * 0.5), (random.nextDouble() - 0.5) * JOSTLE));
		e.resetFallDistance();
		if (t % 4 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(), e.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
		}
	}

	/** Up at the top, waiting for its turn to fall: it barely stirs. */
	private static void hang(ServerLevel level, Entity e) {
		var random = level.getRandom();
		push(e, new Vec3((random.nextDouble() - 0.5) * JOSTLE * 0.5, (random.nextDouble() - 0.5) * JOSTLE * 0.5, (random.nextDouble() - 0.5) * JOSTLE * 0.5));
		e.resetFallDistance();
	}

	private static void push(Entity e, Vec3 v) {
		e.setDeltaMovement(v);
		e.syncVelocity = true;
		if (e instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(v));
		}
	}

	/** One landing: its own thud and a small shudder. */
	private static void thud(ServerLevel level, Vec3 at, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, volume, pitch);
		GravityPower.shake(level, at, IMPACT_SHAKE_RANGE, IMPACT_SHAKE);
	}

	// ---------------------------------------------------------------- the rider (Weightless)

	/** The caster, weightless and near enough, joins the rise. */
	private static void mount(ServerPlayer player, Skyfall skyfall) {
		if (skyfall.rider || player.getAttached(GravityPower.WEIGHTLESS_ON) == null || !player.isAlive() || player.isSpectator()
			|| player.level().dimension() != skyfall.dimension || player.position().distanceToSqr(skyfall.center) > RADIUS * RADIUS
			|| player.getAttached(GravityPower.ROCK_FLIGHT) != null) {
			return;
		}
		skyfall.rider = true;
		RIDES.remove(player.getUUID());
		player.setNoGravity(true);
		player.resetFallDistance();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** The rise is over and the rider stays up: no weight at all while Weightless lasts. */
	private static void hover(ServerPlayer player) {
		player.setNoGravity(false);
		push(player, Vec3.ZERO);
		player.resetFallDistance();
		RIDES.put(player.getUUID(), new Ride(player.level().dimension(), player.level().getGameTime()));
		GravityPower.modifier(player, Attributes.GRAVITY, HOVER_ID, -1.0, true);
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5, player.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
	}

	private static void tickRides(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Ride>> it = RIDES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Ride> entry = it.next();
			Ride ride = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			boolean weightless = player.getAttached(GravityPower.WEIGHTLESS_ON) != null;
			long now = player.level().getGameTime();
			boolean over = !player.isAlive() || player.isSpectator() || player.level().dimension() != ride.dimension
				|| player.getAttached(GravityPower.ROCK_FLIGHT) != null || player.getAbilities().flying || player.isFallFlying()
				|| player.isInWater() || player.isInLava() || player.onGround() && now - ride.since > LAND_GRACE_TICKS
				|| ride.descending && now - ride.descendFrom > DESCENT_MAX_TICKS;
			if (over) {
				it.remove();
				unride(player);
				continue;
			}
			player.resetFallDistance();
			if (!ride.descending && !weightless) {
				// Weightless is over: down you drift, gently
				ride.descending = true;
				ride.descendFrom = now;
				GravityPower.modifier(player, Attributes.GRAVITY, HOVER_ID, 0.0, false);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 0.8F);
			}
			if (ride.descending) {
				// at Weightless's weight (unless it's back on), and the landing never hurts
				GravityPower.modifier(player, Attributes.GRAVITY, DESCENT_ID, DESCENT_GRAVITY, !weightless);
				GravityPower.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, DESCENT_FALL_ID, -1.0, true);
			} else if (player.tickCount % 10 == 0) {
				player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() - 0.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
			}
		}
	}

	private static void unride(ServerPlayer player) {
		GravityPower.modifier(player, Attributes.GRAVITY, HOVER_ID, 0.0, false);
		GravityPower.modifier(player, Attributes.GRAVITY, DESCENT_ID, 0.0, false);
		GravityPower.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, DESCENT_FALL_ID, 0.0, false);
	}

	/** The rider is gone (logged out): let go of everything holding them up. */
	static void land(ServerPlayer player) {
		RIDES.remove(player.getUUID());
		unride(player);
		for (Skyfall skyfall : SKYFALLS) {
			if (skyfall.rider && skyfall.owner.equals(player.getUUID())) {
				skyfall.rider = false;
				player.setNoGravity(false);
			}
		}
	}

	static void clear() {
		SKYFALLS.clear();
		RIDES.clear();
	}
}
