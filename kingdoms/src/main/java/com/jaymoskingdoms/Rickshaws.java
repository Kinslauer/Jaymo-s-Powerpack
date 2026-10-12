package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.26) A settlement's rickshaw pullers ({@link Folk#PULLER}): one of its people (with MCA, one of its villagers,
 * promoted the MCA way) between the shafts of a two-wheeled {@link Wagon#RICKSHAW}. They wait at the rank by the bell
 * and, by day:
 * <ul>
 * <li>meet a miner, woodcutter or farmer walking a load in from far off, take it and haul it to storage, so the
 * gatherer can go straight back to work;</li>
 * <li>run errands to a sister settlement (its overlord, a vassal of its own, or an ally) close by: food, or whatever
 * it has plenty of that the other is short of;</li>
 * <li>carry its people about the town now and then (grown-ups and children), and a ruler on his round if the realm
 * has no carriage for him ({@link Carriages});</li>
 * <li>take a player to another settlement for a fare, paid into the settlement's treasury: right-click a waiting
 * rickshaw for the places it goes.</li>
 * </ul>
 * At dusk they park at the rank and go home to bed. They take the place of no other work; a settlement takes one on
 * once it has a road and enough people ({@link #wanted}).
 */
public final class Rickshaws {
	/** A settlement wants its first puller at this many people (with a road built), its second at this many. */
	static final int FIRST_AT = 12, SECOND_AT = 30;
	/** How far from the rank a puller may go about the town. */
	static final int ROAM = 160;
	/** How often a waiting puller looks for something to do (ticks). */
	static final int LOOK_TICKS = 40;
	/** A gatherer at least this far from storage with a load is met and relieved of it. */
	static final double MEET_FROM = 28.0;
	/** A rickshaw hauls to storage once it holds this many items (or there's nobody else to meet). */
	static final int HAUL_AT = 96, CARGO_MOST = 256;
	/** Errands: to a sister settlement this near, at most this often (ticks) per settlement, carrying at most this much. */
	static final double ERRAND_RANGE = 220.0;
	static final long ERRAND_TICKS = 3600L;
	static final int ERRAND_LOAD = 64, ERRAND_GAP = 128, FOOD_GAP = 64;
	/** Carrying its people: the chance a waiting puller takes a fare, and how far the ride must be. */
	static final int FARE_CHANCE = 6;
	static final double FARE_RANGE = 40.0, FARE_LEAST = 20.0;
	/** A ruler on his round is fetched from this near. */
	static final double RULER_RANGE = 64.0;
	/** Players: settlements this far (at most, at least) are offered, this many of them; the fare's emeralds. */
	static final double RIDE_RANGE = 700.0, RIDE_LEAST = 48.0;
	static final int RIDE_CHOICES = 6, FARE_BASE = 1, FARE_PER_BLOCKS = 96, FARE_MOST = 8;
	/** How near a player must be to the rickshaw to hire it. */
	static final double HIRE_REACH = 6.0;
	static final double PULL_SPEED = 0.7, RIDE_SPEED = 0.95;
	static final double REACH = 3.5, DEPOT_REACH = 4.5, RANK_REACH = 2.5, ARRIVE = 6.0;
	/** No nearer the goal for this long (ticks) and the puller hops ahead this far (a stile, a fence, a ledge). */
	static final int STUCK_TICKS = 100;
	static final int HOP = 8;
	/** A trip given up after this long (ticks). */
	static final long TRIP_MOST = 24000L / 2;
	/** Its puller gone this long (ticks) and a rickshaw is left: its load spilled, the rickshaw taken apart. */
	static final int ABANDON_TICKS = 600;

	private static final int WAIT = 0, MEET = 1, HAUL = 2, ERRAND = 3, RETURN = 4, FETCH = 5, FARE = 6, HIRED = 7, RULER = 8, PARK = 9,
		UNPARK = 10;

	/** A puller's day (not saved: the load, the parking and a player's ride are kept on the {@link Wagon}). */
	private static final class Job {
		int phase = WAIT;
		@Nullable Wagon rickshaw;
		@Nullable BlockPos goal;
		@Nullable UUID other;
		int errandTo = -1;
		@Nullable BlockPos rank;
		long rankAt;
		final Gatherers.Trip trip = new Gatherers.Trip();
		long startedAt;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** Gatherers already being met (by puller). */
	private static final Set<UUID> MET = new HashSet<>();
	/** When each settlement may next send an errand. */
	private static final Map<Integer, Long> NEXT_ERRAND = new HashMap<>();

	private Rickshaws() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			MET.clear();
			NEXT_ERRAND.clear();
		});
	}

	// ---- taking them on ---------------------------------------------------------------------------------------------

	/** Where its pullers wait: beside the bell. */
	static BlockPos rank(ServerLevel level, Faction f) {
		return Construction.standable(level, f.seat().offset(3, 0, 3), 4);
	}

	/** The rank, as {@code job} last found it (looked at again now and then). */
	private static BlockPos rank(ServerLevel level, Faction f, Job job) {
		long now = level.getGameTime();
		if (job.rank == null || now - job.rankAt > 1200L) {
			job.rank = rank(level, f);
			job.rankAt = now;
		}
		return job.rank;
	}

	/** It wants one more puller: a road built, and enough people (counting places waiting to be filled). */
	static boolean wanted(MinecraftServer server, Faction f) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN || f.holdings().depot().isEmpty()
			|| Works.of(server, f.id()).built().stream().noneMatch(b -> b.entry().equals("road"))) {
			return false;
		}
		int have = f.holdings().count(Folk.PULLER) + f.holdings().vacancies().getOrDefault(Folk.roleName(Folk.PULLER), 0);
		int want = f.holdings().population() >= SECOND_AT ? 2 : f.holdings().population() >= FIRST_AT ? 1 : 0;
		return have < want;
	}

	/** A puller for {@code f} (counted in its roster), waiting at the rank; {@code from} where they come from. Null if none could be had. */
	static @Nullable Worker spawn(ServerLevel level, Faction f, @Nullable BlockPos from) {
		if (f.holdings().depot().isEmpty()) {
			return null;
		}
		BlockPos rank = rank(level, f);
		Worker w = Workers.body(level, f, Folk.PULLER, from != null ? from : rank, rank, ROAM);
		if (w == null) {
			return null;
		}
		w.setSite(rank);
		Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.PULLER, 1)));
		JaymosKingdoms.LOGGER.debug("{} took on a rickshaw puller", f.name());
		return w;
	}

	// ---- whose is what ----------------------------------------------------------------------------------------------

	/** {@code e} pulls a rickshaw for a settlement (a puller in its service). */
	static boolean isPuller(@Nullable Entity e) {
		return e instanceof Worker w && w.role() == Folk.PULLER && e.isAlive();
	}

	/** {@code puller}'s rickshaw, found (or made, set down behind them) if it isn't known yet. */
	private static @Nullable Wagon rickshaw(ServerLevel level, Faction f, Worker puller, Job job) {
		if (job.rickshaw != null && !job.rickshaw.isRemoved()) {
			return job.rickshaw;
		}
		UUID id = puller.getUUID();
		List<Wagon> found = level.getEntitiesOfClass(Wagon.class, puller.mob().getBoundingBox().inflate(ROAM),
			w -> w.isAlive() && w.kind() == Wagon.RICKSHAW && id.equals(w.horseId()));
		if (!found.isEmpty()) {
			job.rickshaw = found.getFirst();
			return job.rickshaw;
		}
		job.rickshaw = Wagon.hitch(level, Wagon.RICKSHAW, f, puller.mob(), -1, 0);
		return job.rickshaw;
	}

	/** A puller's on a job that keeps them up past bedtime (a passenger aboard, a load to see in). */
	public static boolean onDuty(Entity puller) {
		Job job = JOBS.get(puller.getUUID());
		return job != null && job.rickshaw != null && !job.rickshaw.isRemoved()
			&& (job.rickshaw.isVehicle() || job.phase == HAUL || job.phase == ERRAND || job.phase == HIRED);
	}

	// ---- a day's work -----------------------------------------------------------------------------------------------

	/** One puller's tick (from {@link Workers#work}). */
	static void tick(ServerLevel level, Worker puller) {
		if (puller.getLastHurtByMob() != null && puller.ticksAlive() - puller.getLastHurtByMobTimestamp() < Workers.FLEE_TICKS) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, puller.factionId());
		if (f == null || f.fallen()) {
			return;
		}
		Job job = JOBS.computeIfAbsent(puller.getUUID(), u -> new Job());
		Wagon rickshaw = rickshaw(level, f, puller, job);
		if (rickshaw == null) {
			return;
		}
		long now = level.getGameTime();
		if (job.phase != WAIT && job.phase != PARK && now - job.startedAt > TRIP_MOST) {
			// (a trip that's gone on too long: given up, home)
			end(level, f, puller, job, rickshaw, now);
		}
		switch (job.phase) {
			case MEET -> meet(level, f, puller, job, rickshaw, now);
			case HAUL -> {
				puller.setTask("hauling to storage");
				if (drive(level, puller, job, Builders.depot(f), DEPOT_REACH, PULL_SPEED, now)) {
					unload(level, f, rickshaw, Builders.depot(f));
					start(job, WAIT, null, now);
				}
			}
			case ERRAND -> errand(level, f, puller, job, rickshaw, now);
			case RETURN -> {
				puller.setTask("heading back to the rank");
				if (drive(level, puller, job, rank(level, f, job), RANK_REACH, PULL_SPEED, now)) {
					start(job, WAIT, null, now);
				}
			}
			case FETCH -> fetch(level, f, puller, job, rickshaw, now);
			case FARE -> fare(level, f, puller, job, rickshaw, now);
			case HIRED -> hired(level, f, puller, job, rickshaw, now);
			case RULER -> ruler(level, f, puller, job, rickshaw, now);
			case PARK -> {
				if (rickshaw.parked()) {
					// (set down for the night: they live as the others do till morning)
					puller.setTask("resting");
					if (Royals.daytime(level)) {
						start(job, UNPARK, null, now);
					}
				} else {
					puller.setTask("parking the rickshaw");
					if (drive(level, puller, job, rank(level, f, job), RANK_REACH, PULL_SPEED, now)) {
						rickshaw.setParked(true);
						puller.setTask("resting");
					}
				}
			}
			case UNPARK -> {
				puller.setTask("fetching the rickshaw");
				if (drive(level, puller, job, rickshaw.blockPosition(), 1.5, PULL_SPEED, now)) {
					rickshaw.setParked(false);
					start(job, WAIT, null, now);
				}
			}
			default -> {
				if (now % LOOK_TICKS == Math.floorMod(puller.getId(), LOOK_TICKS)) {
					decide(level, f, puller, job, rickshaw, now);
				} else if (puller.distanceToSqr(Vec3.atBottomCenterOf(rank(level, f, job))) > 9.0 && !rickshaw.isVehicle()) {
					puller.setTask("heading back to the rank");
					drive(level, puller, job, rank(level, f, job), RANK_REACH, PULL_SPEED, now);
				}
			}
		}
	}

	/** On to {@code phase} (toward {@code goal}, if it has one); whoever it was seeing to is let go (callers set {@link Job#other} after). */
	private static void start(Job job, int phase, @Nullable BlockPos goal, long now) {
		if (job.other != null) {
			MET.remove(job.other);
		}
		job.other = null;
		job.phase = phase;
		job.goal = goal;
		job.trip.reset(now);
		job.startedAt = now;
	}

	/** What to do next, waiting at the rank. */
	private static void decide(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		MinecraftServer server = level.getServer();
		// (back from a restart, someone aboard: a player's ride goes on; anyone else is set down)
		Entity aboard = rickshaw.getFirstPassenger();
		if (aboard instanceof ServerPlayer && rickshaw.ride() >= 0) {
			Faction to = Factions.get(server, rickshaw.ride());
			if (to != null) {
				start(job, HIRED, Construction.standable(level, to.seat(), 6), now);
				return;
			}
		}
		if (aboard != null && !(aboard instanceof Folk ruler && ruler.leader())) {
			setDown(level, rickshaw, aboard);
		}
		if (!Royals.daytime(level)) {
			if (!rickshaw.cargo().isEmpty()) {
				start(job, HAUL, null, now);
			} else {
				start(job, PARK, null, now);
			}
			return;
		}
		if (rickshaw.parked()) {
			start(job, UNPARK, null, now);
			return;
		}
		if (rickshaw.cargo().size() > 0 && cargoCount(rickshaw) >= HAUL_AT) {
			start(job, HAUL, null, now);
			return;
		}
		// a gatherer walking a load in from far off
		Worker gatherer = loadToMeet(level, f, puller);
		if (gatherer != null && cargoCount(rickshaw) < CARGO_MOST) {
			start(job, MEET, null, now);
			job.other = gatherer.getUUID();
			MET.add(job.other);
			puller.setTask("meeting a load");
			return;
		}
		if (!rickshaw.cargo().isEmpty()) {
			start(job, HAUL, null, now);
			return;
		}
		// the ruler, on his round with no carriage of his own
		Folk ruler = rulerToFetch(level, f, rickshaw);
		if (ruler != null) {
			start(job, RULER, null, now);
			job.other = ruler.getUUID();
			return;
		}
		// an errand to a sister settlement
		if (now >= NEXT_ERRAND.getOrDefault(f.id(), 0L) && errandOut(level, f, puller, job, rickshaw, now)) {
			NEXT_ERRAND.put(f.id(), now + ERRAND_TICKS);
			return;
		}
		// one of its people, now and then, across the town
		if (level.getRandom().nextInt(FARE_CHANCE) == 0 && fareOut(level, f, puller, job, rickshaw, now)) {
			return;
		}
		puller.setTask("waiting for a fare");
	}

	/** Ends whatever it was doing: anyone aboard set down, back to the rank. */
	private static void end(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		for (Entity e : List.copyOf(rickshaw.getPassengers())) {
			if (!(e instanceof Folk ruler && ruler.leader())) {
				setDown(level, rickshaw, e);
			}
		}
		rickshaw.setRide(-1);
		start(job, rickshaw.cargo().isEmpty() ? RETURN : HAUL, null, now);
	}

	// ---- loads ------------------------------------------------------------------------------------------------------

	private static int cargoCount(Wagon rickshaw) {
		return rickshaw.cargo().stream().mapToInt(ItemStack::getCount).sum();
	}

	/** The nearest of its gatherers walking a load in from far off whom nobody's meeting yet. */
	private static @Nullable Worker loadToMeet(ServerLevel level, Faction f, Worker puller) {
		Vec3 depot = Vec3.atBottomCenterOf(Builders.depot(f));
		List<Worker> walking = Workers.near(level, new AABB(f.seat()).inflate(ROAM), w -> w.isAlive() && w.factionId() == f.id()
			&& !MET.contains(w.getUUID()) && (Gatherers.delivering(w) || Farmers.delivering(w)) && w.distanceToSqr(depot) > MEET_FROM * MEET_FROM);
		return walking.stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(puller.position()))).orElse(null);
	}

	private static void meet(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Entity found = job.other == null ? null : level.getEntity(job.other);
		if (!(found instanceof Worker gatherer) || !gatherer.isAlive() || !(Gatherers.delivering(gatherer) || Farmers.delivering(gatherer))) {
			start(job, WAIT, null, now);
			return;
		}
		puller.setTask("meeting a load");
		if (!drive(level, puller, job, gatherer.blockPosition(), REACH, PULL_SPEED, now)) {
			return;
		}
		// the load goes into the rickshaw; the gatherer goes back to work
		List<ItemStack> load = new ArrayList<>(Gatherers.handOver(gatherer));
		load.addAll(Farmers.handOver(gatherer));
		for (ItemStack stack : load) {
			rickshaw.addCargo(stack);
		}
		gatherer.getNavigation().stop();
		level.playSound(null, rickshaw.blockPosition(), SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.1F);
		start(job, WAIT, null, now);
		// (more to meet, and room for it: on to the next; else to storage)
		Worker next = loadToMeet(level, f, puller);
		if (next == null || cargoCount(rickshaw) >= HAUL_AT) {
			start(job, HAUL, null, now);
		}
	}

	/** Into {@code f}'s storage; what won't fit is left by its depot. */
	private static void unload(ServerLevel level, Faction f, Wagon rickshaw, BlockPos depot) {
		for (ItemStack stack : rickshaw.takeCargo()) {
			ItemStack left = Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				Block.popResource(level, depot, left);
			}
		}
		level.playSound(null, depot, SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.6F, 1.0F);
	}

	// ---- errands ----------------------------------------------------------------------------------------------------

	/** Its sister settlements near enough for an errand: its overlord, its vassals, its overlord's other vassals, its allies. */
	static List<Faction> sisters(MinecraftServer server, Faction f) {
		List<Faction> out = new ArrayList<>();
		for (Faction other : Factions.all(server)) {
			if (other.id() == f.id() || other.fallen() || other.type() == FactionType.BARBARIAN || other.holdings().depot().isEmpty()
				|| !other.seat().closerThan(f.seat(), ERRAND_RANGE)) {
				continue;
			}
			boolean kin = other.id() == f.overlord() || other.overlord() == f.id() || f.overlord() >= 0 && other.overlord() == f.overlord()
				|| Relations.between(f, other) == Relation.ALLIED;
			if (kin) {
				out.add(other);
			}
		}
		return out;
	}

	/** A load for a sister settlement short of it, if there's one to send: food first, else what it has plenty of. */
	private static boolean errandOut(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		if (!Storage.loaded(level, f)) {
			return false;
		}
		for (Faction sister : sisters(level.getServer(), f)) {
			if (!Storage.loaded(level, sister)) {
				continue;
			}
			ItemStack load = ItemStack.EMPTY;
			int food = Storage.food(level, f), theirs = Storage.food(level, sister);
			if (food - theirs >= FOOD_GAP) {
				load = Storage.take(level, f, Storage::isFood, Math.min(ERRAND_LOAD, (food - theirs) / 2));
			} else {
				Map<Item, Integer> ours = Storage.totals(level, f);
				Item best = null;
				int most = 0;
				for (Map.Entry<Item, Integer> e : ours.entrySet()) {
					int gap = e.getValue() - Storage.count(level, sister, e.getKey());
					if (gap >= ERRAND_GAP && gap > most && e.getKey() != Items.EMERALD) {
						best = e.getKey();
						most = gap;
					}
				}
				if (best != null) {
					load = Storage.take(level, f, best, ERRAND_LOAD);
				}
			}
			if (load.isEmpty()) {
				continue;
			}
			rickshaw.addCargo(load);
			job.errandTo = sister.id();
			start(job, ERRAND, Builders.depot(sister), now);
			puller.setTask("an errand to " + sister.name());
			JaymosKingdoms.LOGGER.debug("{}'s rickshaw takes {} {} to {}", f.name(), load.getCount(), load.getItem(), sister.name());
			return true;
		}
		return false;
	}

	private static void errand(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Faction sister = Factions.get(level.getServer(), job.errandTo);
		if (sister == null || sister.fallen() || job.goal == null) {
			start(job, HAUL, null, now);
			return;
		}
		if (drive(level, puller, job, job.goal, DEPOT_REACH, PULL_SPEED, now)) {
			unload(level, sister, rickshaw, job.goal);
			job.errandTo = -1;
			start(job, RETURN, null, now);
		}
	}

	// ---- its people -------------------------------------------------------------------------------------------------

	/** One of its people (grown or a child) off across the town: fetched, driven to one of its buildings, set down. */
	private static boolean fareOut(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		List<Villager> people = level.getEntitiesOfClass(Villager.class, new AABB(rank(level, f, job)).inflate(FARE_RANGE),
			v -> v.isAlive() && !v.isPassenger() && !v.isSleeping() && !(v instanceof Worker w && w.role() >= 0)
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())));
		List<Works.Built> built = Works.of(level.getServer(), f.id()).built();
		if (people.isEmpty() || built.isEmpty()) {
			return false;
		}
		Villager who = people.get(level.getRandom().nextInt(people.size()));
		Works.Built where = built.get(level.getRandom().nextInt(built.size()));
		BlockPos mid = new BlockPos((where.min().getX() + where.max().getX()) / 2, where.min().getY(), (where.min().getZ() + where.max().getZ()) / 2);
		if (mid.closerThan(who.blockPosition(), FARE_LEAST)) {
			return false;
		}
		start(job, FETCH, Construction.standable(level, mid, 4), now);
		job.other = who.getUUID();
		puller.setTask("fetching a fare");
		return true;
	}

	private static void fetch(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Entity who = job.other == null ? null : level.getEntity(job.other);
		if (!(who instanceof Villager v) || !v.isAlive() || v.isSleeping() || v.isPassenger() || job.goal == null) {
			start(job, WAIT, null, now);
			return;
		}
		if (drive(level, puller, job, v.blockPosition(), REACH, PULL_SPEED, now)) {
			v.getNavigation().stop();
			if (!v.startRiding(rickshaw)) {
				start(job, WAIT, null, now);
				return;
			}
			level.playSound(null, rickshaw.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
			BlockPos goal = job.goal;
			start(job, FARE, goal, now);
			job.other = v.getUUID();
		}
	}

	private static void fare(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Entity aboard = rickshaw.getFirstPassenger();
		if (aboard == null || job.goal == null) {
			start(job, RETURN, null, now);
			return;
		}
		puller.setTask("carrying a fare");
		if (drive(level, puller, job, job.goal, ARRIVE, PULL_SPEED, now)) {
			setDown(level, rickshaw, aboard);
			start(job, RETURN, null, now);
		}
	}

	/** Gets {@code who} down off the rickshaw, at its side. */
	private static void setDown(ServerLevel level, Wagon rickshaw, Entity who) {
		who.stopRiding();
		if (who instanceof Villager v) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6F, 1.0F);
		}
	}

	// ---- the ruler --------------------------------------------------------------------------------------------------

	/** Its ruler, out on his round on foot with no carriage of his own and nobody fetching him, if he's near. */
	private static @Nullable Folk rulerToFetch(ServerLevel level, Faction f, Wagon rickshaw) {
		if (!f.type().isRealm() || f.leader().isEmpty() || Carriages.of(level, f) != null || !Royals.daytime(level)) {
			return null;
		}
		Entity e = level.getEntity(f.leader().get());
		if (!(e instanceof Folk ruler) || !ruler.leader() || ruler.isPassenger() || ruler.getTarget() != null
			|| !Royals.out(ruler) || ruler.distanceTo(rickshaw) > RULER_RANGE) {
			return null;
		}
		for (Job other : JOBS.values()) {
			if (other.phase == RULER && f.leader().get().equals(other.other)) {
				return null;
			}
		}
		return ruler;
	}

	private static void ruler(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Entity e = job.other == null ? null : level.getEntity(job.other);
		if (!(e instanceof Folk ruler) || !ruler.isAlive() || !Royals.daytime(level)) {
			start(job, rickshaw.isVehicle() ? WAIT : RETURN, null, now);
			return;
		}
		if (ruler.getVehicle() == rickshaw) {
			// (he's aboard: he says where, {@link Carriages#moveTo} drives; the trip lasts as long as his round)
			puller.setTask("carrying the " + (f.type() == FactionType.KINGDOM ? "king" : "lord"));
			job.startedAt = now;
			return;
		}
		if (ruler.isPassenger() || !Royals.out(ruler)) {
			// (he got down, or went in)
			start(job, RETURN, null, now);
			return;
		}
		puller.setTask("fetching the " + (f.type() == FactionType.KINGDOM ? "king" : "lord"));
		drive(level, puller, job, ruler.blockPosition(), Carriages.BOARD_REACH - 2.0, PULL_SPEED, now);
	}

	/** The rickshaw waiting by {@code ruler} for him to get in, or null. */
	static @Nullable Wagon waitingFor(ServerLevel level, Folk ruler) {
		for (Job job : JOBS.values()) {
			if (job.phase == RULER && ruler.getUUID().equals(job.other) && job.rickshaw != null && !job.rickshaw.isRemoved()
				&& !job.rickshaw.isVehicle() && job.rickshaw.distanceTo(ruler) <= Carriages.BOARD_REACH) {
				return job.rickshaw;
			}
		}
		return null;
	}

	// ---- players ----------------------------------------------------------------------------------------------------

	/** The settlements a rickshaw of {@code f} would take {@code player} to, nearest first, with their fares. */
	private static List<Faction> destinations(ServerLevel level, Faction f, ServerPlayer player, Vec3 from) {
		List<Faction> out = new ArrayList<>();
		for (Faction other : Factions.all(level.getServer())) {
			double d = Math.sqrt(Vec3.atBottomCenterOf(other.seat()).distanceToSqr(from));
			if (other.fallen() || other.type() == FactionType.BARBARIAN || d < RIDE_LEAST || d > RIDE_RANGE
				|| Reputation.get(player, other.id()) <= Reputation.HOSTILE || Relations.between(f, other) == Relation.WAR) {
				continue;
			}
			out.add(other);
		}
		out.sort(Comparator.comparingDouble(o -> Vec3.atBottomCenterOf(o.seat()).distanceToSqr(from)));
		return out.size() > RIDE_CHOICES ? out.subList(0, RIDE_CHOICES) : out;
	}

	static int fare(Vec3 from, Faction to) {
		double d = Math.sqrt(Vec3.atBottomCenterOf(to.seat()).distanceToSqr(from));
		return Math.min(FARE_MOST, FARE_BASE + (int)(d / FARE_PER_BLOCKS));
	}

	/** A player right-clicked a rickshaw: where it goes and the fares (each a click to hire it), or why not. */
	static void hail(ServerLevel level, Wagon rickshaw, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, rickshaw.faction());
		Entity puller = rickshaw.horse();
		if (f == null || f.fallen() || !isPuller(puller)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.no_puller").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.refused", puller.getName()).withStyle(ChatFormatting.RED));
			return;
		}
		if (!free(puller, rickshaw)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.busy", puller.getName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		List<Faction> to = destinations(level, f, player, rickshaw.position());
		if (to.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.nowhere", puller.getName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.offer", puller.getName()).withStyle(ChatFormatting.GOLD));
		for (Faction dest : to) {
			int fare = fare(rickshaw.position(), dest);
			int blocks = (int)Math.sqrt(Vec3.atBottomCenterOf(dest.seat()).distanceToSqr(rickshaw.position()));
			String command = "/kingdom ride " + rickshaw.getId() + " " + dest.id();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.choice", dest.name(), blocks, fare)
				.withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.jaymoskingdoms.rickshaw.hover", dest.name(), fare)))));
		}
	}

	/** The puller's waiting at the rank (or about the town on nothing that can't wait) with nobody aboard. */
	private static boolean free(Entity puller, Wagon rickshaw) {
		Job job = JOBS.get(puller.getUUID());
		return !rickshaw.isVehicle() && rickshaw.ride() < 0
			&& (job == null || job.phase == WAIT || job.phase == RETURN || job.phase == FETCH || job.phase == UNPARK);
	}

	/** {@code /kingdom ride <rickshaw> <settlement>}: the fare paid, the player aboard and away. Returns 1 if so. */
	static int hire(ServerPlayer player, int rickshawId, int destination) {
		if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(rickshawId) instanceof Wagon rickshaw) || rickshaw.kind() != Wagon.RICKSHAW
			|| player.distanceTo(rickshaw) > HIRE_REACH) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.too_far").withStyle(ChatFormatting.RED));
			return 0;
		}
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, rickshaw.faction());
		Faction to = Factions.get(server, destination);
		Entity puller = rickshaw.horse();
		if (f == null || to == null || !isPuller(puller) || !(puller instanceof Worker w)
			|| destinations(level, f, player, rickshaw.position()).stream().noneMatch(d -> d.id() == destination)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.no_puller").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!free(puller, rickshaw) || player.isPassenger()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.busy", puller.getName()).withStyle(ChatFormatting.RED));
			return 0;
		}
		int fare = fare(rickshaw.position(), to);
		if (!player.isCreative() && player.getInventory().countItem(Items.EMERALD) < fare) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.cant_pay", fare).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!player.startRiding(rickshaw)) {
			return 0;
		}
		if (!player.isCreative()) {
			int take = fare;
			for (int slot = 0; slot < player.getInventory().getContainerSize() && take > 0; slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (stack.is(Items.EMERALD)) {
					int n = Math.min(take, stack.getCount());
					stack.shrink(n);
					take -= n;
				}
			}
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + fare));
		}
		rickshaw.setParked(false);
		rickshaw.setRide(to.id());
		Job job = JOBS.computeIfAbsent(puller.getUUID(), u -> new Job());
		job.rickshaw = rickshaw;
		long now = level.getGameTime();
		start(job, HIRED, Construction.standable(level, to.seat(), 6), now);
		w.setTask("taking a passenger to " + to.name());
		level.playSound(null, rickshaw.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.off", puller.getName(), to.name(), fare).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static void hired(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw, long now) {
		Faction to = Factions.get(level.getServer(), rickshaw.ride());
		Entity aboard = rickshaw.getFirstPassenger();
		if (to == null || job.goal == null || !(aboard instanceof ServerPlayer player)) {
			// (they got down on the way: the fare's kept)
			rickshaw.setRide(-1);
			start(job, RETURN, null, now);
			return;
		}
		puller.setTask("taking a passenger to " + to.name());
		if (drive(level, puller, job, job.goal, ARRIVE, RIDE_SPEED, now)) {
			rickshaw.setRide(-1);
			player.stopRiding();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.arrived", to.name()).withStyle(ChatFormatting.GOLD));
			level.playSound(null, rickshaw.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
			// (the way home is long, and nobody's watching it: they're back at their rank in a puff once out of sight)
			start(job, RETURN, null, now);
		}
	}

	/** A puller far from home with nobody to see: back at the rank, rickshaw and all. */
	private static boolean homeUnseen(ServerLevel level, Faction f, Worker puller, Job job, Wagon rickshaw) {
		BlockPos rank = rank(level, f, job);
		if (puller.blockPosition().closerThan(rank, 96.0) || !level.getEntitiesOfClass(ServerPlayer.class, puller.mob().getBoundingBox().inflate(64.0),
			p -> !p.isSpectator()).isEmpty()) {
			return false;
		}
		puller.teleportTo(rank.getX() + 0.5, rank.getY(), rank.getZ() + 0.5);
		rickshaw.trail(puller.mob());
		return true;
	}

	// ---- getting about ----------------------------------------------------------------------------------------------

	/**
	 * Pulls the rickshaw toward {@code to} (a leg at a time); true once within {@code reach}. Stuck (a fence, a ledge,
	 * deep water) it hops ahead {@link #HOP} blocks in a puff, passenger and all, never the whole way at once.
	 */
	private static boolean drive(ServerLevel level, Worker puller, Job job, BlockPos to, double reach, double speed, long now) {
		Vec3 goal = Vec3.atBottomCenterOf(to);
		double dx = puller.getX() - goal.x, dz = puller.getZ() - goal.z;
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= reach && Math.abs(puller.getY() - goal.y) <= Gatherers.VERTICAL_SLACK) {
			puller.getNavigation().stop();
			return true;
		}
		if (job.phase == RETURN && job.rickshaw != null && !job.rickshaw.isVehicle()) {
			Faction f = Factions.get(level.getServer(), puller.factionId());
			if (f != null && homeUnseen(level, f, puller, job, job.rickshaw)) {
				return true;
			}
		}
		Gatherers.Trip trip = job.trip;
		double d = Math.sqrt(puller.distanceToSqr(goal));
		if (d < trip.best - 0.5) {
			trip.best = d;
			trip.improvedAt = now;
		}
		if (now - trip.improvedAt > STUCK_TICKS) {
			double step = Math.min(HOP, across);
			BlockPos ahead = BlockPos.containing(puller.getX() - dx / across * step, puller.getY(), puller.getZ() - dz / across * step);
			BlockPos at = Construction.standable(level, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ahead), 3);
			level.sendParticles(ParticleTypes.POOF, puller.getX(), puller.getY() + 1.0, puller.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			puller.getNavigation().stop();
			puller.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			if (job.rickshaw != null && !job.rickshaw.isRemoved()) {
				job.rickshaw.trail(puller.mob());
			}
			level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			trip.reset(now);
			return false;
		}
		if (puller.getNavigation().isDone() || now % 20 == 0) {
			BlockPos leg = Construction.leg(level, puller.mob(), to);
			puller.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, speed);
		}
		return false;
	}

	// ---- the rickshaw's own -----------------------------------------------------------------------------------------

	/** What a rickshaw's load looks like ({@link Wagon#STONE}, {@link Wagon#FOOD}, {@link Wagon#GOODS}). */
	static int look(List<ItemStack> cargo) {
		if (cargo.isEmpty()) {
			return Wagon.EMPTY;
		}
		ItemStack most = cargo.stream().max(Comparator.comparingInt(ItemStack::getCount)).orElseThrow();
		if (Storage.isFood(most)) {
			return Wagon.FOOD;
		}
		if (most.getItem() instanceof BlockItem block && (block.getBlock().defaultBlockState().is(BlockTags.BASE_STONE_OVERWORLD)
			|| block.getBlock().defaultBlockState().is(BlockTags.ORES) || most.is(Items.COBBLESTONE))) {
			return Wagon.STONE;
		}
		return Wagon.GOODS;
	}

	/** A rickshaw left without its puller: anyone aboard gets down, its load spills, it's taken apart. */
	static void abandoned(ServerLevel level, Wagon rickshaw) {
		rickshaw.ejectPassengers();
		for (ItemStack stack : rickshaw.takeCargo()) {
			Block.popResource(level, rickshaw.blockPosition(), stack);
		}
		level.sendParticles(ParticleTypes.POOF, rickshaw.getX(), rickshaw.getY() + 0.8, rickshaw.getZ(), 12, 0.5, 0.4, 0.5, 0.02);
		rickshaw.discard();
	}

	/** {@code mob} is a worker of {@code f}'s pulling a rickshaw ({@link Carriages}: a ruler's driver). */
	static @Nullable PathfinderMob puller(Wagon rickshaw) {
		Entity e = rickshaw.horse();
		return isPuller(e) && e instanceof PathfinderMob mob ? mob : null;
	}
}
