package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.jaymoskingdoms.Treaties.Kind;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.519) Envoys carry offers of treaties ({@link Treaties}) between realms. A realm's envoy is a villager with its
 * banner who rides the King's Roads like a courier ({@link Couriers}): while nobody is near they go on in a record, and
 * come into the world when a player is, so you may meet one on the road. At an NPC court the offer is weighed by the
 * ruler's temper, the realm's creed, friction, tension and old grievances ({@link #score}); at a player's court the envoy
 * waits in the hall for the sovereign to hear them. Killed on the way, the sender and the land's realm remember.
 */
final class Envoys {
	/** How often envoys are thought about (ticks). */
	static final int EVERY = 10;
	/** Realms this far apart can treat (blocks). */
	static final double RANGE = GreatWars.TENSION_RANGE;
	/** The score at which a court signs, and the luck (plus or minus) added to an NPC court's score when it decides. */
	static final int ACCEPT_AT = 50, ACCEPT_LUCK = 8;
	/** On the desk: likely at {@code ACCEPT_AT + this} or more, maybe within it, else unlikely. */
	static final int ODDS_BAND = 10;
	/** A proposer must itself score at least {@code ACCEPT_AT - this}. */
	static final int PROPOSE_SLACK = 5;
	/** The chance a day for each eligible NPC pair, and for an NPC toward a player's realm. */
	static final float PROPOSE_CHANCE = 0.10F, PLAYER_PROPOSE_CHANCE = 0.15F;
	/** Envoys on the roads at once. */
	static final int MISSIONS_MOST = 6;
	/** How long an NPC court hears an envoy, and how long one waits for a sovereign (ticks). */
	static final long THINK_TICKS = Wars.NPC_ANSWER_TICKS, WAIT_TICKS = Couriers.WAIT;
	/** Home again, they go in once no player is this near; a sovereign answering must be this near the envoy. */
	static final double HOME_GONE = 16.0, HEAR_RANGE = 8.0;
	/** What a player's realm pays to send one (emeralds' worth, {@link Money#UNIT} each). */
	static final long ENVOY_COST = 20L;
	/** The killer's reputation with the sender, and with the realm whose land it died on; tension added if it died on the target's land. */
	static final int REP_KILL_ENVOY = -200, REP_KILL_GUEST = -60;
	static final float KILL_TENSION = 15.0F;
	/** The score's base by kind (recognition, passage, charter, aid). */
	static final int[] KIND_BASE = {60, 45, 50, 30};
	/** Per point of friction and tension, and the bonus for kindred creeds (friction 0). */
	static final int FRICTION_WEIGHT = 8;
	static final float TENSION_WEIGHT = 0.6F;
	static final int KINDRED_BONUS = 10;
	/** Stored rivals; a truce still running. */
	static final int RIVAL_PENALTY = 20, TRUCE_PENALTY = 10;
	/** Aid: a third realm both are at odds with, or none. */
	static final int COMMON_FOE_BONUS = 20, NO_FOE_PENALTY = 10;
	/** A charter between two rich realms. */
	static final int RICH_TRADE_BONUS = 10;
	/** A purist judge: the proposer of the same blood, or of another. */
	static final int PURIST_KIN = 15, PURIST_STRANGER = 20;
	/** A player side: its sovereign's reputation with the judge, over this, at most this much either way. */
	static final int REP_PER_POINT = 10, REP_MOST = 30;
	/** By the judge's temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST) then kind (recognition, passage, charter, aid). */
	static final int[][] TEMPER_BIAS = {
		{-5, -15, 0, 5},
		{10, 5, 0, 10},
		{-5, 5, -5, 15},
		{0, -25, -5, 0},
		{10, 10, 15, -5},
		{-10, -15, -5, 0}};
	/** By the judge's creed (CROWN, CHIVALRY, CONQUEST, FAITH, COIN, BLOOD) then kind. */
	static final int[][] CREED_BIAS = {
		{5, 0, 0, 5},
		{10, 5, 0, 10},
		{-10, 0, -5, 10},
		{5, 5, 0, 5},
		{0, 5, 20, -5},
		{-5, -10, 0, 5}};
	/** What each creed proposes first (by {@link GreatWars.Creed} order), as {@link Kind} ordinals. */
	static final int[][] ORDER = {
		{0, 3, 1, 2},
		{0, 1, 3, 2},
		{3, 0, 1, 2},
		{0, 1, 2, 3},
		{2, 0, 1, 3},
		{0, 3, 1, 2}};
	/** The legs of a mission. */
	static final int TO = 0, WAIT = 1, BACK = 2;
	/** Lines an envoy says on arriving (lang {@code speech.jaymoskingdoms.envoy.arrive.N}). */
	static final int ARRIVE_LINES = 3;
	static final double SPEECH_RANGE = 16.0;
	/** Near the goal or home this close; the hall they keep to while they wait. */
	static final double HOME = 6.0, HALL_RADIUS = 6.0;
	/** How often the daily round is looked at (ticks). */
	static final int CHECK_TICKS = 1200;
	/** Looking after a day's proposals. */
	private static final int REASONS = 5;

	/**
	 * An envoy's mission: from realm {@code from} to realm {@code to} with {@code kind} (renewing one if {@code renew}), on
	 * {@code leg}, home at {@code home}, to be at {@code goal}, got to {@code at} (out of the world); begun {@code since};
	 * waiting until {@code until}; the envoy villager (once seen) with their whole saved self while they're out of the
	 * world; and the player who sent it, if one did.
	 */
	record Mission(int id, int from, int to, String kind, boolean renew, int leg, BlockPos home, BlockPos goal, BlockPos at, long since, long until,
		Optional<UUID> envoy, boolean rides, CompoundTag body, Optional<UUID> sender) {
		static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Mission::id),
			Codec.INT.fieldOf("from").forGetter(Mission::from),
			Codec.INT.fieldOf("to").forGetter(Mission::to),
			Codec.STRING.fieldOf("kind").forGetter(Mission::kind),
			Codec.BOOL.optionalFieldOf("renew", false).forGetter(Mission::renew),
			Codec.INT.fieldOf("leg").forGetter(Mission::leg),
			BlockPos.CODEC.fieldOf("home").forGetter(Mission::home),
			BlockPos.CODEC.fieldOf("goal").forGetter(Mission::goal),
			BlockPos.CODEC.fieldOf("at").forGetter(Mission::at),
			Codec.LONG.fieldOf("since").forGetter(Mission::since),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(Mission::until),
			UUIDUtil.CODEC.optionalFieldOf("envoy").forGetter(Mission::envoy),
			Codec.BOOL.optionalFieldOf("rides", false).forGetter(Mission::rides),
			CompoundTag.CODEC.optionalFieldOf("body", new CompoundTag()).forGetter(Mission::body),
			UUIDUtil.CODEC.optionalFieldOf("sender").forGetter(Mission::sender)).apply(i, Mission::new));

		Mission at(BlockPos at) {
			return new Mission(this.id, this.from, this.to, this.kind, this.renew, this.leg, this.home, this.goal, at, this.since, this.until, this.envoy, this.rides,
				this.body, this.sender);
		}

		Mission body(CompoundTag body) {
			return new Mission(this.id, this.from, this.to, this.kind, this.renew, this.leg, this.home, this.goal, this.at, this.since, this.until, this.envoy, this.rides,
				body, this.sender);
		}

		Mission leg(int leg, long until) {
			return new Mission(this.id, this.from, this.to, this.kind, this.renew, leg, this.home, this.goal, this.at, this.since, until, this.envoy, this.rides,
				this.body, this.sender);
		}

		Mission envoy(Optional<UUID> envoy) {
			return new Mission(this.id, this.from, this.to, this.kind, this.renew, this.leg, this.home, this.goal, this.at, this.since, this.until, envoy, this.rides,
				this.body, this.sender);
		}

		boolean out() {
			return !this.body.isEmpty();
		}

		Treaties.@Nullable Kind kindOf() {
			return Kind.parse(this.kind);
		}
	}

	record Book(Map<String, Mission> missions, int nextId, long lastDay) {
		static final Book EMPTY = new Book(Map.of(), 1, -1L);
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Mission.CODEC).optionalFieldOf("missions", Map.of()).forGetter(Book::missions),
			Codec.INT.optionalFieldOf("next_id", 1).forGetter(Book::nextId),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Book::lastDay)).apply(i, Book::new));
	}

	/** On the Overworld: every mission by id. Saved. */
	static final AttachmentType<Book> MISSIONS = AttachmentRegistry.create(JaymosKingdoms.id("envoy_missions"), b -> b.persistent(Book.CODEC));
	/** On the envoy villager: the id of their mission. Saved. */
	public static final AttachmentType<Integer> ENVOY = AttachmentRegistry.create(JaymosKingdoms.id("envoy"), b -> b.persistent(Codec.INT));

	/** Out-of-date copies of envoys now in the record, and riderless horses: put away next tick. */
	private static final Set<Entity> STALE = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Envoys taken out of the world by us just now. */
	private static final Set<UUID> TAKING = new java.util.HashSet<>();
	/** Envoys home again, in the world until nobody is near. */
	private static final Set<Villager> GOING_HOME = Collections.newSetFromMap(new IdentityHashMap<>());
	/** A seen envoy's best distance to where they are going so far, and when it last got better, by mission id. */
	private static final Map<Integer, double[]> PROGRESS = new HashMap<>();
	/** The mission each player is hearing now. */
	private static final Map<UUID, Integer> HEARING = new HashMap<>();

	private Envoys() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Envoys::tick);
		ServerEntityEvents.ENTITY_LOAD.register(Envoys::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Envoys::unloaded);
		ServerLivingEntityEvents.AFTER_DEATH.register(Envoys::slain);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager v) || !is(v) || McaCompat.isMca(v)) {
				return InteractionResult.PASS;
			}
			// (without MCA there is no Interact page: right-click the envoy)
			if (player instanceof ServerPlayer sp && hearable(sp, v)) {
				hear(sp, v);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STALE.clear();
			TAKING.clear();
			GOING_HOME.clear();
			PROGRESS.clear();
			HEARING.clear();
		});
	}

	/** {@code e} is an envoy. */
	static boolean is(@Nullable Entity e) {
		return e instanceof Villager && e.hasAttached(ENVOY);
	}

	// ---- the record ---------------------------------------------------------------------------------------------------

	static Book book(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(MISSIONS, Book.EMPTY);
	}

	private static @Nullable Mission mission(MinecraftServer server, int id) {
		return book(server).missions().get(Integer.toString(id));
	}

	private static void put(MinecraftServer server, Mission m) {
		Book b = book(server);
		Map<String, Mission> map = new HashMap<>(b.missions());
		map.put(Integer.toString(m.id()), m);
		Factions.level(server).setAttached(MISSIONS, new Book(Map.copyOf(map), b.nextId(), b.lastDay()));
	}

	private static Mission putIt(MinecraftServer server, Mission m) {
		put(server, m);
		return m;
	}

	private static void drop(MinecraftServer server, int id) {
		Book b = book(server);
		if (b.missions().containsKey(Integer.toString(id))) {
			Map<String, Mission> map = new HashMap<>(b.missions());
			map.remove(Integer.toString(id));
			Factions.level(server).setAttached(MISSIONS, new Book(Map.copyOf(map), b.nextId(), b.lastDay()));
		}
		PROGRESS.remove(id);
	}

	/** The mission {@code from} is sending, or null. */
	static @Nullable Mission outgoing(MinecraftServer server, int from) {
		for (Mission m : book(server).missions().values()) {
			if (m.from() == from) {
				return m;
			}
		}
		return null;
	}

	/** As many envoys are on the roads as there may be. */
	static boolean full(MinecraftServer server) {
		return book(server).missions().size() >= MISSIONS_MOST;
	}

	/** An envoy on the road between {@code a} and {@code b} (either way), or null. */
	static @Nullable Mission between(MinecraftServer server, int a, int b) {
		for (Mission m : book(server).missions().values()) {
			if (m.from() == a && m.to() == b || m.from() == b && m.to() == a) {
				return m;
			}
		}
		return null;
	}

	private static boolean awaiting(MinecraftServer server, int to) {
		for (Mission m : book(server).missions().values()) {
			if (m.to() == to) {
				return true;
			}
		}
		return false;
	}

	// ---- the court's mind -----------------------------------------------------------------------------------------------

	/** The terms of {@code judge}'s score for {@code proposer}'s offer: base, tension, creed, temper, standing, unrecognised. */
	private static int[] terms(MinecraftServer server, Faction judge, Faction proposer, Kind kind) {
		int k = kind.ordinal();
		long now = Factions.level(server).getGameTime();
		int friction = GreatWars.friction(server, judge, proposer);
		int tension = -Math.round(GreatWars.tension(server, judge.id(), proposer.id()) * TENSION_WEIGHT);
		int creed = -friction * FRICTION_WEIGHT + CREED_BIAS[GreatWars.creed(server, judge).ordinal()][k] + (friction == 0 ? KINDRED_BONUS : 0);
		Tempers.Temper t = Tempers.of(judge);
		int temper = t == null ? 0 : TEMPER_BIAS[t.ordinal()][k];
		int standing = 0;
		if (kind == Kind.AID) {
			standing += commonFoe(server, judge, proposer) ? COMMON_FOE_BONUS : -NO_FOE_PENALTY;
		}
		if (kind == Kind.CHARTER && Wealth.worth(server, judge) >= GreatWars.TRADE_WORTH && Wealth.worth(server, proposer) >= GreatWars.TRADE_WORTH) {
			standing += RICH_TRADE_BONUS;
		}
		if (WarData.truce(server, judge.id(), proposer.id(), now)) {
			standing -= TRUCE_PENALTY;
		}
		if (Purists.is(judge)) {
			Heritage.Kind mine = Purists.blood(judge), theirs = Purists.blood(proposer);
			temper += mine != null && mine == theirs ? PURIST_KIN : -PURIST_STRANGER;
		}
		UUID sov = Crowns.sovereign(server, proposer);
		ServerPlayer player = sov == null ? null : server.getPlayerList().getPlayer(sov);
		if (player != null) {
			standing += Math.max(-REP_MOST, Math.min(REP_MOST, Reputation.get(player, judge.id()) / REP_PER_POINT));
		}
		int unrecognised = judge.relation(proposer.id()) == Relation.RIVAL ? -RIVAL_PENALTY : 0;
		return new int[] {KIND_BASE[k], tension, creed, temper, standing, unrecognised};
	}

	/** {@code judge}'s mind about {@code proposer}'s offer of {@code kind}: the higher the likelier to sign. */
	static int score(MinecraftServer server, Faction judge, Faction proposer, Kind kind) {
		int sum = 0;
		for (int t : terms(server, judge, proposer, kind)) {
			sum += t;
		}
		return sum;
	}

	/** The lang key of the biggest drag on the score, for a refusal. */
	static String reason(MinecraftServer server, Faction judge, Faction proposer, Kind kind) {
		int[] t = terms(server, judge, proposer, kind);
		String[] names = {"", "tension", "creed", "temper", "standing", "unrecognised"};
		int worst = 0, at = 0;
		for (int i = 1; i <= REASONS; i++) {
			if (t[i] < worst) {
				worst = t[i];
				at = i;
			}
		}
		return "message.jaymoskingdoms.envoy.reason." + (at == 0 ? "none" : names[at]);
	}

	private static boolean commonFoe(MinecraftServer server, Faction a, Faction b) {
		for (Faction h : Wars.heads(server)) {
			if (h.id() != a.id() && h.id() != b.id() && foeOf(server, a, h) && foeOf(server, b, h)) {
				return true;
			}
		}
		return false;
	}

	private static boolean foeOf(MinecraftServer server, Faction x, Faction h) {
		Relation r = x.relation(h.id());
		return r == Relation.RIVAL || r == Relation.WAR || GreatWars.tension(server, x.id(), h.id()) >= GreatWars.RIVAL_AT;
	}

	/** {@code kind} is open between these two: not at war, not in force with more than a renewal's time left, and recognised first if that is needed. */
	static boolean open(MinecraftServer server, Faction judge, Faction proposer, Kind kind) {
		if (Wars.between(server, judge.id(), proposer.id()) != null) {
			return false;
		}
		Treaties.Treaty held = Treaties.find(server, judge, proposer, kind);
		if (held != null && held.until() - Factions.level(server).getGameTime() > Treaties.RENEW_TICKS) {
			return false;
		}
		return !(Treaties.NEEDS_RECOGNITION && kind != Kind.RECOGNITION && !Treaties.has(server, judge, proposer, Kind.RECOGNITION));
	}

	/** How {@code judge} would take {@code proposer}'s offer: 2 likely, 1 maybe, 0 unlikely, 3 its sovereign decides, -1 closed. */
	static int odds(MinecraftServer server, Faction judge, Faction proposer, Kind kind) {
		if (!open(server, judge, proposer, kind)) {
			return -1;
		}
		if (Crowns.sovereign(server, judge) != null) {
			return 3;
		}
		int s = score(server, judge, proposer, kind);
		return s >= ACCEPT_AT + ODDS_BAND ? 2 : s >= ACCEPT_AT - ODDS_BAND ? 1 : 0;
	}

	// ---- sending -------------------------------------------------------------------------------------------------------

	private static BlockPos hall(MinecraftServer server, Faction f) {
		Castle.Keep keep = Factions.keep(server, f.id());
		return keep != null ? keep.hall() : f.seat();
	}

	/** Once a day each NPC king looks at the realms near enough and may send an envoy. */
	private static void daily(MinecraftServer server, ServerLevel level) {
		long day = Production.day(server);
		Book b = book(server);
		if (b.lastDay() == day) {
			return;
		}
		Factions.level(server).setAttached(MISSIONS, new Book(b.missions(), b.nextId(), day));
		List<Faction> heads = Wars.heads(server);
		for (Faction from : heads) {
			Tempers.Temper temper = Tempers.of(from);
			if (temper == null || Crowns.sovereign(server, from) != null) {
				continue;
			}
			for (Faction to : heads) {
				if (to.id() == from.id() || Raids.horizontal(from.seat(), to.seat()) > RANGE * RANGE) {
					continue;
				}
				if (book(server).missions().size() >= MISSIONS_MOST || outgoing(server, from.id()) != null) {
					break;
				}
				boolean player = Crowns.sovereign(server, to) != null;
				if (Wars.between(server, from.id(), to.id()) != null || between(server, from.id(), to.id()) != null || player && awaiting(server, to.id())) {
					continue;
				}
				Kind pick = null;
				for (int o : ORDER[GreatWars.creed(server, from).ordinal()]) {
					Kind kind = Kind.values()[o];
					if (open(server, to, from, kind)) {
						pick = kind;
						break;
					}
				}
				if (pick == null || score(server, from, to, pick) < ACCEPT_AT - PROPOSE_SLACK
					|| level.getRandom().nextFloat() >= (player ? PLAYER_PROPOSE_CHANCE : PROPOSE_CHANCE)) {
					continue;
				}
				dispatch(server, from, to, pick, Treaties.find(server, from, to, pick) != null, null);
			}
		}
	}

	/** {@code from} sends an envoy to {@code to} with {@code kind}; null if it can't now (too many on the road, or one already). */
	static @Nullable Mission dispatch(MinecraftServer server, Faction from, Faction to, Kind kind, boolean renew, @Nullable UUID sender) {
		if (book(server).missions().size() >= MISSIONS_MOST || outgoing(server, from.id()) != null || between(server, from.id(), to.id()) != null) {
			return null;
		}
		Book b = book(server);
		BlockPos home = hall(server, from), goal = hall(server, to);
		long now = Factions.level(server).getGameTime();
		Mission m = new Mission(b.nextId(), from.id(), to.id(), kind.key(), renew, TO, home, goal, home, now, 0L, Optional.empty(),
			Math.hypot(home.getX() - goal.getX(), home.getZ() - goal.getZ()) > Couriers.RIDE_FROM, new CompoundTag(), Optional.ofNullable(sender));
		Map<String, Mission> map = new HashMap<>(b.missions());
		map.put(Integer.toString(m.id()), m);
		Factions.level(server).setAttached(MISSIONS, new Book(Map.copyOf(map), b.nextId() + 1, b.lastDay()));
		JaymosKingdoms.LOGGER.info("{} sends an envoy to {} for {}", from.name(), to.name(), kind.key());
		return m;
	}

	// ---- in and out of the world ----------------------------------------------------------------------------------------

	private static @Nullable Villager entity(ServerLevel level, Mission m) {
		if (m.envoy().isPresent() && level.getEntity(m.envoy().get()) instanceof Villager v && !v.isRemoved() && v.isAlive() && v.hasAttached(ENVOY)) {
			return v;
		}
		return null;
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof Villager v && v.hasAttached(ENVOY)) {
			Mission m = mission(level.getServer(), v.getAttachedOrElse(ENVOY, -1));
			if (m == null || m.out() || !m.envoy().map(v.getUUID()::equals).orElse(false)) {
				// (a copy left behind, or from a mission long over)
				STALE.add(v);
			}
		}
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof Villager v) || !v.hasAttached(ENVOY) || TAKING.contains(v.getUUID())) {
			return;
		}
		GOING_HOME.remove(v);
		Mission m = mission(level.getServer(), v.getAttachedOrElse(ENVOY, -1));
		if (m == null || m.out() || !m.envoy().map(v.getUUID()::equals).orElse(false)) {
			return;
		}
		if (v.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			// (their chunk went with them: on in the record)
			CompoundTag body = save(level, v);
			if (body != null) {
				put(level.getServer(), m.at(v.blockPosition()).body(body));
			}
		}
	}

	private static @Nullable CompoundTag save(ServerLevel level, Villager envoy) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		return envoy.saveAsPassenger(out) ? out.buildResult() : null;
	}

	private static void dismount(Villager v) {
		Entity horse = v.getVehicle();
		v.stopRiding();
		if (horse != null && horse.entityTags().contains(Couriers.HORSE_TAG)) {
			STALE.add(horse);
		}
	}

	/** Takes {@code envoy} (and their horse) out of the world into the record. */
	private static void takeOut(ServerLevel level, Villager envoy, Mission m) {
		Entity horse = envoy.getVehicle();
		envoy.stopRiding();
		if (horse != null && horse.entityTags().contains(Couriers.HORSE_TAG)) {
			horse.discard();
		}
		CompoundTag body = save(level, envoy);
		if (body == null) {
			return;
		}
		put(level.getServer(), m.at(envoy.blockPosition()).body(body));
		TAKING.add(envoy.getUUID());
		PROGRESS.remove(m.id());
		envoy.discard();
		TAKING.remove(envoy.getUUID());
	}

	/** The envoy of {@code m} in the world at {@code at}: put back from the record, or made new. Null if it can't be now. */
	private static @Nullable Villager appear(ServerLevel level, Mission m, BlockPos at) {
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		// (0.7.519) the hall is under the keep's roof, so a waiting envoy stands in it, not on the heightmap top
		BlockPos ground = m.leg() == WAIT ? Construction.standable(level, at, 3) : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		MinecraftServer server = level.getServer();
		Faction from = Factions.get(server, m.from());
		if (from == null) {
			return null;
		}
		Villager v;
		if (m.out()) {
			Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), m.body()), level,
				EntitySpawnReason.LOAD, c -> {
					c.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, c.getYRot(), c.getXRot());
					return c;
				});
			if (!(loaded instanceof Villager made)) {
				return null;
			}
			// (the record first: the load event sees it back in the world)
			put(server, m.at(ground).body(new CompoundTag()).envoy(Optional.of(made.getUUID())));
			if (!level.addFreshEntity(made)) {
				put(server, m);
				return null;
			}
			v = made;
		} else {
			v = McaCompat.LOADED ? McaCompat.envoy(level, from, ground) : null;
			if (v == null && !McaCompat.LOADED) {
				v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (v == null) {
					return null;
				}
				v.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.0F, 0.0F);
				if (!level.addFreshEntity(v)) {
					return null;
				}
			}
			if (v == null) {
				return null;
			}
			v.setAttached(ENVOY, m.id());
			v.setAttached(Introductions.TITLE, Component.translatable("title.jaymoskingdoms.envoy", from.name()));
			v.setItemSlot(EquipmentSlot.OFFHAND, Outfits.banner(level, from));
			v.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
			v.setPersistenceRequired();
		}
		put(server, m.at(ground).body(new CompoundTag()).envoy(Optional.of(v.getUUID())));
		if (m.leg() == WAIT) {
			v.setHomeTo(m.goal(), (int)HALL_RADIUS);
		}
		return v;
	}

	// ---- on the way ------------------------------------------------------------------------------------------------------

	private static boolean near(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Math.hypot(p.getX() - at.x, p.getZ() - at.z) < range) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (Entity stale : new ArrayList<>(STALE)) {
			if (!stale.isRemoved()) {
				TAKING.add(stale.getUUID());
				stale.discard();
				TAKING.remove(stale.getUUID());
			}
		}
		STALE.clear();
		for (Villager v : new ArrayList<>(GOING_HOME)) {
			if (v.isRemoved() || !v.isAlive()) {
				GOING_HOME.remove(v);
			} else if (!near(level, v.position(), HOME_GONE)) {
				dismount(v);
				GOING_HOME.remove(v);
				TAKING.add(v.getUUID());
				v.discard();
				TAKING.remove(v.getUUID());
			}
		}
		if (server.getTickCount() % CHECK_TICKS == 0) {
			daily(server, level);
		}
		for (Mission m : List.copyOf(book(server).missions().values())) {
			run(level, m);
		}
	}

	/** One look at {@code m}. */
	private static void run(ServerLevel level, Mission m) {
		MinecraftServer server = level.getServer();
		Faction from = Factions.get(server, m.from()), to = Factions.get(server, m.to());
		Treaties.Kind kind = m.kindOf();
		if (from == null || to == null || kind == null || from.fallen() || to.fallen()) {
			cancel(level, m);
			return;
		}
		Villager v = entity(level, m);
		if (v != null && (Downed.isDowned(v) || v.isSleeping())) {
			return;
		}
		BlockPos target = m.leg() == BACK ? m.home() : m.goal();
		Vec3 aim = Vec3.atBottomCenterOf(target);
		Vec3 here = v != null ? v.position() : Vec3.atBottomCenterOf(m.at());
		double d = Math.hypot(here.x - aim.x, here.z - aim.z);
		long now = level.getGameTime();
		if (m.leg() == WAIT) {
			waiting(level, m, v, from, to, kind, d, now);
			return;
		}
		if (d <= HOME) {
			if (m.leg() == BACK) {
				home(level, m, v);
			} else {
				arrived(level, m, v, from, to);
			}
			return;
		}
		if (v == null) {
			// (in the record: in the world if someone's near, else on along the road)
			if (near(level, here, Couriers.APPEAR) && level.isPositionEntityTicking(m.at())) {
				appear(level, m, m.at());
				return;
			}
			double step = (m.rides() ? Couriers.RIDE_SPEED : Couriers.WALK_SPEED) * EVERY;
			Vec3 ahead = Roads.ahead(level, here, target, step);
			Vec3 next = ahead != null ? ahead : here.add(aim.subtract(here).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, step / Math.max(d, 1.0E-3))));
			put(server, m.at(BlockPos.containing(next)));
			return;
		}
		if (d > Couriers.LOCAL && !near(level, here, Couriers.LEAVE)) {
			takeOut(level, v, m);
			return;
		}
		if (m.rides() && d > Couriers.LOCAL && !v.isPassenger() && !v.isInWater()) {
			Couriers.mount(level, v);
		}
		double[] best = PROGRESS.computeIfAbsent(m.id(), id -> new double[] {Double.MAX_VALUE, now});
		if (d < best[0] - 1.0) {
			best[0] = d;
			best[1] = now;
		}
		if (now - (long)best[1] > Couriers.STUCK_TICKS) {
			Vec3 hop = here.add(aim.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(Couriers.HOP));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
			Entity mover = v.getVehicle() != null ? v.getVehicle() : v;
			mover.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			best[1] = now;
			return;
		}
		if (now % 20 == 0 || v.getNavigation().isDone()) {
			walk(level, v, target, aim, d);
		}
	}

	/** Toward {@code target}: straight to it when near, else a stage of the way, along a King's Road where one runs. */
	private static void walk(ServerLevel level, Villager v, BlockPos target, Vec3 aim, double d) {
		float pace = v.isPassenger() ? Couriers.RIDE_PACE : Couriers.PACE;
		if (d <= Couriers.STAGE) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, Construction.standable(level, target, 3), pace, 1);
			return;
		}
		Vec3 here = v.position();
		Vec3 ahead = Roads.ahead(level, here, target, Couriers.STAGE);
		Vec3 next = ahead != null ? ahead : here.add(aim.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(Couriers.STAGE));
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next));
		BehaviorUtils.setWalkAndLookTargetMemories(v, Construction.standable(level, at, 3), pace, 1);
	}

	/** The envoy of {@code m} has got to the far court: a player's hall holds them for the sovereign, an NPC's hears them out. */
	private static void arrived(ServerLevel level, Mission m, @Nullable Villager v, Faction from, Faction to) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		UUID sov = Crowns.sovereign(server, to);
		Treaties.Kind kind = m.kindOf();
		put(server, m.leg(WAIT, now + (sov != null ? WAIT_TICKS : THINK_TICKS)).at(m.goal()));
		PROGRESS.remove(m.id());
		if (v != null) {
			dismount(v);
			v.setHomeTo(m.goal(), (int)HALL_RADIUS);
			if (sov != null) {
				Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.envoy.arrive." + level.getRandom().nextInt(ARRIVE_LINES), from.name()), Speech.GOLD,
					SPEECH_RANGE);
			}
		}
		if (sov != null && kind != null) {
			Post.report(sov, Component.translatable("message.jaymoskingdoms.envoy.waiting", from.name(), to.name(), kind.named(), (int)(WAIT_TICKS / Treaties.DAY_TICKS)));
		}
	}

	/** At the far court: waiting to be heard (or to give up). */
	private static void waiting(ServerLevel level, Mission m, @Nullable Villager v, Faction from, Faction to, Kind kind, double d, long now) {
		MinecraftServer server = level.getServer();
		if (v == null && near(level, Vec3.atBottomCenterOf(m.at()), Couriers.APPEAR) && level.isPositionEntityTicking(m.at())) {
			// (a player is near: the envoy is there in the hall)
			if (appear(level, m, m.goal()) != null) {
				return;
			}
		}
		if (v != null && !near(level, v.position(), Couriers.LEAVE)) {
			takeOut(level, v, m);
			v = null;
			Mission cur = mission(server, m.id());
			if (cur != null) {
				m = cur;
			}
		} else if (v != null && d > HALL_RADIUS * 2 && now % 20 == 0) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, Construction.standable(level, m.goal(), 3), Couriers.PACE, 1);
		}
		if (now < m.until()) {
			return;
		}
		UUID sov = Crowns.sovereign(server, to);
		if (sov != null) {
			// (nobody heard them: they go home, with no penalty)
			Mission next = putIt(server, m.leg(BACK, now));
			if (v != null) {
				Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.envoy.leave", from.name()), Speech.GOLD, SPEECH_RANGE);
			}
			Post.report(sov, Component.translatable("message.jaymoskingdoms.envoy.unanswered", from.name()));
			HEARING.values().remove(next.id());
			return;
		}
		decide(level, m, v, from, to, kind);
	}

	/** An NPC court's answer. */
	private static void decide(ServerLevel level, Mission m, @Nullable Villager v, Faction from, Faction to, Kind kind) {
		MinecraftServer server = level.getServer();
		int luck = level.getRandom().nextInt(2 * ACCEPT_LUCK + 1) - ACCEPT_LUCK;
		boolean sign = open(server, to, from, kind) && score(server, to, from, kind) + luck >= ACCEPT_AT;
		answer(level, m, v, from, to, kind, sign, sign ? "" : reason(server, to, from, kind));
	}

	/** The mission is answered: signed or refused, and the envoy turns for home. */
	private static void answer(ServerLevel level, Mission m, @Nullable Villager v, Faction from, Faction to, Kind kind, boolean sign, String reason) {
		MinecraftServer server = level.getServer();
		if (sign) {
			Treaties.sign(server, from, to, kind, from.id());
		}
		putIt(server, m.leg(BACK, level.getGameTime()).at(m.goal()));
		HEARING.values().remove(m.id());
		if (v != null) {
			Speech.say(level, v, Component.translatable(sign ? "speech.jaymoskingdoms.envoy.signed" : "speech.jaymoskingdoms.envoy.refused", from.name()), Speech.GOLD,
				SPEECH_RANGE);
			v.setHomeTo(m.home(), 64);
		}
		if (m.sender().isPresent()) {
			Post.report(m.sender().get(), sign ? Component.translatable("message.jaymoskingdoms.envoy.sent_signed", to.name(), kind.named())
				: Component.translatable("message.jaymoskingdoms.envoy.sent_refused", to.name(), kind.named(),
					Component.translatable(reason.isEmpty() ? "message.jaymoskingdoms.envoy.reason.none" : reason)));
		}
	}

	/** Back home: the mission is over, and the envoy goes in once nobody is near. */
	private static void home(ServerLevel level, Mission m, @Nullable Villager v) {
		drop(level.getServer(), m.id());
		if (v != null) {
			dismount(v);
			v.getNavigation().stop();
			GOING_HOME.add(v);
		}
	}

	/** The sender or the target is gone: the mission ends, and the envoy goes in once nobody is near. */
	private static void cancel(ServerLevel level, Mission m) {
		Villager v = entity(level, m);
		drop(level.getServer(), m.id());
		HEARING.values().remove(m.id());
		if (v != null) {
			GOING_HOME.add(v);
		}
	}

	// ---- being heard -------------------------------------------------------------------------------------------------

	/** {@code p}, sovereign of the realm the envoy {@code v} waits in, may hear them. */
	static boolean hearable(ServerPlayer p, LivingEntity v) {
		Integer id = v.getAttached(ENVOY);
		if (id == null) {
			return false;
		}
		Mission m = mission(p.level().getServer(), id);
		return m != null && m.leg() == WAIT && Crowns.rules(p.level().getServer(), Factions.get(p.level().getServer(), m.to()), p.getUUID())
			&& v.distanceTo(p) <= HEAR_RANGE * 2.0F;
	}

	/** {@code p} hears the envoy {@code v}: the offer comes up on their screen. */
	static void hear(ServerPlayer p, LivingEntity v) {
		MinecraftServer server = p.level().getServer();
		Integer id = v.getAttached(ENVOY);
		Mission m = id == null ? null : mission(server, id);
		Faction from = m == null ? null : Factions.get(server, m.from()), to = m == null ? null : Factions.get(server, m.to());
		Kind kind = m == null ? null : m.kindOf();
		if (m == null || from == null || to == null || kind == null || !hearable(p, v)
			|| !ServerPlayNetworking.canSend(p, DiplomacyNet.EnvoyOffer.TYPE)) {
			return;
		}
		Tempers.Temper t = Tempers.of(from);
		DiplomacyNet.EnvoyOffer offer = new DiplomacyNet.EnvoyOffer(v.getId(), m.id(), from.name(), v.getDisplayName().getString(), kind.key(), Treaties.DAYS[kind.ordinal()],
			m.renew(), GreatWars.creed(server, from).key(), t == null ? "" : t.key(), to.relation(from.id()).id(),
			Math.round(GreatWars.tension(server, from.id(), to.id())), Crowns.sovereign(server, from) != null);
		HEARING.put(p.getUUID(), m.id());
		ServerPlayNetworking.send(p, offer);
	}

	/** {@code p}'s answer to the offer they heard. */
	static void answered(ServerPlayer p, DiplomacyNet.EnvoyAnswer answer) {
		MinecraftServer server = p.level().getServer();
		Integer hearing = HEARING.get(p.getUUID());
		if (hearing == null || hearing != answer.mission()) {
			return;
		}
		HEARING.remove(p.getUUID());
		Mission m = mission(server, answer.mission());
		if (m == null || m.leg() != WAIT || answer.choice() == DiplomacyNet.NOT_YET) {
			return;
		}
		Faction from = Factions.get(server, m.from()), to = Factions.get(server, m.to());
		Kind kind = m.kindOf();
		ServerLevel level = Factions.level(server);
		Villager v = entity(level, m);
		if (from == null || to == null || kind == null || !Crowns.rules(server, to, p.getUUID()) || v == null || v.distanceTo(p) > HEAR_RANGE) {
			return;
		}
		boolean sign = answer.choice() == DiplomacyNet.SIGN && open(server, to, from, kind);
		answer(level, m, v, from, to, kind, sign, sign ? "" : "message.jaymoskingdoms.envoy.reason.none");
	}

	// ---- killed on the road --------------------------------------------------------------------------------------------

	private static void slain(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Villager v) || !(v.level() instanceof ServerLevel level) || !v.hasAttached(ENVOY)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Mission m = mission(server, v.getAttachedOrElse(ENVOY, -1));
		if (m == null) {
			return;
		}
		drop(server, m.id());
		HEARING.values().remove(m.id());
		Faction from = Factions.get(server, m.from()), to = Factions.get(server, m.to());
		if (from == null) {
			return;
		}
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.envoy.slain", from.name()).withStyle(ChatFormatting.RED));
		Faction land = Banners.owner(server, ChunkPos.containing(v.blockPosition()));
		if (source.getEntity() instanceof ServerPlayer killer) {
			Reputation.change(killer, from, REP_KILL_ENVOY);
			if (land != null && land.type().isRealm() && land.id() != from.id()) {
				Reputation.change(killer, land, REP_KILL_GUEST);
			}
		}
		if (to != null && land != null && Treaties.head(server, land).id() == Treaties.head(server, to).id()) {
			GreatWars.setTension(server, from.id(), to.id(), GreatWars.tension(server, from.id(), to.id()) + KILL_TENSION);
		}
	}
}
