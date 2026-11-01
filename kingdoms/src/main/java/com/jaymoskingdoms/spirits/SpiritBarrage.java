package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.467) The barrage clash. Andy: "when one of these punches clashes with another you get a barrage style clash, that
 * requires precise timing or manipulation otherwise to win" (the stand rush, two flurries of fists meeting in the middle).
 * Two strong strikes ({@link SpiritPunch}) meeting start it, and so can a scripted fight ({@link #start}: the Grue trial's
 * finale). Both sides are rooted face to face, the spirits' fists pound each other in the middle ({@link Poses#BARRAGE}),
 * and the film runs ({@code ClashCinematic}: letterbox, side camera, tug bar) to its own drum track
 * ({@code BarrageMusic}). The drum is the clock.
 * <ul>
 * <li><b>Strike on the beat.</b> Each drum beat ({@link #BEATS}: {@link #LEAD_IN} ticks of count-in, then every 10 ticks,
 * every 8, and every 6 at the end) you tap the spirit key. Within {@link #PERFECT} ticks of it is a perfect blow
 * ({@link #PTS_PERFECT} points), within {@link #GOOD} a good one ({@link #PTS_GOOD}); blows landed in a row add
 * {@link #STREAK_BONUS} each, up to {@link #STREAK_MAX}. A beat let pass scores nothing.</li>
 * <li><b>No mashing.</b> A tap outside the window, or a second tap on the same beat, staggers you: {@link #STAGGER} points
 * to them and your next beat is lost.</li>
 * <li><b>Feint</b> (the manipulation): sneak and tap on a beat. Your fist pulls short; if they struck that beat, their blow
 * goes past you and its worth is yours instead ({@link #FEINT_GAIN}), and their streak breaks. If they didn't strike, the
 * feint is wasted. A feint costs {@link #FEINT_COST} mana and can be thrown once in {@link #FEINT_EVERY} beats.</li>
 * </ul>
 * The tap's timing is judged on the tapper's own screen against the beats (sent as an offset, {@link Tap}), so lag doesn't
 * move the beat. It is decided at a {@link #DECIDE} point lead or when the drum ends; a near tie ({@link #DRAW_BAND}) is a
 * draw and both are thrown back. The winner's flurry breaks through: {@link #RUSH_TICKS} ticks of blows on the loser, a last
 * one with an impact frame, and the loser thrown {@link #THROW} blocks. The winner's bond grows. A non-player side (a folk
 * keeper, a boss) keeps the beat by its {@code skill}.
 */
public final class SpiritBarrage {
	/** How long after a punch (ticks) an answering punch still meets it. */
	public static final int MEET_WINDOW = 12;
	/** How far apart (blocks) two punches can be thrown and meet. */
	static final double MEET_RANGE = 10.0;
	/** The count-in before the first beat (ticks): two clicks, 10 ticks apart. */
	public static final int LEAD_IN = 20;
	/** The beats, as ticks after the start: 6 every 10, 6 every 8, 8 every 6. The drum track is written to these. */
	public static final int[] BEATS = beats(new int[] {6, 6, 8}, new int[] {10, 8, 6});
	/** The windows round a beat (ticks either side): a perfect blow, a good one. */
	public static final float PERFECT = 1.0F, GOOD = 2.5F;
	/** A beat is judged this many ticks after it (the window, and room for the tap to arrive). */
	static final int JUDGE_AFTER = 7;
	/** Points: a perfect blow, a good one, each blow of a streak after the first (and the most it adds), a stagger, a feint that catches. */
	static final float PTS_PERFECT = 4.0F, PTS_GOOD = 2.0F, STREAK_BONUS = 0.5F, STREAK_MAX = 2.0F, STAGGER = 3.0F, FEINT_GAIN = 4.0F;
	/** A feint's mana, and the fewest beats between two. */
	static final float FEINT_COST = 8.0F;
	static final int FEINT_EVERY = 3;
	/** The lead that decides it early (points; the lead shown is this scaled to 100), and how near a tie is a draw. */
	static final float DECIDE = 30.0F, DRAW_BAND = 2.0F;
	/** The winner's flurry breaking through: how long (ticks), a blow every this many, each blow's hurt, the last one's. */
	static final int RUSH_TICKS = 20, RUSH_EVERY = 2;
	static final float RUSH_HIT = 1.0F, RUSH_FINAL = 4.0F;
	/** The loser's throw and a draw's push (blocks); the winner's bond. */
	static final double THROW = 8.0, PUSH = 3.0;
	static final int WIN_BOND = 4;
	/** How far a rooted side may drift (blocks) before it is set back. */
	static final double DRIFT = 0.15;
	/** How long the finished barrage's attachment stays for the flash (ticks). */
	static final int FLASH_KEEP = 14;
	/** The AI side: its chance to strike a beat at no skill and at full; its chance a struck beat is perfect; its feint chance. */
	static final float AI_STRIKE_LOW = 0.45F, AI_STRIKE_HIGH = 0.95F, AI_PERFECT_LOW = 0.2F, AI_PERFECT_HIGH = 0.8F, AI_FEINT = 0.12F;

	/** What happened on a side's last beat, for its HUD ({@link Barrage#feedA}): code in the low 4 bits, beat index above. */
	public static final int FEED_PERFECT = 1, FEED_GOOD = 2, FEED_MISS = 3, FEED_STAGGER = 4, FEED_SLIPPED = 5, FEED_CAUGHT = 6,
		FEED_WASTED = 7;
	/** A beat's acts. */
	static final int NONE = 0, STRIKE_PERFECT = 1, STRIKE_GOOD = 2, FEINT = 3;

	/** How a barrage ended, for {@link Ending}: as the player's side ({@code start}'s first argument) saw it. */
	public enum Result {
		/** The player's flurry broke through. */
		WON,
		/** The foe's did. */
		LOST,
		/** Neither: both thrown back. */
		DRAW,
		/** Broken off: a side died, left, changed world, or {@link #cancel} was called. */
		BROKEN
	}

	/** Told once, when a barrage started by {@link #start} ends (after the winner's flurry has landed). */
	@FunctionalInterface
	public interface Ending {
		void ended(Result result);
	}

	/**
	 * A barrage, on both sides (synced to everyone): the two entity ids, the game time it began, the lead of {@code a} (-100 to
	 * 100), the winner once decided (0 none yet or a draw, 1 {@code a}, 2 {@code b}), when the winner's flurry began (0 not
	 * yet), when it ended (0 running; then the closing flash), each side's colour (RGB), and each side's last beat
	 * ({@link #FEED_PERFECT}...).
	 */
	public record Barrage(int a, int b, long start, int lead, int winner, long rush, long end, int colorA, int colorB, int feedA, int feedB) {
		public static final StreamCodec<ByteBuf, Barrage> STREAM_CODEC = StreamCodec.of((buf, v) -> {
			ByteBufCodecs.VAR_INT.encode(buf, v.a);
			ByteBufCodecs.VAR_INT.encode(buf, v.b);
			ByteBufCodecs.VAR_LONG.encode(buf, v.start);
			ByteBufCodecs.VAR_INT.encode(buf, v.lead);
			ByteBufCodecs.VAR_INT.encode(buf, v.winner);
			ByteBufCodecs.VAR_LONG.encode(buf, v.rush);
			ByteBufCodecs.VAR_LONG.encode(buf, v.end);
			ByteBufCodecs.INT.encode(buf, v.colorA);
			ByteBufCodecs.INT.encode(buf, v.colorB);
			ByteBufCodecs.VAR_INT.encode(buf, v.feedA);
			ByteBufCodecs.VAR_INT.encode(buf, v.feedB);
		}, buf -> new Barrage(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf)));

		/** Whether it is over (the closing flash is still showing). */
		public boolean over() {
			return end != 0L;
		}

		/** Whether the winner's flurry is landing now. */
		public boolean rushing() {
			return rush != 0L && end == 0L;
		}
	}

	/** The barrage an entity is in (or has just left: {@link Barrage#over}). Not saved. Synced to everyone. */
	public static final AttachmentType<Barrage> BARRAGE = AttachmentRegistry.create(JaymosKingdoms.id("spirit_barrage"),
		builder -> builder.syncWith(Barrage.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * Player → server: a tap in a barrage. {@code beat} is the beat it was nearest on their screen, {@code offset} how far from
	 * it (hundredths of a tick, early negative), {@code feint} whether they sneaked.
	 */
	public record Tap(int beat, int offset, boolean feint) implements CustomPacketPayload {
		public static final Type<Tap> TYPE = new Type<>(JaymosKingdoms.id("spirit_barrage_tap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tap> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.beat);
				buf.writeInt(p.offset);
				buf.writeBoolean(p.feint);
			}, buf -> new Tap(buf.readVarInt(), buf.readInt(), buf.readBoolean()));

		@Override
		public Type<Tap> type() {
			return TYPE;
		}
	}

	/** A running barrage. {@code score} is a's points less b's. Side 0 is {@code a}, 1 is {@code b}. */
	private static final class Session {
		final LivingEntity[] side = new LivingEntity[2];
		final Vec3[] spot = new Vec3[2];
		final int[][] act = new int[2][BEATS.length];
		final boolean[][] lost = new boolean[2][BEATS.length];
		final int[] streak = new int[2], lastFeint = {-99, -99}, feed = new int[2], color = new int[2];
		final long start;
		final float skill;
		final @Nullable Ending onEnd;
		float score;
		int lead, judged, winner;
		long rush;
		boolean told;

		Session(LivingEntity a, LivingEntity b, long start, float skill, @Nullable Ending onEnd) {
			this.side[0] = a;
			this.side[1] = b;
			this.spot[0] = a.position();
			this.spot[1] = b.position();
			this.start = start;
			this.skill = Math.clamp(skill, 0.0F, 1.0F);
			this.onEnd = onEnd;
			this.color[0] = colorOf(a);
			this.color[1] = colorOf(b);
		}

		int of(Entity e) {
			return this.side[0] == e ? 0 : 1;
		}

		Barrage record(long end) {
			return new Barrage(this.side[0].getId(), this.side[1].getId(), this.start, this.lead, this.winner, this.rush, end, this.color[0],
				this.color[1], this.feed[0], this.feed[1]);
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Finished barrages whose attachment is still up: the entity, and the game time to take it off. */
	private static final Map<Entity, Long> LINGER = new HashMap<>();
	/** While a barrage's own blows are being dealt (they get through the guard on the two sides). */
	private static boolean dealing;

	private SpiritBarrage() {
	}

	private static int[] beats(int[] counts, int[] gaps) {
		List<Integer> out = new ArrayList<>();
		int t = LEAD_IN;
		for (int i = 0; i < counts.length; i++) {
			for (int n = 0; n < counts[i]; n++) {
				out.add(t);
				t += gaps[i];
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("spirit barrage attachment: {}", BARRAGE);
		PayloadTypeRegistry.serverboundPlay().register(Tap.TYPE, Tap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Tap.TYPE, (payload, context) -> tap(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(SpiritBarrage::tick);
		// the two sides can't hurt each other but by the barrage's own blows (others still can)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Session s = SESSIONS.get(entity.getUUID());
			return dealing || s == null || source.getEntity() != s.side[1 - s.of(entity)];
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SpiritPunch.forget(handler.getPlayer().getUUID());
			Session s = SESSIONS.get(handler.getPlayer().getUUID());
			if (s != null) {
				abandon(s);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			List<Session> all = new ArrayList<>(SESSIONS.values());
			for (Session s : all) {
				tell(s, Result.BROKEN);
			}
			SESSIONS.clear();
			LINGER.clear();
			SpiritPunch.clear();
		});
	}

	// ---- the public side ---------------------------------------------------------------------------------------------

	/** Whether {@code entity} is in a barrage (counting the winner's flurry, not the closing flash). */
	public static boolean busy(@Nullable Entity entity) {
		return entity != null && SESSIONS.containsKey(entity.getUUID());
	}

	/** Whether {@code entity} is in a barrage: the same as {@link #busy}. */
	public static boolean inBarrage(@Nullable Entity entity) {
		return busy(entity);
	}

	/**
	 * Starts a barrage between {@code player} and {@code foe} now (the Grue trial's finale, a folk keeper answering a punch).
	 * {@code foe} may be any living thing; it keeps the beat by {@code skill} (0 clumsy to 1 near flawless). Neither needs a
	 * spirit out. {@code onEnd} is told once how it ended, also when it is broken off. A scripted barrage (one with an
	 * {@code onEnd}) never takes either side below 1 health: what the result means is the caller's. False, and nothing
	 * starts, when either is already in one, dead, or not in the same world.
	 */
	public static boolean start(ServerPlayer player, LivingEntity foe, float skill, @Nullable Ending onEnd) {
		if (player == foe || busy(player) || busy(foe) || SpiritClash.inClash(player) || !player.isAlive() || !foe.isAlive()
			|| player.isSpectator() || player.level() != foe.level()) {
			return false;
		}
		begin(player, foe, skill, onEnd);
		return true;
	}

	/** Breaks off the barrage {@code entity} is in (its {@link Ending} is told {@link Result#BROKEN}). */
	public static void cancel(Entity entity) {
		Session s = SESSIONS.get(entity.getUUID());
		if (s != null) {
			abandon(s);
		}
	}

	/** Two keepers' punches met ({@link SpiritPunch}): {@code first} threw first. False if they can't lock. */
	static boolean meet(ServerPlayer first, ServerPlayer second) {
		if (busy(first) || busy(second) || SpiritClash.inClash(first) || SpiritClash.inClash(second) || first.level() != second.level()
			|| first.distanceTo(second) > MEET_RANGE || !Spirits.out(first) || !Spirits.out(second)) {
			return false;
		}
		begin(first, second, 0.0F, null);
		return true;
	}

	private static void begin(LivingEntity a, LivingEntity b, float skill, @Nullable Ending onEnd) {
		long now = a.level().getGameTime();
		Session s = new Session(a, b, now, skill, onEnd);
		SESSIONS.put(a.getUUID(), s);
		SESSIONS.put(b.getUUID(), s);
		LINGER.remove(a);
		LINGER.remove(b);
		sync(s);
		face(a, b);
		face(b, a);
		pose(a, Poses.BARRAGE);
		pose(b, Poses.BARRAGE);
		if (b instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		ServerLevel level = (ServerLevel)a.level();
		Vec3 mid = mid(s);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
		PowersCompat.impactFrame(level, mid);
		shake(a, 0.5F);
		shake(b, 0.5F);
	}

	// ---- taps --------------------------------------------------------------------------------------------------------

	private static void tap(ServerPlayer player, Tap tap) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null || s.rush != 0L || tap.beat() < s.judged || tap.beat() >= BEATS.length) {
			return;
		}
		int me = s.of(player), k = tap.beat();
		long now = player.level().getGameTime();
		// (a tap claimed for a beat far from now is someone's clock gone wrong: let it go)
		long beatAt = s.start + BEATS[k];
		if (now < beatAt - 10L || now > beatAt + JUDGE_AFTER) {
			return;
		}
		float off = Math.abs(tap.offset() / 100.0F);
		if (s.lost[me][k]) {
			return;
		}
		if (off > GOOD || s.act[me][k] != NONE) {
			stagger(s, me, k);
			return;
		}
		if (tap.feint() && k - s.lastFeint[me] >= FEINT_EVERY && Mana.pay(player, FEINT_COST, Mana.WILL)) {
			s.act[me][k] = FEINT;
			s.lastFeint[me] = k;
			return;
		}
		s.act[me][k] = off <= PERFECT ? STRIKE_PERFECT : STRIKE_GOOD;
	}

	/** Side {@code me} mistimed on beat {@code k}: points to the other side, and its next beat is lost. */
	private static void stagger(Session s, int me, int k) {
		s.score += me == 0 ? -STAGGER : STAGGER;
		s.streak[me] = 0;
		if (k + 1 < BEATS.length) {
			s.lost[me][k + 1] = true;
		}
		s.feed[me] = (k << 4) | FEED_STAGGER;
		LivingEntity who = s.side[me];
		who.level().playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 0.6F);
		shake(who, 0.3F);
		sync(s);
	}

	/** A side that isn't a player keeps the beat by its skill. */
	private static void think(Session s, int me, int k) {
		LivingEntity who = s.side[me];
		if (who instanceof ServerPlayer || s.lost[me][k]) {
			return;
		}
		var random = who.getRandom();
		if (k - s.lastFeint[me] >= FEINT_EVERY && random.nextFloat() < AI_FEINT * s.skill) {
			s.act[me][k] = FEINT;
			s.lastFeint[me] = k;
			return;
		}
		if (random.nextFloat() < lerp(s.skill, AI_STRIKE_LOW, AI_STRIKE_HIGH)) {
			s.act[me][k] = random.nextFloat() < lerp(s.skill, AI_PERFECT_LOW, AI_PERFECT_HIGH) ? STRIKE_PERFECT : STRIKE_GOOD;
		}
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	/** Beat {@code k} is judged: blows, feints, misses. */
	private static void judge(Session s, int k) {
		float[] gain = new float[2];
		int[] feed = new int[2];
		boolean bothLanded = true;
		for (int me = 0; me < 2; me++) {
			int other = 1 - me;
			int mine = s.act[me][k], theirs = s.act[other][k];
			if (s.lost[me][k]) {
				feed[me] = FEED_STAGGER;
				bothLanded = false;
				continue;
			}
			if (mine == STRIKE_PERFECT || mine == STRIKE_GOOD) {
				if (theirs == FEINT) {
					// pulled short: your blow goes past them, and they have its worth
					gain[other] += FEINT_GAIN;
					s.streak[me] = 0;
					feed[me] = FEED_CAUGHT;
					feed[other] = FEED_SLIPPED;
					bothLanded = false;
					continue;
				}
				s.streak[me]++;
				gain[me] += (mine == STRIKE_PERFECT ? PTS_PERFECT : PTS_GOOD) + Math.min(STREAK_MAX, (s.streak[me] - 1) * STREAK_BONUS);
				feed[me] = mine == STRIKE_PERFECT ? FEED_PERFECT : FEED_GOOD;
			} else {
				bothLanded = false;
				if (mine == FEINT) {
					if (feed[me] != FEED_SLIPPED) {
						feed[me] = theirs == STRIKE_PERFECT || theirs == STRIKE_GOOD ? FEED_SLIPPED : FEED_WASTED;
					}
				} else {
					s.streak[me] = 0;
					feed[me] = FEED_MISS;
				}
			}
		}
		s.score += gain[0] - gain[1];
		s.feed[0] = (k << 4) | feed[0];
		s.feed[1] = (k << 4) | feed[1];
		ServerLevel level = (ServerLevel)s.side[0].level();
		Vec3 mid = mid(s);
		if (bothLanded) {
			// fist on fist
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.6F + 0.4F * level.getRandom().nextFloat());
			level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 10, 0.25, 0.25, 0.25, 0.5);
			Mana.sendSeen(level, Mana.MOTE, mid.x, mid.y, mid.z, 10, 0.3, 0.3, 0.3, 0.15);
		} else if (gain[0] != gain[1]) {
			// one side's blows got through
			LivingEntity hit = s.side[gain[0] > gain[1] ? 1 : 0];
			Vec3 at = hit.getBoundingBox().getCenter();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.25, 0.3, 0.25, 0.3);
			shake(hit, 0.35F);
		}
		updateLead(s);
		sync(s);
	}

	private static void updateLead(Session s) {
		s.lead = Math.round(Math.clamp(s.score * 100.0F / DECIDE, -100.0F, 100.0F));
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long clock = server.overworld().getGameTime();
		SpiritPunch.tick(clock);
		if (!LINGER.isEmpty()) {
			List<Entity> done = new ArrayList<>();
			for (Map.Entry<Entity, Long> e : LINGER.entrySet()) {
				if (clock >= e.getValue() || e.getKey().isRemoved()) {
					done.add(e.getKey());
				}
			}
			for (Entity e : done) {
				LINGER.remove(e);
				Barrage b = e.getAttached(BARRAGE);
				if (b != null && b.over() && !busy(e)) {
					e.removeAttached(BARRAGE);
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
			LivingEntity a = s.side[0], b = s.side[1];
			boolean gone = a.isRemoved() || b.isRemoved() || a.level() != b.level() || a instanceof ServerPlayer pa && pa.isSpectator()
				|| b instanceof ServerPlayer pb && pb.isSpectator();
			if (gone || (!a.isAlive() || !b.isAlive()) && s.rush == 0L) {
				abandon(s);
				continue;
			}
			long now = a.level().getGameTime();
			long t = now - s.start;
			root(a, s.spot[0], b);
			root(b, s.spot[1], a);
			if (s.rush != 0L) {
				rush(s, now);
				continue;
			}
			if (t % 8L == 0L) {
				pose(a, Poses.BARRAGE);
				pose(b, Poses.BARRAGE);
			}
			ServerLevel level = (ServerLevel)a.level();
			Vec3 mid = mid(s);
			if (t >= LEAD_IN - 4) {
				// the fists pounding in the middle, every tick
				Mana.sendSeen(level, Mana.MOTE, mid.x, mid.y, mid.z, 3, 0.35, 0.3, 0.35, 0.1);
				if (t % 2L == 0L) {
					level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.2);
					level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.35F,
						0.8F + 0.6F * level.getRandom().nextFloat());
				}
			}
			for (int k = s.judged; k < BEATS.length && t >= BEATS[k]; k++) {
				if (t == BEATS[k]) {
					think(s, 0, k);
					think(s, 1, k);
				}
			}
			while (s.judged < BEATS.length && t >= BEATS[s.judged] + JUDGE_AFTER) {
				judge(s, s.judged);
				s.judged++;
				if (Math.abs(s.score) >= DECIDE) {
					break;
				}
			}
			if (Math.abs(s.score) >= DECIDE || s.judged >= BEATS.length) {
				decide(s, now);
			}
		}
	}

	/** It is decided: a draw ends it now; otherwise the winner's flurry begins. */
	private static void decide(Session s, long now) {
		updateLead(s);
		if (Math.abs(s.score) < DRAW_BAND) {
			s.winner = 0;
			LivingEntity a = s.side[0], b = s.side[1];
			push(a, b, PUSH);
			push(b, a, PUSH);
			shake(a, 0.5F);
			shake(b, 0.5F);
			a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
			close(s, now, Result.DRAW);
			return;
		}
		s.winner = s.score > 0.0F ? 1 : 2;
		s.rush = now;
		LivingEntity win = s.side[s.winner - 1], lose = s.side[2 - s.winner];
		pose(win, Poses.BARRAGE);
		pose(lose, Poses.IDLE);
		sync(s);
		say(win, "clash.win");
	}

	/** The winner's flurry landing on the loser; at its end the last blow, and the throw. */
	private static void rush(Session s, long now) {
		LivingEntity win = s.side[s.winner - 1], lose = s.side[2 - s.winner];
		ServerLevel level = (ServerLevel)win.level();
		long t = now - s.rush;
		if (t % 8L == 0L) {
			pose(win, Poses.BARRAGE);
		}
		Vec3 at = lose.getBoundingBox().getCenter();
		if (t < RUSH_TICKS && t % RUSH_EVERY == 0L && lose.isAlive()) {
			hit(s, level, win, lose, RUSH_HIT);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.3, 0.35, 0.3, 0.4);
			Mana.sendSeen(level, Mana.MOTE, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.12);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.7F + 0.6F * level.getRandom().nextFloat());
			shake(lose, 0.4F);
			return;
		}
		if (t < RUSH_TICKS && lose.isAlive()) {
			return;
		}
		// the last blow
		if (lose.isAlive()) {
			hit(s, level, win, lose, RUSH_FINAL);
		}
		PowersCompat.impactFrame(level, at);
		push(lose, win, THROW);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		shake(win, 0.5F);
		shake(lose, 1.0F);
		if (win instanceof ServerPlayer player) {
			Spirits.addBond(player, WIN_BOND);
		}
		if (lose.isAlive()) {
			say(lose, "clash.lose");
		}
		close(s, now, s.winner == 1 ? Result.WON : Result.LOST);
	}

	/** One of the barrage's own blows (scaled by the striker's spirit level; never below 1 health in a scripted barrage). */
	private static void hit(Session s, ServerLevel level, LivingEntity win, LivingEntity lose, float base) {
		SpiritState state = Spirits.state(win);
		float damage = base * (state == null ? 1.0F : Spirits.damageScale(state));
		if (s.onEnd != null) {
			damage = Math.min(damage, lose.getHealth() - 1.0F);
		}
		if (damage <= 0.0F) {
			return;
		}
		lose.invulnerableTime = 0;
		dealing = true;
		try {
			lose.hurtServer(level, SpiritFoes.source(level, win), damage);
		} finally {
			dealing = false;
		}
	}

	/** Ends it: the closing flash, rest poses, and the caller is told. */
	private static void close(Session s, long now, Result result) {
		LivingEntity a = s.side[0], b = s.side[1];
		SESSIONS.remove(a.getUUID());
		SESSIONS.remove(b.getUUID());
		Barrage over = s.record(now);
		a.setAttached(BARRAGE, over);
		b.setAttached(BARRAGE, over);
		LINGER.put(a, now + FLASH_KEEP);
		LINGER.put(b, now + FLASH_KEEP);
		pose(a, Poses.IDLE);
		pose(b, Poses.IDLE);
		tell(s, result);
	}

	/** A side dropped out: it ends with no winner and no flash, and the caller is told it was broken off. */
	private static void abandon(Session s) {
		for (LivingEntity e : s.side) {
			SESSIONS.remove(e.getUUID());
			LINGER.remove(e);
			e.removeAttached(BARRAGE);
			if (e.isAlive() && !e.isRemoved()) {
				pose(e, Poses.IDLE);
			}
		}
		tell(s, Result.BROKEN);
	}

	private static void tell(Session s, Result result) {
		if (s.told || s.onEnd == null) {
			return;
		}
		s.told = true;
		try {
			s.onEnd.ended(result);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.error("a barrage's ending failed", e);
		}
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	private static void sync(Session s) {
		Barrage now = s.record(0L);
		s.side[0].setAttached(BARRAGE, now);
		s.side[1].setAttached(BARRAGE, now);
	}

	private static Vec3 mid(Session s) {
		LivingEntity a = s.side[0], b = s.side[1];
		return a.getEyePosition().add(b.getEyePosition()).scale(0.5).add(0.0, -0.2, 0.0);
	}

	/** The colour (RGB) of the spirit {@code who} keeps: the Grue's red when it holds them, the mana's blue with none. */
	private static int colorOf(LivingEntity who) {
		if (Grue.taken(who)) {
			return Grue.COLOR;
		}
		SpiritState state = Spirits.state(who);
		return state == null ? 0x4C8DFF : Spirits.of(state.kind()).color() & 0xFFFFFF;
	}

	/** The arms of {@code who}, if it keeps a spirit, hold {@code pose} from now. */
	private static void pose(LivingEntity who, int pose) {
		if (who instanceof ServerPlayer player) {
			Spirits.show(player, pose);
			return;
		}
		SpiritState state = Spirits.state(who);
		if (state != null) {
			who.setAttached(Spirits.SPIRIT, state.withShow(who.level().getGameTime(), pose));
		}
	}

	private static void shake(LivingEntity who, float strength) {
		if (who instanceof ServerPlayer player) {
			PowersCompat.shake(player, strength);
		}
	}

	private static void say(LivingEntity who, String event) {
		SpiritState state = Spirits.state(who);
		if (who instanceof ServerPlayer player && state != null && state.kind() != Spirits.QUIET && !Grue.taken(player)) {
			SpiritVoice.sayNow(player, state.kind(), event);
		}
	}

	private static float yawTo(LivingEntity who, LivingEntity at) {
		Vec3 to = at.position().subtract(who.position());
		return (float)Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F;
	}

	private static void face(LivingEntity who, LivingEntity at) {
		float yaw = yawTo(who, at);
		if (who instanceof ServerPlayer player) {
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, 0.0F);
		} else {
			who.setYRot(yaw);
			who.setYHeadRot(yaw);
			who.setYBodyRot(yaw);
		}
	}

	/** Holds {@code who} where the barrage found it, facing {@code at}. */
	private static void root(LivingEntity who, Vec3 spot, LivingEntity at) {
		who.setDeltaMovement(Vec3.ZERO);
		who.fallDistance = 0.0;
		if (who instanceof ServerPlayer player) {
			if (player.position().distanceToSqr(spot) > DRIFT * DRIFT) {
				player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
			}
			return;
		}
		if (who instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (who.position().distanceToSqr(spot) > DRIFT * DRIFT) {
			who.setPos(spot.x, spot.y, spot.z);
		}
		face(who, at);
	}

	/** Throws {@code who} {@code blocks} away from {@code from}. */
	private static void push(LivingEntity who, LivingEntity from, double blocks) {
		Vec3 away = who.position().subtract(from.position());
		away = new Vec3(away.x, 0.0, away.z);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, who.getYRot()).reverse() : away.normalize();
		double speed = blocks * 0.26;
		who.setDeltaMovement(away.x * speed, 0.3 + blocks * 0.02, away.z * speed);
		who.syncVelocity = true;
	}
}
