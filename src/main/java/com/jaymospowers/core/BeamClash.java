package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A beam battle: two held streams (Wind Whistle, Smoke/Soot Jet, or any beam that {@link #register}s) meeting head-on
 * lock into a clash instead of passing through each other. The knot where they grind together sits on the line between
 * the two users; a balance from -1 to 1 moves it. Each side pushes with a steady base by its power level (more
 * ascended, more with every mastered ability, a ki wave stronger in every form) plus a surge built by tapping X while
 * holding it, weighed by the two sides' powers (a release shorter than {@link #GRACE_TICKS} is a tap, not a let-go). Both are rooted, slowed and hungry while it lasts. The knot reaching a user overwhelms them; a
 * user letting go has the other's stream roll through; a stalemate past {@link #MAX_TICKS} detonates the knot.
 * Server-authoritative: the clients only send their held X (the beams' own Hold packets, routed here by {@link #input}).
 * A fired beam (one with a {@link Shape}: the Z Fighters' ki waves) travels on its own once let go: it meets others
 * where its head has got to, every press of its key is a tap, and only looking away lets it go; winning, it travels on.
 * Everyone near sees it through the synced {@link #CLASH} (the client draws it: {@code client/BeamClashClient}).
 */
public final class BeamClash {
	// ---- meeting ----
	/** Two streams meet when their lines pass within this many blocks of each other. */
	public static final double MEET_DISTANCE = 1.5;
	/** Each user's look must be within this of the line to the other (cosine: about 37 degrees). */
	public static final double FACING_COS = 0.8;
	/** The users must be at least this far apart (blocks). */
	public static final double MIN_APART = 3.0;
	/** The knot starts no nearer either end than this share of the way. */
	public static final double START_SHARE_MIN = 0.2;
	// ---- the struggle ----
	/** Balance a tick from the steady push, before bonuses. */
	public static final float BASE_PUSH = 0.005F;
	/** The steady push grows by this share when ascended, and by this share per mastered ability. */
	public static final float ASCENDED_BONUS = 0.6F, MASTERY_BONUS = 0.1F;
	/**
	 * A tap of X adds this to the surge; the surge fades by this factor a tick and holds at most this. (0.17.1) Andy:
	 * "near impossible to break without a SERIOUS struggle ... overdone": each tap is worth half again what it was and
	 * the surge fades slower, so about six taps a second fills it (before, nothing short of ten did), and from there
	 * power decides.
	 */
	public static final float TAP_PUSH = 0.009F, SURGE_DECAY = 0.9F, SURGE_MAX = 0.03F;
	/**
	 * (0.17.1) Power levels: each side's power is its steady push over the base (ascension, mastery, a ki wave's live
	 * strength, which rises with every form). Its taps and its surge's cap are weighed by the square root of its power
	 * over the other's, kept between these, so the stronger side's mashing counts for more.
	 */
	public static final float TAP_WEIGHT_MIN = 0.75F, TAP_WEIGHT_MAX = 1.35F;
	/**
	 * Powering up mid-clash (a Z Fighter going Super): power jumping by this share in a tick fills that side's surge at
	 * once, shoves the knot and shakes both.
	 */
	public static final float POWER_UP_JUMP = 0.06F, POWER_UP_SHAKE = 1.4F;
	/** Taps closer together than this many ticks count once (no macro wins). */
	public static final int TAP_MIN_TICKS = 2;
	/** A release of X shorter than this is a tap; longer is letting go. */
	public static final int GRACE_TICKS = 6;
	/** The push wavers by up to this much a tick, so no two clashes run the same. */
	public static final float JITTER = 0.004F;
	/** A stalemate this long detonates the knot (8 s). */
	public static final int MAX_TICKS = 160;
	/** A user looking further than this off the knot has let go (cosine: 60 degrees). */
	public static final double AIM_BREAK_COS = 0.5;
	/** Pulled further apart than both ranges and this, the streams part with nothing (blocks). */
	public static final double BREAK_SLACK = 6.0;
	/** Hunger a tick for each user, on top of the stream's own. */
	public static final float EXHAUSTION = 0.05F;
	/** Rooted and slowed: walking speed and jumping lost (shares). */
	public static final double ROOT_SLOW = 0.9, ROOT_JUMP = 1.0;
	/** Light screen shake for both every this many ticks, this strong (degrees); harder for whoever is losing. */
	public static final int SHAKE_EVERY = 8;
	public static final float SHAKE = 0.5F, SHAKE_LOSING = 0.6F;
	/** The jolt when the streams lock (degrees). */
	public static final float LOCK_SHAKE = 1.5F;
	/** The knot hurts and throws out what wanders into it (not the two users): radius, damage, every, push. */
	public static final double KNOT_RADIUS = 1.6, KNOT_THROW = 0.35;
	public static final float KNOT_DAMAGE = 1.0F;
	public static final int KNOT_HURT_EVERY = 10;
	/** Smoke churned into the knot a tick by a smoke side (the real gas, so it lingers). */
	public static final float KNOT_SMOKE = 0.1F;
	/** Soot Jet: the knot blinds what's within this of it, and the other user once the knot is this near them. */
	public static final double SOOT_RADIUS = 3.0, SOOT_REACH = 5.0;
	public static final int SOOT_BLIND_TICKS = 30;
	/** Each stream stops this short of the knot. */
	public static final double KNOT_GAP = 0.5;
	// ---- endings ----
	/** Overwhelmed: damage, how hard they're thrown (blocks/tick) and lifted. */
	public static final float OVERWHELM_DAMAGE = 6.0F;
	public static final double OVERWHELM_THROW = 1.8, OVERWHELM_LIFT = 0.55;
	/** Wind overwhelms: the loser spins this many degrees a tick, slowing over this many ticks. */
	public static final float SPIN_DEGREES = 40.0F;
	public static final int SPIN_TICKS = 24;
	/** Smoke overwhelms: engulfed in this much smoke, blinded, out of breath; Soot blinds longer. */
	public static final float ENGULF_SMOKE = 14.0F;
	public static final int ENGULF_BLIND_TICKS = 40, SOOT_ENGULF_BLIND_TICKS = 80;
	/** The loser can't start a stream again for this long. */
	public static final int LOSER_COOLDOWN = 60;
	/** Overwhelmed: the loser's shake and the winner's (degrees). */
	public static final float OVERWHELM_SHAKE = 3.0F, WINNER_SHAKE = 1.2F;
	/** Let go: the other's stream rolls through, throwing (blocks/tick) and hurting the one who let go. */
	public static final double ROLL_THROW = 0.9;
	public static final float ROLL_DAMAGE = 2.0F, ROLL_SHAKE = 1.6F;
	/** Detonation: both thrown back, hurt, and both streams resting this long; what's near the knot is thrown too. */
	public static final double DETONATE_THROW = 1.4, DETONATE_LIFT = 0.5, DETONATE_RADIUS = 6.0;
	public static final float DETONATE_DAMAGE = 4.0F, DETONATE_SMOKE = 24.0F, DETONATE_SHAKE = 3.0F;
	/** How far the detonation's shake reaches (blocks). */
	public static final double DETONATE_SHAKE_RANGE = 24.0;
	public static final int DETONATE_COOLDOWN = 100;
	/** Screen washes on the endings ({@code WindNet.ScreenFx} vignettes): length in ticks. */
	public static final int FX_TICKS = 30;

	/** What a stream is, for its look and for what it does to whoever it overwhelms. */
	public static final int KIND_WIND = 0, KIND_SMOKE = 1, KIND_SOOT = 2, KIND_KI = 3;
	// ---- ki ----
	/** Ki overwhelms: the loser is also burned this much more and set alight this long (ticks). */
	public static final float KI_OVERWHELM_DAMAGE = 4.0F;
	public static final int KI_BURN_TICKS = 40;

	/**
	 * A beam that can clash: its kind for this user, whether it's running, how far it reaches and how to stop it.
	 * Wind Whistle and Smoke Jet register themselves; another beam (Z Fighter ki) can join the same way.
	 */
	public record Beam(ToIntFunction<ServerPlayer> kind, Predicate<ServerPlayer> active, double range, Consumer<ServerPlayer> stop,
		@Nullable Shape shape) {
		/** A held stream: it runs from the eyes the way the user looks, as far as the blocks let it. */
		public Beam(ToIntFunction<ServerPlayer> kind, Predicate<ServerPlayer> active, double range, Consumer<ServerPlayer> stop) {
			this(kind, active, range, stop, null);
		}

		boolean fired() {
			return this.shape != null;
		}
	}

	/**
	 * A fired beam's own line while it's {@code active}: where it leaves, which way it runs, how far its head has got
	 * (blocks), how hard it pushes (times the steady push), and the key that pushes it (a key mapping's name, for the
	 * lock message).
	 */
	public interface Shape {
		Vec3 from(ServerPlayer player);

		Vec3 dir(ServerPlayer player);

		double reach(ServerPlayer player);

		float strength(ServerPlayer player);

		String pushKey();
	}

	/** Smoke or Soot: real gas churned into the knot. */
	static boolean smoky(int kind) {
		return kind == KIND_SMOKE || kind == KIND_SOOT;
	}

	/**
	 * On each of the two users, synced to everyone who sees them. {@code balance} is from this user's side (+1: the knot
	 * is on the other user); the pushes are balance a tick.
	 */
	public record State(int opponent, int kind, int theirKind, Vec3 knot, float balance, float push, float theirPush, int age, boolean lead) {
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.of((buf, s) -> {
			buf.writeInt(s.opponent);
			buf.writeByte(s.kind);
			buf.writeByte(s.theirKind);
			buf.writeDouble(s.knot.x);
			buf.writeDouble(s.knot.y);
			buf.writeDouble(s.knot.z);
			buf.writeFloat(s.balance);
			buf.writeFloat(s.push);
			buf.writeFloat(s.theirPush);
			buf.writeShort(s.age);
			buf.writeBoolean(s.lead);
		}, buf -> new State(buf.readInt(), buf.readByte(), buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readShort(), buf.readBoolean()));
	}

	public static final AttachmentType<State> CLASH = AttachmentRegistry.create(JaymosPowers.id("beam_clash"),
		builder -> builder.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Server → the overwhelmed: spin your view (a Wind user's win). */
	public record Spin(float degrees, int ticks) implements CustomPacketPayload {
		public static final Type<Spin> TYPE = new Type<>(JaymosPowers.id("beam_clash_spin"));
		public static final StreamCodec<ByteBuf, Spin> CODEC = StreamCodec.of((buf, s) -> {
			buf.writeFloat(s.degrees);
			buf.writeShort(s.ticks);
		}, buf -> new Spin(buf.readFloat(), Math.min(buf.readShort(), 100)));

		@Override
		public Type<Spin> type() {
			return TYPE;
		}
	}

	private static final Identifier SLOW_ID = JaymosPowers.id("beam_clash_root");
	private static final Identifier JUMP_ID = JaymosPowers.id("beam_clash_jump");
	private static final List<Beam> BEAMS = new ArrayList<>();

	private static final class Side {
		final UUID id;
		final Beam beam;
		float surge;
		long lastTap = Long.MIN_VALUE / 2;
		/** When X was let go (a tap in progress), or -1 while held. */
		long releasedAt = -1;
		float push;
		/** This side's power level (its steady push over {@link #BASE_PUSH}) as of last tick; 0 before the first. */
		float power;
		int kind;

		Side(UUID id, Beam beam) {
			this.id = id;
			this.beam = beam;
		}
	}

	private static final class Clash {
		final Side a, b;
		/** +1: the knot is on b (a is winning). */
		float balance;
		int age;
		Vec3 knot = Vec3.ZERO;

		Clash(Side a, Side b, float balance) {
			this.a = a;
			this.b = b;
			this.balance = balance;
		}

		Side side(UUID id) {
			return this.a.id.equals(id) ? this.a : this.b;
		}

		Side other(Side side) {
			return side == this.a ? this.b : this.a;
		}
	}

	private static final List<Clash> CLASHES = new ArrayList<>();
	private static final Map<UUID, Clash> BY_PLAYER = new HashMap<>();
	/** Player → the game time their streams rest until (after losing or a detonation). */
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private BeamClash() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Spin.TYPE, Spin.CODEC);
		BeamClashTiming.init();
		ServerTickEvents.END_SERVER_TICK.register(BeamClash::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLASHES.clear();
			BY_PLAYER.clear();
			RESTING.clear();
		});
	}

	/** A beam joins the clashes (from its power's init). */
	public static void register(Beam beam) {
		BEAMS.add(beam);
	}

	/** Both sides: whether this player is locked in a clash. */
	public static boolean isClashing(Player player) {
		return player.getAttached(CLASH) != null;
	}

	/** True while a lost clash or a detonation still rests this player's streams (their starts are refused). */
	public static boolean resting(ServerPlayer player) {
		Long until = RESTING.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() >= until) {
			RESTING.remove(player.getUUID());
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.resting"));
		return true;
	}

	/**
	 * From a beam's Hold packet: while clashing, X down and up are taps and let-gos here (the stream keeps running until
	 * a release outlasts {@link #GRACE_TICKS}). Returns true when it was taken.
	 */
	public static boolean input(ServerPlayer player, boolean down) {
		Clash clash = BY_PLAYER.get(player.getUUID());
		if (clash == null) {
			return false;
		}
		Side side = clash.side(player.getUUID());
		long now = player.level().getGameTime();
		if (side.beam.fired()) {
			// a fired beam runs on by itself: every press is a tap
			if (down) {
				tap(player, clash, side, now);
			}
			return true;
		}
		if (!down) {
			side.releasedAt = now;
			return true;
		}
		if (side.releasedAt >= 0) {
			side.releasedAt = -1;
			tap(player, clash, side, now);
		}
		return true;
	}

	private static void tap(ServerPlayer player, Clash clash, Side side, long now) {
		if (now - side.lastTap >= TAP_MIN_TICKS) {
			side.lastTap = now;
			float weight = weight(side, clash.other(side));
			side.surge = Math.min(SURGE_MAX * weight, side.surge + TAP_PUSH * weight);
			// each shove of the knot bangs
			ServerLevel level = player.level();
			level.playSound(null, clash.knot.x, clash.knot.y, clash.knot.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.5F,
				0.7F + level.getRandom().nextFloat() * 0.5F);
		}
	}

	/** (0.17.45, Andy's NOEA picks) How many ticks this player's clash has run, or -1 when they aren't in one (the timing game's clock). */
	public static int age(ServerPlayer player) {
		Clash clash = BY_PLAYER.get(player.getUUID());
		return clash == null ? -1 : clash.age;
	}

	/**
	 * (0.17.45, Andy's NOEA picks) The timing game's shove: adds (or, negative, takes) {@code taps} presses' worth of
	 * surge to this player's side, weighed like a tap by the two powers; a good hit may overshoot the usual surge
	 * ceiling by {@code cap} times. False when they aren't in a clash.
	 */
	public static boolean shove(ServerPlayer player, float taps, float cap) {
		Clash clash = BY_PLAYER.get(player.getUUID());
		if (clash == null) {
			return false;
		}
		Side side = clash.side(player.getUUID());
		float weight = weight(side, clash.other(side));
		if (taps >= 0.0F) {
			side.surge = Math.min(SURGE_MAX * weight * cap, side.surge + TAP_PUSH * weight * taps);
		} else {
			side.surge = Math.max(0.0F, side.surge + TAP_PUSH * weight * taps);
		}
		return true;
	}

	/** A stream's reach, cut short at the knot while it clashes (from the beam's own tick). */
	public static double clamp(Player player, Vec3 from, double reach) {
		State state = player.getAttached(CLASH);
		if (state == null) {
			return reach;
		}
		return Math.max(0.0, Math.min(reach, from.distanceTo(state.knot()) - KNOT_GAP));
	}

	// ---- the tick ----------------------------------------------------------------------------------------------

	private record Stream(ServerPlayer player, Beam beam, Vec3 from, Vec3 look, double reach) {
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Clash> it = CLASHES.iterator(); it.hasNext(); ) {
			Clash clash = it.next();
			if (!step(server, clash)) {
				it.remove();
			}
		}
		if (BEAMS.isEmpty()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<Stream> streams = null;
			for (ServerPlayer player : level.players()) {
				if (BY_PLAYER.containsKey(player.getUUID()) || !player.isAlive() || player.isSpectator()) {
					continue;
				}
				Beam beam = beamOf(player);
				if (beam == null) {
					continue;
				}
				Vec3 look, from;
				double reach;
				if (beam.shape() != null) {
					// a fired beam meets others where its head has got to
					look = beam.shape().dir(player);
					from = beam.shape().from(player);
					reach = beam.shape().reach(player);
				} else {
					look = player.getLookAngle();
					from = player.getEyePosition().add(look.scale(0.6));
					HitResult hit = level.clip(new ClipContext(from, from.add(look.scale(beam.range())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
					reach = hit.getType() == HitResult.Type.MISS ? beam.range() : hit.getLocation().distanceTo(from);
				}
				if (streams == null) {
					streams = new ArrayList<>();
				}
				streams.add(new Stream(player, beam, from, look, reach));
			}
			if (streams == null || streams.size() < 2) {
				continue;
			}
			for (int i = 0; i < streams.size(); i++) {
				Stream s = streams.get(i);
				if (BY_PLAYER.containsKey(s.player.getUUID())) {
					continue;
				}
				for (int j = i + 1; j < streams.size(); j++) {
					Stream t = streams.get(j);
					if (!BY_PLAYER.containsKey(t.player.getUUID()) && meet(s, t)) {
						start(level, s, t);
						break;
					}
				}
			}
		}
	}

	private static @Nullable Beam beamOf(ServerPlayer player) {
		for (Beam beam : BEAMS) {
			if (beam.active().test(player)) {
				return beam;
			}
		}
		return null;
	}

	/** Head-on: facing each other, within both reaches, and the two lines passing close. */
	private static boolean meet(Stream s, Stream t) {
		Vec3 d = t.from.subtract(s.from);
		double apart = d.length();
		if (apart < MIN_APART || apart > s.reach + t.reach + MEET_DISTANCE) {
			return false;
		}
		Vec3 dir = d.scale(1.0 / apart);
		if (s.look.dot(dir) < FACING_COS || t.look.dot(dir) > -FACING_COS) {
			return false;
		}
		return segmentDistance(s.from, s.from.add(s.look.scale(s.reach)), t.from, t.from.add(t.look.scale(t.reach))) <= MEET_DISTANCE;
	}

	/** The closest two segments come (p1-q1, p2-q2). */
	static double segmentDistance(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
		Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
		double s, t;
		if (a <= 1.0E-6 && e <= 1.0E-6) {
			return r.length();
		}
		if (a <= 1.0E-6) {
			s = 0.0;
			t = Math.clamp(f / e, 0.0, 1.0);
		} else {
			double c = d1.dot(r);
			if (e <= 1.0E-6) {
				t = 0.0;
				s = Math.clamp(-c / a, 0.0, 1.0);
			} else {
				double b = d1.dot(d2), denom = a * e - b * b;
				s = denom != 0.0 ? Math.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
				t = (b * s + f) / e;
				if (t < 0.0) {
					t = 0.0;
					s = Math.clamp(-c / a, 0.0, 1.0);
				} else if (t > 1.0) {
					t = 1.0;
					s = Math.clamp((b - c) / a, 0.0, 1.0);
				}
			}
		}
		return p1.add(d1.scale(s)).distanceTo(p2.add(d2.scale(t)));
	}

	private static void start(ServerLevel level, Stream s, Stream t) {
		double apart = s.from.distanceTo(t.from);
		// where the two streams overlap, the knot starts in the middle of it
		double mid = (apart - t.reach + s.reach) * 0.5;
		double share = Math.clamp(mid / apart, START_SHARE_MIN, 1.0 - START_SHARE_MIN);
		Clash clash = new Clash(new Side(s.player.getUUID(), s.beam), new Side(t.player.getUUID(), t.beam), (float)(share * 2.0 - 1.0));
		clash.knot = s.from.add(t.from.subtract(s.from).scale(share));
		CLASHES.add(clash);
		BY_PLAYER.put(clash.a.id, clash);
		BY_PLAYER.put(clash.b.id, clash);
		root(s.player, true);
		root(t.player, true);
		Vec3 k = clash.knot;
		level.playSound(null, k.x, k.y, k.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, k.x, k.y, k.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, k.x, k.y, k.z, 1, 0.0, 0.0, 0.0, 0.0);
		Shakes.send(s.player, LOCK_SHAKE, k);
		Shakes.send(t.player, LOCK_SHAKE, k);
		s.player.sendOverlayMessage(lockedMessage(s.beam));
		t.player.sendOverlayMessage(lockedMessage(t.beam));
	}

	private static Component lockedMessage(Beam beam) {
		return beam.shape() == null ? Component.translatable("message.jaymospowers.beam_clash.locked")
			: Component.translatable("message.jaymospowers.beam_clash.locked_key", Component.keybind(beam.shape().pushKey()));
	}

	/** One tick of a clash; false once it has ended. */
	private static boolean step(MinecraftServer server, Clash clash) {
		ServerPlayer a = server.getPlayerList().getPlayer(clash.a.id);
		ServerPlayer b = server.getPlayerList().getPlayer(clash.b.id);
		boolean aOn = holding(a, clash.a), bOn = holding(b, clash.b);
		if (a == null || b == null || a.level() != b.level()) {
			end(clash, a, b);
			return false;
		}
		ServerLevel level = a.level();
		long now = level.getGameTime();
		// a release that outlasts the grace is letting go (a fired beam isn't held)
		aOn &= clash.a.beam.fired() || clash.a.releasedAt < 0 || now - clash.a.releasedAt <= GRACE_TICKS;
		bOn &= clash.b.beam.fired() || clash.b.releasedAt < 0 || now - clash.b.releasedAt <= GRACE_TICKS;
		Vec3 ea = a.getEyePosition(), eb = b.getEyePosition();
		Vec3 ab = eb.subtract(ea);
		double apart = ab.length();
		if (apart > clash.a.beam.range() + clash.b.beam.range() + BREAK_SLACK || apart < 0.5) {
			// pulled apart: the streams part with nothing
			end(clash, a, b);
			return false;
		}
		Vec3 dir = ab.scale(1.0 / apart);
		// looking away from the knot is letting go
		aOn &= a.getLookAngle().dot(dir) >= AIM_BREAK_COS;
		bOn &= b.getLookAngle().dot(dir.scale(-1.0)) >= AIM_BREAK_COS;
		if (!aOn || !bOn) {
			if (aOn) {
				rollThrough(clash, a, b);
			} else if (bOn) {
				rollThrough(clash, b, a);
			} else {
				stopBeam(clash.a, a);
				stopBeam(clash.b, b);
				end(clash, a, b);
			}
			return false;
		}
		clash.age++;
		clash.a.kind = clash.a.beam.kind().applyAsInt(a);
		clash.b.kind = clash.b.beam.kind().applyAsInt(b);
		clash.a.push = push(a, b, clash, clash.a);
		clash.b.push = push(b, a, clash, clash.b);
		clash.balance = Math.clamp(clash.balance + clash.a.push - clash.b.push, -1.0F, 1.0F);
		clash.knot = ea.add(ab.scale((1.0 + clash.balance) * 0.5)).add(0.0, -0.2, 0.0);
		if (clash.balance >= 1.0F) {
			overwhelm(clash, a, b);
			return false;
		}
		if (clash.balance <= -1.0F) {
			overwhelm(clash, b, a);
			return false;
		}
		if (clash.age >= MAX_TICKS) {
			detonate(clash, a, b);
			return false;
		}
		a.causeFoodExhaustion(EXHAUSTION);
		b.causeFoodExhaustion(EXHAUSTION);
		knot(level, clash, a, b);
		if (clash.age % SHAKE_EVERY == 0) {
			Shakes.send(a, SHAKE + SHAKE_LOSING * Math.max(0.0F, clash.balance), SHAKE_EVERY + 2, clash.knot);
			Shakes.send(b, SHAKE + SHAKE_LOSING * Math.max(0.0F, -clash.balance), SHAKE_EVERY + 2, clash.knot);
		}
		sync(clash, a, b);
		return true;
	}

	private static boolean holding(@Nullable ServerPlayer player, Side side) {
		return player != null && player.isAlive() && !player.isSpectator() && side.beam.active().test(player);
	}

	/** A side's power level: ascension, mastery, and a fired beam's own (live) strength. */
	private static float power(ServerPlayer player, Side side) {
		return (1.0F + (Fate.isAscended(player) ? ASCENDED_BONUS : 0.0F) + MASTERY_BONUS * Mastery.unlocked(player))
			* (side.beam.shape() == null ? 1.0F : Math.max(0.0F, side.beam.shape().strength(player)));
	}

	/** How much a side's taps count against the other's: the square root of their powers' ratio, kept in bounds. */
	static float weight(Side side, Side other) {
		if (side.power <= 0.0F || other.power <= 0.0F) {
			return 1.0F;
		}
		return Math.clamp((float)Math.sqrt(side.power / other.power), TAP_WEIGHT_MIN, TAP_WEIGHT_MAX);
	}

	/** This tick's push: the steady base (by power), the surge from taps, and a waver. Powering up surges at once. */
	private static float push(ServerPlayer player, ServerPlayer other, Clash clash, Side side) {
		float power = power(player, side);
		boolean poweredUp = side.power > 0.0F && power > side.power * (1.0F + POWER_UP_JUMP);
		side.power = power;
		side.surge *= SURGE_DECAY;
		if (poweredUp) {
			// powered up mid-clash: it pours straight into the knot
			side.surge = SURGE_MAX * weight(side, clash.other(side));
			Vec3 k = clash.knot;
			player.level().playSound(null, k.x, k.y, k.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			Shakes.send(player, POWER_UP_SHAKE, k);
			Shakes.send(other, POWER_UP_SHAKE, k);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.power_up"));
			other.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.they_power_up"));
		}
		return BASE_PUSH * power + side.surge + (player.getRandom().nextFloat() - 0.5F) * JITTER;
	}

	/** What happens in and around the knot: smoke churned in, soot blinding, bystanders thrown out. */
	private static void knot(ServerLevel level, Clash clash, ServerPlayer a, ServerPlayer b) {
		Vec3 k = clash.knot;
		for (Side side : new Side[] {clash.a, clash.b}) {
			ServerPlayer owner = side == clash.a ? a : b;
			ServerPlayer other = side == clash.a ? b : a;
			if (smoky(side.kind)) {
				com.jaymospowers.power.smoke.SmokeField.puff(level, k, KNOT_SMOKE, owner);
			}
			if (side.kind == KIND_SOOT && clash.age % 10 == 0) {
				if (other.getEyePosition().distanceTo(k) <= SOOT_REACH) {
					other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SOOT_BLIND_TICKS, 0), owner);
				}
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k, k).inflate(SOOT_RADIUS),
					e -> e != a && e != b && e.isAlive() && e.position().distanceTo(k) <= SOOT_RADIUS + 1.0)) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SOOT_BLIND_TICKS, 0), owner);
				}
			}
		}
		if (clash.age % KNOT_HURT_EVERY == 0) {
			for (Entity e : level.getEntities((Entity)null, new AABB(k, k).inflate(KNOT_RADIUS),
				e -> e != a && e != b && e.isAlive() && !e.isSpectator() && !e.noPhysics)) {
				Vec3 out = e.getBoundingBox().getCenter().subtract(k);
				out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
				e.setDeltaMovement(e.getDeltaMovement().add(out.scale(KNOT_THROW)).add(0.0, KNOT_THROW * 0.5, 0.0));
				e.syncVelocity = true;
				if (e instanceof LivingEntity living) {
					living.hurtServer(level, a.damageSources().playerAttack(a), KNOT_DAMAGE);
				}
			}
		}
		if (clash.age % 20 == 0) {
			level.playSound(null, k.x, k.y, k.z, SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.7F, 0.5F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	private static void sync(Clash clash, ServerPlayer a, ServerPlayer b) {
		a.setAttached(CLASH, new State(b.getId(), clash.a.kind, clash.b.kind, clash.knot, clash.balance, clash.a.push, clash.b.push, clash.age, true));
		b.setAttached(CLASH, new State(a.getId(), clash.b.kind, clash.a.kind, clash.knot, -clash.balance, clash.b.push, clash.a.push, clash.age, false));
	}

	// ---- endings -----------------------------------------------------------------------------------------------

	/** The knot reached {@code loser}: they're blasted back, and how depends on what beat them. */
	private static void overwhelm(Clash clash, ServerPlayer winner, ServerPlayer loser) {
		ServerLevel level = winner.level();
		Side win = clash.side(winner.getUUID());
		Side lose = clash.side(loser.getUUID());
		Vec3 at = loser.position().add(0.0, 1.0, 0.0);
		throwBack(loser, winner.position(), OVERWHELM_THROW, OVERWHELM_LIFT);
		loser.hurtServer(level, winner.damageSources().playerAttack(winner), OVERWHELM_DAMAGE);
		if (win.kind == KIND_WIND) {
			// thrown and spun
			ServerPlayNetworking.send(loser, new Spin(SPIN_DEGREES * (level.getRandom().nextBoolean() ? 1.0F : -1.0F), SPIN_TICKS));
			level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.6, 0.8, 0.6, 0.15);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4F, 0.8F);
			screen(loser, 0xE8F4FF);
		} else if (win.kind == KIND_KI) {
			// the ki rolls over them and bursts: burned and set alight
			loser.hurtServer(level, winner.damageSources().playerAttack(winner), KI_OVERWHELM_DAMAGE);
			loser.igniteForTicks(KI_BURN_TICKS);
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.6, 0.6, 0.6, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.6, 0.8, 0.6, 0.5);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 25, 0.5, 0.7, 0.5, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 1.2F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.5F);
			screen(loser, 0xFFF2C0);
		} else {
			// engulfed: smoke all round, blinded, out of breath
			boolean soot = win.kind == KIND_SOOT;
			com.jaymospowers.power.smoke.SmokeField.puff(level, at, ENGULF_SMOKE, winner);
			loser.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, soot ? SOOT_ENGULF_BLIND_TICKS : ENGULF_BLIND_TICKS, 0), winner);
			loser.setAirSupply(0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 40, 0.7, 0.9, 0.7, 0.05);
			if (soot) {
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 20, 0.5, 0.7, 0.5, 0.05);
			}
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
			screen(loser, soot ? 0x0A0A0A : 0x505050);
		}
		Shakes.send(loser, OVERWHELM_SHAKE, clash.knot);
		Shakes.send(winner, WINNER_SHAKE, clash.knot);
		stopBeam(lose, loser);
		if (win.releasedAt >= 0) {
			stopBeam(win, winner);
		}
		rest(loser, LOSER_COOLDOWN);
		loser.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.overwhelmed"));
		winner.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.won"));
		end(clash, winner, loser);
	}

	/** {@code quitter} let go: the other's stream rolls straight through onto them. */
	private static void rollThrough(Clash clash, ServerPlayer holder, ServerPlayer quitter) {
		ServerLevel level = holder.level();
		Side hold = clash.side(holder.getUUID());
		stopBeam(clash.side(quitter.getUUID()), quitter);
		throwBack(quitter, holder.position(), ROLL_THROW, ROLL_THROW * 0.3);
		quitter.hurtServer(level, holder.damageSources().playerAttack(holder), ROLL_DAMAGE);
		// the stream rushing on, all the way
		Vec3 from = clash.knot, to = quitter.getEyePosition();
		Vec3 dir = to.subtract(from);
		int n = Math.max(1, (int)(dir.length() / 0.6));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(dir.scale(i / (double)n));
			level.sendParticles(hold.kind == KIND_WIND ? ParticleTypes.CLOUD : hold.kind == KIND_KI ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.LARGE_SMOKE,
				p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.02);
		}
		level.playSound(null, to.x, to.y, to.z, hold.kind == KIND_WIND ? SoundEvents.BREEZE_SHOOT : hold.kind == KIND_KI ? SoundEvents.BEACON_DEACTIVATE
			: SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
		Shakes.send(quitter, ROLL_SHAKE, from);
		end(clash, holder, quitter);
	}

	/** A stalemate: the knot bursts, throwing both back, and both streams rest a while. */
	private static void detonate(Clash clash, ServerPlayer a, ServerPlayer b) {
		ServerLevel level = a.level();
		Vec3 k = clash.knot;
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, k.x, k.y, k.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, k.x, k.y, k.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, k.x, k.y, k.z, 80, 1.2, 1.2, 1.2, 0.35);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, k.x, k.y, k.z, 60, 1.0, 1.0, 1.0, 0.25);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, k.x, k.y, k.z, 40, 0.6, 0.6, 0.6, 0.6);
		level.playSound(null, k.x, k.y, k.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, k.x, k.y, k.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
		if (smoky(clash.a.kind) || smoky(clash.b.kind)) {
			com.jaymospowers.power.smoke.SmokeField.puff(level, k, DETONATE_SMOKE, smoky(clash.a.kind) ? a : b);
		}
		for (Entity e : level.getEntities((Entity)null, new AABB(k, k).inflate(DETONATE_RADIUS),
			e -> e != a && e != b && e.isAlive() && !e.isSpectator() && !e.noPhysics)) {
			throwBack(e, k, DETONATE_THROW * (1.0 - e.position().distanceTo(k) / (DETONATE_RADIUS * 1.8)), DETONATE_LIFT);
		}
		for (ServerPlayer player : new ServerPlayer[] {a, b}) {
			throwBack(player, k, DETONATE_THROW, DETONATE_LIFT);
			player.hurtServer(level, level.damageSources().explosion(null, null), DETONATE_DAMAGE);
			stopBeam(clash.side(player.getUUID()), player);
			rest(player, DETONATE_COOLDOWN);
			screen(player, 0xFFFFFF);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.beam_clash.detonated"));
		}
		Shakes.around(level, k, DETONATE_SHAKE_RANGE, DETONATE_SHAKE);
		end(clash, a, b);
	}

	/** Thrown away from {@code from}, a little up. */
	private static void throwBack(Entity e, Vec3 from, double speed, double lift) {
		if (speed <= 0.0) {
			return;
		}
		Vec3 away = e.position().subtract(from);
		away = new Vec3(away.x, 0.0, away.z);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		double resist = e instanceof LivingEntity living ? 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 1.0;
		e.setDeltaMovement(away.scale(speed * resist).add(0.0, lift, 0.0));
		e.syncVelocity = true;
	}

	private static void screen(ServerPlayer player, int color) {
		ServerPlayNetworking.send(player, new com.jaymospowers.power.wind.WindNet.ScreenFx(com.jaymospowers.power.wind.WindNet.ScreenFx.VIGNETTE, color, FX_TICKS));
	}

	private static void stopBeam(Side side, @Nullable ServerPlayer player) {
		if (player != null) {
			side.beam.stop().accept(player);
		}
	}

	private static void rest(ServerPlayer player, int ticks) {
		RESTING.merge(player.getUUID(), player.level().getGameTime() + ticks, Math::max);
	}

	/** Forgets the clash and frees both users; any stream still let go (mid-tap) stops. */
	private static void end(Clash clash, @Nullable ServerPlayer a, @Nullable ServerPlayer b) {
		BY_PLAYER.remove(clash.a.id);
		BY_PLAYER.remove(clash.b.id);
		for (ServerPlayer player : new ServerPlayer[] {a, b}) {
			if (player == null) {
				continue;
			}
			Side side = clash.side(player.getUUID());
			if (side.releasedAt >= 0) {
				stopBeam(side, player);
			}
			player.removeAttached(CLASH);
			root(player, false);
		}
	}

	private static void root(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
			if (on) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, -ROOT_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (jump != null) {
			jump.removeModifier(JUMP_ID);
			if (on) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID, -ROOT_JUMP, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}
}
