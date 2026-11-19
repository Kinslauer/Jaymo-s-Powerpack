package com.jaymospowers.power.chrono;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sneak + R, "Put it back as it was" (a Chrono with all four abilities unlocked). The first press marks a space
 * {@link #SIDE} blocks across, a cube round the spot you aim at (or where you stand). The second press, later, turns the
 * space back: walls broken, houses burnt, holes blasted in it, are put back, running outward from the middle over
 * {@link #RUN_TICKS} ticks in a golden clockwork wave, like time running backwards. It costs a long wait
 * ({@link #COOLDOWN}) and hunger.
 *
 * <p>Which moment it goes back to: if the mark is older than {@link #FRESH_TICKS} (30 s), the space as it was when marked;
 * if the mark is younger, the space as it was {@link #FRESH_TICKS} ago, which can be from before the mark. A mark lasts
 * {@link #MARK_TICKS}; marking again from another dimension or over {@link #FAR} blocks away replaces it.
 *
 * <p>What it keeps is cheap: only blocks that changed, and their old state, caught as they change ({@code ChronoMendMixin}
 * on {@code Level.setBlock}). A Chrono with the ability keeps a rolling log of the changes round them for the last 30 s
 * ({@link #WATCH}); a mark keeps the first old state of each block that changes inside it. Blocks with their own data
 * (signs, banners, heads, spawners) get it back. <b>Containers get their block back empty</b>: nothing that was inside is
 * restored (what spilled out when it broke stays where it fell), and entities (mobs, items, projectiles) are never
 * restored, so nothing can be duplicated. The restore sets blocks without block updates or drops.
 */
public final class ChronoMend {
	// ---- tuning ----
	/** The space marked: blocks across (a cube), so this many either side of the middle. */
	public static final int SIDE = 25, HALF = SIDE / 2;
	/** How far the aim reaches to pick the middle (blocks); with nothing in reach the mark is where you stand. */
	static final double AIM = 32.0;
	/** A mark younger than this (ticks, 30 s) turns back to 30 s ago instead of to the mark. */
	static final int FRESH_TICKS = 600;
	/** A mark lasts this long (5 minutes), and is replaced by a mark made this far from it (blocks). */
	static final int MARK_TICKS = 6000;
	static final double FAR = 64.0;
	/** The rolling log covers changes this far round the Chrono (blocks), and holds at most {@link #LOG_MAX} of them. */
	static final int WATCH = 48, LOG_MAX = 60000;
	/** A mark keeps at most this many changed blocks. */
	static final int DIFF_MAX = 60000;
	/** The wave: ticks it takes to run the space back, and the most blocks it dresses in sparks a tick. */
	static final int RUN_TICKS = 24, SPARKS = 14;
	/** The wait after a turn-back (ticks, 3 minutes), the hunger it costs and the food it needs. */
	static final int COOLDOWN = 3600;
	static final float EXHAUSTION = 8.0F;
	static final int MIN_FOOD = 8;
	static final int GOLD = 0xFFD070;
	/** Setting a block back: tell the clients, keep the shape as saved, and drop nothing. */
	private static final int SET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final String TABLE = "chrono_mend";

	/** A block as it was: its state, and its block entity's data if it is one worth keeping (never a container's). */
	record Saved(BlockState state, @Nullable CompoundTag data) {
	}

	private record Entry(long tick, long pos, Saved saved) {
	}

	/** A Chrono's rolling log of changes near them. */
	private static final class Watch {
		final ArrayDeque<Entry> log = new ArrayDeque<>();
		ResourceKey<Level> dim;
		BlockPos at = BlockPos.ZERO;
	}

	/** A marked space and what has changed in it since. */
	private static final class Mark {
		final ResourceKey<Level> dim;
		final BlockPos centre;
		final long at;
		final Map<Long, Saved> changed = new HashMap<>();

		Mark(ResourceKey<Level> dim, BlockPos centre, long at) {
			this.dim = dim;
			this.centre = centre;
			this.at = at;
		}

		boolean covers(BlockPos pos) {
			return Math.abs(pos.getX() - centre.getX()) <= HALF && Math.abs(pos.getY() - centre.getY()) <= HALF
				&& Math.abs(pos.getZ() - centre.getZ()) <= HALF;
		}
	}

	/** A turn-back under way: the blocks to set back, nearest the middle first, in the order they go. */
	private static final class Run {
		final ServerLevel level;
		final BlockPos centre;
		final List<Map.Entry<Long, Saved>> blocks;
		int next, tick;

		Run(ServerLevel level, BlockPos centre, List<Map.Entry<Long, Saved>> blocks) {
			this.level = level;
			this.centre = centre;
			this.blocks = blocks;
		}
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** True while a turn-back sets blocks, so they are not logged as changes. */
	private static boolean restoring;

	private ChronoMend() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ChronoMend::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WATCHES.clear();
			MARKS.clear();
			RUNS.clear();
		});
	}

	/** Whether {@code player} has the ability: a Chrono with every ability unlocked. */
	static boolean able(Player player) {
		return Powers.has(player, ChronoPower.INSTANCE) && Mastery.unlocked(player) >= Mastery.ALL;
	}

	static void forget(ServerPlayer player) {
		WATCHES.remove(player.getUUID());
		MARKS.remove(player.getUUID());
		RUNS.remove(player.getUUID());
		PickKit.forget(player);
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_mend." + key).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	// ---------------------------------------------------------------- the key

	/** Sneak + R: mark a space, or turn the marked one back. */
	static void key(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (RUNS.containsKey(player.getUUID())) {
			return;
		}
		Mark mark = MARKS.get(player.getUUID());
		if (mark != null && (now - mark.at > MARK_TICKS || !mark.dim.equals(level.dimension()) || player.position().distanceTo(Vec3.atCenterOf(mark.centre)) > FAR)) {
			mark = null;
		}
		if (mark == null) {
			mark(player, level, now);
			return;
		}
		if (!PickKit.ready(TABLE, player, 0)) {
			say(player, "cooling");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			say(player, "hungry");
			return;
		}
		Map<Long, Saved> back = now - mark.at < FRESH_TICKS ? lately(player, mark, now) : mark.changed;
		List<Map.Entry<Long, Saved>> blocks = new ArrayList<>();
		for (Map.Entry<Long, Saved> e : back.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (level.hasChunkAt(pos) && level.getBlockState(pos) != e.getValue().state()) {
				blocks.add(e);
			}
		}
		if (blocks.isEmpty()) {
			say(player, "unchanged");
			return;
		}
		BlockPos middleBlock = mark.centre;
		blocks.sort((a, b) -> Double.compare(BlockPos.of(a.getKey()).distSqr(middleBlock), BlockPos.of(b.getKey()).distSqr(middleBlock)));
		MARKS.remove(player.getUUID());
		Watch watch = WATCHES.get(player.getUUID());
		if (watch != null) {
			watch.log.clear();
		}
		RUNS.put(player.getUUID(), new Run(level, mark.centre, blocks));
		PickKit.spend(TABLE, player, 0, COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		Vec3 centre = Vec3.atCenterOf(mark.centre);
		MagicFx.sigil(level, centre, (float)HALF, GOLD, RUN_TICKS + 10);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	private static void mark(ServerPlayer player, ServerLevel level, long now) {
		HitResult hit = player.pick(AIM, 1.0F, false);
		BlockPos centre = hit.getType() == HitResult.Type.MISS ? player.blockPosition() : BlockPos.containing(hit.getLocation());
		MARKS.put(player.getUUID(), new Mark(level.dimension(), centre, now));
		Vec3 middle = Vec3.atCenterOf(centre);
		MagicFx.sigil(level, middle, (float)HALF, GOLD, 40);
		MagicFx.ring(level, middle, (float)HALF, GOLD);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		say(player, "marked");
	}

	/** The marked space as it was {@link #FRESH_TICKS} ago, from the Chrono's rolling log: the oldest recent old state of each block that changed. */
	private static Map<Long, Saved> lately(ServerPlayer player, Mark mark, long now) {
		Map<Long, Saved> back = new LinkedHashMap<>();
		Watch watch = WATCHES.get(player.getUUID());
		if (watch == null) {
			return back;
		}
		for (Entry e : watch.log) {
			if (now - e.tick() <= FRESH_TICKS && mark.covers(BlockPos.of(e.pos()))) {
				back.putIfAbsent(e.pos(), e.saved());
			}
		}
		return back;
	}

	// ---------------------------------------------------------------- catching changes

	/** Called before every block set on a level ({@code ChronoMendMixin}): notes the old block for whoever is keeping watch. */
	public static void changed(Level level, BlockPos pos, BlockState now) {
		if (restoring || WATCHES.isEmpty() && MARKS.isEmpty() || !(level instanceof ServerLevel server)
			|| !server.getServer().isSameThread() || server.isOutsideBuildHeight(pos)) {
			return;
		}
		BlockState old = server.getBlockState(pos);
		if (old == now) {
			return;
		}
		Saved saved = null;
		long gameTime = server.getGameTime();
		for (Watch watch : WATCHES.values()) {
			if (watch.dim == server.dimension() && Math.abs(pos.getX() - watch.at.getX()) <= WATCH && Math.abs(pos.getY() - watch.at.getY()) <= WATCH
				&& Math.abs(pos.getZ() - watch.at.getZ()) <= WATCH && watch.log.size() < LOG_MAX) {
				saved = saved == null ? save(server, pos, old) : saved;
				watch.log.addLast(new Entry(gameTime, pos.asLong(), saved));
			}
		}
		for (Mark mark : MARKS.values()) {
			if (mark.dim == server.dimension() && mark.covers(pos) && mark.changed.size() < DIFF_MAX && !mark.changed.containsKey(pos.asLong())) {
				saved = saved == null ? save(server, pos, old) : saved;
				mark.changed.put(pos.asLong(), saved);
			}
		}
	}

	private static Saved save(ServerLevel level, BlockPos pos, BlockState state) {
		CompoundTag data = null;
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity != null && !(entity instanceof Container)) {
			data = entity.saveWithFullMetadata(level.registryAccess());
		}
		return new Saved(state, data);
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!able(player) || !player.isAlive()) {
				if (WATCHES.remove(id) != null) {
					MARKS.remove(id);
				}
				continue;
			}
			Watch watch = WATCHES.computeIfAbsent(id, k -> new Watch());
			watch.dim = player.level().dimension();
			watch.at = player.blockPosition();
			long now = player.level().getGameTime();
			while (!watch.log.isEmpty() && now - watch.log.peekFirst().tick() > FRESH_TICKS + 40) {
				watch.log.pollFirst();
			}
			Mark mark = MARKS.get(id);
			if (mark != null && now - mark.at > MARK_TICKS) {
				MARKS.remove(id);
			}
		}
		if (RUNS.isEmpty()) {
			return;
		}
		RUNS.values().removeIf(run -> !step(run));
	}

	/** One tick of a turn-back: the next share of blocks goes back, with sparks, a ring running outward and a clock's tick. */
	private static boolean step(Run run) {
		ServerLevel level = run.level;
		int total = run.blocks.size();
		int upTo = run.tick >= RUN_TICKS - 1 ? total : Math.min(total, (int)Math.ceil(total * (run.tick + 1) / (double)RUN_TICKS));
		int from = run.next;
		restoring = true;
		try {
			for (; run.next < upTo; run.next++) {
				Map.Entry<Long, Saved> e = run.blocks.get(run.next);
				BlockPos pos = BlockPos.of(e.getKey());
				if (level.hasChunkAt(pos)) {
					put(level, pos, e.getValue());
				}
			}
		} finally {
			restoring = false;
		}
		int made = run.next - from;
		if (made > 0) {
			int stride = Math.max(1, made / SPARKS);
			for (int i = from; i < run.next; i += stride) {
				BlockPos pos = BlockPos.of(run.blocks.get(i).getKey());
				level.sendParticles(i % 2 == 0 ? MagicFx.GLYPH : MagicFx.spark(GOLD, 0.7F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
			}
			BlockPos last = BlockPos.of(run.blocks.get(run.next - 1).getKey());
			if (run.tick % 3 == 0) {
				MagicFx.ring(level, Vec3.atCenterOf(run.centre), null, (float)Math.sqrt(last.distSqr(run.centre)) + 1.0F, GOLD, 6);
			}
			level.playSound(null, last, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F + 0.8F * run.tick / RUN_TICKS);
		}
		run.tick++;
		if (run.next >= total) {
			Vec3 middle = Vec3.atCenterOf(run.centre);
			MagicFx.blast(level, middle, 3.0F, GOLD);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
			return false;
		}
		return true;
	}

	/** One block back as it was: the state, then a block entity's data where it was kept. */
	private static void put(ServerLevel level, BlockPos pos, Saved saved) {
		level.setBlock(pos, saved.state(), SET);
		if (saved.data() != null) {
			BlockEntity entity = BlockEntity.loadStatic(pos, saved.state(), saved.data(), level.registryAccess());
			if (entity != null) {
				level.setBlockEntity(entity);
			}
		}
	}
}
