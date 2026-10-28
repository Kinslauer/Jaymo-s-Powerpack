package com.jaymoskingdoms;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.299) Andy (2026-10-28): "Kingdoms should get visitors." Visiting Villagers' idea, remade (that mod is All Rights
 * Reserved; nothing of it is copied).
 * <p>
 * Of a morning, a settlement that's somewhere worth visiting gets travellers for the day: a few parties of one or two
 * grown villagers (MCA's, in a trade of their own or none, so you can trade with them), now and then with a child. They
 * come in on foot from the outskirts, from a named town nearby (they say so) or from far off, mill about the market,
 * look round, eat a meal or two in town ({@link Meals}: a visitor's meal pays the realm the whole price, money from
 * outside) and spend at the stalls. At dusk they head back the way they came and are gone. They take no beds, no job
 * sites, no posts, and never join the town (MCA's residency leaves them be: {@code McaVisitorResidencyMixin}).
 * <p>
 * A settlement needs {@link #LEAST_VILLAGERS} villagers and a guard (an iron golem, or a realm's guards) before anyone
 * visits, and none come while it's at war or under attack. More come ({@link #appeal}) for a cookhouse, a tavern, a
 * prosperous realm, cheap food and a festival; fewer when food is dear.
 * {@code /kingdom larder} tells today's visitors; {@code /kingdom visitors now} (ops) brings a party at once.
 */
public final class Visitors {
	/** How often visitors are thought about (ticks). */
	static final int EVERY = 40;
	/** Arrivals, by the time of day; when they set off home. */
	static final long ARRIVE_FROM = 300L, ARRIVE_TO = 4500L, LEAVE_AT = 11000L;
	/** The chance each think that a party due today arrives. */
	static final float PARTY_CHANCE = 0.04F;
	/** A party: grown ones (1 to this), the chance of a child along; the chance a grown one has no trade. */
	static final int PARTY_MOST = 2;
	static final float CHILD_CHANCE = 0.25F, NO_TRADE_CHANCE = 0.25F;
	/** The most parties a day; what draws them (each adds one to the day's draw). */
	static final int PARTIES_MOST = 5, FESTIVAL_DRAW = 2;
	/** A settlement needs this many villagers of its own before anyone visits. */
	static final int LEAST_VILLAGERS = 6;
	/** They come in from this far out; a named town is one within this. */
	static final int FROM_MIN = 40, FROM_MORE = 16;
	static final double ORIGIN_RANGE = 2000.0;
	/** While in town: how far a look round takes them from the seat; how long they stay at a spot (ticks). */
	static final int ROAM = 24, LINGER_MIN = 300, LINGER_MORE = 400;
	static final float WALK = 0.5F;
	/** What a visitor spends at the stalls (bits, {@link Meals#BITS} an emerald), once a visit. */
	static final int MARKET_SPEND = 8;
	/** Leaving: gone at their road's end, or once nobody's within this; after this long on the way (ticks), gone if nobody's near. */
	static final double UNSEEN = 40.0, NEAR = 16.0;
	static final long LEAVING_TICKS = 2400L;
	/** The chance each think that a visitor in town says something. */
	static final float SAY_CHANCE = 0.01F;
	static final int LINES = 4;
	static final double SPEECH_RANGE = 12.0;
	/** The trades a visitor may follow. */
	private static final List<net.minecraft.resources.ResourceKey<VillagerProfession>> TRADES = List.of(VillagerProfession.FARMER,
		VillagerProfession.FISHERMAN, VillagerProfession.SHEPHERD, VillagerProfession.FLETCHER, VillagerProfession.LIBRARIAN,
		VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC, VillagerProfession.ARMORER, VillagerProfession.WEAPONSMITH,
		VillagerProfession.TOOLSMITH, VillagerProfession.BUTCHER, VillagerProfession.LEATHERWORKER, VillagerProfession.MASON);

	/** A visit: to which settlement, from where (their road's end), the day, and the town they came from ("" from far off). */
	public record Visit(int faction, BlockPos from, long day, String origin) {
		static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("faction").forGetter(Visit::faction),
				BlockPos.CODEC.fieldOf("from").forGetter(Visit::from),
				Codec.LONG.fieldOf("day").forGetter(Visit::day),
				Codec.STRING.optionalFieldOf("origin", "").forGetter(Visit::origin))
			.apply(i, Visit::new));
	}

	public static final AttachmentType<Visit> VISIT = AttachmentRegistry.create(JaymosKingdoms.id("visit"), builder -> builder.persistent(Visit.CODEC));

	/** What a visitor is about in town: where to, since when, how long, whether they've got there. */
	private static final class Roam {
		final BlockPos to;
		final long started;
		final int linger;
		long arrived = -1L;
		final boolean market;

		Roam(BlockPos to, long started, int linger, boolean market) {
			this.to = to;
			this.started = started;
			this.linger = linger;
			this.market = market;
		}
	}

	private static final Set<Villager> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<UUID, Roam> ROAMING = new HashMap<>();
	/** Who's set off home, since when; who's said hello; who's spent at the stalls. */
	private static final Map<UUID, Long> LEAVING = new HashMap<>();
	private static final Set<UUID> GREETED = new HashSet<>();
	private static final Set<UUID> SPENT = new HashSet<>();
	/** Per settlement: {day, parties still due}. */
	private static final Map<Integer, long[]> DUE = new HashMap<>();

	private Visitors() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Visitors::tick);
		ServerEntityEvents.ENTITY_LOAD.register(Visitors::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				LOADED.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			ROAMING.clear();
			LEAVING.clear();
			GREETED.clear();
			SPENT.clear();
			DUE.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("visitors").then(Commands.literal("now").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(Visitors::now)))));
	}

	/** {@code e} is a visitor here for the day. */
	public static boolean is(@Nullable Entity e) {
		return e instanceof Villager && e.hasAttached(VISIT);
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof Villager v && v.hasAttached(VISIT)) {
			LOADED.add(v);
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		long day = Festivals.day(level);
		if (t >= ARRIVE_FROM && t < ARRIVE_TO) {
			for (Faction f : Factions.all(server)) {
				if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
					continue;
				}
				long[] due = DUE.get(f.id());
				if (due == null || due[0] != day) {
					due = new long[] {day, draw(level, f, day)};
					DUE.put(f.id(), due);
				}
				if (due[1] > 0 && level.getRandom().nextFloat() < PARTY_CHANCE && welcome(level, f) && party(level, f, day) > 0) {
					due[1]--;
				}
			}
		}
		for (Villager v : new ArrayList<>(LOADED)) {
			if (!v.isAlive() || v.level() != level) {
				LOADED.remove(v);
				continue;
			}
			Visit visit = v.getAttached(VISIT);
			if (visit == null) {
				LOADED.remove(v);
				continue;
			}
			step(level, v, visit, t, day, now);
		}
	}

	/** The parties due at {@code f} today: up to its {@link #appeal}, at random. */
	private static int draw(ServerLevel level, Faction f, long day) {
		int appeal = appeal(level, f, day);
		return appeal <= 0 ? 0 : level.getRandom().nextInt(appeal + 1);
	}

	/** What draws visitors to {@code f}: one to start, and one each for a cookhouse, a tavern, a prosperous realm, cheap food; two for a festival; one less for dear food. */
	static int appeal(ServerLevel level, Faction f, long day) {
		MinecraftServer server = level.getServer();
		if (Economy.atWar(server, f) || Economy.underAttack(server, f)) {
			return 0;
		}
		int appeal = 1 + Meals.eateries(level, f).size();
		if (Wealth.atLeast(server, f, Wealth.Tier.PROSPEROUS)) {
			appeal++;
		}
		int index = Meals.index(level, f);
		appeal += index <= Meals.CHEAP ? 1 : index >= Meals.DEAR ? -1 : 0;
		if (f.type().isRealm() && Festivals.festivalDay(level, f, day)) {
			appeal += FESTIVAL_DRAW;
		}
		return Math.max(0, Math.min(PARTIES_MOST, appeal));
	}

	/** {@code f} is somewhere to visit just now: enough villagers of its own and a guard, at peace. */
	private static boolean welcome(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (Economy.atWar(server, f) || Economy.underAttack(server, f)) {
			return false;
		}
		AABB box = new AABB(f.seat()).inflate(Economy.POPULATION_RANGE);
		if (level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !is(v)).size() < LEAST_VILLAGERS) {
			return false;
		}
		return f.type().isRealm() || !level.getEntitiesOfClass(IronGolem.class, box, IronGolem::isAlive).isEmpty();
	}

	/** A party comes in to {@code f} from the outskirts; how many came (0 if there was nowhere to come from). */
	static int party(ServerLevel level, Faction f, long day) {
		RandomSource random = level.getRandom();
		BlockPos from = null;
		for (int tries = 0; tries < 10 && from == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int r = FROM_MIN + random.nextInt(FROM_MORE);
			BlockPos column = f.seat().offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()) {
				from = Construction.standable(level, top, 2);
			}
		}
		if (from == null) {
			return 0;
		}
		String origin = origin(level, f);
		int grown = 1 + random.nextInt(PARTY_MOST);
		int came = 0;
		for (int i = 0; i <= grown; i++) {
			boolean child = i == grown;
			if (child && random.nextFloat() >= CHILD_CHANCE) {
				break;
			}
			VillagerProfession job = child || random.nextFloat() < NO_TRADE_CHANCE ? null
				: BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(TRADES.get(random.nextInt(TRADES.size()))).value();
			Vec3 at = Vec3.atBottomCenterOf(from).add(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
			Villager v = McaCompat.visitor(level, at, job, child);
			if (v == null) {
				continue;
			}
			v.setAttached(VISIT, new Visit(f.id(), from, day, origin));
			LOADED.add(v);
			came++;
			walk(v, Construction.standable(level, f.seat().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 3));
		}
		if (came > 0) {
			JaymosKingdoms.LOGGER.debug("{} visitors came to {} from {}", came, f.name(), origin.isEmpty() ? "afar" : origin);
		}
		return came;
	}

	/** The town a party comes from: the nearest other settlement at peace with {@code f}, else an MCA town not its own; "" from far off. */
	private static String origin(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		Faction best = null;
		double bestD = ORIGIN_RANGE * ORIGIN_RANGE;
		for (Faction o : Factions.all(server)) {
			if (o.id() == f.id() || o.fallen() || o.type() == FactionType.BARBARIAN || Relations.atWar(f, o)) {
				continue;
			}
			double d = o.seat().distSqr(f.seat());
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		if (best != null && level.getRandom().nextInt(4) != 0) {
			return best.name();
		}
		List<McaCompat.Town> towns = new ArrayList<>();
		for (McaCompat.Town town : McaCompat.towns(level)) {
			double d = town.center().distSqr(f.seat());
			if (d > 128 * 128 && d < ORIGIN_RANGE * ORIGIN_RANGE) {
				towns.add(town);
			}
		}
		return towns.isEmpty() ? "" : towns.get(level.getRandom().nextInt(towns.size())).name();
	}

	/** A visitor's think: about the town by day, home at dusk, and gone at their road's end (or once nobody sees). */
	private static void step(ServerLevel level, Villager v, Visit visit, long t, long day, long now) {
		RandomSource random = level.getRandom();
		boolean leaving = visit.day() != day || t >= LEAVE_AT;
		if (leaving) {
			ROAMING.remove(v.getUUID());
			long since = LEAVING.computeIfAbsent(v.getUUID(), k -> now);
			if (since == now && random.nextInt(3) == 0) {
				say(level, v, "leave", random, Component.empty());
			}
			Player near = level.getNearestPlayer(v, UNSEEN);
			boolean home = v.blockPosition().distSqr(visit.from()) <= 9;
			if (home || near == null || now - since > LEAVING_TICKS && v.distanceToSqr(near) > NEAR * NEAR) {
				gone(v);
				return;
			}
			if (!Meals.eating(v) && Routines.idle(v) && v.getNavigation().isDone()) {
				walk(v, visit.from());
			}
			return;
		}
		Faction f = Factions.get(level.getServer(), visit.faction());
		if (f == null || f.fallen()) {
			gone(v);
			return;
		}
		if (!GREETED.contains(v.getUUID()) && v.blockPosition().distSqr(f.seat()) < 20 * 20) {
			GREETED.add(v.getUUID());
			if (!v.isBaby()) {
				say(level, v, visit.origin().isEmpty() ? "arrive_afar" : "arrive", random, Component.literal(visit.origin()));
			}
		}
		if (Meals.eating(v) || !Routines.idle(v) || v.getTradingPlayer() != null) {
			return;
		}
		Roam r = ROAMING.get(v.getUUID());
		if (r == null || r.arrived >= 0 && now - r.arrived >= r.linger || r.arrived < 0 && now - r.started > Routines.TRAVEL_TICKS) {
			r = roam(level, f, random, now);
			ROAMING.put(v.getUUID(), r);
			walk(v, r.to);
			return;
		}
		if (r.arrived < 0) {
			if (v.position().distanceToSqr(Vec3.atBottomCenterOf(r.to)) <= 6.25) {
				r.arrived = now;
				if (r.market && !v.isBaby() && SPENT.add(v.getUUID())) {
					// (a few things at the stalls: money from outside)
					Meals.pay(level.getServer(), f, MARKET_SPEND);
				}
			} else if (v.getNavigation().isDone()) {
				walk(v, r.to);
			}
			return;
		}
		if (random.nextFloat() < SAY_CHANCE && !v.isBaby()) {
			say(level, v, "town", random, Component.literal(f.name()));
		}
	}

	/** Somewhere to go in town: the market, the cookhouse or tavern, or a look round. */
	private static Roam roam(ServerLevel level, Faction f, RandomSource random, long now) {
		int linger = LINGER_MIN + random.nextInt(LINGER_MORE);
		List<Meals.Eatery> eateries = Meals.eateries(level, f);
		int pick = random.nextInt(4);
		if (pick == 1 && !eateries.isEmpty()) {
			BlockPos counter = eateries.get(random.nextInt(eateries.size())).counter();
			return new Roam(Construction.standable(level, counter.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), 3), now, linger, false);
		}
		if (pick >= 2) {
			double angle = random.nextDouble() * Math.PI * 2;
			int r = 6 + random.nextInt(ROAM);
			BlockPos column = f.seat().offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r));
			return new Roam(Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column), 3), now, linger, false);
		}
		return new Roam(Construction.standable(level, f.seat().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 3), now, linger, true);
	}

	/** A visitor's gone home: out of the world, and out of MCA's books. */
	private static void gone(Villager v) {
		UUID id = v.getUUID();
		LOADED.remove(v);
		ROAMING.remove(id);
		LEAVING.remove(id);
		GREETED.remove(id);
		SPENT.remove(id);
		McaCompat.leaveVillage(v);
		McaCompat.vanish(v);
	}

	private static void walk(Villager v, BlockPos to) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, WALK, 1);
	}

	private static void say(ServerLevel level, Villager v, String kind, RandomSource random, Component arg) {
		Speech.say(level, v, Component.translatable("visitors.jaymoskingdoms." + kind + "." + random.nextInt(LINES), arg), Speech.WHITE, SPEECH_RANGE);
	}

	/** {@code /kingdom visitors now}: a party comes in to the settlement round you at once. */
	private static int now(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Faction f = Factions.around(server, c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 160.0);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			c.getSource().sendFailure(Component.literal("No town here"));
			return 0;
		}
		int came = party(level, f, Festivals.day(level));
		c.getSource().sendSuccess(() -> Component.literal(came + " visitors on their way to " + f.name()), true);
		return came > 0 ? 1 : 0;
	}
}
