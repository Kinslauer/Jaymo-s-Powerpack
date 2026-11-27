package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 3 of the symbiote's 1.13 plan: what it remembers and what it makes of the world (our own code; the ideas came from
 * "Symbiote: A Bonding Experience", nothing of its code). Everything it says goes through {@link WebSymbiote#whisper}, so
 * each line darkens your screen. Its nature (the temper, {@link WebSymbioteMind#temper}) picks the wording of the lines that
 * carry feeling ({@code memory.<event>.<temper>}); the plain ones have two ({@code memory.<event>.1/2}), see
 * {@code lang/parts/web_memory.json}.
 * <ul>
 * <li><b>Memories</b>: a short log of moments ({@link #STARVED}, {@link #HELD}, {@link #PRIED}, {@link #IGNORED}); now and
 * then, calm, it brings one up.</li>
 * <li><b>Fear</b>: a kind of creature that hurt you badly ({@link #FEAR_HIT_SHARE} of your health in one blow) is feared
 * for a while: it flinches (a short darkening of the screen) when one is near and asks to walk wide. Not a grudge
 * ({@link WebGrudges} wants payback).</li>
 * <li><b>Jealousy</b>: another player or a tame pet close to you, with you standing there, for long enough, and nobody
 * fighting beside you: the screen's edge pulls red ({@link WebSymbioteMind#flare}) and it says so. Its mood decides how
 * soon it minds ({@link #JEALOUS_MOOD}).</li>
 * <li><b>Firsts</b>: the first time it meets each kind of creature (sometimes it turns your head to look), eats each kind,
 * and the first Nether, End, thunderstorm, Deep Dark and Ancient City. Saved, kept through death.</li>
 * <li><b>Other hosts</b>: a mob another symbiote has taken ({@link WebInhabit}, {@link WebBehemoth}) or a roaming symbiote
 * ({@link WebSymbioteBlob}) is narrated: far, closer, further, holding, coming, feeding, and whether it has noticed you.</li>
 * <li><b>The bond fades</b>: a day with nothing done together ({@link #together}: a meal, a fight beside you, a request
 * granted) costs {@link #FADE_BOND} bond; dropping a named stage is said.</li>
 * <li><b>Careless hurts</b>: a fall, a cactus or berry bush, walking into fire.</li>
 * </ul>
 */
public final class WebSymbioteMemory {
	// ---- tuning ----------------------------------------------------------------------------------------------------
	/** How many moments it keeps, and how long one lasts (ticks; a week), and the same kind isn't logged again within the gap. */
	static final int MEMORY_MAX = 6;
	static final long MOMENT_LIFE = 168000L, MOMENT_GAP = 1200L;
	/** Starving this long (ticks) is a moment. */
	static final int STARVED_TICKS = 1200;
	/** A callback: the first one after this long (ticks) on, then this far apart (plus up to the same again), this often to speak when due. */
	static final int CALLBACK_FIRST = 6000, CALLBACK_GAP = 12000;
	static final float CALLBACK_CHANCE = 0.5F;
	/** Whatever else it has said lately: another line waits this long (ticks) after one of these. */
	static final int SAY_GAP = 200;
	/** Fear: a blow of at least {@code FEAR_MIN_HIT} hit points and this share of your health; the fear lasts this long (ticks; two days); most it holds at once. */
	static final float FEAR_MIN_HIT = 6.0F, FEAR_HIT_SHARE = 0.3F;
	static final long FEAR_TICKS = 48000L;
	static final int FEAR_MAX = 3;
	/** By temper: how long a fear lasts (times {@code FEAR_TICKS}): the wary hold on to it, the warm let go. */
	static final float[] FEAR_TEMPER = {1.0F, 1.0F, 1.0F, 0.7F, 1.5F};
	/** It flinches when one is this near (blocks) and asks to walk wide when this near for {@code WIDE_AFTER} ticks. */
	static final double FEAR_RANGE = 14.0, WIDE_RANGE = 8.0;
	static final int WIDE_AFTER = 60, FLINCH_FX = 16, FLINCH_GAP = 900, WIDE_GAP = 1800;
	/** Jealousy: another player within this far, or a tame pet within {@code JEALOUS_PET} while you stand still, for this long (ticks), unmoved. */
	static final double JEALOUS_RANGE = 3.0, JEALOUS_PET = 2.5;
	static final int JEALOUS_TICKS = 1800, JEALOUS_GAP = 6000, JEALOUS_STRESS = 4, FLARE_TICKS = 100;
	/** Fighting lately (ticks): nothing counts then. */
	static final int FIGHT_TICKS = 200;
	/** By mood (content, anxious, coiled, grieving): how long it takes to mind, as a share of {@code JEALOUS_TICKS}; grieving, it doesn't. */
	static final float[] JEALOUS_MOOD = {1.5F, 1.0F, 0.6F, 0.0F};
	/** By temper: the same again (the warm one is easy, the sour one isn't). */
	static final float[] JEALOUS_TEMPER = {1.0F, 1.0F, 0.8F, 1.4F, 1.0F};
	/** First meetings: how far it looks, the chance it turns your head to look, and how long between looks (ticks). */
	static final double MEET_RANGE = 14.0;
	static final float MEET_LOOK_CHANCE = 0.35F;
	static final int MEET_LOOK_GAP = 1200;
	/** Other hosts: how far it can tell, where "far" ends, how much a distance must change (blocks in a second) to be coming or going, and the gap between lines. */
	static final double TRACK_RANGE = 48.0, TRACK_FAR = 28.0, TRACK_MOVE = 1.5, TRACK_NEAR_COMING = 10.0;
	static final int TRACK_GAP = 900;
	/** The bond fades: this much for each day with nothing done together (at most {@code FADE_DAYS} days at once), never under {@code FADE_FLOOR}. */
	static final int FADE_BOND = 1, FADE_DAYS = 2, FADE_FLOOR = 5;
	static final long DAY = 24000L;
	/** Careless hurts: a line at most this often (ticks). */
	static final int CARELESS_GAP = 1200;
	static final float CARELESS_FALL = 2.0F;
	/** How often each part looks (ticks). */
	static final int SCAN = 20, SLOW_SCAN = 100;

	/** The moments it keeps. */
	public static final int STARVED = 0, HELD = 1, PRIED = 2, IGNORED = 3;
	static final String[] MOMENT_NAMES = {"starved", "held", "pried", "ignored"};
	/** The places it marks, as bits of {@link Book#places}. */
	static final int NETHER = 1, END = 2, STORM = 4, DEEP_DARK = 8, CITY = 16;

	/** One moment: which kind ({@link #STARVED}..) and when. */
	public record Moment(int kind, long at) {
		public static final Codec<Moment> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("kind").forGetter(Moment::kind),
				Codec.LONG.optionalFieldOf("at", 0L).forGetter(Moment::at))
			.apply(i, Moment::new));
	}

	/** A kind of creature it fears (its entity type id) until a game time. */
	public record Fear(String kind, long until) {
		public static final Codec<Fear> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Fear::kind),
				Codec.LONG.optionalFieldOf("until", 0L).forGetter(Fear::until))
			.apply(i, Fear::new));
	}

	/**
	 * What it keeps: the moments, the fears, the kinds met and eaten (entity type ids), the places seen (bits), the last day
	 * something was done together and the last day the fade was reckoned. Saved and kept through death with the symbiote.
	 */
	public record Book(List<Moment> moments, List<Fear> fears, List<String> met, List<String> ate, int places, long togetherDay, long fadeDay) {
		public static final Book EMPTY = new Book(List.of(), List.of(), List.of(), List.of(), 0, 0L, 0L);
		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
				Moment.CODEC.listOf().optionalFieldOf("moments", List.of()).forGetter(Book::moments),
				Fear.CODEC.listOf().optionalFieldOf("fears", List.of()).forGetter(Book::fears),
				Codec.STRING.listOf().optionalFieldOf("met", List.of()).forGetter(Book::met),
				Codec.STRING.listOf().optionalFieldOf("ate", List.of()).forGetter(Book::ate),
				Codec.INT.optionalFieldOf("places", 0).forGetter(Book::places),
				Codec.LONG.optionalFieldOf("together_day", 0L).forGetter(Book::togetherDay),
				Codec.LONG.optionalFieldOf("fade_day", 0L).forGetter(Book::fadeDay))
			.apply(i, Book::new));

		Book withMoments(List<Moment> value) {
			return new Book(value, fears, met, ate, places, togetherDay, fadeDay);
		}

		Book withFears(List<Fear> value) {
			return new Book(moments, value, met, ate, places, togetherDay, fadeDay);
		}

		Book withMet(String kind) {
			List<String> next = new ArrayList<>(met);
			next.add(kind);
			return new Book(moments, fears, next, ate, places, togetherDay, fadeDay);
		}

		Book withAte(String kind) {
			List<String> next = new ArrayList<>(ate);
			next.add(kind);
			return new Book(moments, fears, met, next, places, togetherDay, fadeDay);
		}

		Book withPlace(int bit) {
			return new Book(moments, fears, met, ate, places | bit, togetherDay, fadeDay);
		}

		Book withTogether(long day) {
			return new Book(moments, fears, met, ate, places, day, fadeDay);
		}

		Book withFade(long day) {
			return new Book(moments, fears, met, ate, places, togetherDay, day);
		}
	}

	/** Saved with the player and kept through death (like the bond itself); the server's alone, never synced. */
	public static final AttachmentType<Book> BOOK = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_memory"),
		builder -> builder.persistent(Book.CODEC).copyOnDeath());

	/** Server-only, per player: gaps, counters and what it was last doing. */
	private static final class Run {
		long sayAt, callbackAt, flinchAt, wideAt, jealousAt, carelessAt, trackAt, lookAt;
		int starveTicks, jealousTicks, nearFear;
		boolean starved, fearNear;
		Vec3 lastPos = Vec3.ZERO;
		boolean still;
		int trackId = -1;
		double trackDist;
		String trackState = "";
		boolean trackNoticed;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private WebSymbioteMemory() {
	}

	public static Book book(Player player) {
		Book book = player.getAttached(BOOK);
		return book == null ? Book.EMPTY : book;
	}

	private static void write(ServerPlayer player, Book book) {
		if (!book.equals(player.getAttached(BOOK))) {
			player.setAttached(BOOK, book);
		}
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % SCAN == 0 && active(player)) {
					tick(player);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer host && !blocked && damageTaken > 0.0F && active(host)) {
				hurt(host, source, damageTaken);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNS.remove(handler.player.getUUID()));
	}

	/** It is bonded to a living, awake host in the game proper, and past its settling in. */
	private static boolean active(ServerPlayer player) {
		return WebSymbiote.isBonded(player) && player.isAlive() && !player.isSpectator() && !player.isCreative() && !WebSymbioteBond.dormant(player);
	}

	private static Run run(ServerPlayer player) {
		return RUNS.computeIfAbsent(player.getUUID(), id -> new Run());
	}

	// ===== speaking ==================================================================================================

	/**
	 * Says {@code event} ({@code memory.<event>...}), in the temper's own wording if {@code tempered}, else one of two plain
	 * ones, with {@code args}. False when it can't speak now (it's mid-sentence, or in a takeover, or has just spoken): the
	 * caller tries again later. True when spoken, or swallowed by grief (nothing to retry).
	 */
	private static boolean say(ServerPlayer player, Run run, long now, String event, boolean tempered, Object... args) {
		if (now < run.sayAt || !WebSymbiote.canSpeak(player, now) || WebSymbiote.driving(player)) {
			return false;
		}
		run.sayAt = now + SAY_GAP;
		if (WebSymbioteMind.quiet(player)) {
			return true;
		}
		String variant = tempered && WebSymbioteMind.temper(player) != 0 ? WebSymbioteMind.temperName(WebSymbioteMind.temper(player))
			: String.valueOf(1 + player.getRandom().nextInt(2));
		WebSymbiote.whisper(player, "memory." + event + "." + variant, args);
		return true;
	}

	private static String kindOf(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	private static Component nameOf(String kind) {
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(kind);
		return id == null ? Component.literal(kind) : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription()).orElse(Component.literal(kind));
	}

	/** Fighting lately, or something hostile after you: no jealousy, no looking about. */
	private static boolean fighting(ServerPlayer player) {
		return player.tickCount - player.getLastHurtMobTimestamp() < FIGHT_TICKS || player.tickCount - player.getLastHurtByMobTimestamp() < FIGHT_TICKS
			|| !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0), m -> m.isAlive() && m instanceof Enemy && m.getTarget() == player).isEmpty();
	}

	// ===== memories ==================================================================================================

	/** Logs a moment ({@link #STARVED}, {@link #HELD}, {@link #PRIED}, {@link #IGNORED}); the same kind isn't logged twice in a row too soon. */
	public static void remember(ServerPlayer player, int kind) {
		long now = player.level().getGameTime();
		Book book = book(player);
		List<Moment> moments = new ArrayList<>(book.moments());
		for (Moment moment : moments) {
			if (moment.kind() == kind && now - moment.at() < MOMENT_GAP) {
				return;
			}
		}
		moments.add(new Moment(kind, now));
		while (moments.size() > MEMORY_MAX) {
			moments.remove(0);
		}
		write(player, book.withMoments(moments));
	}

	/** Something done together (a meal, a fight beside you, a request granted): today the bond doesn't fade. */
	static void together(ServerPlayer player) {
		long day = player.level().getGameTime() / DAY;
		Book book = book(player);
		if (book.togetherDay() != day) {
			write(player, book.withTogether(day));
		}
	}

	/** It just finished eating {@code food} (what it was, gone now): the first of each kind is remarked on. */
	static void ate(ServerPlayer player, Entity food) {
		together(player);
		String kind = kindOf(food);
		Book book = book(player);
		if (book.ate().contains(kind)) {
			return;
		}
		long now = player.level().getGameTime();
		Run run = run(player);
		run.sayAt = Math.min(run.sayAt, now);
		if (say(player, run, now, "eat_first", false, food.getDisplayName())) {
			write(player, book.withAte(kind));
		}
	}

	private static void callback(ServerPlayer player, Run run, long now, Book book) {
		if (book.moments().isEmpty() || now < run.callbackAt) {
			return;
		}
		if (run.callbackAt == 0L) {
			run.callbackAt = now + CALLBACK_FIRST + player.getRandom().nextInt(CALLBACK_GAP);
			return;
		}
		WebSymbioteMind.Mood mood = WebSymbioteMind.mood(player);
		if (mood == WebSymbioteMind.Mood.GRIEVING || mood == WebSymbioteMind.Mood.COILED || fighting(player)) {
			return;
		}
		if (player.getRandom().nextFloat() >= CALLBACK_CHANCE) {
			run.callbackAt = now + SLOW_SCAN * 6L;
			return;
		}
		Moment moment = book.moments().get(player.getRandom().nextInt(book.moments().size()));
		if (say(player, run, now, "callback_" + MOMENT_NAMES[Mth.clamp(moment.kind(), 0, MOMENT_NAMES.length - 1)], true)) {
			run.callbackAt = now + CALLBACK_GAP + player.getRandom().nextInt(CALLBACK_GAP);
		}
	}

	// ===== what hurts you ============================================================================================

	private static void hurt(ServerPlayer host, DamageSource source, float amount) {
		long now = host.level().getGameTime();
		Run run = run(host);
		// careless hurts
		String careless = source.is(DamageTypes.FALL) && amount >= CARELESS_FALL ? "careless_fall"
			: source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) ? "careless_prick"
			: source.is(DamageTypes.IN_FIRE) ? "careless_fire" : null;
		if (careless != null && now >= run.carelessAt && say(host, run, now, careless, true)) {
			run.carelessAt = now + CARELESS_GAP;
		}
		// fear: a creature that hurt you badly
		if (source.getEntity() instanceof LivingEntity foe && !(foe instanceof Player) && foe != host
			&& !(foe instanceof OwnableEntity owned && owned.getOwner() == host)
			&& amount >= Math.max(FEAR_MIN_HIT, host.getMaxHealth() * FEAR_HIT_SHARE)) {
			fear(host, run, now, foe);
		}
	}

	private static void fear(ServerPlayer host, Run run, long now, LivingEntity foe) {
		String kind = kindOf(foe);
		Book book = book(host);
		List<Fear> fears = new ArrayList<>(book.fears());
		boolean fresh = true;
		long until = now + Math.round(FEAR_TICKS * FEAR_TEMPER[Mth.clamp(WebSymbioteMind.temper(host), 0, FEAR_TEMPER.length - 1)]);
		for (int i = 0; i < fears.size(); i++) {
			if (fears.get(i).kind().equals(kind)) {
				fresh = fears.get(i).until() <= now;
				fears.set(i, new Fear(kind, Math.max(until, fears.get(i).until())));
				until = -1L;
				break;
			}
		}
		if (until >= 0L) {
			fears.add(new Fear(kind, until));
			while (fears.size() > FEAR_MAX) {
				// the one that will fade soonest goes
				int soonest = 0;
				for (int i = 1; i < fears.size(); i++) {
					if (fears.get(i).until() < fears.get(soonest).until()) {
						soonest = i;
					}
				}
				fears.remove(soonest);
			}
		}
		write(host, book.withFears(fears));
		if (fresh) {
			WebSymbiote.dread(host, FLINCH_FX, true);
			say(host, run, now, "fear_born", true, foe.getDisplayName());
		}
	}

	// ===== the tick ==================================================================================================

	private static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Run run = run(player);
		WebSymbioteState state = WebSymbiote.state(player);
		Book book = book(player);
		Vec3 here = player.position();
		run.still = here.subtract(run.lastPos).horizontalDistanceSqr() < 0.01 && player.onGround();
		run.lastPos = here;

		// starving: a moment
		if (state.satiety() <= WebSymbiote.STARVING) {
			run.starveTicks += SCAN;
			if (!run.starved && run.starveTicks >= STARVED_TICKS) {
				run.starved = true;
				remember(player, STARVED);
			}
		} else {
			run.starveTicks = 0;
			run.starved = false;
		}
		// moments fade
		if (!book.moments().isEmpty() && book.moments().get(0).at() + MOMENT_LIFE < now) {
			List<Moment> kept = new ArrayList<>(book.moments());
			kept.removeIf(m -> m.at() + MOMENT_LIFE < now);
			book = book.withMoments(kept);
			write(player, book);
		}
		if (!book.fears().isEmpty() && book.fears().stream().anyMatch(f -> f.until() <= now)) {
			List<Fear> kept = new ArrayList<>(book.fears());
			kept.removeIf(f -> f.until() <= now);
			book = book.withFears(kept);
			write(player, book);
		}

		fears(player, run, now, book);
		jealousy(player, run, now);
		firstMeetings(player, run, now, book);
		firstPlaces(player, run, now, book);
		tracks(player, run, now);
		if (player.tickCount % SLOW_SCAN == 0) {
			callback(player, run, now, book);
			fade(player, run, now, state, book);
		}
	}

	// ===== fear ======================================================================================================

	private static void fears(ServerPlayer player, Run run, long now, Book book) {
		if (book.fears().isEmpty()) {
			run.fearNear = false;
			run.nearFear = 0;
			return;
		}
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FEAR_RANGE),
			e -> e.isAlive() && e != player && !(e instanceof Player))) {
			String kind = kindOf(e);
			if (book.fears().stream().anyMatch(f -> f.kind().equals(kind))) {
				double d = e.distanceToSqr(player);
				if (d < best) {
					best = d;
					nearest = e;
				}
			}
		}
		if (nearest == null) {
			run.fearNear = false;
			run.nearFear = 0;
			return;
		}
		if (!run.fearNear && now >= run.flinchAt) {
			// it flinches: the screen darkens a moment
			run.fearNear = true;
			WebSymbiote.dread(player, FLINCH_FX, false);
			if (say(player, run, now, "fear_flinch", true, nearest.getDisplayName())) {
				run.flinchAt = now + FLINCH_GAP;
			}
		}
		if (best <= WIDE_RANGE * WIDE_RANGE) {
			run.nearFear += SCAN;
			if (run.nearFear >= WIDE_AFTER && now >= run.wideAt && say(player, run, now, "fear_wide", true, nearest.getDisplayName())) {
				run.wideAt = now + WIDE_GAP;
				run.nearFear = 0;
			}
		} else {
			run.nearFear = 0;
		}
	}

	// ===== jealousy ==================================================================================================

	private static void jealousy(ServerPlayer player, Run run, long now) {
		WebSymbioteMind.Mood mood = WebSymbioteMind.mood(player);
		float scale = JEALOUS_MOOD[mood.ordinal()];
		if (scale <= 0.0F || WebSymbioteMind.hiding(player) || fighting(player)
				|| WebSymbioteSettings.off(player, WebSymbioteSettings.Setting.JEALOUSY)) {
			run.jealousTicks = 0;
			return;
		}
		boolean near = false;
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && other.distanceTo(player) <= JEALOUS_RANGE) {
				near = true;
				break;
			}
		}
		if (!near && run.still) {
			for (TamableAnimal pet : player.level().getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(JEALOUS_PET),
				pet -> pet.isAlive() && pet.isTame())) {
				near = true;
				break;
			}
		}
		if (!near) {
			run.jealousTicks = Math.max(0, run.jealousTicks - 2 * SCAN);
			return;
		}
		run.jealousTicks += SCAN;
		float need = JEALOUS_TICKS * scale * JEALOUS_TEMPER[Mth.clamp(WebSymbioteMind.temper(player), 0, JEALOUS_TEMPER.length - 1)];
		if (run.jealousTicks >= need && now >= run.jealousAt && say(player, run, now, "jealous", true)) {
			run.jealousAt = now + JEALOUS_GAP;
			run.jealousTicks = 0;
			WebSymbioteMind.flare(player, now + FLARE_TICKS);
			WebSymbioteMind.addStress(player, JEALOUS_STRESS);
		}
	}

	// ===== first meetings ============================================================================================

	private static void firstMeetings(ServerPlayer player, Run run, long now, Book book) {
		if (WebSymbiote.driving(player) || com.jaymospowers.core.wounds.Downed.isDowned(player)) {
			return;
		}
		LivingEntity first = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MEET_RANGE),
			e -> e.isAlive() && e != player && !(e instanceof Player) && !(e instanceof WebSymbioteBlob) && !e.isInvisible())) {
			double d = e.distanceToSqr(player);
			if (d < best && !book.met().contains(kindOf(e)) && player.hasLineOfSight(e)) {
				best = d;
				first = e;
			}
		}
		if (first == null || !say(player, run, now, "meet_first", false, first.getDisplayName())) {
			return;
		}
		write(player, book.withMet(kindOf(first)));
		// sometimes it turns your head to look
		if (now >= run.lookAt && run.still && best > 9.0 && !fighting(player) && player.getRandom().nextFloat() < MEET_LOOK_CHANCE) {
			run.lookAt = now + MEET_LOOK_GAP;
			Vec3 to = first.getBoundingBox().getCenter().subtract(player.getEyePosition());
			float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float)-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
			player.connection.send(new ClientboundPlayerRotationPacket(yaw, false, pitch, false));
		}
	}

	// ===== first places ==============================================================================================

	private static void firstPlaces(ServerPlayer player, Run run, long now, Book book) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		int bit = 0;
		String event = null;
		if (level.dimension() == Level.NETHER && (book.places() & NETHER) == 0) {
			bit = NETHER;
			event = "place_nether";
		} else if (level.dimension() == Level.END && (book.places() & END) == 0) {
			bit = END;
			event = "place_end";
		} else if (level.dimension() == Level.OVERWORLD) {
			if ((book.places() & STORM) == 0 && level.isThundering() && level.canSeeSky(at)) {
				bit = STORM;
				event = "place_storm";
			} else if ((book.places() & DEEP_DARK) == 0 && level.getBiome(at).is(Biomes.DEEP_DARK)) {
				bit = DEEP_DARK;
				event = "place_deep_dark";
			} else if ((book.places() & CITY) == 0 && at.getY() < 16
				&& level.structureManager().getStructureWithPieceAt(at, holder -> holder.is(BuiltinStructures.ANCIENT_CITY)).isValid()) {
				bit = CITY;
				event = "place_city";
			}
		}
		if (event != null && say(player, run, now, event, false)) {
			write(player, book.withPlace(bit));
		}
	}

	// ===== other hosts ===============================================================================================

	private static void tracks(ServerPlayer player, Run run, long now) {
		Mob nearest = null;
		double best = TRACK_RANGE * TRACK_RANGE;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TRACK_RANGE),
			m -> m.isAlive() && (m instanceof WebSymbioteBlob || WebInhabit.isHost(m) || WebBehemoth.isHost(m)))) {
			double d = mob.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = mob;
			}
		}
		if (nearest == null) {
			run.trackId = -1;
			run.trackState = "";
			run.trackNoticed = false;
			return;
		}
		double dist = Math.sqrt(best);
		boolean fresh = nearest.getId() != run.trackId;
		double change = fresh ? 0.0 : dist - run.trackDist;
		run.trackId = nearest.getId();
		run.trackDist = dist;
		boolean noticed = nearest.getTarget() == player;
		String state;
		if (nearest instanceof WebSymbioteBlob blob && blob.feast > 0) {
			state = "feeding";
		} else if (fresh) {
			state = dist > TRACK_FAR ? "far" : "closer";
		} else if (change <= -TRACK_MOVE) {
			state = noticed || dist < TRACK_NEAR_COMING ? "coming" : "closer";
		} else if (change >= TRACK_MOVE) {
			state = "further";
		} else {
			state = run.trackState.isEmpty() ? "holding" : dist > TRACK_FAR && run.trackState.equals("far") ? "far" : "holding";
		}
		if (now < run.trackAt) {
			return;
		}
		// (it says what it senses has changed; a first word on noticing you, once, whatever else it is doing)
		if (noticed && !run.trackNoticed) {
			if (say(player, run, now, "track_noticed", false)) {
				run.trackNoticed = true;
				run.trackAt = now + TRACK_GAP;
			}
			return;
		}
		if (!noticed) {
			run.trackNoticed = false;
		}
		if (!state.equals(run.trackState) && say(player, run, now, "track_" + state, false)) {
			run.trackState = state;
			run.trackAt = now + TRACK_GAP;
		}
	}

	// ===== the bond fades ============================================================================================

	private static void fade(ServerPlayer player, Run run, long now, WebSymbioteState state, Book book) {
		long day = now / DAY;
		if (book.fadeDay() == 0L || WebSymbioteStrains.asleep(player)) {
			if (book.fadeDay() == 0L) {
				write(player, book.withFade(day));
			}
			return;
		}
		if (day <= book.fadeDay()) {
			return;
		}
		// each day gone by: did anything happen together in it? (togetherDay is the last that did)
		int missed = 0;
		for (long d = book.fadeDay(); d < day && missed < FADE_DAYS; d++) {
			if (book.togetherDay() < d) {
				missed++;
			}
		}
		write(player, book.withFade(day));
		if (missed == 0 || state.bond() <= FADE_FLOOR) {
			return;
		}
		int before = WebSymbioteMind.stage(state.bond());
		WebSymbiote.addBond(player, -Math.min(missed * FADE_BOND, state.bond() - FADE_FLOOR));
		int after = WebSymbioteMind.stage(WebSymbiote.state(player).bond());
		run.sayAt = Math.min(run.sayAt, now);
		if (after < before) {
			WebSymbiote.whisper(player, "memory.fade_stage." + WebSymbioteMind.stageName(WebSymbiote.state(player).bond()));
		} else {
			say(player, run, now, "fade_day", true);
		}
	}
}
