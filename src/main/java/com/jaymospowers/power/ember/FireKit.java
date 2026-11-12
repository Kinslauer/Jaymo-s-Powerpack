package com.jaymospowers.power.ember;

import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The fire kit's gaps, for the Emberborn and Pyre. What already existed: Kindle is the cone of fire ({@link EmberKindle});
 * the Flame Avatar has Fire Fist, Flaming Pillar (round it or at the cursor) and Ignition Switch (tap lights what is in
 * front, hold sets off everything burning) ({@link AvatarMoves}); Pyre has a bolt, a jet, a nova and a trail of fire.
 * Added here:
 * <ul>
 * <li><b>Fire wall</b>: a standing wall {@link #WALL_WIDTH} wide, {@link #WALL_HEIGHT} high, for {@link #WALL_TICKS}. It is
 * an effect that burns whatever crosses it; real fire is also lit along its foot only where fire may be set (the fire
 * spread rule is on and the user may build there).</li>
 * <li><b>Flame ring</b>: a ring of flame held {@link #RING_RADIUS} from the user that burns and shoves what touches it, for
 * at most {@link #RING_TICKS}.</li>
 * <li><b>Rays</b>: {@link #RAY_COUNT} rays of fire one after another, each along the look at the moment it goes.</li>
 * <li><b>Sky column</b>: a marked spot, then a column of fire falls on it.</li>
 * <li><b>Chain</b> (passive): a creature burning because of you passes the fire to the nearest unlit foe near it, up to
 * {@link #CHAIN_GENERATIONS} generations.</li>
 * <li><b>Burst</b> (passive): a creature burning from your fire that dies bursts in a small blast that lights its neighbours.</li>
 * </ul>
 * Cooldowns are kept here (no HUD chip); each move costs a little hunger. Nothing here breaks a block.
 */
public final class FireKit {
	// ---- tuning ----
	public static final int WALL_COOLDOWN = 200, RING_COOLDOWN = 160, RAYS_COOLDOWN = 160, COLUMN_COOLDOWN = 240;
	/** Wall: width and height (blocks), life, reach to aim, burn seconds and damage every {@link #HIT_EVERY} ticks. */
	static final int WALL_WIDTH = 7, WALL_HEIGHT = 3, WALL_TICKS = 140, WALL_BURN = 4;
	static final double WALL_REACH = 14.0;
	static final float WALL_DAMAGE = 2.0F;
	/** Ring: radius, life, burn seconds, damage, the outward shove. */
	static final double RING_RADIUS = 2.4, RING_BAND = 0.9, RING_PUSH = 0.35;
	static final int RING_TICKS = 100, RING_HOLD_TICKS = 400, RING_BURN = 3;
	static final float RING_DAMAGE = 1.5F;
	/** Rays: how many, ticks between, reach, damage, burn seconds. */
	static final int RAY_COUNT = 3, RAY_GAP = 6, RAY_BURN = 3;
	static final double RAY_REACH = 26.0;
	static final float RAY_DAMAGE = 4.5F;
	/** Column: how far it can be marked, the warning (ticks), reach, damage, burn seconds, height it falls from. */
	static final double COLUMN_REACH = 28.0, COLUMN_RADIUS = 3.0, COLUMN_FROM = 22.0;
	static final int COLUMN_DELAY = 20, COLUMN_BURN = 6;
	static final float COLUMN_DAMAGE = 9.0F;
	/** Ticks between hits of the wall and the ring. */
	static final int HIT_EVERY = 10;
	/** Chain: how far the fire jumps, ticks between looks, generations, how long a credit lasts, burn seconds. */
	static final double CHAIN_RANGE = 4.5;
	static final int CHAIN_EVERY = 10, CHAIN_GENERATIONS = 3, CREDIT_TICKS = 300, CHAIN_BURN = 4;
	/** Burst: reach, damage, burn seconds, and how many may chain off one another. */
	static final double BURST_RADIUS = 3.2;
	static final float BURST_DAMAGE = 4.0F;
	static final int BURST_BURN = 4, BURST_DEPTH = 3;
	static final float EXHAUSTION = 1.5F;
	static final int COLOR = 0xFF8A1E, WHITE = 0xFFE9B0;

	private static final class Wall {
		final ServerLevel level;
		final ServerPlayer owner;
		final AABB box;
		final Vec3 end, along;
		final long until;

		Wall(ServerLevel level, ServerPlayer owner, AABB box, Vec3 end, Vec3 along, long until) {
			this.level = level;
			this.owner = owner;
			this.box = box;
			this.end = end;
			this.along = along;
			this.until = until;
		}
	}

	private static final class Ring {
		long until;
		boolean hold;
	}

	private static final class Rays {
		final ServerPlayer owner;
		int left;
		long next;

		Rays(ServerPlayer owner, int left, long next) {
			this.owner = owner;
			this.left = left;
			this.next = next;
		}
	}

	private static final class Column {
		final ServerLevel level;
		final ServerPlayer owner;
		final Vec3 at;
		final long due;

		Column(ServerLevel level, ServerPlayer owner, Vec3 at, long due) {
			this.level = level;
			this.owner = owner;
			this.at = at;
			this.due = due;
		}
	}

	/** Whose fire a creature is burning with, for the chain and the burst. */
	private static final class Credit {
		final UUID owner;
		final long time;
		final int generation;
		boolean jumped;

		Credit(UUID owner, long time, int generation) {
			this.owner = owner;
			this.time = time;
			this.generation = generation;
		}
	}

	private static final List<Wall> WALLS = new ArrayList<>();
	private static final Map<UUID, Ring> RINGS = new HashMap<>();
	private static final List<Rays> RAYS = new ArrayList<>();
	private static final List<Column> COLUMNS = new ArrayList<>();
	private static final Map<UUID, Credit> CREDITS = new HashMap<>();
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	private static final int WALL = 0, RING = 1, RAYS_AT = 2, COLUMN = 3;
	private static int depth;

	private FireKit() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FireKit::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WALLS.clear();
			RINGS.clear();
			RAYS.clear();
			COLUMNS.clear();
			CREDITS.clear();
			COOLDOWNS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Credit credit = CREDITS.remove(entity.getUUID());
			if (credit != null && entity.isOnFire() && entity.level() instanceof ServerLevel level && level.getGameTime() - credit.time < CREDIT_TICKS
				&& level.getServer().getPlayerList().getPlayer(credit.owner) instanceof ServerPlayer owner && depth < BURST_DEPTH) {
				deathBurst(level, owner, entity);
			}
		});
	}

	// ---- shared ----

	/** Something this player may hurt: a living thing that is not them, their pet, or a player they may not harm. */
	static boolean foe(ServerPlayer owner, Entity entity) {
		return entity instanceof LivingEntity && entity != owner && entity.isAlive() && !entity.isSpectator()
			&& !(entity instanceof TamableAnimal pet && pet.isOwnedBy(owner))
			&& (!(entity instanceof Player other) || owner.canHarmPlayer(other));
	}

	/** The owner's fire damage to {@code target}: credited for the chain and the burst, and not fed back into the heat gauge. */
	static void hurt(ServerPlayer owner, LivingEntity target, float amount) {
		credit(target, owner);
		boolean was = RunningHot.internal;
		RunningHot.internal = true;
		try {
			target.hurtServer(owner.level(), owner.damageSources().playerAttack(owner), amount);
		} finally {
			RunningHot.internal = was;
		}
	}

	/** {@code target} is burning (or hurt) by {@code owner}'s fire now. */
	static void credit(LivingEntity target, ServerPlayer owner) {
		credit(target, owner, 0);
	}

	private static void credit(LivingEntity target, ServerPlayer owner, int generation) {
		CREDITS.put(target.getUUID(), new Credit(owner.getUUID(), owner.level().getGameTime(), generation));
	}

	/** Fire blocks may be lit here: the fire spread rule is on and the player may build at {@code pos}. */
	private static boolean fireOk(ServerLevel level, ServerPlayer player, BlockPos pos) {
		return level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER) > 0 && player.mayInteract(level, pos);
	}

	private static boolean ready(ServerPlayer player, int slot) {
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[4]);
		return player.level().getGameTime() >= ready[slot];
	}

	private static void cooldown(ServerPlayer player, int slot, int ticks) {
		COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[4])[slot] = player.level().getGameTime() + ticks;
		player.causeFoodExhaustion(EXHAUSTION);
	}

	/** Where the player points: a block within {@code reach}, or the end of the reach. */
	private static Vec3 aim(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(reach));
		HitResult hit = player.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** {@code point} dropped to the ground under it (up to 8 blocks). */
	private static Vec3 ground(ServerLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		for (int i = 0; i < 8 && level.getBlockState(pos.below()).isAir(); i++) {
			pos = pos.below();
		}
		return new Vec3(point.x, level.getBlockState(BlockPos.containing(point)).isAir() ? pos.getY() : Math.floor(point.y) + 1.0, point.z);
	}

	// ---- the moves ----

	/** A standing fire wall where the player points, square across their look. */
	public static boolean wall(ServerPlayer player) {
		if (!ready(player, WALL)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 centre = ground(level, aim(player, WALL_REACH));
		Vec3 look = player.getLookAngle();
		boolean alongX = Math.abs(look.z) > Math.abs(look.x);
		double half = WALL_WIDTH / 2.0;
		Vec3 along = alongX ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 0.0, 1.0);
		AABB box = alongX ? new AABB(centre.x - half, centre.y, centre.z - 0.6, centre.x + half, centre.y + WALL_HEIGHT, centre.z + 0.6)
			: new AABB(centre.x - 0.6, centre.y, centre.z - half, centre.x + 0.6, centre.y + WALL_HEIGHT, centre.z + half);
		WALLS.add(new Wall(level, player, box, centre.subtract(along.scale(half)), along, level.getGameTime() + WALL_TICKS));
		for (int i = -(WALL_WIDTH / 2); i <= WALL_WIDTH / 2; i++) {
			BlockPos pos = BlockPos.containing(centre.x + along.x * i, centre.y + 0.1, centre.z + along.z * i);
			if (fireOk(level, player, pos)) {
				EmberKindle.ignite(level, pos);
			}
		}
		MagicFx.wave(level, centre.subtract(along.scale(half)).add(0.0, 0.5, 0.0), along, WALL_WIDTH, 1.2F, COLOR, 12);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
		cooldown(player, WALL, WALL_COOLDOWN);
		return true;
	}

	/** The flame ring: {@code hold} keeps it going while the key is held (see {@link #ringStop}); otherwise it lasts its time. */
	public static boolean ring(ServerPlayer player, boolean hold) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring != null && !hold) {
			RINGS.remove(player.getUUID());
			return false;
		}
		if (ring != null || !ready(player, RING)) {
			return false;
		}
		ring = new Ring();
		ring.hold = hold;
		ring.until = player.level().getGameTime() + (hold ? RING_HOLD_TICKS : RING_TICKS);
		RINGS.put(player.getUUID(), ring);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 1.2F, 0.8F);
		return true;
	}

	/** A held ring is let go. */
	public static void ringStop(ServerPlayer player) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring != null && ring.hold) {
			RINGS.remove(player.getUUID());
			cooldown(player, RING, RING_COOLDOWN);
		}
	}

	/** Rays of fire one after another along the look. */
	public static boolean rays(ServerPlayer player) {
		if (!ready(player, RAYS_AT)) {
			return false;
		}
		RAYS.add(new Rays(player, RAY_COUNT, player.level().getGameTime()));
		cooldown(player, RAYS_AT, RAYS_COOLDOWN);
		return true;
	}

	/** A column of fire from the sky onto the spot the player points at, after a warning. */
	public static boolean column(ServerPlayer player) {
		if (!ready(player, COLUMN)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 at = aim(player, COLUMN_REACH);
		COLUMNS.add(new Column(level, player, at, level.getGameTime() + COLUMN_DELAY));
		MagicFx.sigil(level, at.add(0.0, 0.1, 0.0), (float)COLUMN_RADIUS, COLOR, COLUMN_DELAY);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
		cooldown(player, COLUMN, COLUMN_COOLDOWN);
		return true;
	}

	/** The Flame Avatar's V: a tap raises the wall, a hold keeps the ring. */
	public static void avatarKey(ServerPlayer player, boolean down, boolean hold) {
		if (down || !FlameAvatar.is(player)) {
			return;
		}
		if (hold) {
			ringStop(player);
		} else {
			wall(player);
		}
	}

	public static boolean ringing(ServerPlayer player) {
		return RINGS.containsKey(player.getUUID());
	}

	// ---- ticking ----

	private static void tick(MinecraftServer server) {
		for (Iterator<Wall> it = WALLS.iterator(); it.hasNext();) {
			if (wallStep(it.next())) {
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Ring>> it = RINGS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Ring> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || owner.level().getGameTime() >= entry.getValue().until) {
				it.remove();
				if (owner != null) {
					cooldown(owner, RING, RING_COOLDOWN);
				}
			} else {
				ringStep(owner);
			}
		}
		for (Iterator<Rays> it = RAYS.iterator(); it.hasNext();) {
			Rays rays = it.next();
			long now = rays.owner.level().getGameTime();
			if (!rays.owner.isAlive()) {
				it.remove();
			} else if (now >= rays.next) {
				ray(rays.owner);
				rays.next = now + RAY_GAP;
				if (--rays.left <= 0) {
					it.remove();
				}
			}
		}
		for (Iterator<Column> it = COLUMNS.iterator(); it.hasNext();) {
			Column column = it.next();
			if (column.level.getGameTime() >= column.due) {
				fall(column);
				it.remove();
			}
		}
		if (server.getTickCount() % CHAIN_EVERY == 0) {
			chain(server);
		}
	}

	private static boolean wallStep(Wall wall) {
		ServerLevel level = wall.level;
		long now = level.getGameTime();
		if (now >= wall.until || !wall.owner.isAlive()) {
			return true;
		}
		if (now % 3 == 0) {
			for (int i = 0; i < WALL_WIDTH; i++) {
				Vec3 foot = wall.end.add(wall.along.scale(i + level.getRandom().nextDouble()));
				level.sendParticles(ParticleTypes.FLAME, foot.x, foot.y + 0.2, foot.z, 2, 0.1, WALL_HEIGHT * 0.4, 0.1, 0.02);
			}
		}
		if (now % 12 == 0) {
			MagicFx.wave(level, wall.end.add(0.0, 0.5 + level.getRandom().nextInt(WALL_HEIGHT - 1), 0.0), wall.along, WALL_WIDTH, 0.7F, COLOR, 10);
		}
		if (now % 5 == 0) {
			boolean damage = now % HIT_EVERY == 0;
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, wall.box, e -> foe(wall.owner, e))) {
				Flames.touch(level, living, WALL_BURN, wall.owner);
				credit(living, wall.owner);
				if (damage) {
					hurt(wall.owner, living, WALL_DAMAGE);
				}
			}
		}
		return false;
	}

	private static void ringStep(ServerPlayer owner) {
		ServerLevel level = owner.level();
		long now = level.getGameTime();
		double spin = now * 0.35;
		for (int i = 0; i < 6; i++) {
			double angle = spin + Math.PI * 2.0 * i / 6.0;
			level.sendParticles(ParticleTypes.FLAME, owner.getX() + Math.cos(angle) * RING_RADIUS, owner.getY() + 0.5 + (i % 2) * 0.6,
				owner.getZ() + Math.sin(angle) * RING_RADIUS, 1, 0.05, 0.1, 0.05, 0.01);
		}
		if (now % 12 == 0) {
			MagicFx.ring(level, owner.position().add(0.0, 0.2, 0.0), (float)RING_RADIUS, COLOR);
		}
		if (now % 20 == 0) {
			owner.causeFoodExhaustion(0.2F);
		}
		if (now % 5 != 0) {
			return;
		}
		boolean damage = now % HIT_EVERY == 0;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(RING_RADIUS + RING_BAND), e -> foe(owner, e))) {
			Vec3 out = living.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
			double distance = out.length();
			if (distance < RING_RADIUS - RING_BAND || distance > RING_RADIUS + RING_BAND) {
				continue;
			}
			Flames.touch(level, living, RING_BURN, owner);
			if (damage) {
				hurt(owner, living, RING_DAMAGE);
			}
			if (distance > 1.0E-3) {
				living.push(out.normalize().scale(RING_PUSH).add(0.0, 0.1, 0.0));
				living.syncVelocity = true;
			}
		}
	}

	private static void ray(ServerPlayer owner) {
		ServerLevel level = owner.level();
		Vec3 eye = owner.getEyePosition();
		Vec3 look = owner.getLookAngle();
		Vec3 end = aim(owner, RAY_REACH);
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> foe(owner, e))) {
			var hit = living.getBoundingBox().inflate(0.4).clip(eye, end);
			if (hit.isPresent() && eye.distanceToSqr(hit.get()) < best) {
				best = eye.distanceToSqr(hit.get());
				target = living;
				end = hit.get();
			}
		}
		Vec3 from = eye.add(look.scale(0.8)).subtract(0.0, 0.25, 0.0);
		MagicFx.beam(level, from, end, 0.14F, COLOR, 6);
		MagicFx.blast(level, end, 0.9F, WHITE);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.5F);
		if (target != null) {
			hurt(owner, target, RAY_DAMAGE);
			Flames.touch(level, target, RAY_BURN, owner);
		}
	}

	private static void fall(Column column) {
		ServerLevel level = column.level;
		ServerPlayer owner = column.owner;
		Vec3 at = column.at;
		MagicFx.beam(level, at.add(0.0, COLUMN_FROM, 0.0), at, 1.1F, WHITE, 8);
		MagicFx.blast(level, at.add(0.0, 0.5, 0.0), (float)COLUMN_RADIUS, COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		if (owner.isAlive()) {
			Shakes.heavy(owner, at, 16.0, 0.7F);
		}
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(COLUMN_RADIUS, COLUMN_FROM * 0.4, COLUMN_RADIUS), e -> foe(owner, e))) {
			hurt(owner, living, COLUMN_DAMAGE);
			Flames.touch(level, living, COLUMN_BURN, owner);
			living.push(0.0, 0.5, 0.0);
			living.syncVelocity = true;
		}
		for (int i = 0; i < 5; i++) {
			BlockPos pos = BlockPos.containing(at.x + level.getRandom().nextInt(5) - 2, at.y, at.z + level.getRandom().nextInt(5) - 2);
			if (fireOk(level, owner, pos)) {
				EmberKindle.ignite(level, pos);
			}
		}
	}

	/** Fire that jumps: each creature burning from a player's fire passes it on once to the nearest unlit foe. */
	private static void chain(MinecraftServer server) {
		if (CREDITS.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<Map.Entry<UUID, Credit>> entries = new ArrayList<>(CREDITS.entrySet());
		for (Map.Entry<UUID, Credit> entry : entries) {
			Credit credit = entry.getValue();
			if (now - credit.time > CREDIT_TICKS) {
				CREDITS.remove(entry.getKey());
				continue;
			}
			if (credit.jumped || credit.generation >= CHAIN_GENERATIONS || !(server.getPlayerList().getPlayer(credit.owner) instanceof ServerPlayer owner)) {
				continue;
			}
			ServerLevel level = owner.level();
			if (!(level.getEntity(entry.getKey()) instanceof LivingEntity source) || !source.isOnFire()) {
				continue;
			}
			LivingEntity next = null;
			double best = CHAIN_RANGE * CHAIN_RANGE;
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(CHAIN_RANGE), e -> e != source && !e.isOnFire() && foe(owner, e))) {
				if (near.distanceToSqr(source) < best) {
					best = near.distanceToSqr(source);
					next = near;
				}
			}
			if (next == null) {
				continue;
			}
			credit.jumped = true;
			Flames.touch(level, next, CHAIN_BURN, owner);
			credit(next, owner, credit.generation + 1);
			MagicFx.stroke(level, source.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.12F, COLOR);
			level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
		}
	}

	/** A creature burning from a player's fire died: a small blast lights its neighbours. */
	private static void deathBurst(ServerLevel level, ServerPlayer owner, LivingEntity dead) {
		Vec3 at = dead.getBoundingBox().getCenter();
		MagicFx.blast(level, at, 2.2F, COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
		depth++;
		try {
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS), e -> e != dead && foe(owner, e))) {
				Flames.touch(level, near, BURST_BURN, owner);
				hurt(owner, near, BURST_DAMAGE);
			}
		} finally {
			depth--;
		}
	}
}
