package com.jaymoskingdoms;

import com.jaymoskingdoms.nightfall.Nightfall;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.530) What is happening in a town, as work for its folk. While a town is hungry, sick, hurt, raided, burned, sacked, in
 * drought, missing one of its own or feuding with a neighbour, two to four of its villagers wear the gold mark and ask for
 * help with that trouble ({@link Quests#offerNow}, as {@link DisasterRelief} does for a disaster). Players near the town hear
 * when it starts and ends; when it ends the asks not yet taken are taken back. Food, timber and iron brought for a trouble
 * go into the town's stores, so a famine can really end.
 */
public final class TownTroubles {
	public static final String FAMINE = "famine", PLAGUE = "plague", WOUNDS = "wounds", RAID = "raid", FIRE = "fire", WAR = "war",
		DROUGHT = "drought", MISSING = "missing", FEUD = "feud";
	/** When a town has more than {@link #MOST_AT_ONCE}, these come first. */
	static final List<String> ORDER = List.of(WAR, RAID, FIRE, MISSING, FAMINE, PLAGUE, WOUNDS, DROUGHT, FEUD);

	/** How often the towns are looked at (ticks); a town needs a player this near its seat to be looked at; how far its folk are asked; how far players hear of it. */
	static final int CHECK_TICKS = 600;
	static final double PLAYER_NEAR = 160.0, ASK_RANGE = 48.0, LOCAL_NEWS = 300.0;
	/** How many villagers ask (least, most), and how many troubles a town has at once. */
	static final int ASKERS_MIN = 2, ASKERS_MAX = 4, MOST_AT_ONCE = 2;
	/** A famine: food in store below this, with at least this many villagers. */
	static final int FAMINE_FOOD = 16, FAMINE_FOLK = 6;
	/** Plague: this many sick villagers; wounds: this many hurt. */
	static final int PLAGUE_SICK = 2, WOUNDED_AT = 3;
	/** A fire: this many fires lit inside this many ticks. */
	static final int FIRE_BLAZES = 10, FIRE_WINDOW = 2400;
	/** How many days an event's trouble lasts, and how many dry days make a drought. */
	static final int RAID_DAYS = 2, FIRE_DAYS = 2, WAR_DAYS = 3, MISSING_DAYS = 6, DROUGHT_DAYS = 5;
	/** A feud: a neighbour this near; a camp this near a captive's cell is the one that holds them. */
	static final double FEUD_RANGE = 800.0, MISSING_CAMP_RANGE = 160.0;
	/** What a trouble's quest pays (emeralds' worth: least, spread, more for a kill), and the special ones. */
	static final int TROUBLE_EMERALDS = 5, TROUBLE_SPREAD = 4, TROUBLE_SLAY_MORE = 3;
	static final int TROUBLE_CHIEF_EMERALDS = 14, TROUBLE_RESCUE_EMERALDS = 12, TROUBLE_LETTER_EMERALDS = 9;
	static final int TROUBLE_HEARTS = 6, TROUBLE_REPUTATION = 25, TROUBLE_STANDING = 10;
	static final Set<Climate> DROUGHT_LANDS = EnumSet.of(Climate.PLAINS, Climate.SAVANNA, Climate.DESERT, Climate.BADLANDS);
	/** The quests whose goods go into the town's stores (a prefix ends with a dot). */
	private static final List<String> STOCKED = List.of("trouble.famine.", "trouble.raid.stores", "trouble.war.bread", "trouble.war.iron", "trouble.fire.timber");

	/**
	 * A trouble in a town: its kind, the day it began, the day it's over (-1: while its condition holds), what it's about (a camp,
	 * a captive, a rival), who was asked, and the day they were last asked.
	 */
	record Trouble(String kind, long since, long until, String data, List<UUID> askers, long asked) {
		static final Codec<Trouble> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Trouble::kind),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Trouble::since),
				Codec.LONG.optionalFieldOf("until", -1L).forGetter(Trouble::until),
				Codec.STRING.optionalFieldOf("data", "").forGetter(Trouble::data),
				UUIDUtil.CODEC.listOf().optionalFieldOf("askers", List.of()).forGetter(Trouble::askers),
				Codec.LONG.optionalFieldOf("asked", -1L).forGetter(Trouble::asked))
			.apply(i, Trouble::new));

		Trouble withAsked(List<UUID> askers, long asked) {
			return new Trouble(kind, since, until, data, List.copyOf(askers), asked);
		}
	}

	/** "faction id|kind" → the trouble. On the Overworld. */
	static final AttachmentType<Map<String, Trouble>> TROUBLES = AttachmentRegistry.create(JaymosKingdoms.id("town_troubles"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Trouble.CODEC)));
	/** The last game day it rained (for drought). On the Overworld. */
	static final AttachmentType<Long> DRY = AttachmentRegistry.create(JaymosKingdoms.id("town_dry"), builder -> builder.persistent(Codec.LONG));

	/** Town id → {fires counted, window start (game time)}. Not saved: a restart forgets a half-counted fire. */
	private static final Map<Integer, int[]> FIRES = new HashMap<>();
	private static final Map<Integer, Long> FIRE_SINCE = new HashMap<>();

	/** One quest a trouble can ask: the trade whose folk it suits ("" for any), and how it's made for a giver. */
	private record Ask(String job, Function<Quest.Giver, Quest> make) {
	}

	private TownTroubles() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				check(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIRES.clear();
			FIRE_SINCE.clear();
		});
	}

	// ---- state -----------------------------------------------------------------------------------------------------------

	private static Map<String, Trouble> all(ServerLevel level) {
		return level.getAttachedOrElse(TROUBLES, Map.<String, Trouble>of());
	}

	private static void save(ServerLevel level, Map<String, Trouble> map) {
		if (map.isEmpty()) {
			level.removeAttached(TROUBLES);
		} else {
			level.setAttached(TROUBLES, Map.copyOf(map));
		}
	}

	private static String key(int faction, String kind) {
		return faction + "|" + kind;
	}

	private static int factionOf(String key) {
		try {
			return Integer.parseInt(key.substring(0, key.indexOf('|')));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Whether {@code f} has trouble {@code kind} just now. */
	static boolean has(MinecraftServer server, int faction, String kind) {
		return all(Factions.level(server)).containsKey(key(faction, kind));
	}

	private static int count(Map<String, Trouble> map, int faction) {
		String prefix = faction + "|";
		return (int)map.keySet().stream().filter(k -> k.startsWith(prefix)).count();
	}

	private static boolean standing(Faction f) {
		return !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	// ---- the check -------------------------------------------------------------------------------------------------------

	private static void check(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long today = Quests.day(level);
		if (level.isRaining() || level.getAttached(DRY) == null) {
			level.setAttached(DRY, today);
		}
		Map<String, Trouble> map = new HashMap<>(all(level));
		// (towns that have fallen, and event troubles past their day)
		for (Map.Entry<String, Trouble> e : List.copyOf(map.entrySet())) {
			int id = factionOf(e.getKey());
			Faction f = Factions.get(server, id);
			Trouble t = e.getValue();
			if (f == null || !standing(f)) {
				map.remove(e.getKey());
				finish(level, null, t);
			} else if (t.until() >= 0 && today >= t.until()) {
				map.remove(e.getKey());
				finish(level, f, t);
			}
		}
		save(level, map);
		for (Faction f : Factions.all(server)) {
			if (!standing(f) || !level.isPositionEntityTicking(f.seat()) || !playerNear(level, f)) {
				continue;
			}
			conditions(level, f, today);
			for (String kind : ORDER) {
				Trouble t = all(level).get(key(f.id(), kind));
				if (t != null && t.asked() < today && (!kind.equals(RAID) || !Raids.raided(f.id()))) {
					ask(level, f, t);
				}
			}
		}
	}

	private static boolean playerNear(ServerLevel level, Faction f) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(f.seat(), PLAYER_NEAR)) {
				return true;
			}
		}
		return false;
	}

	/** Starts the troubles that now hold in {@code f} (most urgent first, up to {@link #MOST_AT_ONCE}), and ends the ones that no longer do. */
	private static void conditions(ServerLevel level, Faction f, long today) {
		MinecraftServer server = level.getServer();
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE), v -> v.isAlive() && !v.isBaby());
		for (String kind : ORDER) {
			Trouble t = all(level).get(key(f.id(), kind));
			if (t != null && t.until() < 0) {
				if (!holds(level, f, kind, folk, today, t)) {
					end(level, f, t);
				}
			}
		}
		for (String kind : ORDER) {
			if (all(level).containsKey(key(f.id(), kind)) || count(all(level), f.id()) >= MOST_AT_ONCE) {
				continue;
			}
			String data = starts(level, f, kind, folk, today);
			if (data != null) {
				start(level, f, kind, -1L, data);
			}
		}
	}

	/** Whether a condition trouble still holds. */
	private static boolean holds(ServerLevel level, Faction f, String kind, List<Villager> folk, long today, Trouble t) {
		MinecraftServer server = level.getServer();
		return switch (kind) {
			case FAMINE -> !Storage.loaded(level, f) || Storage.food(level, f) < TownProjects.foodReserve(server, f);
			case PLAGUE -> sick(folk) > 0;
			case WOUNDS -> hurt(folk) >= 1;
			case DROUGHT -> !level.isRaining();
			case FEUD -> feudWith(server, f) != null;
			default -> true;
		};
	}

	/** Whether a condition trouble starts now: its data, or null. */
	private static @Nullable String starts(ServerLevel level, Faction f, String kind, List<Villager> folk, long today) {
		MinecraftServer server = level.getServer();
		switch (kind) {
			case FAMINE -> {
				float scale = TownProjects.foodReserve(server, f) / (float)Economy.FOOD_RESERVE;
				return Storage.loaded(level, f) && folk.size() >= FAMINE_FOLK && Storage.food(level, f) < Math.round(FAMINE_FOOD * scale) ? "" : null;
			}
			case PLAGUE -> {
				return sick(folk) >= PLAGUE_SICK + TownProjects.sickMore(server, f) ? "" : null;
			}
			case WOUNDS -> {
				return hurt(folk) >= WOUNDED_AT + TownProjects.woundedMore(server, f) ? "" : null;
			}
			case DROUGHT -> {
				long dry = level.getAttachedOrElse(DRY, today);
				return !level.isRaining() && today - dry >= DROUGHT_DAYS && DROUGHT_LANDS.contains(Climate.at(level, f.seat()))
					&& !TownProjects.done(server, f.id(), "well") ? "" : null;
			}
			case FEUD -> {
				Faction other = feudWith(server, f);
				return other == null ? null : Integer.toString(other.id());
			}
			default -> {
				return null;
			}
		}
	}

	private static int sick(List<Villager> folk) {
		int n = 0;
		for (Villager v : folk) {
			if (v.hasEffect(Nightfall.ATROPHY)) {
				n++;
			}
		}
		return n;
	}

	private static int hurt(List<Villager> folk) {
		int n = 0;
		for (Villager v : folk) {
			if (PowersCompat.injuries(v).any()) {
				n++;
			}
		}
		return n;
	}

	/** The nearest standing neighbour of {@code f} that is its rival or at war with it, or null. */
	private static @Nullable Faction feudWith(MinecraftServer server, Faction f) {
		Faction best = null;
		double bestD = FEUD_RANGE * FEUD_RANGE;
		for (Faction other : Factions.all(server)) {
			if (other.id() == f.id() || !standing(other) || other.leader().isEmpty()) {
				continue;
			}
			double d = other.seat().distSqr(f.seat());
			Relation r = Relations.between(f, other);
			if (d <= bestD && (r == Relation.RIVAL || r == Relation.WAR)) {
				best = other;
				bestD = d;
			}
		}
		return best;
	}

	// ---- starting and ending ---------------------------------------------------------------------------------------------

	private static void start(ServerLevel level, Faction f, String kind, long until, String data) {
		Map<String, Trouble> map = new HashMap<>(all(level));
		map.put(key(f.id(), kind), new Trouble(kind, Quests.day(level), until, data, List.of(), -1L));
		save(level, map);
		news(level, f, Component.translatable("trouble.jaymoskingdoms." + kind + ".start", f.name()).withStyle(ChatFormatting.GOLD));
		JaymosKingdoms.LOGGER.debug("trouble {} in {}", kind, f.name());
	}

	private static void end(ServerLevel level, Faction f, Trouble t) {
		remove(level, f, t, false);
	}

	/** Removes a trouble. {@code quiet} is for one pushed out to make room: its cause is still there, so no "over" news. */
	private static void remove(ServerLevel level, Faction f, Trouble t, boolean quiet) {
		Map<String, Trouble> map = new HashMap<>(all(level));
		map.remove(key(f.id(), t.kind()));
		save(level, map);
		finish(level, quiet ? null : f, t);
	}

	/** The news and the taking back, after a trouble has been removed. {@code f} is null for a town that has fallen. */
	private static void finish(ServerLevel level, @Nullable Faction f, Trouble t) {
		if (f != null) {
			news(level, f, Component.translatable("trouble.jaymoskingdoms." + t.kind() + ".end", f.name()).withStyle(ChatFormatting.GREEN));
		}
		for (UUID id : t.askers()) {
			if (level.getEntity(id) instanceof LivingEntity v) {
				Quests.withdraw(v, "trouble." + t.kind() + ".");
			}
		}
	}

	private static void news(ServerLevel level, Faction f, Component message) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(f.seat(), LOCAL_NEWS)) {
				p.sendSystemMessage(message);
			}
		}
	}

	// ---- events (called from the hooks) ----------------------------------------------------------------------------------

	/**
	 * Town {@code faction} has had a {@code kind} event ({@link #RAID}, {@link #FIRE}, {@link #WAR}, {@link #MISSING}): its trouble
	 * starts, or is renewed, for a few days. The town need not be loaded: asking waits for the next check where it is.
	 */
	public static void event(MinecraftServer server, int faction, String kind, String data) {
		ServerLevel level = Factions.level(server);
		Faction f = Factions.get(server, faction);
		if (f == null || !standing(f)) {
			return;
		}
		int days = switch (kind) {
			case RAID -> RAID_DAYS;
			case FIRE -> FIRE_DAYS;
			case WAR -> WAR_DAYS;
			case MISSING -> MISSING_DAYS;
			default -> 0;
		};
		if (days <= 0) {
			return;
		}
		long until = Quests.day(level) + days;
		Map<String, Trouble> map = new HashMap<>(all(level));
		Trouble old = map.get(key(faction, kind));
		if (old != null) {
			map.put(key(faction, kind), new Trouble(kind, old.since(), until, data, old.askers(), old.asked()));
			save(level, map);
			return;
		}
		if (count(map, faction) >= MOST_AT_ONCE) {
			// (the most urgent are kept: the least urgent standing one makes room, if this is more urgent)
			String worst = null;
			for (String k : ORDER) {
				if (map.containsKey(key(faction, k))) {
					worst = k;
				}
			}
			if (worst == null || ORDER.indexOf(worst) < ORDER.indexOf(kind)) {
				return;
			}
			remove(level, f, map.get(key(faction, worst)), true);
		}
		start(level, f, kind, until, data);
	}

	/** (Called by the portal raid) A party of Hornkin has come through a portal near {@code seat}: the town nearest is raided, with no camp. */
	public static void portalRaid(ServerLevel level, BlockPos seat) {
		Faction town = Factions.around(level.getServer(), level, seat, Villages.VILLAGE_RADIUS);
		if (town != null) {
			event(level.getServer(), town.id(), RAID, "-1");
		}
	}

	/** A fire was lit in {@code f}: enough of them in a short while is a fire to recover from. */
	static void fire(MinecraftServer server, Faction f) {
		if (!standing(f)) {
			return;
		}
		long now = Factions.level(server).getGameTime();
		int[] counted = FIRES.computeIfAbsent(f.id(), id -> new int[] {0});
		long since = FIRE_SINCE.getOrDefault(f.id(), now);
		if (counted[0] == 0 || now - since > FIRE_WINDOW) {
			counted[0] = 0;
			FIRE_SINCE.put(f.id(), now);
		}
		counted[0]++;
		if (counted[0] >= FIRE_BLAZES) {
			counted[0] = 0;
			event(server, f.id(), FIRE, "");
		}
	}

	/** {@code v}, a villager of {@code home}, has been carried off to {@code at}: their town is missing one of its own. */
	static void missing(ServerLevel level, Faction home, Villager v, BlockPos at) {
		Faction camp = null;
		double bestD = MISSING_CAMP_RANGE * MISSING_CAMP_RANGE;
		for (Faction c : Factions.all(level.getServer(), FactionType.BARBARIAN)) {
			double d = c.seat().distSqr(at);
			if (!c.fallen() && d < bestD) {
				camp = c;
				bestD = d;
			}
		}
		String data = v.getUUID() + "|" + v.getName().getString().replace('|', ' ') + "|" + at.getX() + "|" + at.getY() + "|" + at.getZ() + "|" + (camp == null ? -1 : camp.id());
		event(level.getServer(), home.id(), MISSING, data);
	}

	/** The captive {@code who} is home: their town stops looking. */
	static void found(MinecraftServer server, UUID who) {
		ServerLevel level = Factions.level(server);
		for (Map.Entry<String, Trouble> e : List.copyOf(all(level).entrySet())) {
			Trouble t = e.getValue();
			if (t.kind().equals(MISSING) && t.data().startsWith(who.toString())) {
				Faction f = Factions.get(server, factionOf(e.getKey()));
				if (f != null) {
					end(level, f, t);
				}
			}
		}
	}

	// ---- asking ----------------------------------------------------------------------------------------------------------

	private static void ask(ServerLevel level, Faction f, Trouble t) {
		RandomSource random = level.getRandom();
		long today = Quests.day(level);
		// (yesterday's asks that nobody took are over)
		for (UUID id : t.askers()) {
			if (level.getEntity(id) instanceof LivingEntity v) {
				Quests.withdraw(v, "trouble." + t.kind() + ".");
			}
		}
		List<Villager> candidates = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(ASK_RANGE), v -> {
			if (!v.isAlive() || v.isBaby() || Quests.offering(v)) {
				return false;
			}
			Faction home = Quests.settlement(level, v);
			return home != null && home.id() == f.id();
		}));
		Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
		List<Ask> asks = new ArrayList<>(asks(level, f, t, random));
		Collections.shuffle(asks, new java.util.Random(random.nextLong()));
		int n = Math.min(Math.min(ASKERS_MIN + random.nextInt(ASKERS_MAX - ASKERS_MIN + 1), asks.size()), candidates.size());
		List<UUID> askers = new ArrayList<>();
		// (a villager of the trade the ask suits goes first, then anyone)
		for (Ask ask : asks.subList(0, n)) {
			Villager pick = null;
			for (Villager v : candidates) {
				if (!ask.job().isEmpty() && QuestBoard.job(v).equals(ask.job())) {
					pick = v;
					break;
				}
			}
			if (pick == null && !candidates.isEmpty()) {
				pick = candidates.get(0);
			}
			if (pick == null) {
				break;
			}
			candidates.remove(pick);
			Quest.Giver who = new Quest.Giver(pick.getUUID(), pick.getName().getString(), f.id(), false, QuestBoard.job(pick));
			Quest q = ask.make().apply(who);
			if (q != null) {
				Quests.offerNow(level, pick, q);
				askers.add(pick.getUUID());
			}
		}
		Map<String, Trouble> map = new HashMap<>(all(level));
		if (map.containsKey(key(f.id(), t.kind()))) {
			map.put(key(f.id(), t.kind()), t.withAsked(askers, today));
			save(level, map);
		}
	}

	// ---- the quests ------------------------------------------------------------------------------------------------------

	private static int base(RandomSource random) {
		return TROUBLE_EMERALDS + random.nextInt(TROUBLE_SPREAD + 1);
	}

	private static Quest.Reward pay(int emeralds) {
		return new Quest.Reward(emeralds, List.of(), Optional.empty(), Optional.empty(), TROUBLE_HEARTS, TROUBLE_REPUTATION, TROUBLE_STANDING);
	}

	private static boolean hasItem(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(Quests.itemId(id))).isPresent();
	}

	/** A trouble's gather quest: null if the item isn't in this pack. */
	private static @Nullable Ask gather(Faction f, RandomSource random, String kind, String key, String item, int min, int max, String job) {
		if (!hasItem(item)) {
			return null;
		}
		int count = min + random.nextInt(max - min + 1);
		int emeralds = base(random);
		return new Ask(job, who -> new Quest(UUID.randomUUID().toString(), Quest.GATHER, "trouble." + kind + "." + key, who,
			new Quest.Objective(item, count, 0, Optional.empty(), Optional.empty(), "", f.name(), false), pay(emeralds), 0L));
	}

	private static @Nullable Ask slay(Faction f, RandomSource random, String kind, String key, String entity, int min, int max, String job) {
		if (!entity.startsWith("*") && BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(entity)).isEmpty()) {
			return null;
		}
		int count = min + random.nextInt(max - min + 1);
		int emeralds = base(random) + TROUBLE_SLAY_MORE;
		return new Ask(job, who -> new Quest(UUID.randomUUID().toString(), Quest.SLAY, "trouble." + kind + "." + key, who,
			new Quest.Objective(entity, count, 0, Optional.empty(), Optional.empty(), "", f.name(), false), pay(emeralds), 0L));
	}

	/** Rubble to clear round the square. */
	private static Ask rubble(Faction f, RandomSource random, String kind, String key, int min, int max, String job) {
		int count = min + random.nextInt(max - min + 1);
		int emeralds = base(random);
		return new Ask(job, who -> new Quest(UUID.randomUUID().toString(), Quest.RELIEF, "trouble." + kind + "." + key, who,
			new Quest.Objective("rubble", count, 0, Optional.of(f.seat()), Optional.empty(), "", f.name(), false), pay(emeralds), 0L));
	}

	/** Slay a camp's chief; null if there is no standing camp with a leader at {@code campId}. */
	private static @Nullable Ask chief(MinecraftServer server, RandomSource random, String kind, String campId) {
		int id;
		try {
			id = Integer.parseInt(campId);
		} catch (NumberFormatException e) {
			return null;
		}
		Faction camp = Factions.get(server, id);
		if (camp == null || camp.fallen() || camp.type() != FactionType.BARBARIAN || camp.leader().isEmpty()) {
			return null;
		}
		int emeralds = TROUBLE_CHIEF_EMERALDS + random.nextInt(TROUBLE_SPREAD + 1);
		return new Ask("guard", who -> new Quest(UUID.randomUUID().toString(), Quest.CHIEF, "trouble." + kind + ".chief", who,
			new Quest.Objective("camp:" + camp.id(), 1, 0, Optional.of(camp.seat()), camp.leader(), "", camp.name(), false), pay(emeralds), 0L));
	}

	private static List<Ask> asks(ServerLevel level, Faction f, Trouble t, RandomSource r) {
		MinecraftServer server = level.getServer();
		List<Ask> out = new ArrayList<>();
		String k = t.kind();
		switch (k) {
			case FAMINE -> {
				out.add(gather(f, r, k, "bread", "minecraft:bread", 12, 20, "farmer"));
				out.add(gather(f, r, k, "potatoes", "minecraft:potato", 16, 32, "farmer"));
				out.add(gather(f, r, k, "fish", "minecraft:cod", 10, 16, "fisherman"));
				out.add(gather(f, r, k, "meat", "minecraft:cooked_beef", 8, 12, "butcher"));
			}
			case PLAGUE -> {
				out.add(gather(f, r, k, "garlic", "jaymoskingdoms:garlic", 6, 10, "cleric"));
				out.add(slay(f, r, k, "bleeders", "jaymoskingdoms:night_bleeder", 2, 4, "cleric"));
				out.add(gather(f, r, k, "honey", "minecraft:honey_bottle", 3, 6, ""));
			}
			case WOUNDS -> {
				out.add(gather(f, r, k, "bandages", "jaymospowers:linen_bandage", 3, 6, "cleric"));
				out.add(gather(f, r, k, "splints", "jaymospowers:splint", 2, 4, "cleric"));
			}
			case RAID -> {
				if (t.data().equals("-1")) {
					out.add(slay(f, r, k, "hornkin", "*hornkin", 3, 5, "guard"));
				} else {
					out.add(chief(server, r, k, t.data()));
				}
				out.add(gather(f, r, k, "stores", "minecraft:wheat", 16, 32, "farmer"));
				out.add(rubble(f, r, k, "rubble", 16, 32, "builder"));
				out.add(gather(f, r, k, "arrows", "minecraft:arrow", 24, 48, "fletcher"));
			}
			case FIRE -> {
				out.add(gather(f, r, k, "buckets", "minecraft:water_bucket", 2, 4, ""));
				out.add(gather(f, r, k, "timber", "minecraft:oak_log", 16, 32, "woodcutter"));
				out.add(rubble(f, r, k, "clear", 16, 32, "builder"));
			}
			case WAR -> {
				out.add(rubble(f, r, k, "rubble", 24, 40, "mason"));
				out.add(gather(f, r, k, "iron", "minecraft:iron_ingot", 8, 16, "armorer"));
				out.add(gather(f, r, k, "bread", "minecraft:bread", 12, 20, ""));
			}
			case DROUGHT -> {
				out.add(gather(f, r, k, "water", "minecraft:water_bucket", 2, 4, "farmer"));
				out.add(gather(f, r, k, "seeds", "minecraft:wheat_seeds", 16, 32, "farmer"));
				out.add(gather(f, r, k, "bonemeal", "minecraft:bone_meal", 12, 24, "farmer"));
			}
			case MISSING -> {
				out.add(rescue(level, f, r, t.data()));
				String[] parts = t.data().split("\\|", 6);
				if (parts.length == 6) {
					out.add(chief(server, r, k, parts[5]));
				}
			}
			case FEUD -> {
				out.add(peace(level, f, r, t.data()));
				out.add(gather(f, r, k, "arrows", "minecraft:arrow", 24, 48, "fletcher"));
				out.add(gather(f, r, k, "shields", "minecraft:shield", 2, 3, "armorer"));
			}
			default -> {
			}
		}
		out.removeIf(java.util.Objects::isNull);
		return out;
	}

	/** Bring home the captive named in {@code data} ({@code uuid|name|x|y|z|campId}); null for a test captive or bad data. */
	private static @Nullable Ask rescue(ServerLevel level, Faction f, RandomSource random, String data) {
		String[] parts = data.split("\\|", 6);
		if (parts.length != 6) {
			return null;
		}
		UUID captive;
		BlockPos at;
		int campId;
		try {
			captive = UUID.fromString(parts[0]);
			at = new BlockPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
			campId = Integer.parseInt(parts[5]);
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (captive.equals(net.minecraft.util.Util.NIL_UUID)) {
			return null;
		}
		Faction camp = campId < 0 ? null : Factions.get(level.getServer(), campId);
		String place = camp == null || camp.fallen() ? Component.translatableWithFallback("trouble.jaymoskingdoms.missing.wilds", "the wilds").getString() : camp.name();
		int emeralds = TROUBLE_RESCUE_EMERALDS + random.nextInt(TROUBLE_SPREAD + 1);
		return new Ask("", who -> new Quest(UUID.randomUUID().toString(), Quest.RESCUE, "trouble.missing.rescue", who,
			new Quest.Objective("captive:" + captive, 1, 0, Optional.of(at), Optional.of(captive), parts[1], place, false), pay(emeralds), 0L));
	}

	/** A letter of peace to the ruler of the rival named in {@code data}; null if there is none. */
	private static @Nullable Ask peace(ServerLevel level, Faction f, RandomSource random, String data) {
		Faction rival;
		try {
			rival = Factions.get(level.getServer(), Integer.parseInt(data));
		} catch (NumberFormatException e) {
			return null;
		}
		if (rival == null || !standing(rival) || rival.leader().isEmpty()) {
			return null;
		}
		String ruler = rival.type() == FactionType.KINGDOM ? "king" : rival.type() == FactionType.FIEFDOM ? "lord" : "elder";
		int emeralds = TROUBLE_LETTER_EMERALDS + random.nextInt(TROUBLE_SPREAD + 1);
		return new Ask("", who -> new Quest(UUID.randomUUID().toString(), Quest.DELIVER, "trouble.feud.peace", who,
			new Quest.Objective("leader:" + rival.id() + ":" + ruler, 1, 0, Optional.of(rival.seat()), Optional.empty(), "", rival.name(), false), pay(emeralds), 0L));
	}

	// ---- bringing goods in -----------------------------------------------------------------------------------------------

	/** {@code q}, a gather quest, was just handed in to {@code f}'s villager: if it is a trouble's food or timber, it goes into the town's stores. */
	static void handedIn(ServerLevel level, Faction f, Quest q) {
		boolean stocked = false;
		for (String prefix : STOCKED) {
			if (q.template().startsWith(prefix)) {
				stocked = true;
				break;
			}
		}
		if (!stocked || f.holdings().storage().isEmpty()) {
			return;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(Quests.itemId(q.objective().target()))).orElse(null);
		if (item != null) {
			// (what doesn't fit is dropped silently)
			Storage.put(level, f, item, q.objective().count());
		}
	}

	// ---- for ops ---------------------------------------------------------------------------------------------------------

	/** (Testing) Starts {@code kind} in {@code f} and asks at once. False for an unknown kind. */
	static boolean test(ServerPlayer player, Faction f, String kind) {
		if (!ORDER.contains(kind)) {
			return false;
		}
		ServerLevel level = Factions.level(player.level().getServer());
		MinecraftServer server = level.getServer();
		String data = "";
		long until = -1L;
		switch (kind) {
			case RAID, MISSING, FEUD -> {
				Faction near = null;
				double bestD = Double.MAX_VALUE;
				for (Faction other : Factions.all(server)) {
					boolean wanted = kind.equals(FEUD) ? standing(other) && other.id() != f.id() : other.type() == FactionType.BARBARIAN && !other.fallen();
					double d = other.seat().distSqr(f.seat());
					if (wanted && d < bestD) {
						near = other;
						bestD = d;
					}
				}
				BlockPos at = player.blockPosition();
				data = kind.equals(MISSING) ? net.minecraft.util.Util.NIL_UUID + "|Test|" + at.getX() + "|" + at.getY() + "|" + at.getZ() + "|" + (near == null ? -1 : near.id())
					: near == null ? (kind.equals(RAID) ? "-1" : "") : Integer.toString(near.id());
				until = kind.equals(FEUD) ? -1L : Quests.day(level) + (kind.equals(RAID) ? RAID_DAYS : MISSING_DAYS);
			}
			case FIRE, WAR -> until = Quests.day(level) + (kind.equals(FIRE) ? FIRE_DAYS : WAR_DAYS);
			default -> {
			}
		}
		Map<String, Trouble> map = new HashMap<>(all(level));
		map.remove(key(f.id(), kind));
		save(level, map);
		start(level, f, kind, until, data);
		ask(level, f, all(level).get(key(f.id(), kind)));
		return true;
	}

	/** (Testing) Ends every trouble in {@code f}. How many there were. */
	static int clear(ServerLevel level, Faction f) {
		int n = 0;
		for (String kind : ORDER) {
			Trouble t = all(level).get(key(f.id(), kind));
			if (t != null) {
				end(level, f, t);
				n++;
			}
		}
		return n;
	}
}
