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
 * <p>(0.20.41, Hematic's idea) Whoever walks through a fresh pool carries it on their feet for a few steps
 * ({@link #print}): a line of footprints, left and right, pointing the way they went, fainter as it wears off. The prints
 * are the walker's (tracked like their blood).
 * <p>(0.20.54) Andy: "rain should wash away blood". A stain out in the rain shrinks by {@link #RAIN_WASH} each sync
 * ({@link #wash}), so a big pool is gone in about half a minute, a drip's in a few seconds; under a roof or the trees it
 * stays. A washed stain stops being a trail to follow.
 */
public final class BloodTrail {
	/** How long a stain lies (ticks), the last {@link #FADE} of them fading away. */
	public static final int LIFE = 12000, FADE = 1200;
	/** (0.20.54) How much of a stain's size the rain washes away each {@link #SYNC_TICKS}. */
	static final float RAIN_WASH = 0.03F;
	/** How often players are sent the stains near them (ticks), and how near (blocks). */
	static final int SYNC_TICKS = 10;
	static final double SYNC_RANGE = 64.0;
	/** Blood landing this close (blocks across, and up or down) to a stain soaks into it, which grows this much, to this size. */
	static final double MERGE = 0.45, MERGE_UP = 0.5;
	static final float GROW = 0.25F, MAX_SCALE = 1.8F;
	/**
	 * How far under a bleeder blood can fall to the ground (blocks). (0.21.50) Andy: "a bleeding body above a ledge can drip
	 * a pool up to 20 blocks below it". A stain that far down shows once the drop could have fallen there (a drop falls
	 * {@link #DROP_FALL} blocks a tick per tick, the client's {@code BloodDrop}).
	 */
	static final double FALL = 20.0, DROP_FALL = 0.06;
	/** The most stains a level keeps (the oldest go first). */
	static final int MAX_MARKS = 6000;
	/** (0.19.62) Blood magic ({@link #draw}): a stain drawn smaller than this dries away, gone this many ticks later. */
	static final float DRAWN_DRY = 0.08F;
	static final int DRAWN_GONE = 20;

	/**
	 * (0.20.41) Footprints: how often walkers are checked (ticks); a pool this big or more, spilt less than this many ticks
	 * ago, wets the feet for this many prints, one every this far (blocks), this big (a man's; scaled by width), set this
	 * far either side of the path.
	 */
	static final int PRINT_TICKS = 4, FRESH_FOR = 4200, PRINTS = 10;
	static final float MIN_POOL = 0.3F, PRINT_SCALE = 0.26F;
	static final double STRIDE = 0.8, PRINT_SIDE = 0.12, WALKERS_RANGE = 40.0;
	/**
	 * (0.21.28) Walking through a pool: how much of it each foot set in it carries off (stain size, each check; twice that
	 * running), at least how fast it must be moving (blocks a tick), the most a step smears out of what it carries, and a
	 * skid's size next to a print's.
	 */
	static final float WALK_SOAK = 0.05F, STEP_SPILL = 0.18F, SKID = 1.4F;
	static final double WALK_MOVING = 0.03;

	/** (0.17.182) Mobs that follow blood trails to whoever left them. */
	public static final TagKey<EntityType<?>> TRACKERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("blood_trackers"));

	/** One stain on the ground. {@code by} bled it (null: a body that was already dead). */
	public static final class Mark {
		final long id;
		public final double x, y, z;
		final int color;
		final @Nullable UUID by;
		float scale;
		/** (0.19.55) Round, or a smear along one axis ({@link BloodParticles.Puddle#shape}). */
		final int shape;
		/** When it was spilt (or last bled on), and the game time players may first see it (a drop still falling). */
		public long laid;
		long showFrom;
		long expires;
		/** Counts its changes, so a grown stain is sent again. */
		int version;

		Mark(long id, double x, double y, double z, int color, @Nullable UUID by, float scale, long now, long delay, int shape) {
			this.id = id;
			this.shape = shape;
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

	/** (0.20.41) Bloody feet: prints left to lay, their colour, where the last one fell, how far along the next is, which foot. */
	private static final class Feet {
		int left;
		int color;
		/** (0.21.28) Blood picked up off a pool walked through, smeared out over the next steps before the prints. */
		float wet;
		Vec3 last;
		double carry;
		boolean right;

		Feet(Vec3 at) {
			this.last = at;
		}
	}

	private static final Map<LivingEntity, Feet> FEET = new WeakHashMap<>();

	private BloodTrail() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BloodTrail::tick);
		// (0.21.28) the downed and the dead bleed out into pools
		BleedOut.init();
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
		return lay(level, by, color, x, from, z, scale, delay, BloodParticles.Puddle.ROUND);
	}

	/**
	 * (0.19.55) Andy liked Death Engine's blood trail behind a dragged body (ideas only). A smear of {@code body}'s blood
	 * (tracked like any stain if it's alive) dragged along the ground from {@code from} to {@code to}: one long, narrow
	 * stain lying the way it was dragged, this wide. Smears never soak into the stains around them, so a dragged body
	 * leaves an unbroken streak rather than one growing pool.
	 */
	public static @Nullable Mark smear(ServerLevel level, Entity body, int color, Vec3 from, Vec3 to, float scale) {
		UUID by = body instanceof LivingEntity living && living.isAlive() ? body.getUUID() : null;
		double dx = to.x - from.x, dz = to.z - from.z;
		int shape = Math.abs(dx) >= Math.abs(dz) ? BloodParticles.Puddle.SMEAR_X : BloodParticles.Puddle.SMEAR_Z;
		return lay(level, by, color, (from.x + to.x) * 0.5, Math.max(from.y, to.y) + 0.5, (from.z + to.z) * 0.5, scale, 0, shape);
	}

	private static @Nullable Mark lay(ServerLevel level, @Nullable UUID by, int color, double x, double from, double z, float scale, int delay,
		int shape) {
		Vec3 top = new Vec3(x, from, z);
		BlockHitResult hit = level.clip(new ClipContext(top, top.subtract(0.0, FALL, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
			CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
			return null;
		}
		Vec3 at = hit.getLocation();
		if (delay > 0) {
			// (0.21.50) a long drop takes longer to land
			delay = Math.max(delay, Mth.ceil(Math.sqrt(2.0 * Math.max(0.0, from - 0.5 - at.y) / DROP_FALL)));
		}
		long now = level.getGameTime();
		Marks marks = LEVELS.computeIfAbsent(level, l -> new Marks());
		List<Mark> chunk = marks.byChunk.computeIfAbsent(ChunkPos.pack(BlockPos.containing(at)), c -> new ArrayList<>());
		for (Mark mark : shape != BloodParticles.Puddle.ROUND ? List.<Mark>of() : chunk) {
			if (mark.shape == BloodParticles.Puddle.ROUND && Math.abs(mark.x - at.x) < MERGE && Math.abs(mark.z - at.z) < MERGE && Math.abs(mark.y - at.y) < MERGE_UP
				&& java.util.Objects.equals(mark.by, by)) {
				mark.scale = Math.max(Math.max(mark.scale, scale), Math.min(MAX_SCALE, mark.scale + GROW * scale));
				mark.laid = now;
				mark.expires = now + LIFE;
				mark.version++;
				return mark;
			}
		}
		Mark mark = new Mark(marks.nextId++, at.x, at.y, at.z, color, by, scale, now, delay, shape);
		chunk.add(mark);
		marks.order.addLast(mark);
		while (marks.order.size() > MAX_MARKS) {
			forget(marks, marks.order.pollFirst());
		}
		return mark;
	}

	/**
	 * (0.21.28) Andy: "blood should pool under the downed and corpses until they bleed out". Pours {@code amount} of
	 * {@code by}'s blood (stain size) on the ground under (x, from, z): into the round pool of theirs already lying there
	 * (it grows by exactly that, up to {@link #MAX_SCALE}), or a new one that small. Returns how much went in: less than
	 * {@code amount} when the pool there is full, nothing when it's full or there's no dry ground (water, lava, a drop
	 * over {@link #FALL}). So what a body pours is all there is to draw back up ({@link #draw}): a body's blood is finite.
	 */
	static float pour(ServerLevel level, @Nullable UUID by, int color, double x, double from, double z, float amount) {
		if (amount <= 0.0F) {
			return 0.0F;
		}
		Vec3 top = new Vec3(x, from, z);
		BlockHitResult hit = level.clip(new ClipContext(top, top.subtract(0.0, FALL, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
			CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
			return 0.0F;
		}
		Vec3 at = hit.getLocation();
		long now = level.getGameTime();
		Marks marks = LEVELS.computeIfAbsent(level, l -> new Marks());
		List<Mark> chunk = marks.byChunk.computeIfAbsent(ChunkPos.pack(BlockPos.containing(at)), c -> new ArrayList<>());
		for (Mark mark : chunk) {
			if (mark.shape == BloodParticles.Puddle.ROUND && mark.expires > now && Math.abs(mark.x - at.x) < MERGE && Math.abs(mark.z - at.z) < MERGE
				&& Math.abs(mark.y - at.y) < MERGE_UP && java.util.Objects.equals(mark.by, by)) {
				float in = Math.min(amount, MAX_SCALE - mark.scale);
				if (in <= 0.0F) {
					return 0.0F;
				}
				mark.scale += in;
				mark.laid = now;
				mark.expires = now + LIFE;
				mark.version++;
				return in;
			}
		}
		Mark mark = new Mark(marks.nextId++, at.x, at.y, at.z, color, by, Math.min(amount, MAX_SCALE), now, 0, BloodParticles.Puddle.ROUND);
		chunk.add(mark);
		marks.order.addLast(mark);
		while (marks.order.size() > MAX_MARKS) {
			forget(marks, marks.order.pollFirst());
		}
		return mark.scale;
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
		if (server.getTickCount() % PRINT_TICKS == 0) {
			FEET.keySet().removeIf(body -> !body.isAlive());
			for (ServerLevel level : server.getAllLevels()) {
				walk(level);
			}
		}
		if (server.getTickCount() % SYNC_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Marks marks = LEVELS.get(level);
			if (marks == null) {
				continue;
			}
			long now = level.getGameTime();
			if (level.isRaining()) {
				wash(level, marks, now);
			}
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

	// ---- footprints (0.20.41) ------------------------------------------------------------------------------------------

	/** Everyone walking near a player: feet wet in a pool, and bloody feet leaving prints. */
	private static void walk(ServerLevel level) {
		Marks marks = LEVELS.get(level);
		boolean anyFeet = false;
		for (LivingEntity body : FEET.keySet()) {
			if (body.level() == level) {
				anyFeet = true;
				break;
			}
		}
		if (marks == null && !anyFeet) {
			return;
		}
		java.util.Set<LivingEntity> seen = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WALKERS_RANGE), BloodTrail::walker)) {
				if (seen.add(body)) {
					step(level, body);
				}
			}
		}
	}

	/** Something with feet, on its feet on dry ground. */
	private static boolean walker(LivingEntity body) {
		return body.isAlive() && body.onGround() && !body.isInWater() && !body.isSpectator()
			&& !(body instanceof net.minecraft.world.entity.monster.cubemob.Slime) && body.getBbWidth() <= 3.0F;
	}

	private static void step(ServerLevel level, LivingEntity body) {
		Vec3 at = body.position();
		long now = level.getGameTime();
		Feet feet = FEET.get(body);
		if (feet != null && feet.left > 0) {
			print(level, body, feet, at);
		}
		// a fresh pool underfoot wets the feet again
		Mark pool = null;
		for (Mark mark : near(level, at, 0.75 * MAX_SCALE * 1.5 + 0.2, mark -> mark.shape == BloodParticles.Puddle.ROUND && mark.scale >= MIN_POOL
			&& now - mark.laid < FRESH_FOR && Math.abs(mark.y - at.y) < 0.6
			&& Math.hypot(mark.x - at.x, mark.z - at.z) < 0.75 * mark.scale + 0.15)) {
			pool = mark;
			break;
		}
		if (pool != null) {
			if (feet == null) {
				feet = new Feet(at);
				FEET.put(body, feet);
			} else if (feet.left <= 0) {
				feet.last = at;
				feet.carry = 0.0;
			}
			feet.left = PRINTS;
			feet.color = pool.color;
			// (0.21.28) a foot through it splashes it on: blood carried off to skid out over the next steps
			Vec3 v = body.getDeltaMovement();
			if (v.x * v.x + v.z * v.z > WALK_MOVING * WALK_MOVING) {
				float soak = WALK_SOAK * (body.isSprinting() ? 2.0F : 1.0F);
				feet.wet += soak(level, at, 0.75 * pool.scale + 0.15, soak, null);
			}
		} else if (feet != null && feet.left <= 0) {
			FEET.remove(body);
		}
	}

	/**
	 * (0.20.41) Andy liked Hematic's stains that "smear and transfer on contact". Lays {@code body}'s bloody footprints
	 * along the ground it has walked since the last one, a stride apart, left and right of its path, pointing the way it
	 * went.
	 */
	private static void print(ServerLevel level, LivingEntity body, Feet feet, Vec3 at) {
		double dx = at.x - feet.last.x, dz = at.z - feet.last.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > STRIDE * 6.0) {
			// a jump, a teleport, a fall: start again from here
			feet.last = at;
			feet.carry = 0.0;
			return;
		}
		if (dist < 1.0E-3) {
			return;
		}
		double ux = dx / dist, uz = dz / dist;
		// the way it points, in sixteenths of a turn (0 = south, as yaw)
		int dir = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-ux, uz)) / 22.5F), 16);
		float size = PRINT_SCALE * Mth.clamp(body.getBbWidth() / 0.6F, 0.7F, 2.5F);
		UUID by = body.getUUID();
		double d = STRIDE - feet.carry;
		while (d <= dist && feet.left > 0) {
			double side = (feet.right ? -1.0 : 1.0) * PRINT_SIDE;
			double x = feet.last.x + ux * d + uz * side, z = feet.last.z + uz * d - ux * side;
			int shape = BloodParticles.Puddle.PRINT + dir + (feet.right ? BloodParticles.Puddle.PRINT_RIGHT : 0);
			// fainter (smaller) as the blood wears off the soles
			float scale = size * (0.65F + 0.35F * feet.left / (float) PRINTS);
			if (feet.wet > 0.02F) {
				// (0.21.28) still dripping from the pool: a skid along the way it went, wider for what it carries
				float spill = Math.min(feet.wet, STEP_SPILL);
				feet.wet -= spill;
				shape = Math.abs(ux) >= Math.abs(uz) ? BloodParticles.Puddle.SMEAR_X : BloodParticles.Puddle.SMEAR_Z;
				scale = size * SKID + spill;
			}
			lay(level, by, feet.color, x, at.y + 0.5, z, scale, 0, shape);
			feet.left--;
			feet.right = !feet.right;
			d += STRIDE;
		}
		feet.carry = dist - (d - STRIDE);
		feet.last = at;
	}

	/**
	 * (0.20.54) The rain washes every stain it falls on (loaded ground only) smaller; one washed down past
	 * {@link #DRAWN_DRY} is gone in a moment, as when blood magic draws one dry. Players near are sent it smaller.
	 */
	private static void wash(ServerLevel level, Marks marks, long now) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Mark mark : marks.order) {
			if (mark.scale <= 0.0F || now < mark.showFrom) {
				continue;
			}
			pos.set(mark.x, mark.y + 0.5, mark.z);
			if (!level.isLoaded(pos) || !level.isRainingAt(pos)) {
				continue;
			}
			mark.scale -= RAIN_WASH;
			if (mark.scale < DRAWN_DRY) {
				mark.scale = 0.0F;
				mark.expires = Math.min(mark.expires, now + DRAWN_GONE);
			}
			mark.version++;
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
			level.sendParticles(player, new BloodParticles.Puddle(mark.color, mark.scale, mark.id, age, life, mark.shape), true, true, mark.x, mark.y,
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

	/**
	 * For Jaymo's Kingdoms' hunters (looked up by reflection, so plain types only): with {@code by} null, the nearest
	 * stain within {@code range} of {@code from} whose living bleeder {@code prey} accepts; otherwise the next of
	 * {@code by}'s stains spilt after {@code after} ({@link #fresher}). As {@code {Vec3 at, UUID by, Long laid}}, or null.
	 */
	public static Object @Nullable [] follow(ServerLevel level, Vec3 from, double range, @Nullable UUID by, long after,
		Predicate<LivingEntity> prey) {
		Mark found;
		if (by != null) {
			found = fresher(level, from, range, by, after);
		} else {
			found = null;
			double best = Double.MAX_VALUE;
			for (Mark mark : near(level, from, range, mark -> {
				LivingEntity bleeder = bleeder(level, mark.by);
				return bleeder != null && prey.test(bleeder);
			})) {
				double d = from.distanceToSqr(mark.x, mark.y, mark.z);
				if (d < best) {
					best = d;
					found = mark;
				}
			}
		}
		return found == null || found.by == null ? null : new Object[] {found.position(), found.by, found.laid};
	}

	/**
	 * (0.19.62) Andy: "Blood magic should draw from stains on ground". Draws the blood out of the stains within
	 * {@code range} of {@code at}, the nearest first: each gives up to {@code each} of its size, until {@code most} is
	 * drawn in all. A drawn stain shrinks (each player near is sent it smaller, and it eases down), and one drawn down
	 * past {@link #DRAWN_DRY} dries away. {@code drawn} hears where each one lay, for the blood's path to whoever drew
	 * it. Returns how much was drawn, in stain size (a drip's stain is about 0.2, a pool 1 to 3). Plain types only:
	 * Jaymo's Kingdoms' blood magic calls it by reflection.
	 */
	public static float draw(ServerLevel level, Vec3 at, double range, float each, float most, java.util.function.@Nullable Consumer<Vec3> drawn) {
		if (each <= 0.0F || most <= 0.0F) {
			return 0.0F;
		}
		List<Mark> found = near(level, at, range, mark -> mark.scale > 0.0F);
		found.sort(java.util.Comparator.comparingDouble(mark -> at.distanceToSqr(mark.x, mark.y, mark.z)));
		long now = level.getGameTime();
		float total = 0.0F;
		for (Mark mark : found) {
			float take = Math.min(Math.min(each, mark.scale), most - total);
			if (take <= 0.0F) {
				break;
			}
			mark.scale -= take;
			if (mark.scale < DRAWN_DRY) {
				// drawn dry: gone in a moment (the last of it, too little to hold the eye, counts as drawn)
				take += mark.scale;
				mark.scale = 0.0F;
				mark.expires = Math.min(mark.expires, now + DRAWN_GONE);
			}
			mark.version++;
			total += take;
			if (drawn != null) {
				drawn.accept(mark.position());
			}
		}
		return total;
	}

	/**
	 * (0.21.28) Andy: "dragging and walking through puddles should smear more than the regular trails". Takes up to
	 * {@code most} (stain size) out of the round pools within {@code range} of {@code at} (anyone's, the nearest first),
	 * the blood a body dragged or a foot set through them carries off to smear further on. A pool taken down past
	 * {@link #DRAWN_DRY} dries away. {@code color} (if given) hears the colour of the last pool it took from. Returns how
	 * much was taken: blood moved, never made, so a body's blood stays finite.
	 */
	public static float soak(ServerLevel level, Vec3 at, double range, float most, int @Nullable [] color) {
		if (most <= 0.0F) {
			return 0.0F;
		}
		List<Mark> found = near(level, at, range, mark -> mark.shape == BloodParticles.Puddle.ROUND && mark.scale > 0.0F
			&& Math.abs(mark.y - at.y) < 0.6);
		found.sort(java.util.Comparator.comparingDouble(mark -> at.distanceToSqr(mark.x, mark.y, mark.z)));
		long now = level.getGameTime();
		float total = 0.0F;
		for (Mark mark : found) {
			float take = Math.min(mark.scale, most - total);
			if (take <= 0.0F) {
				break;
			}
			mark.scale -= take;
			if (mark.scale < DRAWN_DRY) {
				take += mark.scale;
				mark.scale = 0.0F;
				mark.expires = Math.min(mark.expires, now + DRAWN_GONE);
			}
			mark.version++;
			total += take;
			if (color != null) {
				color[0] = mark.color;
			}
		}
		return total;
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
