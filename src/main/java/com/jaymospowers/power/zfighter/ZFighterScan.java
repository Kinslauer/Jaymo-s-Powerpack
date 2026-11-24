package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The scouter's hold-to-scan (Wan's Bio Scanner's idea only, our own code). Wearing the scouter, holding its key on a living
 * thing, a plant or living block, or the open sky or ground sends {@link ZFighterSensesNet.ScanAsk}s; the server builds the
 * reading's lines once ({@link ZFighterScanInfo}) and lets one more come through every {@link #LINE_TICKS} of looking, the first
 * at once. Let go early and the client keeps only what came through. A reading that comes all the way through chimes, counts
 * as studying a beast ({@link ZFighterScanKingdoms#studied}) and, for a living thing, can then be marked with a tap of the key
 * ({@link #mark}): a mark lasts {@link #MARK_TICKS} or until it is lost for {@link #MARK_LOSE_TICKS}, and is sent to the
 * client twice a second for its chevron and through-the-wall outline.
 * <p>
 * Everyone reads: no ki is needed. Something hiding its ki ({@link ZFighterKiSense#hidesKi}) reads as nothing and can't be
 * marked. The server remembers which Z Fighter forms the wearer has seen ({@link #FORMS_SEEN}).
 */
public final class ZFighterScan {
	/** Ticks of looking for each line after the first. */
	static final int LINE_TICKS = 14;
	/** A hold ends this many ticks after the last ask, and one ask counts for at most this many ticks of looking. */
	static final int ASK_STALE = 12, ASK_MOST = 6;
	/** How far off a creature, a block and the place can be scanned (blocks). */
	static final double ENTITY_RANGE = 48.0, BLOCK_RANGE = 24.0;
	/** How long after a full scan of a creature it can be marked (ticks). */
	static final int FULL_WINDOW = 1200;
	/** How many things the basic scouter holds marked at once (a better scouter would raise it). */
	public static final int MARKS_BASIC = 1;
	/** A mark's life, how far from the wearer it can still be followed (blocks), and how long it may be lost before it is let go (ticks). */
	static final int MARK_TICKS = 6000, MARK_LOSE_TICKS = 200;
	static final double MARK_RANGE = 96.0;
	/** Ticks between the marks' positions being sent. */
	static final int MARK_SEND_EVERY = 10;

	/** The Z Fighter forms the wearer has seen (see {@link ZFighterScanInfo#formCode}). Saved, kept through death. */
	public static final AttachmentType<List<Integer>> FORMS_SEEN = AttachmentRegistry.create(JaymosPowers.id("zfighter_scan_forms_seen"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	/** One hold on one target. */
	private static final class Session {
		final int kind;
		final long key;
		final int id;
		final List<Component> lines;
		final boolean full;
		long lastAsk;
		int held;
		boolean done;

		Session(int kind, long key, int id, List<Component> lines, boolean full, long now) {
			this.kind = kind;
			this.key = key;
			this.id = id;
			this.lines = lines;
			this.full = full;
			this.lastAsk = now;
		}
	}

	/** The living thing a player last read all the way through, and when. */
	private record Full(UUID entity, long at) {
	}

	/** A marked living thing: until when, and when it was last in reach. */
	private static final class Mark {
		final UUID entity;
		final long until;
		long lastSeen;

		Mark(UUID entity, long until, long now) {
			this.entity = entity;
			this.until = until;
			this.lastSeen = now;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Full> FULLS = new HashMap<>();
	private static final Map<UUID, List<Mark>> MARKS = new HashMap<>();
	private static final Set<UUID> SENT = new HashSet<>();

	private ZFighterScan() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterScan::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SESSIONS.clear();
			FULLS.clear();
			MARKS.clear();
			SENT.clear();
		});
	}

	private static void forget(UUID id) {
		SESSIONS.remove(id);
		FULLS.remove(id);
		MARKS.remove(id);
		SENT.remove(id);
	}

	/** How many things {@code player}'s scouter can hold marked at once: one, on the only scouter there is. */
	public static int markLimit(ServerPlayer player) {
		ItemStack scouter = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
		return scouter.is(ZFighterScouter.SCOUTER) ? MARKS_BASIC : 0;
	}

	// ---- the hold ----

	/** From the wearer, holding the scan key on {@code kind} (an entity {@code id} or a block {@code pos}). */
	static void ask(ServerPlayer player, int kind, int id, BlockPos pos) {
		if (!ZFighterScouter.wearing(player) || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Session session = SESSIONS.get(player.getUUID());
		long key = kind == ZFighterSensesNet.SCAN_ENTITY ? id : kind == ZFighterSensesNet.SCAN_BLOCK ? pos.asLong() : Long.MIN_VALUE;
		if (session != null && session.kind == kind && session.key == key && now - session.lastAsk <= ASK_STALE) {
			session.held += (int)Math.min(ASK_MOST, Math.max(0L, now - session.lastAsk));
			session.lastAsk = now;
		} else {
			session = begin(player, level, kind, key, id, pos, now);
			if (session == null) {
				SESSIONS.remove(player.getUUID());
				return;
			}
			SESSIONS.put(player.getUUID(), session);
		}
		int total = session.lines.size();
		int shown = Math.min(total, 1 + session.held / LINE_TICKS);
		boolean finishing = shown >= total && !session.done;
		if (finishing) {
			session.done = true;
			finish(player, level, session);
		}
		ZFighterSensesNet.send(player, new ZFighterSensesNet.ScanLines(kind, id, new ArrayList<>(session.lines.subList(0, shown)), total, finishing));
	}

	/** Checks the target and builds its lines; null if there is nothing there to read. */
	private static Session begin(ServerPlayer player, ServerLevel level, int kind, long key, int id, BlockPos pos, long now) {
		if (kind == ZFighterSensesNet.SCAN_ENTITY) {
			Entity found = level.getEntity(id);
			if (!(found instanceof LivingEntity target) || !target.isAlive() || target == player || target.distanceToSqr(player) > ENTITY_RANGE * ENTITY_RANGE
				|| !player.hasLineOfSight(target)) {
				return null;
			}
			if (ZFighterKiSense.hidesKi(target)) {
				return new Session(kind, key, id, List.of(Component.translatable("hud.jaymospowers.scan.unreadable")), false, now);
			}
			if (target instanceof ServerPlayer fighter && Powers.has(fighter, ZFighterPower.INSTANCE)) {
				see(player, ZFighterScanInfo.formCode(fighter));
			}
			return new Session(kind, key, id, ZFighterScanInfo.creature(player, target, player.getAttachedOrElse(FORMS_SEEN, List.of())), true, now);
		}
		BlockPos at = kind == ZFighterSensesNet.SCAN_BLOCK ? pos : player.blockPosition();
		if (kind == ZFighterSensesNet.SCAN_BLOCK) {
			if (player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) > (BLOCK_RANGE + 2.0) * (BLOCK_RANGE + 2.0) || !level.isLoaded(at)) {
				return null;
			}
			List<Component> lines = ZFighterScanInfo.block(level, at);
			if (!lines.isEmpty()) {
				return new Session(kind, key, id, lines, false, now);
			}
		}
		return new Session(kind, key, id, ZFighterScanInfo.place(level, at), false, now);
	}

	private static void see(ServerPlayer viewer, int code) {
		List<Integer> seen = viewer.getAttachedOrElse(FORMS_SEEN, List.of());
		if (!seen.contains(code)) {
			List<Integer> next = new ArrayList<>(seen);
			next.add(code);
			viewer.setAttached(FORMS_SEEN, List.copyOf(next));
		}
	}

	/** A reading came all the way through. */
	private static void finish(ServerPlayer player, ServerLevel level, Session session) {
		if (session.kind != ZFighterSensesNet.SCAN_ENTITY || !session.full) {
			return;
		}
		if (level.getEntity(session.id) instanceof LivingEntity target) {
			FULLS.put(player.getUUID(), new Full(target.getUUID(), level.getGameTime()));
			ZFighterScanKingdoms.studied(player, target);
		}
	}

	// ---- marks ----

	/** From the wearer, a tap of the key: mark the last living thing read all the way through, or let it go if it is marked. */
	static void mark(ServerPlayer player) {
		if (!ZFighterScouter.wearing(player) || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		List<Mark> marks = MARKS.computeIfAbsent(player.getUUID(), u -> new ArrayList<>());
		Full full = FULLS.get(player.getUUID());
		if (full == null || now - full.at() > FULL_WINDOW || !(level.getEntity(full.entity()) instanceof LivingEntity target) || !target.isAlive()
			|| target.distanceToSqr(player) > MARK_RANGE * MARK_RANGE || ZFighterKiSense.hidesKi(target)) {
			return;
		}
		if (marks.removeIf(m -> m.entity.equals(full.entity()))) {
			send(player, level);
			return;
		}
		while (marks.size() >= Math.max(1, markLimit(player))) {
			marks.remove(0);
		}
		marks.add(new Mark(full.entity(), now + MARK_TICKS, now));
		send(player, level);
	}

	private static void tick(MinecraftServer server) {
		if (MARKS.isEmpty() || server.getTickCount() % MARK_SEND_EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, List<Mark>>> it = MARKS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, List<Mark>> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			boolean worn = ZFighterScouter.wearing(player);
			entry.getValue().removeIf(m -> {
				if (!worn || now >= m.until) {
					return true;
				}
				Entity e = level.getEntity(m.entity);
				if (e instanceof LivingEntity living && living.isAlive() && !ZFighterKiSense.hidesKi(living) && living.distanceToSqr(player) <= MARK_RANGE * MARK_RANGE) {
					m.lastSeen = now;
					return false;
				}
				return e instanceof LivingEntity living && !living.isAlive() || now - m.lastSeen > MARK_LOSE_TICKS;
			});
			send(player, level);
			if (entry.getValue().isEmpty()) {
				it.remove();
			}
		}
	}

	/** Sends where the player's marks are now (nothing, once, when the last is gone). */
	private static void send(ServerPlayer player, ServerLevel level) {
		List<ZFighterSensesNet.Marked> out = new ArrayList<>();
		for (Mark m : MARKS.getOrDefault(player.getUUID(), List.of())) {
			if (level.getEntity(m.entity) instanceof LivingEntity e && e.isAlive() && e.distanceToSqr(player) <= MARK_RANGE * MARK_RANGE) {
				out.add(new ZFighterSensesNet.Marked(e.getId(), e.getX(), e.getY(), e.getZ()));
			}
		}
		if (out.isEmpty() && !SENT.remove(player.getUUID())) {
			return;
		}
		if (!out.isEmpty()) {
			SENT.add(player.getUUID());
		}
		ZFighterSensesNet.send(player, new ZFighterSensesNet.ScanMarks(out));
	}
}
