package com.jaymospowers.power.web;

import com.google.gson.stream.JsonReader;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.150) The symbiote's mind: Trust, Mood, named bond stages, standing orders and requests. Taken from the
 * "Symbiote: A Bonding Experience" mod's ideas (never its code), as Andy ticked them off a checklist.
 * <ul>
 * <li><b>Trust</b> (0-{@link #TRUST_MAX}, saved) is kept apart from {@code bond} (which still opens the abilities). It rises
 * with meals, requests granted and fighting beside you; it falls when a request is ignored, when you hurt it (the bond
 * drops by two or more at once: fire, noise, wrenching free) and while it starves. Trust decides whether it obeys a
 * standing order (a roll when you give one, {@link #OBEY_FULL} is a sure thing), whether it ignores a radial pick
 * ({@link #ignores}), and how often it takes the wheel ({@link #takeoverAllowed}: the more it trusts you, the more of
 * its idle takeovers it lets go by).</li>
 * <li><b>Mood</b> is read off its stress, hunger, losses and neglect: {@code CONTENT}, {@code ANXIOUS}, {@code COILED}, or
 * {@code GRIEVING} (you went down, you came within a heart of dying, a pet or a friend died near you). It changes its voice
 * ({@link #voice}: a mood-specific variant of a line where one exists, else the old line; grieving, it all but falls
 * silent: {@link #quiet}) and the colour of your screen's edge (client {@code WebSymbioteMindUi}).</li>
 * <li><b>Stages</b> are names for the bond: Attached, Integrated, Cooperative, Dominant ({@link #STAGE_AT}, at the
 * unlocks of Wall Crawl, Living Armor and the whole bond), said once when you reach one.</li>
 * <li><b>Orders</b> (the X radial's Status page): {@code PROTECT} guards you early ({@link #protectHealth}, and a shell of
 * absorption when something is on you), {@code HUNT} lets it hunt on a lighter appetite ({@link #huntBelow}; still kill,
 * then feed), {@code HIDE} holds it back (suit and tendrils drawn in, far fewer takeovers, no gifts, no hunts but
 * starving, no requests).</li>
 * <li><b>Requests</b>: now and then it asks for meat, a kill, time deep underground, a night hunt under open sky, or to
 * be worn. Granted in time: trust and bond up, stress down. Ignored: trust down, stress up (and rising from halfway), then
 * it does it itself: it hunts, tunnels down ({@link WebSymbioteDig}) or wears the suit.</li>
 * </ul>
 * Tuning numbers are the constants at the top.
 */
public final class WebSymbioteMind {
	// ===== trust =====================================================================================================
	public static final int TRUST_MAX = 100;
	/** Trust gained for a meal it was given, for fighting beside you (once per {@code FIGHT_GAP}), and for a request granted. */
	static final int TRUST_FEED = 2, TRUST_FIGHT = 1, TRUST_REQUEST = 5, FIGHT_GAP = 400;
	/** Trust lost for each point of bond torn off in one go (fire, noise, wrenching free), and for an ignored request. */
	static final int TRUST_HURT = 2, TRUST_IGNORED = 6;
	/** A well-fed, calm symbiote warms a point this often; a starving one cools a point this often. */
	static final int TRUST_DRIFT_TICKS = 2400, TRUST_STARVE_TICKS = 1200;
	/** Satiety up this much between two samples is a meal. */
	static final int MEAL_JUMP = 4;
	/** At or above this trust it always obeys an order; below, trust/this of the time. Under {@code ORDER_BREAK} it drops one it had. */
	static final int OBEY_FULL = 70, ORDER_BREAK = 15;
	/** Under {@code LISTEN_TRUST} a radial pick is ignored up to {@code TRUST_IGNORE_MAX} of the time (at no trust). */
	static final int LISTEN_TRUST = 50;
	static final float TRUST_IGNORE_MAX = 0.35F;
	/** Idle takeovers it lets go by: none up to {@code SKIP_FROM} trust, rising to {@code SKIP_MAX} at full; hiding or grieving, at least this. */
	static final int SKIP_FROM = 30;
	static final float SKIP_MAX = 0.6F, HIDE_SKIP = 0.85F, GRIEF_SKIP = 0.7F;

	// ===== orders ====================================================================================================
	public static final int ORDER_NONE = 0, PROTECT = 1, HUNT = 2, HIDE = 3;
	static final String[] ORDER_NAMES = {"none", "protect", "hunt", "hide"};
	/** Protect: it fights for you at or under this share of your health (instead of {@link WebSymbiote#PROTECT_HEALTH}), and shields you. */
	static final float PROTECT_HEALTH = 0.7F, PROTECT_SHIELD = 4.0F;
	static final int PROTECT_SHIELD_GAP = 400;
	static final double PROTECT_RANGE = 10.0;
	/** Hunt: it hunts below this satiety (instead of {@link WebSymbiote#HUNGRY_HUNT_BELOW}), this often at each feeding check. */
	static final int HUNT_BELOW = 75;
	static final float HUNT_CHANCE = 0.25F;

	// ===== mood ======================================================================================================
	/** The moods, in saved order. */
	public enum Mood {
		CONTENT("content"), ANXIOUS("anxious"), COILED("coiled"), GRIEVING("grieving");

		private final String key;

		Mood(String key) {
			this.key = key;
		}

		/** The lang-key / variant name. */
		public String key() {
			return this.key;
		}

		public static Mood of(int id) {
			Mood[] all = values();
			return id >= 0 && id < all.length ? all[id] : CONTENT;
		}
	}

	/** Stress that coils it, and that makes it anxious. */
	public static final int COILED_STRESS = 55, ANXIOUS_STRESS = 25;
	/** Grief lasts this long after: you went down, you came within a heart of dying, a pet or a friend died. */
	static final int GRIEF_DOWNED = 3600, GRIEF_NEAR_DEATH = 2400, GRIEF_LOSS = 4800;
	/** "Within a heart of dying": at or under this share of your health. A friend is a player this near when they die. */
	static final float NEAR_DEATH_HEALTH = 0.15F;
	static final double FRIEND_RANGE = 48.0;
	/** Unfed this long (ticks since a meal) it is anxious from neglect. */
	static final int NEGLECT_MEAL_TICKS = 18000;
	/** A new mood has to hold for this many mood checks (20 ticks each) before it takes; it announces itself at most this often. */
	static final int MOOD_SETTLE = 3, MOOD_SAY_GAP = 2400;
	/** Grieving, this share of its commentary (narration and remarks) is swallowed. */
	static final float GRIEF_QUIET = 0.92F;
	/** Mood-specific variants of each line: how many there are. */
	static final int VARIANTS = 2;

	// ===== stages ====================================================================================================
	/** The bond each stage starts at (from the abilities' unlocks: Wall Crawl 30, Living Armor 60, the big body 100). */
	public static final int[] STAGE_AT = {0, 30, 60, WebSymbiote.BOND_MAX};
	static final String[] STAGE_NAMES = {"attached", "integrated", "cooperative", "dominant"};

	// ===== requests ==================================================================================================
	public static final int REQ_NONE = 0, MEAT = 1, KILL = 2, DEEP = 3, NIGHT = 4, WORN = 5;
	static final String[] REQ_NAMES = {"none", "meat", "kill", "deep", "night", "worn"};
	/** How long you get for each, in ticks. */
	static final int[] REQ_TICKS = {0, 6000, 6000, 9600, 4800, 4800};
	/** It starts asking at this bond; the first ask comes this long after it bonds or the world loads; between asks, this long. */
	static final int REQUEST_BOND = 15, FIRST_GAP = 6000, GAP_MIN = 9600, GAP_MAX = 16800;
	/** Time deep underground it wants, and time in the suit (ticks); "deep" is this far under sea level with no sky above. */
	static final int DEEP_NEED = 1200, WORN_NEED = 600, DEEP_BELOW = 24;
	/** It nags at this share of the time gone, and again, louder, at this share. */
	static final float NAG_AT = 0.5F, LAST_AT = 0.85F;
	/** Past halfway, a point of stress this often, up to {@code REQUEST_STRESS_CAP}. */
	static final int NAG_STRESS_TICKS = 100, REQUEST_STRESS_CAP = 70;
	/** A request granted: the bond rises, its stress falls; ignored: stress rises. */
	static final int GRANTED_BOND = 2, GRANTED_RELIEF = 10, IGNORED_STRESS = 15;
	/** After an ignored request it keeps trying to do it itself for this long. */
	static final int FORCE_TICKS = 2400, FORCE_RETRY = 100, FORCE_AGAIN = 600;
	/** Satiety above this and it doesn't ask for meat. */
	static final int MEAT_SATIETY = 70;
	static final int ASK_LINES = 2, LINES = 2;

	/** Sampling: everything but the event hooks looks every this many ticks. */
	static final int SAMPLE = 5, MOOD_EVERY = 20;

	/**
	 * What it has in mind: saved with the player (not through death: a new body is a new bond) and synced to the owner for
	 * the Status page and the screen-edge tint. {@code reqEnd} is the game time the request runs out; {@code reqProg} the
	 * ticks of it done (deep or worn); {@code nextReq} when it may ask again; {@code griefUntil} when its grief lifts.
	 */
	public record Mind(int trust, int order, int mood, int req, long reqEnd, int reqProg, long nextReq, long griefUntil) {
		public static final Codec<Mind> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("trust", 0).forGetter(Mind::trust),
				Codec.INT.optionalFieldOf("order", 0).forGetter(Mind::order),
				Codec.INT.optionalFieldOf("mood", 0).forGetter(Mind::mood),
				Codec.INT.optionalFieldOf("req", 0).forGetter(Mind::req),
				Codec.LONG.optionalFieldOf("req_end", 0L).forGetter(Mind::reqEnd),
				Codec.INT.optionalFieldOf("req_prog", 0).forGetter(Mind::reqProg),
				Codec.LONG.optionalFieldOf("next_req", 0L).forGetter(Mind::nextReq),
				Codec.LONG.optionalFieldOf("grief_until", 0L).forGetter(Mind::griefUntil))
			.apply(i, Mind::new));

		public static final StreamCodec<ByteBuf, Mind> STREAM_CODEC = StreamCodec.of(
			(buf, mind) -> {
				ByteBufCodecs.VAR_INT.encode(buf, mind.trust);
				ByteBufCodecs.VAR_INT.encode(buf, mind.order);
				ByteBufCodecs.VAR_INT.encode(buf, mind.mood);
				ByteBufCodecs.VAR_INT.encode(buf, mind.req);
				ByteBufCodecs.VAR_LONG.encode(buf, mind.reqEnd);
				ByteBufCodecs.VAR_INT.encode(buf, mind.reqProg);
				ByteBufCodecs.VAR_LONG.encode(buf, mind.nextReq);
				ByteBufCodecs.VAR_LONG.encode(buf, mind.griefUntil);
			},
			buf -> new Mind(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

		public Mood moodNow() {
			return Mood.of(mood);
		}

		Mind withTrust(int value) {
			return new Mind(Mth.clamp(value, 0, TRUST_MAX), order, mood, req, reqEnd, reqProg, nextReq, griefUntil);
		}

		Mind withOrder(int value) {
			return new Mind(trust, value, mood, req, reqEnd, reqProg, nextReq, griefUntil);
		}

		Mind withMood(int value) {
			return new Mind(trust, order, value, req, reqEnd, reqProg, nextReq, griefUntil);
		}

		Mind withRequest(int kind, long end, int progress) {
			return new Mind(trust, order, mood, kind, end, progress, nextReq, griefUntil);
		}

		Mind withProgress(int progress) {
			return new Mind(trust, order, mood, req, reqEnd, progress, nextReq, griefUntil);
		}

		Mind withNext(long value) {
			return new Mind(trust, order, mood, req, reqEnd, reqProg, value, griefUntil);
		}

		Mind withGrief(long until) {
			return new Mind(trust, order, mood, req, reqEnd, reqProg, nextReq, until);
		}
	}

	public static final AttachmentType<Mind> MIND = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_mind"),
		builder -> builder.persistent(Mind.CODEC).syncWith(Mind.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Client → server: a standing order from the Status page ({@code ORDER_NONE} is "at ease"). */
	public record Order(int order) implements CustomPacketPayload {
		public static final Type<Order> TYPE = new Type<>(JaymosPowers.id("web_symbiote_order"));
		public static final StreamCodec<ByteBuf, Order> CODEC = ByteBufCodecs.VAR_INT.map(Order::new, Order::order);

		@Override
		public Type<Order> type() {
			return TYPE;
		}
	}

	/** Server-only bookkeeping, one per bonded player. */
	private static final class Run {
		int satiety;
		int bond;
		long lastMealAt;
		long lastFightAt;
		long lastDriftAt;
		long lastStarveAt;
		boolean wasDowned;
		boolean wasNearDeath;
		int stage = -1;
		Mood pending = Mood.CONTENT;
		int pendingFor;
		long moodSaidAt;
		long shieldAt;
		/** Ignored request it is doing itself: which, until when, when it may try again. */
		int forceKind;
		long forceUntil;
		long forceRetryAt;
		/** Time done toward a deep or worn request that hasn't been saved yet (ticks). */
		int progress;
		boolean wearItself;
		boolean nagged, warned;
		long stressAt;

		Run(WebSymbioteState state, long now) {
			this.satiety = state.satiety();
			this.bond = state.bond();
			this.lastMealAt = now;
			this.lastDriftAt = now;
			this.lastStarveAt = now;
			this.lastFightAt = now;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static volatile @Nullable Set<String> variants;

	private WebSymbioteMind() {
	}

	// ===== registration ==============================================================================================

	static void register() {
		// (payload types before receivers)
		PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (payload, ctx) -> order(ctx.player(), payload.order()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RUNS.remove(handler.player.getUUID());
			WebSymbioteDig.forget(handler.player);
		});
		// a new body is nobody's: no trust, no orders, no request
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.removeAttached(MIND);
				RUNS.remove(newPlayer.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				onDeath(level, entity, source);
			}
		});
	}

	private static void onDeath(ServerLevel level, LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		// kills beside you (or by it, on the wheel): trust, and a request ticked off
		if (source.getEntity() instanceof ServerPlayer killer && killer != entity && WebSymbiote.isBonded(killer) && killer.isAlive()) {
			killed(killer, entity);
		}
		// a loss: its host's pet
		if (entity instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer owner && owner != entity && WebSymbiote.isBonded(owner)) {
			grieve(owner, GRIEF_LOSS, "pet");
		}
		// or friend: another player dying near it
		if (entity instanceof ServerPlayer dead) {
			for (ServerPlayer other : level.players()) {
				if (other != dead && WebSymbiote.isBonded(other) && other.isAlive() && other.distanceTo(dead) <= FRIEND_RANGE) {
					grieve(other, GRIEF_LOSS, "friend");
				}
			}
		}
	}

	// ===== reading ===================================================================================================

	/** Its mind (a new bond's trust starts level with its bond). */
	public static Mind mind(Player player) {
		Mind mind = player.getAttached(MIND);
		return mind != null ? mind : new Mind(Mth.clamp(WebSymbiote.state(player).bond(), 0, TRUST_MAX), ORDER_NONE, 0, REQ_NONE, 0L, 0, 0L, 0L);
	}

	public static Mood mood(Player player) {
		return mind(player).moodNow();
	}

	/** The stage's index for a bond (0-3). */
	public static int stage(int bond) {
		int stage = 0;
		for (int i = 0; i < STAGE_AT.length; i++) {
			if (bond >= STAGE_AT[i]) {
				stage = i;
			}
		}
		return stage;
	}

	/** The stage's lang name ({@code attached}, {@code integrated}, {@code cooperative}, {@code dominant}). */
	public static String stageName(int bond) {
		return STAGE_NAMES[stage(bond)];
	}

	public static String orderName(int order) {
		return ORDER_NAMES[Mth.clamp(order, 0, ORDER_NAMES.length - 1)];
	}

	public static String requestName(int kind) {
		return REQ_NAMES[Mth.clamp(kind, 0, REQ_NAMES.length - 1)];
	}

	private static void write(ServerPlayer player, Mind mind) {
		if (!mind.equals(player.getAttached(MIND))) {
			player.setAttached(MIND, mind);
		}
	}

	// ===== hooks for the symbiote's own logic =========================================================================

	/** Hide is on and it still listens to you. */
	public static boolean hiding(Player player) {
		Mind mind = player.getAttached(MIND);
		return mind != null && mind.order == HIDE && mind.trust >= ORDER_BREAK;
	}

	private static boolean ordered(Player player, int order) {
		Mind mind = player.getAttached(MIND);
		return mind != null && mind.order == order && mind.trust >= ORDER_BREAK;
	}

	/** The health share under which it fights for you: {@code base}, or more on the Protect order. */
	static float protectHealth(ServerPlayer player, float base) {
		return ordered(player, PROTECT) ? Math.max(base, PROTECT_HEALTH) : base;
	}

	/** The satiety under which it hunts: {@code base}, or more on the Hunt order. */
	static int huntBelow(ServerPlayer player, int base) {
		return ordered(player, HUNT) ? Math.max(base, HUNT_BELOW) : base;
	}

	/** How likely each feeding check is to send it hunting: {@code base}; more on Hunt; none hiding. */
	static float huntChance(ServerPlayer player, float base) {
		if (hiding(player)) {
			return 0.0F;
		}
		return ordered(player, HUNT) ? Math.max(base, HUNT_CHANCE) : base;
	}

	/** It is doing an ignored request of this kind itself, right now: a hunt for meat. */
	static boolean forcing(ServerPlayer player, int kind, long now) {
		Run run = RUNS.get(player.getUUID());
		return run != null && run.forceKind == kind && now < run.forceUntil;
	}

	/** An ignored request to be worn: once, it puts the suit on itself. */
	static boolean wearsItself(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run != null && run.wearItself) {
			run.wearItself = false;
			WebSymbiote.whisper(player, "req.worn.itself");
			return true;
		}
		return false;
	}

	/** A radial pick: ignored at a low trust, on top of a low bond's ignoring ({@code base} is the chance the bond gives). */
	static boolean ignores(ServerPlayer player, float base) {
		Mind mind = mind(player);
		float chance = base;
		if (mind.trust < LISTEN_TRUST) {
			chance = Math.max(chance, TRUST_IGNORE_MAX * (1.0F - mind.trust / (float)LISTEN_TRUST));
		}
		return chance > 0.0F && player.getRandom().nextFloat() < chance;
	}

	/** An idle takeover (neglect, curiosity, a tour) may go ahead: trust lets some go by; hiding or grieving, most. */
	static boolean takeoverAllowed(ServerPlayer player) {
		Mind mind = mind(player);
		float skip = mind.trust <= SKIP_FROM ? 0.0F : SKIP_MAX * (mind.trust - SKIP_FROM) / (float)(TRUST_MAX - SKIP_FROM);
		if (hiding(player)) {
			skip = Math.max(skip, HIDE_SKIP);
		}
		if (mind.moodNow() == Mood.GRIEVING) {
			skip = Math.max(skip, GRIEF_SKIP);
		}
		return skip <= 0.0F || player.getRandom().nextFloat() >= skip;
	}

	/** Grieving, most of its commentary is swallowed. */
	static boolean quiet(ServerPlayer player) {
		return mind(player).moodNow() == Mood.GRIEVING && player.getRandom().nextFloat() < GRIEF_QUIET;
	}

	/**
	 * The lang key to speak for {@code key}: a line of its running commentary ({@code say.<act>.N}, {@code see.<topic>.N},
	 * the black one's) is swapped for a variant in its mood ({@code mood.say.<act>.<mood>.N}) where one exists; anything
	 * else, or no variant, is {@code key} unchanged.
	 */
	static String voice(ServerPlayer player, String key) {
		boolean say = key.startsWith("say.");
		if (!say && !key.startsWith("see.")) {
			return key;
		}
		Mind mind = player.getAttached(MIND);
		if (mind == null) {
			return key;
		}
		String rest = key.substring(4);
		if (rest.startsWith("white.") || rest.startsWith("red.")) {
			return key;
		}
		int dot = rest.lastIndexOf('.');
		if (dot <= 0) {
			return key;
		}
		String base = "mood." + (say ? "say." : "see.") + rest.substring(0, dot) + "." + mind.moodNow().key() + ".";
		Set<String> known = variantKeys();
		int first = 1 + player.getRandom().nextInt(VARIANTS);
		for (int k = 0; k < VARIANTS; k++) {
			String candidate = base + (1 + (first - 1 + k) % VARIANTS);
			if (known.contains(candidate)) {
				return candidate;
			}
		}
		return key;
	}

	/** The mood variants the lang file has (read once, from the built {@code en_us.json}). */
	private static Set<String> variantKeys() {
		Set<String> known = variants;
		if (known != null) {
			return known;
		}
		known = new HashSet<>();
		String prefix = "message.jaymospowers.web_symbiote.";
		try (InputStream in = WebSymbioteMind.class.getResourceAsStream("/assets/jaymospowers/lang/en_us.json")) {
			if (in != null) {
				JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				reader.beginObject();
				while (reader.hasNext()) {
					String name = reader.nextName();
					reader.skipValue();
					if (name.startsWith(prefix + "mood.")) {
						known.add(name.substring(prefix.length()));
					}
				}
			}
		} catch (Exception e) {
			JaymosPowers.LOGGER.warn("Couldn't read the symbiote's mood lines", e);
		}
		variants = known;
		return known;
	}

	// ===== events ====================================================================================================

	private static void killed(ServerPlayer player, LivingEntity victim) {
		long now = player.level().getGameTime();
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		boolean enemy = victim instanceof Enemy || victim instanceof Mob mob && mob.getTarget() == player;
		boolean driving = WebSymbiote.driving(player);
		if (enemy && now - run.lastFightAt >= FIGHT_GAP) {
			// fighting together
			run.lastFightAt = now;
			addTrust(player, TRUST_FIGHT);
		}
		Mind mind = mind(player);
		if (mind.req == KILL && enemy || mind.req == NIGHT && enemy && nightUnderSky(player)) {
			complete(player, !driving);
		} else if (run.forceKind != REQ_NONE && now < run.forceUntil && driving && enemy
			&& (run.forceKind == KILL || run.forceKind == NIGHT)) {
			// the kill it asked for, done itself
			run.forceKind = REQ_NONE;
			WebSymbiote.whisper(player, "req.itself_done." + (1 + player.getRandom().nextInt(LINES)));
			relieve(player, GRANTED_RELIEF / 2);
		}
	}

	private static boolean nightUnderSky(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isDarkOutside()
			&& level.canSeeSky(net.minecraft.core.BlockPos.containing(player.getEyePosition()));
	}

	private static void grieve(ServerPlayer player, int ticks, String why) {
		long now = player.level().getGameTime();
		Mind mind = mind(player);
		boolean fresh = !(now < mind.griefUntil);
		write(player, mind.withGrief(Math.max(mind.griefUntil, now + ticks)).withMood(Mood.GRIEVING.ordinal()));
		if (fresh) {
			WebSymbiote.whisper(player, "mood.grieving." + why);
		}
	}

	private static void addTrust(ServerPlayer player, int delta) {
		Mind mind = mind(player);
		write(player, mind.withTrust(mind.trust + delta));
	}

	private static void relieve(ServerPlayer player, int amount) {
		addStress(player, -amount);
	}

	/** Stress up or down, on the state as it stands now (the tick's own copy may be a step behind). */
	private static void addStress(ServerPlayer player, int delta) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.bonded()) {
			int stress = Mth.clamp(state.stress() + delta, 0, WebSymbiote.STRESS_MAX);
			if (stress != state.stress()) {
				WebSymbiote.set(player, state.withStress(stress));
			}
		}
	}

	// ===== orders ====================================================================================================

	private static void order(ServerPlayer player, int order) {
		if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || order < 0 || order > HIDE) {
			return;
		}
		if (WebSymbioteBond.dormant(player)) {
			WebSymbioteBond.refuse(player);
			return;
		}
		Mind mind = mind(player);
		if (order == ORDER_NONE) {
			if (mind.order != ORDER_NONE) {
				write(player, mind.withOrder(ORDER_NONE));
				WebSymbiote.whisper(player, "order.none");
			}
			return;
		}
		if (order == mind.order) {
			return;
		}
		boolean obeys = player.isCreative() || !WebSymbiote.sulking(player) && player.getRandom().nextFloat() < mind.trust / (float)OBEY_FULL;
		if (!obeys) {
			// it doesn't trust you enough (or is too sore): it says so, and it frets
			addStress(player, 2);
			WebSymbiote.whisper(player, "order." + (WebSymbiote.sulking(player) ? "sulk" : "refuse") + "." + (1 + player.getRandom().nextInt(LINES)));
			return;
		}
		write(player, mind.withOrder(order));
		WebSymbiote.whisper(player, "order." + ORDER_NAMES[order] + ".yes");
		if (order == HIDE) {
			WebSymbiote.mindHide(player);
		}
	}

	// ===== the tick ==================================================================================================

	/** From the symbiote's tick (bonded, alive): see the class comment. */
	static void tick(ServerPlayer player, WebSymbioteState state, long now) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			run = new Run(state, now);
			RUNS.put(player.getUUID(), run);
		}
		if (player.tickCount % SAMPLE != 0) {
			return;
		}
		if (WebSymbioteDig.active(player) && !WebSymbiote.driving(player)) {
			WebSymbioteDig.forget(player);
		}
		Mind mind = player.getAttached(MIND);
		boolean fresh = mind == null;
		if (fresh) {
			mind = new Mind(Mth.clamp(state.bond(), 0, TRUST_MAX), ORDER_NONE, 0, REQ_NONE, 0L, 0, 0L, 0L);
		}
		Mind before = mind;
		boolean creative = player.isCreative();

		// meals: the satiety jumped. Given by you: trust. Its own hunt (on the wheel): none, but it ends a forced hunt
		int satiety = state.satiety();
		if (satiety - run.satiety >= MEAL_JUMP) {
			run.lastMealAt = now;
			if (!WebSymbiote.driving(player)) {
				mind = mind.withTrust(mind.trust + TRUST_FEED);
				if (mind.req == MEAT) {
					write(player, mind);
					complete(player, true);
					mind = mind(player);
				}
			}
			if (run.forceKind == MEAT) {
				run.forceKind = REQ_NONE;
				WebSymbiote.whisper(player, "req.itself_done." + (1 + player.getRandom().nextInt(LINES)));
			}
		}
		run.satiety = satiety;
		// hurt by you: the bond dropped by two or more at once
		int bond = state.bond();
		if (run.bond - bond >= 2) {
			mind = mind.withTrust(mind.trust - TRUST_HURT * (run.bond - bond));
		}
		run.bond = bond;
		// the slow drift: warm while fed and calm, cooling while it starves
		if (state.satiety() <= WebSymbiote.STARVING) {
			if (now - run.lastStarveAt >= TRUST_STARVE_TICKS) {
				run.lastStarveAt = now;
				mind = mind.withTrust(mind.trust - 1);
			}
		} else {
			run.lastStarveAt = now;
			if (now - run.lastDriftAt >= TRUST_DRIFT_TICKS) {
				run.lastDriftAt = now;
				if (WebSymbiote.wellFed(state) && state.stress() < ANXIOUS_STRESS) {
					mind = mind.withTrust(mind.trust + 1);
				}
			}
		}

		// losses: you went down, or came within a heart of it
		boolean downed = Downed.isDowned(player);
		if (downed && !run.wasDowned) {
			mind = mind.withGrief(Math.max(mind.griefUntil, now + GRIEF_DOWNED));
			write(player, mind);
			WebSymbiote.whisper(player, "mood.grieving.downed");
			mind = mind(player);
		}
		run.wasDowned = downed;
		boolean nearDeath = !downed && player.getHealth() <= player.getMaxHealth() * NEAR_DEATH_HEALTH;
		if (nearDeath && !run.wasNearDeath && !creative && mind.moodNow() != Mood.GRIEVING) {
			mind = mind.withGrief(Math.max(mind.griefUntil, now + GRIEF_NEAR_DEATH));
			write(player, mind);
			WebSymbiote.whisper(player, "mood.grieving.near_death");
			mind = mind(player);
		}
		run.wasNearDeath = nearDeath || downed;

		// stages
		int stage = stage(bond);
		if (run.stage >= 0 && stage > run.stage && !fresh) {
			WebSymbiote.whisper(player, "stage." + STAGE_NAMES[stage]);
		}
		run.stage = stage;

		// standing orders
		if (mind.order != ORDER_NONE && mind.trust < ORDER_BREAK) {
			mind = mind.withOrder(ORDER_NONE);
			WebSymbiote.whisper(player, "order.defy");
		}
		if (mind.order == PROTECT && !creative) {
			protect(player, run, now);
		}

		// requests
		if (!creative) {
			mind = tickRequest(player, state, run, mind, now);
		} else if (mind.req != REQ_NONE) {
			mind = mind.withRequest(REQ_NONE, 0L, 0);
		}

		// mood
		if (player.tickCount % MOOD_EVERY == 0) {
			mind = tickMood(player, state, run, mind, now);
		}

		if (fresh || !mind.equals(before)) {
			write(player, mind);
		}
	}

	/** Protect: something on you within range and you're hurt: a shell of absorption, now and then. */
	private static void protect(ServerPlayer player, Run run, long now) {
		if (now - run.shieldAt < PROTECT_SHIELD_GAP || player.getHealth() >= player.getMaxHealth()) {
			return;
		}
		boolean threat = !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PROTECT_RANGE),
			m -> m.isAlive() && m.getTarget() == player).isEmpty();
		if (!threat) {
			return;
		}
		run.shieldAt = now;
		if (player.getAbsorptionAmount() < PROTECT_SHIELD) {
			player.setAbsorptionAmount(PROTECT_SHIELD);
		}
		ServerLevel level = player.level();
		level.sendParticles(WebSymbiote.dust(1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.7, 0.4, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		WebSymbiote.whisper(player, "order.protect.shield." + (1 + player.getRandom().nextInt(LINES)));
	}

	// ===== mood ======================================================================================================

	private static Mind tickMood(ServerPlayer player, WebSymbioteState state, Run run, Mind mind, long now) {
		Mood target;
		if (now < mind.griefUntil) {
			target = Mood.GRIEVING;
		} else if (state.stress() >= COILED_STRESS || state.satiety() <= WebSymbiote.STARVING) {
			target = Mood.COILED;
		} else if (state.stress() >= ANXIOUS_STRESS || state.satiety() < WebSymbiote.SELF_FEED_BELOW || state.bond() < WebSymbiote.NEGLECT_BOND
			|| now - run.lastMealAt > NEGLECT_MEAL_TICKS || overdue(mind, now)) {
			target = Mood.ANXIOUS;
		} else {
			target = Mood.CONTENT;
		}
		Mood current = mind.moodNow();
		if (target == current) {
			run.pending = current;
			run.pendingFor = 0;
			return mind;
		}
		// grief takes at once and lifts at once; the rest must hold a moment
		if (target != run.pending) {
			run.pending = target;
			run.pendingFor = 0;
		}
		if (++run.pendingFor < MOOD_SETTLE && target != Mood.GRIEVING && current != Mood.GRIEVING) {
			return mind;
		}
		run.pendingFor = 0;
		Mind next = mind.withMood(target.ordinal());
		if (now - run.moodSaidAt >= MOOD_SAY_GAP && !WebSymbiote.driving(player)) {
			run.moodSaidAt = now;
			boolean worse = target.ordinal() > current.ordinal() && target != Mood.GRIEVING;
			if (target == Mood.COILED || target == Mood.ANXIOUS && worse || target == Mood.CONTENT && current != Mood.ANXIOUS) {
				write(player, next);
				WebSymbiote.whisper(player, "mood." + target.key() + ".in." + (1 + player.getRandom().nextInt(LINES)));
				return mind(player);
			}
		}
		return next;
	}

	private static boolean overdue(Mind mind, long now) {
		if (mind.req == REQ_NONE) {
			return false;
		}
		long total = REQ_TICKS[mind.req];
		return total > 0 && mind.reqEnd - now < total * (1.0F - NAG_AT);
	}

	// ===== requests ==================================================================================================

	private static Mind tickRequest(ServerPlayer player, WebSymbioteState state, Run run, Mind mind, long now) {
		// a forced job: keep trying until it takes
		if (run.forceKind != REQ_NONE) {
			if (now >= run.forceUntil) {
				run.forceKind = REQ_NONE;
			} else if (now >= run.forceRetryAt && !busy(player)) {
				run.forceRetryAt = now + FORCE_RETRY;
				if (run.forceKind == KILL || run.forceKind == NIGHT) {
					if (WebSymbiote.mindHunt(player, now)) {
						// (if its prey gets away it tries again later)
						run.forceRetryAt = now + FORCE_AGAIN;
					}
				} else if (run.forceKind == DEEP && WebSymbiote.mindDig(player, now)) {
					run.forceKind = REQ_NONE;
				}
			}
		}
		if (mind.req == REQ_NONE) {
			return offer(player, state, mind, now);
		}
		int kind = mind.req;
		int total = REQ_TICKS[kind];
		long left = mind.reqEnd - now;
		// progress: time deep, time in the suit
		if (kind == DEEP && deep(player)) {
			run.progress += SAMPLE;
		} else if (kind == WORN && WebSymbiote.suitShown(player)) {
			run.progress += SAMPLE;
		}
		if (run.progress > 0 && (player.tickCount % 100 == 0 || run.progress + mind.reqProg >= need(kind))) {
			mind = mind.withProgress(mind.reqProg + run.progress);
			run.progress = 0;
		}
		if (need(kind) > 0 && mind.reqProg + run.progress >= need(kind)) {
			write(player, mind);
			complete(player, true);
			return mind(player);
		}
		// it nags, louder, and frets
		float used = 1.0F - left / (float)total;
		if (!run.nagged && used >= NAG_AT) {
			run.nagged = true;
			WebSymbiote.whisper(player, "req." + REQ_NAMES[kind] + ".nag");
		}
		if (!run.warned && used >= LAST_AT) {
			run.warned = true;
			WebSymbiote.whisper(player, "req." + REQ_NAMES[kind] + ".last");
			WebSymbiote.dread(player, 80, true, true);
		}
		if (used >= NAG_AT && now - run.stressAt >= NAG_STRESS_TICKS && state.stress() < REQUEST_STRESS_CAP) {
			run.stressAt = now;
			addStress(player, 1);
		}
		if (left <= 0L) {
			return expire(player, state, run, mind, now);
		}
		return mind;
	}

	private static boolean busy(ServerPlayer player) {
		return Downed.isDowned(player) || WebSymbioteRescue.rescuing(player);
	}

	private static int need(int kind) {
		return kind == DEEP ? DEEP_NEED : kind == WORN ? WORN_NEED : 0;
	}

	private static boolean deep(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.dimension() == net.minecraft.world.level.Level.OVERWORLD && player.getY() < level.getSeaLevel() - DEEP_BELOW
			&& !level.canSeeSky(net.minecraft.core.BlockPos.containing(player.getEyePosition()));
	}

	/** Time to ask for something? Only a host it can trust to be listening: bonded, awake, not hiding, not grieving. */
	private static Mind offer(ServerPlayer player, WebSymbioteState state, Mind mind, long now) {
		if (mind.nextReq == 0L) {
			return mind.withNext(now + FIRST_GAP);
		}
		if (now < mind.nextReq || state.bond() < REQUEST_BOND || mind.moodNow() == Mood.GRIEVING || mind.order == HIDE
			|| WebSymbioteBond.dormant(player) || WebSymbioteStrains.asleep(player) || WebSymbiote.driving(player) || WebSymbiote.sulking(player)
			|| busy(player) || WebSymbiote.roaring(player)) {
			return mind;
		}
		List<Integer> options = new ArrayList<>();
		boolean white = state.kind() == WebSymbioteStrain.WHITE;
		ServerLevel level = player.level();
		boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
		if (state.satiety() <= MEAT_SATIETY) {
			options.add(MEAT);
		}
		if (!white) {
			options.add(KILL);
		}
		if (overworld && player.getY() > level.getSeaLevel() - DEEP_BELOW + 8) {
			options.add(DEEP);
		}
		if (!white && overworld && nightUnderSky(player)) {
			options.add(NIGHT);
		}
		if (!state.has(WebSymbioteState.SUIT) && WebSymbiote.suitAllowed(player) && !WebSymbiote.lockedOut(player)) {
			options.add(WORN);
		}
		if (options.isEmpty()) {
			return mind.withNext(now + 1200L);
		}
		int kind = options.get(player.getRandom().nextInt(options.size()));
		Run run = RUNS.get(player.getUUID());
		if (run != null) {
			run.nagged = false;
			run.warned = false;
			run.progress = 0;
			run.stressAt = now;
		}
		WebSymbiote.whisper(player, "req." + REQ_NAMES[kind] + ".ask." + (1 + player.getRandom().nextInt(ASK_LINES)));
		return mind.withRequest(kind, now + REQ_TICKS[kind], 0);
	}

	private static long gap(ServerPlayer player, Mind mind) {
		// a trusting one asks a little less often
		float scale = 1.3F - mind.trust / 200.0F;
		return Math.round((GAP_MIN + player.getRandom().nextInt(GAP_MAX - GAP_MIN + 1)) * scale);
	}

	/** Granted. {@code byYou}: trust and bond rise; done by itself, only some of the stress goes. */
	private static void complete(ServerPlayer player, boolean byYou) {
		Mind mind = mind(player);
		if (mind.req == REQ_NONE) {
			return;
		}
		long now = player.level().getGameTime();
		Run run = RUNS.get(player.getUUID());
		if (run != null) {
			run.progress = 0;
		}
		Mind next = mind.withRequest(REQ_NONE, 0L, 0).withNext(now + gap(player, mind));
		if (byYou) {
			next = next.withTrust(next.trust + TRUST_REQUEST);
		}
		write(player, next);
		if (byYou) {
			WebSymbiote.addBond(player, GRANTED_BOND);
			relieve(player, GRANTED_RELIEF);
			WebSymbiote.whisper(player, "req.thanks." + (1 + player.getRandom().nextInt(LINES)));
		}
	}

	/** Ignored: trust down, stress up, and it sets about doing it itself. */
	private static Mind expire(ServerPlayer player, WebSymbioteState state, Run run, Mind mind, long now) {
		int kind = mind.req;
		Mind next = mind.withRequest(REQ_NONE, 0L, 0).withNext(now + gap(player, mind)).withTrust(mind.trust - TRUST_IGNORED);
		addStress(player, IGNORED_STRESS);
		run.progress = 0;
		WebSymbiote.whisper(player, "req." + REQ_NAMES[kind] + ".ignored");
		boolean white = state.kind() == WebSymbioteStrain.WHITE;
		switch (kind) {
			case MEAT -> {
				if (!white) {
					run.forceKind = MEAT;
				}
			}
			case KILL, NIGHT -> {
				if (!white) {
					run.forceKind = kind;
				}
			}
			case DEEP -> run.forceKind = DEEP;
			case WORN -> run.wearItself = true;
			default -> {
			}
		}
		run.forceUntil = now + FORCE_TICKS;
		run.forceRetryAt = now + 20L;
		return next;
	}

	/** The dig it started is over (deep enough, or it gave up): a deep request it was doing itself is done. */
	static void digEnded(ServerPlayer player, boolean deepEnough) {
		Run run = RUNS.get(player.getUUID());
		if (run != null && deepEnough) {
			relieve(player, GRANTED_RELIEF / 2);
		}
	}

	// ===== the Status page's view of a request =======================================================================

	/** Ticks left on the current request (0: none). */
	public static long requestLeft(Player player, long now) {
		Mind mind = mind(player);
		return mind.req == REQ_NONE ? 0L : Math.max(0L, mind.reqEnd - now);
	}
}
