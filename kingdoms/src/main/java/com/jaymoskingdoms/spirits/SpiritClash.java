package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two keepers whose spirits strike at each other lock arms. Both cast a strike (a grab, a shove, a pin, a blade, a
 * javelin) at the other within {@link #WINDOW} ticks, both spirits out and the two within {@link #RANGE} blocks: both are
 * rooted, hold {@link Poses#CLASH}, and tap the spirit key for {@link #TICKS} ticks (the dispatcher in {@link Spirits}
 * hands each CAST to {@link #tap}). A tap adds {@link #TAP} plus a quarter of the level to your side; a level adds
 * {@link #LEVEL_RATE} a tick besides. It ends at the time, or at a {@link #DECIDE} lead: the loser is thrown back
 * {@link #THROW} blocks and takes {@link #HIT}, the winner's bond grows {@link #WIN_BOND}; a draw pushes both back
 * {@link #PUSH}. {@link #CLASH} is synced to everyone so viewers (and the film, {@code ClashCinematic}) see it.
 */
public final class SpiritClash {
	/** How far apart two strikes may be and still meet (ticks); how close the two must stand (blocks). */
	static final int WINDOW = 20;
	static final double RANGE = 4.0;
	/** How far in front the aimed player may stand (blocks) and how wide the aim (degrees either side). */
	static final double AIM_REACH = 5.0, AIM_ARC = 50.0;
	/** The contest's length (ticks). */
	static final int TICKS = 100;
	/** A tap's points, and the extra per this many levels. */
	static final float TAP = 1.0F, TAP_PER_LEVELS = 4.0F;
	/** Points each level adds a tick. */
	static final float LEVEL_RATE = 0.02F;
	/** The lead that decides it early (points); the lead shown is this scaled to 100. */
	static final float DECIDE = 40.0F;
	/** The loser's throw (blocks) and damage; the winner's bond; a draw's push (blocks). */
	static final double THROW = 6.0, PUSH = 3.0;
	static final float HIT = 8.0F;
	static final int WIN_BOND = 3;
	/** How far a rooted player may drift (blocks) before they are set back. */
	static final double DRIFT = 0.15;
	/** Screen shake while it runs: the least and the most (it grows with the lead's swing). */
	static final float SHAKE_MIN = 0.08F, SHAKE_MAX = 0.35F;
	/** How long the finished clash's attachment stays for the flash (ticks). */
	static final int FLASH_KEEP = 14;

	/**
	 * A clash in progress: the entity ids of both sides, the game time it began, and the lead of {@code a} over {@code b}
	 * (-100 to 100). Set on both players; absent when none. Synced to everyone. {@code end} is 0 while it runs, or the game
	 * time it ended, with {@code winner} 0 (a draw), 1 ({@code a}) or 2 ({@code b}), for the closing flash.
	 */
	public record Clash(int a, int b, long start, int lead, long end, int winner) {
		public static final Codec<Clash> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("a", 0).forGetter(Clash::a),
				Codec.INT.optionalFieldOf("b", 0).forGetter(Clash::b),
				Codec.LONG.optionalFieldOf("start", 0L).forGetter(Clash::start),
				Codec.INT.optionalFieldOf("lead", 0).forGetter(Clash::lead),
				Codec.LONG.optionalFieldOf("end", 0L).forGetter(Clash::end),
				Codec.INT.optionalFieldOf("winner", 0).forGetter(Clash::winner))
			.apply(i, Clash::new));
		public static final StreamCodec<ByteBuf, Clash> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Clash::a,
			ByteBufCodecs.VAR_INT, Clash::b,
			ByteBufCodecs.VAR_LONG, Clash::start,
			ByteBufCodecs.VAR_INT, Clash::lead,
			ByteBufCodecs.VAR_LONG, Clash::end,
			ByteBufCodecs.VAR_INT, Clash::winner,
			Clash::new);

		/** Whether it is over (the closing flash is still showing). */
		public boolean over() {
			return end != 0L;
		}
	}

	/** The clash a player is in (or has just left: see {@link Clash#over}). Not saved. Synced to everyone. */
	public static final AttachmentType<Clash> CLASH = AttachmentRegistry.create(JaymosKingdoms.id("spirit_clash"),
		builder -> builder.syncWith(Clash.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** A strike at a player: who, and when. */
	private record Strike(UUID target, long at) {
	}

	/** A running contest. {@code score} is a's points less b's. */
	private static final class Session {
		final UUID a, b;
		final long start;
		final Vec3 spotA, spotB;
		float score;
		int lead;
		long tappedA = -1L, tappedB = -1L;

		Session(ServerPlayer a, ServerPlayer b, long start) {
			this.a = a.getUUID();
			this.b = b.getUUID();
			this.start = start;
			this.spotA = a.position();
			this.spotB = b.position();
		}
	}

	private static final Map<UUID, Strike> STRIKES = new HashMap<>();
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Finished clashes whose attachment is still up: the player, and the game time to take it off. */
	private static final Map<UUID, Long> LINGER = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private SpiritClash() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("spirit clash attachment: {}", CLASH);
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerTickEvents.END_SERVER_TICK.register(SpiritClash::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			STRIKES.clear();
			SESSIONS.clear();
			LINGER.clear();
			server = null;
		});
	}

	/** Whether {@code player} is in a clash right now. */
	public static boolean inClash(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** Whether {@code move} is a strike (one that can meet another's). */
	static boolean strikes(Move move) {
		return switch (move.id()) {
			case "grab", "shove", "pin" -> true;
			default -> move.intent() == Mana.HARM;
		};
	}

	/** The player {@code caster} is aiming at (nearest in front within reach), or null. */
	public static @Nullable ServerPlayer aimed(ServerPlayer caster) {
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-3) {
			return null;
		}
		flat = flat.normalize();
		double cos = Math.cos(Math.toRadians(AIM_ARC));
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer other : ((ServerLevel)caster.level()).players()) {
			if (other == caster || other.isSpectator() || !other.isAlive()) {
				continue;
			}
			Vec3 to = other.position().subtract(caster.position());
			double dist = to.horizontalDistance();
			if (dist > AIM_REACH || Math.abs(to.y) > 3.0) {
				continue;
			}
			if (dist > 0.5 && new Vec3(to.x, 0.0, to.z).normalize().dot(flat) < cos) {
				continue;
			}
			if (dist < bestDist) {
				bestDist = dist;
				best = other;
			}
		}
		return best;
	}

	/**
	 * {@code player} has just cast {@code move} (a successful cast) at {@code target}, the player it aimed at (null: nobody).
	 * If {@code target} struck at {@code player} within {@link #WINDOW} ticks, the two lock arms.
	 */
	public static void noteStrike(ServerPlayer player, Move move, @Nullable ServerPlayer target) {
		if (target == null || target == player || !strikes(move) || inClash(player) || inClash(target)) {
			return;
		}
		long now = player.level().getGameTime();
		Strike theirs = STRIKES.get(target.getUUID());
		if (theirs != null && theirs.target().equals(player.getUUID()) && now - theirs.at() <= WINDOW && canLock(player, target)) {
			STRIKES.remove(target.getUUID());
			STRIKES.remove(player.getUUID());
			begin(target, player, now);
			return;
		}
		STRIKES.put(player.getUUID(), new Strike(target.getUUID(), now));
	}

	private static boolean canLock(ServerPlayer p, ServerPlayer q) {
		SpiritState a = Spirits.state(p), b = Spirits.state(q);
		return a != null && b != null && a.out() && b.out() && p.level() == q.level() && p.isAlive() && q.isAlive()
			&& !p.isSpectator() && !q.isSpectator() && p.distanceTo(q) <= RANGE;
	}

	private static void begin(ServerPlayer a, ServerPlayer b, long now) {
		Session session = new Session(a, b, now);
		SESSIONS.put(a.getUUID(), session);
		SESSIONS.put(b.getUUID(), session);
		LINGER.remove(a.getUUID());
		LINGER.remove(b.getUUID());
		Clash clash = new Clash(a.getId(), b.getId(), now, 0, 0L, 0);
		a.setAttached(CLASH, clash);
		b.setAttached(CLASH, clash);
		face(a, b);
		face(b, a);
		Spirits.show(a, Poses.CLASH);
		Spirits.show(b, Poses.CLASH);
		ServerLevel level = (ServerLevel)a.level();
		Vec3 mid = a.position().add(b.position()).scale(0.5);
		level.playSound(null, mid.x, mid.y + 1.0, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		PowersCompat.shake(a, SHAKE_MAX);
		PowersCompat.shake(b, SHAKE_MAX);
	}

	private static void face(ServerPlayer who, ServerPlayer at) {
		Vec3 to = at.position().subtract(who.position());
		float yaw = (float)Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F;
		who.connection.teleport(who.getX(), who.getY(), who.getZ(), yaw, 0.0F);
	}

	/** A CAST tap from {@code player}'s spirit key while they clash: it adds to their side (one a tick at most). */
	public static void tap(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		SpiritState state = Spirits.state(player);
		if (session == null || state == null) {
			return;
		}
		long now = player.level().getGameTime();
		float points = TAP + state.level() / TAP_PER_LEVELS;
		if (player.getUUID().equals(session.a)) {
			if (session.tappedA == now) {
				return;
			}
			session.tappedA = now;
			session.score += points;
		} else {
			if (session.tappedB == now) {
				return;
			}
			session.tappedB = now;
			session.score -= points;
		}
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT,
			SoundSource.PLAYERS, 0.7F, 1.0F + 0.4F * player.getRandom().nextFloat());
	}

	private static void tick(MinecraftServer minecraft) {
		long clock = minecraft.overworld().getGameTime();
		// finished clashes: take the flash's attachment off
		if (!LINGER.isEmpty()) {
			List<UUID> done = new ArrayList<>();
			for (Map.Entry<UUID, Long> e : LINGER.entrySet()) {
				if (clock >= e.getValue()) {
					done.add(e.getKey());
				}
			}
			for (UUID id : done) {
				LINGER.remove(id);
				ServerPlayer p = minecraft.getPlayerList().getPlayer(id);
				if (p != null) {
					Clash c = p.getAttached(CLASH);
					if (c != null && c.over()) {
						p.removeAttached(CLASH);
					}
				}
			}
		}
		if (SESSIONS.isEmpty()) {
			return;
		}
		List<Session> running = new ArrayList<>();
		for (Session s : SESSIONS.values()) {
			if (!running.contains(s)) {
				running.add(s);
			}
		}
		for (Session s : running) {
			ServerPlayer a = minecraft.getPlayerList().getPlayer(s.a), b = minecraft.getPlayerList().getPlayer(s.b);
			if (a == null || b == null || !a.isAlive() || !b.isAlive() || a.isSpectator() || b.isSpectator() || a.level() != b.level()) {
				abandon(s);
				continue;
			}
			SpiritState sa = Spirits.state(a), sb = Spirits.state(b);
			if (sa == null || sb == null || !sa.out() || !sb.out()) {
				abandon(s);
				continue;
			}
			long t = a.level().getGameTime() - s.start;
			s.score += LEVEL_RATE * (sa.level() - sb.level());
			root(a, s.spotA);
			root(b, s.spotB);
			if (t % 10L == 0L) {
				// (the arms keep their lock)
				Spirits.show(a, Poses.CLASH);
				Spirits.show(b, Poses.CLASH);
			}
			int lead = Math.round(Math.clamp(s.score * 100.0F / DECIDE, -100.0F, 100.0F));
			if (lead != s.lead) {
				s.lead = lead;
				Clash clash = new Clash(a.getId(), b.getId(), s.start, lead, 0L, 0);
				a.setAttached(CLASH, clash);
				b.setAttached(CLASH, clash);
			}
			if (t % 5L == 0L) {
				float shake = SHAKE_MIN + (SHAKE_MAX - SHAKE_MIN) * Math.min(1.0F, Math.abs(s.score) / DECIDE);
				PowersCompat.shake(a, shake);
				PowersCompat.shake(b, shake);
				Vec3 mid = a.position().add(b.position()).scale(0.5);
				Mana.sendSeen((ServerLevel)a.level(), Mana.MOTE, mid.x, mid.y + 1.2, mid.z, 6, 0.4, 0.4, 0.4, 0.08);
			}
			if (Math.abs(s.score) >= DECIDE || t >= TICKS) {
				finish(s, a, b, sa, sb);
			}
		}
	}

	/** Holds {@code p} where the clash found them. */
	private static void root(ServerPlayer p, Vec3 spot) {
		p.setDeltaMovement(Vec3.ZERO);
		if (p.position().distanceToSqr(spot) > DRIFT * DRIFT) {
			p.connection.teleport(spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
		}
		p.fallDistance = 0.0;
	}

	private static void finish(Session s, ServerPlayer a, ServerPlayer b, SpiritState sa, SpiritState sb) {
		SESSIONS.remove(s.a);
		SESSIONS.remove(s.b);
		long now = a.level().getGameTime();
		int winner = Math.abs(s.score) < 1.0F ? 0 : s.score > 0.0F ? 1 : 2;
		Clash over = new Clash(a.getId(), b.getId(), s.start, s.lead, now, winner);
		a.setAttached(CLASH, over);
		b.setAttached(CLASH, over);
		LINGER.put(s.a, now + FLASH_KEEP);
		LINGER.put(s.b, now + FLASH_KEEP);
		Spirits.show(a, Poses.IDLE);
		Spirits.show(b, Poses.IDLE);
		ServerLevel level = (ServerLevel)a.level();
		if (winner == 0) {
			push(a, b, PUSH);
			push(b, a, PUSH);
			PowersCompat.shake(a, 0.5F);
			PowersCompat.shake(b, 0.5F);
			level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
			return;
		}
		ServerPlayer win = winner == 1 ? a : b, lose = winner == 1 ? b : a;
		SpiritState sw = winner == 1 ? sa : sb, sl = winner == 1 ? sb : sa;
		push(lose, win, THROW);
		lose.hurtServer(level, SpiritFoes.source(level, win), HIT);
		Spirits.addBond(win, WIN_BOND);
		PowersCompat.shake(win, 0.4F);
		PowersCompat.shake(lose, 0.9F);
		level.playSound(null, lose.getX(), lose.getY(), lose.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		if (sw.kind() != Spirits.QUIET) {
			SpiritVoice.sayNow(win, sw.kind(), "clash.win");
		}
		if (sl.kind() != Spirits.QUIET && lose.isAlive()) {
			SpiritVoice.sayNow(lose, sl.kind(), "clash.lose");
		}
	}

	/** Throws {@code who} {@code blocks} away from {@code from}. */
	private static void push(ServerPlayer who, ServerPlayer from, double blocks) {
		Vec3 away = who.position().subtract(from.position());
		away = new Vec3(away.x, 0.0, away.z);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, who.getYRot()).reverse() : away.normalize();
		// (drag takes the launch speed's reach to about four times it)
		double speed = blocks * 0.26;
		who.setDeltaMovement(away.x * speed, 0.3 + blocks * 0.02, away.z * speed);
		who.syncVelocity = true;
	}

	/** A side dropped out (death, a disconnect, a spirit put away): the clash ends with no winner and no flash. */
	private static void abandon(Session s) {
		SESSIONS.remove(s.a);
		SESSIONS.remove(s.b);
		LINGER.remove(s.a);
		LINGER.remove(s.b);
		for (UUID id : new UUID[] {s.a, s.b}) {
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.removeAttached(CLASH);
				if (p.isAlive()) {
					Spirits.show(p, Poses.IDLE);
				}
			}
		}
	}

	/** {@code id} left the game: their clash ends, and nothing about them is kept. */
	public static void forget(UUID id) {
		STRIKES.remove(id);
		Session s = SESSIONS.get(id);
		if (s != null) {
			abandon(s);
		}
		LINGER.remove(id);
	}
}
