package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Powers;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke as a real gas, block by block (our own take on volumetric smoke; Physics Mod's is closed). Every block of air
 * holds some amount of smoke (1 is thick enough that you can't see through it). Every {@link #STEP} ticks each block's
 * smoke:
 * <ul>
 * <li>rises: a share climbs into the block above, so it pools under ceilings and fills rooms from the top down;</li>
 * <li>spreads: it flows toward thinner neighbours, sideways freely and downward a little;</li>
 * <li>leaks: full blocks stop it, water kills it, and doors, slabs, fences, bars and the like let a quarter through
 * (leaves half);</li>
 * <li>drifts: under the open sky the wind carries it (the wind wanders, stronger in rain and storms), and Wind users
 * flying through it scatter it;</li>
 * <li>fades: a little goes every step, faster under the sky and much faster in the rain;</li>
 * <li>follows its path (after Advanced Chimneys' smoke): it gets sootier and thicker passing through fire, cleaner
 * through leaves, and rides Advanced Chimneys' chimneys, ducts and pipes up and out of their vents
 * ({@link ChimneyCompat}). Smoke made by a fire is thicker and darker ({@link SmokeHeat}).</li>
 * </ul>
 * Thick smoke hides: a mob can't take (or keep) a target when the line between their eyes runs through enough of it
 * ({@link #blocksSight}). Each player is sent the smoke near them ({@link SmokeNet.Field}); the client draws it as soft
 * volume and greys out your view when your head is in it (not your own smoke: a Smoke user sees through that).
 * Not saved: smoke is gone after a restart.
 */
public final class SmokeField {
	// ---- tuning ----
	/** Ticks between simulation steps. */
	static final int STEP = 2;
	/** Thinner than this and it's gone. */
	static final float MIN = 0.03F;
	/** The most smoke one block holds; more spills into the blocks around it. */
	static final float CAP = 3.0F;
	/** Share of a block's smoke that climbs into the block above each step. */
	static final float RISE = 0.2F;
	/** Share of the difference that flows to a thinner neighbour beside it, and below it, each step. */
	static final float SPREAD = 0.1F;
	static final float SINK = 0.035F;
	/** Lost each step: a share, plus a little more indoors, more under the sky, a lot in the rain. */
	static final float FADE = 0.003F;
	static final float DECAY = 0.004F;
	static final float OPEN_DECAY = 0.007F;
	static final float RAIN_DECAY = 0.035F;
	/** Share carried downwind each step under the sky, at full wind. */
	static final float DRIFT = 0.12F;
	/** How much gets through a part block (doors, slabs, fences, bars), and through leaves. */
	static final float LEAK = 0.25F;
	static final float LEAVES = 0.5F;
	/** Inside an Advanced Chimneys chimney, duct, pipe or pump, this share is drawn up (or along) each step. */
	static final float DRAFT = 0.6F;
	/** Smoke passing through fire comes out this much thicker and sootier; through leaves it comes out cleaner. */
	static final float FIRE_FEED = 1.15F;
	static final float FIRE_SOOT = 0.3F;
	static final float LEAF_CLEAN = 0.8F;
	/** Kinds of block, for the path smoke takes (kept with each block's leak in the step's cache). */
	static final int PLAIN = 0;
	static final int CONDUIT = 1;
	static final int VENT = 2;
	static final int HOT = 3;
	static final int FILTER = 4;
	/** Cells kept per dimension; past this it fades three times as fast until it's back under. */
	static final int MAX_CELLS = 24000;
	/** Smoke this thick at your head hides you; the line between two pairs of eyes is blocked by this much in all. */
	public static final float THICK = 0.5F;
	static final float SIGHT_BLOCKED = 1.6F;
	/** Closer than this, a mob still makes you out through smoke. */
	static final double SEE_THROUGH = 3.0;
	/** Ticks between sending players the smoke near them, how far (blocks) and how many cells at most. */
	static final int SYNC = 4;
	static final int SYNC_RANGE = 48;
	static final int SYNC_MAX = 6000;
	/** Ticks between making mobs lose targets they can't see. */
	static final int SIGHT_EVERY = 10;
	/** A Wind user scatters smoke within this many blocks when flying faster than {@link #SCATTER_SPEED} blocks a tick. */
	static final double SCATTER_RADIUS = 2.5;
	static final double SCATTER_SPEED = 0.6;

	/** One dimension's smoke: density and who made it (a player's entity id; 0 for nobody), and the wind. */
	static final class Field {
		Long2FloatOpenHashMap density = new Long2FloatOpenHashMap();
		Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
		/** How sooty each block's smoke is, 0 (pale grey) to 1 (black), from what made it and what it passed through. */
		Long2FloatOpenHashMap soot = new Long2FloatOpenHashMap();
		double windAngle = Math.random() * Math.PI * 2.0;
		double wind = 0.3;
		boolean crowded;
	}

	private static final Map<ResourceKey<Level>, Field> FIELDS = new HashMap<>();
	/** Players who were last sent some smoke: they get one empty field when it's all gone or out of range. */
	private static final Set<UUID> SENT = new HashSet<>();

	private SmokeField() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokeField::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FIELDS.clear();
			SENT.clear();
		});
	}

	// ---------------------------------------------------------------- reading

	/** How thick the smoke is in the block at {@code pos} (0 for none). */
	public static float density(Level level, BlockPos pos) {
		Field field = FIELDS.get(level.dimension());
		return field == null ? 0.0F : field.density.get(pos.asLong());
	}

	/** The player (entity id) whose smoke fills the block at {@code pos}, or 0. */
	static int owner(Level level, BlockPos pos) {
		Field field = FIELDS.get(level.dimension());
		return field == null ? 0 : field.owner.get(pos.asLong());
	}

	/** Whether there's any smoke in this dimension (cheap; checked before anything costly). */
	static boolean any(Level level) {
		Field field = FIELDS.get(level.dimension());
		return field != null && !field.density.isEmpty();
	}

	/**
	 * Whether smoke stands between two points: the smoke along the line (sampled every half block) adds up to
	 * {@link #SIGHT_BLOCKED}, or the far end is buried in it. Up close ({@link #SEE_THROUGH}) never.
	 */
	public static boolean blocksSight(Level level, Vec3 from, Vec3 to) {
		Field field = FIELDS.get(level.dimension());
		if (field == null || field.density.isEmpty()) {
			return false;
		}
		double distance = from.distanceTo(to);
		if (distance < SEE_THROUGH) {
			return false;
		}
		if (field.density.get(BlockPos.containing(to).asLong()) >= THICK * 2.0F) {
			return true;
		}
		int samples = Math.min(128, Mth.ceil(distance * 2.0));
		float sum = 0.0F;
		long last = Long.MIN_VALUE;
		for (int i = 1; i <= samples; i++) {
			long key = BlockPos.containing(from.lerp(to, i / (double)samples)).asLong();
			if (key == last) {
				continue;
			}
			last = key;
			sum += field.density.get(key);
			if (sum >= SIGHT_BLOCKED) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code looker} can't make {@code target} out through smoke. */
	public static boolean hidden(LivingEntity looker, LivingEntity target) {
		return blocksSight(looker.level(), looker.getEyePosition(), target.getEyePosition());
	}

	// ---------------------------------------------------------------- making smoke

	/**
	 * Adds {@code amount} of smoke at {@code at}, made by {@code owner} (or null). What the block can't hold spills
	 * outward into open blocks nearby, so a big puff makes a cloud at once.
	 */
	public static void puff(ServerLevel level, Vec3 at, float amount, @org.jspecify.annotations.Nullable Entity owner) {
		// a Smoke user by a fire makes more smoke, and darker ({@link SmokeHeat})
		float soot = SmokeHeat.BASE_SOOT;
		if (owner instanceof ServerPlayer player) {
			amount *= SmokeHeat.boost(player);
			soot = SmokeHeat.soot(player);
		}
		if (amount <= 0.0F) {
			return;
		}
		Field field = FIELDS.computeIfAbsent(level.dimension(), k -> new Field());
		int ownerId = owner == null ? 0 : owner.getId();
		BlockPos start = BlockPos.containing(at);
		if (leak(level, start) <= 0.0F) {
			start = start.above();
			if (leak(level, start) <= 0.0F) {
				return;
			}
		}
		// fill outward from the start, breadth first, through open blocks
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		LongOpenHashSet seen = new LongOpenHashSet();
		long first = start.asLong();
		queue.enqueue(first);
		seen.add(first);
		float left = amount;
		while (left > 0.0F && !queue.isEmpty() && seen.size() < 600) {
			long key = queue.dequeueLong();
			float now = field.density.get(key);
			float room = CAP - now;
			if (room > 0.0F) {
				float put = Math.min(room, left);
				field.density.put(key, now + put);
				field.soot.put(key, (field.soot.get(key) * now + soot * put) / (now + put));
				if (ownerId != 0 || now < put) {
					field.owner.put(key, ownerId);
				}
				left -= put;
			}
			for (Direction direction : Direction.values()) {
				long next = BlockPos.offset(key, direction);
				if (seen.add(next) && leak(level, BlockPos.of(next)) > 0.0F) {
					queue.enqueue(next);
				}
			}
		}
	}

	/** Blows the smoke within {@code radius} of {@code at} away along {@code push} (blocks; its length is how far). */
	public static void blow(ServerLevel level, Vec3 at, double radius, Vec3 push) {
		Field field = FIELDS.get(level.dimension());
		if (field == null || field.density.isEmpty()) {
			return;
		}
		int r = Mth.ceil(radius);
		BlockPos center = BlockPos.containing(at);
		List<long[]> moves = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			long key = pos.asLong();
			float d = field.density.get(key);
			if (d <= 0.0F || Vec3.atCenterOf(pos).distanceToSqr(at) > radius * radius) {
				continue;
			}
			BlockPos to = BlockPos.containing(Vec3.atCenterOf(pos).add(push));
			if (!to.equals(pos) && leak(level, to) > 0.0F) {
				moves.add(new long[] {key, to.asLong()});
			}
		}
		for (long[] move : moves) {
			float d = field.density.get(move[0]) * 0.7F;
			if (d <= 0.0F) {
				continue;
			}
			field.density.addTo(move[0], -d);
			field.density.put(move[1], Math.min(CAP, field.density.get(move[1]) + d));
			field.owner.put(move[1], field.owner.get(move[0]));
			field.soot.put(move[1], field.soot.get(move[0]));
		}
	}

	/** Takes {@code share} of the smoke within {@code radius} of {@code at} away (a wind scattering it). */
	public static void thin(ServerLevel level, Vec3 at, double radius, float share) {
		Field field = FIELDS.get(level.dimension());
		if (field == null || field.density.isEmpty()) {
			return;
		}
		int r = Mth.ceil(radius);
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			long key = pos.asLong();
			float d = field.density.get(key);
			if (d <= 0.0F || Vec3.atCenterOf(pos).distanceToSqr(at) > radius * radius) {
				continue;
			}
			if (d * (1.0F - share) < 0.02F) {
				field.density.remove(key);
			} else {
				field.density.put(key, d * (1.0F - share));
			}
		}
	}

	/** Clears every block of smoke within {@code radius} of {@code at}. */
	static void clear(ServerLevel level, Vec3 at, double radius) {
		Field field = FIELDS.get(level.dimension());
		if (field == null) {
			return;
		}
		double r2 = radius * radius;
		field.density.long2FloatEntrySet().removeIf(e -> Vec3.atCenterOf(BlockPos.of(e.getLongKey())).distanceToSqr(at) <= r2);
	}

	// ---------------------------------------------------------------- the simulation

	/**
	 * How much smoke gets into the block at {@code pos}: 1 for open air (and anything you can walk through), 0 for full
	 * blocks, water or lava, and unloaded or out-of-world blocks; leaves and part blocks let some through.
	 */
	static float leak(Level level, BlockPos pos) {
		float cell = cell(level, pos);
		return cell - kindOf(cell) * 10.0F;
	}

	/**
	 * The block at {@code pos} for the step: its leak (0-1) plus ten times its kind ({@link #PLAIN}, {@link #CONDUIT},
	 * {@link #VENT}, {@link #HOT}, {@link #FILTER}). Advanced Chimneys' chimneys, ducts, pipes and pumps are hollow to
	 * smoke ({@link ChimneyCompat}).
	 */
	static float cell(Level level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
			return 0.0F;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return 1.0F;
		}
		if (!state.getFluidState().isEmpty()) {
			return 0.0F;
		}
		if (ChimneyCompat.conduit(state)) {
			return 1.0F + CONDUIT * 10.0F;
		}
		if (ChimneyCompat.vent(state)) {
			return 1.0F + VENT * 10.0F;
		}
		if (state.is(BlockTags.LEAVES)) {
			return LEAVES + FILTER * 10.0F;
		}
		int kind = SmokeHeat.hot(state) ? HOT : PLAIN;
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return 1.0F + kind * 10.0F;
		}
		return state.isCollisionShapeFullBlock(level, pos) ? 0.0F : LEAK + kind * 10.0F;
	}

	static int kindOf(float cell) {
		return (int)(cell / 10.0F);
	}

	private static void tick(MinecraftServer server) {
		if (FIELDS.isEmpty()) {
			if (!SENT.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					sync(player, null);
				}
			}
			return;
		}
		long time = server.overworld().getGameTime();
		for (ServerLevel level : server.getAllLevels()) {
			Field field = FIELDS.get(level.dimension());
			if (field == null) {
				continue;
			}
			if (time % STEP == 0) {
				scatter(level);
				step(level, field);
			}
			if (time % SIGHT_EVERY == 0 && !field.density.isEmpty()) {
				loseSight(level);
			}
		}
		FIELDS.values().removeIf(field -> field.density.isEmpty());
		if (time % SYNC == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				sync(player, FIELDS.get(player.level().dimension()));
			}
		}
	}

	/** One step of rising, spreading, drifting and fading, into fresh maps. */
	private static void step(ServerLevel level, Field field) {
		// the wind wanders, and blows harder in rain and storms
		field.windAngle += (level.getRandom().nextDouble() - 0.5) * 0.08;
		double target = level.isThundering() ? 1.0 : level.isRaining() ? 0.7 : 0.3;
		field.wind += (target - field.wind) * 0.01;
		float windX = (float)(Math.cos(field.windAngle) * field.wind);
		float windZ = (float)(Math.sin(field.windAngle) * field.wind);
		Direction downwindX = windX >= 0 ? Direction.EAST : Direction.WEST;
		Direction downwindZ = windZ >= 0 ? Direction.SOUTH : Direction.NORTH;
		float decayScale = field.crowded ? 3.0F : 1.0F;

		Long2FloatOpenHashMap from = field.density;
		Long2FloatOpenHashMap next = new Long2FloatOpenHashMap(from.size() * 2);
		Long2IntOpenHashMap owners = new Long2IntOpenHashMap(from.size() * 2);
		Long2FloatOpenHashMap sootMass = new Long2FloatOpenHashMap(from.size() * 2);
		Long2FloatOpenHashMap cells = new Long2FloatOpenHashMap(from.size() * 3);
		cells.defaultReturnValue(-1.0F);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// up, the five other sides and two downwind: eight at most
		long[] to = new long[8];
		float[] flow = new float[8];
		int[] kinds = new int[8];
		for (Long2FloatMap.Entry entry : from.long2FloatEntrySet()) {
			long key = entry.getLongKey();
			float d = entry.getFloatValue();
			float here = cellAt(level, cells, key, cursor);
			if (here <= 0.0F) {
				continue; // filled in, or flooded: the smoke there is gone
			}
			boolean duct = kindOf(here) == CONDUIT;
			cursor.set(key);
			boolean open = !duct && level.canSeeSky(cursor);
			float decay = DECAY + (open ? OPEN_DECAY + (level.isRainingAt(cursor) ? RAIN_DECAY : 0.0F) : 0.0F);
			float keep = d * (1.0F - FADE * decayScale) - decay * decayScale;
			if (keep <= 0.0F) {
				continue;
			}
			int owner = field.owner.get(key);
			float soot = field.soot.get(key);
			int n = 0;
			// up: inside a chimney it's drawn up hard
			long up = BlockPos.offset(key, Direction.UP);
			float cellUp = cellAt(level, cells, up, cursor);
			if (cellUp > 0.0F) {
				to[n] = up;
				kinds[n] = kindOf(cellUp);
				flow[n++] = keep * (duct ? DRAFT : RISE) * open(cellUp);
			}
			// sideways and down, toward thinner smoke (inside a chimney, only along it or out of a vent)
			for (Direction direction : Direction.values()) {
				if (direction == Direction.UP) {
					continue;
				}
				long side = BlockPos.offset(key, direction);
				float cell = cellAt(level, cells, side, cursor);
				int kind = kindOf(cell);
				float diff = keep - from.get(side);
				if (cell <= 0.0F || diff <= 0.0F || duct && kind != CONDUIT && kind != VENT) {
					continue;
				}
				to[n] = side;
				kinds[n] = kind;
				float share = duct && kind == CONDUIT ? DRAFT * 0.5F : direction == Direction.DOWN ? SINK : SPREAD;
				flow[n++] = diff * share * open(cell);
			}
			// downwind, under the sky
			if (open) {
				long x = BlockPos.offset(key, downwindX);
				float cellX = cellAt(level, cells, x, cursor);
				if (cellX > 0.0F) {
					to[n] = x;
					kinds[n] = kindOf(cellX);
					flow[n++] = keep * DRIFT * Math.abs(windX) * open(cellX);
				}
				long z = BlockPos.offset(key, downwindZ);
				float cellZ = cellAt(level, cells, z, cursor);
				if (cellZ > 0.0F) {
					to[n] = z;
					kinds[n] = kindOf(cellZ);
					flow[n++] = keep * DRIFT * Math.abs(windZ) * open(cellZ);
				}
			}
			float total = 0.0F;
			for (int i = 0; i < n; i++) {
				total += flow[i];
			}
			float scale = total > keep * 0.8F ? keep * 0.8F / total : 1.0F;
			add(next, owners, sootMass, key, keep - total * scale, owner, soot);
			for (int i = 0; i < n; i++) {
				// what it passes through changes it: fire feeds it and blackens it, leaves clean it
				float amount = flow[i] * scale;
				float carried = soot;
				if (kinds[i] == HOT) {
					amount *= FIRE_FEED;
					carried = Math.min(1.0F, soot + FIRE_SOOT);
				} else if (kinds[i] == FILTER) {
					carried = soot * LEAF_CLEAN;
				}
				add(next, owners, sootMass, to[i], amount, owner, carried);
			}
		}
		next.long2FloatEntrySet().removeIf(e -> e.getFloatValue() < MIN);
		Long2FloatOpenHashMap soots = new Long2FloatOpenHashMap(next.size() * 2);
		for (Long2FloatMap.Entry entry : next.long2FloatEntrySet()) {
			soots.put(entry.getLongKey(), Mth.clamp(sootMass.get(entry.getLongKey()) / entry.getFloatValue(), 0.0F, 1.0F));
			if (entry.getFloatValue() > CAP) {
				entry.setValue(CAP);
			}
		}
		owners.keySet().retainAll(next.keySet());
		field.density = next;
		field.owner = owners;
		field.soot = soots;
		field.crowded = next.size() > MAX_CELLS;
	}

	/** The leak (0-1) part of a {@link #cell} value. */
	private static float open(float cell) {
		return cell - kindOf(cell) * 10.0F;
	}

	private static float cellAt(Level level, Long2FloatOpenHashMap cache, long key, BlockPos.MutableBlockPos cursor) {
		float cell = cache.get(key);
		if (cell < 0.0F) {
			cell = cell(level, cursor.set(key));
			cache.put(key, cell);
		}
		return cell;
	}

	/** Adds smoke to a cell of the next step; the owner of the bigger share wins it, and the soot mixes by amount. */
	private static void add(Long2FloatOpenHashMap next, Long2IntOpenHashMap owners, Long2FloatOpenHashMap sootMass, long key, float amount,
			int owner, float soot) {
		if (amount <= 0.0F) {
			return;
		}
		float before = next.addTo(key, amount);
		sootMass.addTo(key, amount * soot);
		if (amount >= before) {
			owners.put(key, owner);
		}
	}

	/** Wind users flying fast through smoke scatter it ahead of them. */
	private static void scatter(ServerLevel level) {
		var wind = Powers.byName("wind");
		if (wind == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!Powers.has(player, wind)) {
				continue;
			}
			Vec3 moved = player.position().subtract(player.xo, player.yo, player.zo);
			if (moved.lengthSqr() < SCATTER_SPEED * SCATTER_SPEED) {
				continue;
			}
			blow(level, player.getBoundingBox().getCenter(), SCATTER_RADIUS, moved.normalize().scale(2.5));
		}
	}

	/** Mobs lose any target they can't see for smoke. */
	private static void loseSight(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Mob mob && mob.isAlive() && mob.getTarget() != null && hidden(mob, mob.getTarget())) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
	}

	// ---------------------------------------------------------------- sending it

	/** Sends {@code player} the smoke within {@link #SYNC_RANGE} of them (an empty field once when there's none). */
	private static void sync(ServerPlayer player, @org.jspecify.annotations.Nullable Field field) {
		BlockPos origin = player.blockPosition();
		List<Long> cells = new ArrayList<>();
		if (field != null) {
			int ox = origin.getX();
			int oy = origin.getY();
			int oz = origin.getZ();
			for (Long2FloatMap.Entry entry : field.density.long2FloatEntrySet()) {
				long key = entry.getLongKey();
				if (Math.abs(BlockPos.getX(key) - ox) <= SYNC_RANGE && Math.abs(BlockPos.getY(key) - oy) <= SYNC_RANGE
					&& Math.abs(BlockPos.getZ(key) - oz) <= SYNC_RANGE) {
					cells.add(key);
					if (cells.size() >= SYNC_MAX) {
						break;
					}
				}
			}
		}
		if (cells.isEmpty()) {
			if (SENT.remove(player.getUUID())) {
				SmokeNet.send(player, new SmokeNet.Field(origin.asLong(), new byte[0]));
			}
			return;
		}
		SENT.add(player.getUUID());
		byte[] data = new byte[cells.size() * SmokeNet.Field.STRIDE];
		int i = 0;
		int self = player.getId();
		for (long key : cells) {
			data[i++] = (byte)(BlockPos.getX(key) - origin.getX());
			data[i++] = (byte)(BlockPos.getY(key) - origin.getY());
			data[i++] = (byte)(BlockPos.getZ(key) - origin.getZ());
			data[i++] = (byte)Mth.clamp(Math.round(field.density.get(key) / CAP * 255.0F), 1, 255);
			int soot = Math.round(Mth.clamp(field.soot.get(key), 0.0F, 1.0F) * 127.0F);
			data[i++] = (byte)(soot << 1 | (field.owner.get(key) == self ? SmokeNet.Field.YOURS : 0));
		}
		SmokeNet.send(player, new SmokeNet.Field(origin.asLong(), data));
	}

	static void forget(ServerPlayer player) {
		SENT.remove(player.getUUID());
	}
}
