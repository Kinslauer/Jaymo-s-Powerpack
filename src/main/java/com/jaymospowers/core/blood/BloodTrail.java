package com.jaymospowers.core.blood;

import com.jaymospowers.JaymosPowers;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.182) Andy: blood that falls "needs to stay on the ground where it falls, allowing for players and mobs to track
 * it". The server keeps every stain ({@link Mark}): where it lies, whose blood it is and when it was spilt. Each player
 * near one is sent it once (a {@link BloodParticles.Puddle} carrying the mark's id, age and life), so it lies there for
 * {@link #LIFE}, darkening as it dries, for anyone who comes by later; a body that keeps bleeding on one spot grows the
 * stain there instead of stacking new ones. Hunting mobs ({@link #TRACKERS}) follow a living bleeder's stains from the
 * oldest to the freshest ({@link BloodTracking}).
 */
public final class BloodTrail {
	/** How long a stain lies (ticks), the last {@link #FADE} of them fading away. */
	public static final int LIFE = 12000, FADE = 1200;
	/** How often players are sent the stains near them (ticks), and how near (blocks). */
	static final int SYNC_TICKS = 10;
	static final double SYNC_RANGE = 64.0;
	/** Blood landing this close (blocks across, and up or down) to a stain soaks into it, which grows this much, to this size. */
	static final double MERGE = 0.45, MERGE_UP = 0.5;
	static final float GROW = 0.25F, MAX_SCALE = 1.8F;
	/** How far under a bleeder blood can fall to the ground (blocks). */
	static final double FALL = 6.0;
	/** The most stains a level keeps (the oldest go first). */
	static final int MAX_MARKS = 6000;

	/** (0.17.182) Mobs that follow blood trails to whoever left them. */
	public static final TagKey<EntityType<?>> TRACKERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("blood_trackers"));

	/** One stain on the ground. {@code by} bled it (null: a body that was already dead). */
	public static final class Mark {
		final long id;
		public final double x, y, z;
		final int color;
		final @Nullable UUID by;
		float scale;
		/** When it was spilt (or last bled on), and the game time players may first see it (a drop still falling). */
		public long laid;
		long showFrom;
		long expires;
		/** Counts its changes, so a grown stain is sent again. */
		int version;

		Mark(long id, double x, double y, double z, int color, @Nullable UUID by, float scale, long now, long delay) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.by = by;
			this.scale = scale;
			this.laid = now;
			this.showFrom = now + delay;
			this.expires = now + LIFE;
		}

		public @Nullable UUID by() {
			return this.by;
		}

		public Vec3 position() {
			return new Vec3(this.x, this.y, this.z);
		}
	}

	/** One level's stains, by chunk, oldest first. */
	private static final class Marks {
		final Long2ObjectOpenHashMap<List<Mark>> byChunk = new Long2ObjectOpenHashMap<>();
		final ArrayDeque<Mark> order = new ArrayDeque<>();
		long nextId = 1;
	}

	/** Which stains (by id, at which version) a player has been sent, in which level. */
	private static final class Sent {
		final ServerLevel level;
		final Long2IntOpenHashMap versions = new Long2IntOpenHashMap();

		Sent(ServerLevel level) {
			this.level = level;
			this.versions.defaultReturnValue(-1);
		}
	}

	private static final Map<ServerLevel, Marks> LEVELS = new WeakHashMap<>();
	private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

	private BloodTrail() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BloodTrail::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && entity.getType().builtInRegistryHolder().is(TRACKERS)) {
				mob.getGoalSelector().addGoal(BloodTracking.PRIORITY, new BloodTracking(mob));
			}
		});
	}

	/**
	 * {@code count} stains of {@code body}'s blood (a living one's, to be tracked; or a dead body's), this big, on the ground within {@code spread} blocks of where it stands,
	 * shown after {@code delay} ticks (the time the drop takes to fall). Nothing lands in water or lava.
	 */
	public static void bleed(ServerLevel level, Entity body, int color, int count, double spread, float scale, int delay) {
		UUID by = body instanceof LivingEntity living && living.isAlive() ? body.getUUID() : null;
		for (int i = 0; i < count; i++) {
			double x = body.getX() + (level.getRandom().nextDouble() * 2.0 - 1.0) * spread;
			double z = body.getZ() + (level.getRandom().nextDouble() * 2.0 - 1.0) * spread;
			lay(level, by, color, x, body.getY() + 0.5, z, scale, delay);
		}
	}

	/** A stain of this colour and size on the ground under (x, from, z), bled by {@code by}; null if it found no ground. */
	public static @Nullable Mark lay(ServerLevel level, @Nullable UUID by, int color, double x, double from, double z, float scale, int delay) {
		Vec3 top = new Vec3(x, from, z);
		BlockHitResult hit = level.clip(new ClipContext(top, top.subtract(0.0, FALL, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
			CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
			return null;
		}
		Vec3 at = hit.getLocation();
		long now = level.getGameTime();
		Marks marks = LEVELS.computeIfAbsent(level, l -> new Marks());
		List<Mark> chunk = marks.byChunk.computeIfAbsent(ChunkPos.pack(BlockPos.containing(at)), c -> new ArrayList<>());
		for (Mark mark : chunk) {
			if (Math.abs(mark.x - at.x) < MERGE && Math.abs(mark.z - at.z) < MERGE && Math.abs(mark.y - at.y) < MERGE_UP
				&& java.util.Objects.equals(mark.by, by)) {
				mark.scale = Math.max(Math.max(mark.scale, scale), Math.min(MAX_SCALE, mark.scale + GROW * scale));
				mark.laid = now;
				mark.expires = now + LIFE;
				mark.version++;
				return mark;
			}
		}
		Mark mark = new Mark(marks.nextId++, at.x, at.y, at.z, color, by, scale, now, delay);
		chunk.add(mark);
		marks.order.addLast(mark);
		while (marks.order.size() > MAX_MARKS) {
			forget(marks, marks.order.pollFirst());
		}
		return mark;
	}

	private static void forget(Marks marks, Mark mark) {
		long key = ChunkPos.pack(BlockPos.containing(mark.x, mark.y, mark.z));
		List<Mark> chunk = marks.byChunk.get(key);
		if (chunk != null) {
			chunk.remove(mark);
			if (chunk.isEmpty()) {
				marks.byChunk.remove(key);
			}
		}
	}

	/** Every stain within {@code range} of {@code at} that {@code test} likes. */
	public static List<Mark> near(ServerLevel level, Vec3 at, double range, Predicate<Mark> test) {
		List<Mark> found = new ArrayList<>();
		Marks marks = LEVELS.get(level);
		if (marks == null) {
			return found;
		}
		long now = level.getGameTime();
		double rangeSqr = range * range;
		int r = Mth.ceil(range / 16.0);
		int cx = Mth.floor(at.x) >> 4, cz = Mth.floor(at.z) >> 4;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				List<Mark> chunk = marks.byChunk.get(ChunkPos.pack(cx + dx, cz + dz));
				if (chunk == null) {
					continue;
				}
				for (Mark mark : chunk) {
					if (mark.expires > now && mark.showFrom <= now && at.distanceToSqr(mark.x, mark.y, mark.z) <= rangeSqr && test.test(mark)) {
						found.add(mark);
					}
				}
			}
		}
		return found;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SYNC_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Marks marks = LEVELS.get(level);
			if (marks == null) {
				continue;
			}
			long now = level.getGameTime();
			List<Long> gone = new ArrayList<>();
			for (Mark mark : marks.order) {
				if (mark.expires <= now) {
					gone.add(mark.id);
				}
			}
			if (!gone.isEmpty()) {
				marks.order.removeIf(mark -> {
					if (mark.expires <= now) {
						forget(marks, mark);
						return true;
					}
					return false;
				});
				SENT.values().forEach(sent -> gone.forEach(sent.versions::remove));
			}
			if (marks.order.isEmpty()) {
				LEVELS.remove(level);
				continue;
			}
			for (ServerPlayer player : level.players()) {
				send(level, player, now);
			}
		}
	}

	/** Sends {@code player} the stains near it it hasn't seen yet (or seen smaller). */
	private static void send(ServerLevel level, ServerPlayer player, long now) {
		Sent sent = SENT.get(player);
		if (sent == null || sent.level != level) {
			sent = new Sent(level);
			SENT.put(player, sent);
		}
		for (Mark mark : near(level, player.position(), SYNC_RANGE, mark -> true)) {
			if (sent.versions.get(mark.id) == mark.version) {
				continue;
			}
			sent.versions.put(mark.id, mark.version);
			int age = (int) Math.max(0L, now - mark.showFrom);
			int life = (int) Math.max(1L, mark.expires - now);
			level.sendParticles(player, new BloodParticles.Puddle(mark.color, mark.scale, mark.id, age, life), true, true, mark.x, mark.y,
				mark.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The freshest of {@code by}'s stains within {@code range} of {@code from} spilt after {@code after}, or null. */
	public static @Nullable Mark fresher(ServerLevel level, Vec3 from, double range, UUID by, long after) {
		Mark best = null;
		for (Mark mark : near(level, from, range, mark -> by.equals(mark.by) && mark.laid > after)) {
			// the next one along: the oldest of the fresher ones, so the trail is followed step by step
			if (best == null || mark.laid < best.laid) {
				best = mark;
			}
		}
		return best;
	}

	/** Whether {@code entity} is in this level and alive. */
	static @Nullable LivingEntity bleeder(ServerLevel level, @Nullable UUID by) {
		if (by == null) {
			return null;
		}
		Entity entity = level.getEntity(by);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}
}
