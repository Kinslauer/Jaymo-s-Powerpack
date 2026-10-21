package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * War parties: when a player is near a kingdom, fiefdom or village, the nearest barbarian camp within reach may raid
 * it. The raid is announced to the players nearby (war horns), and the party comes over the land from the camp's
 * side ({@link #MUSTER_DISTANCE} out), marches on the seat, falls on it (the target counts as {@link Economy#underAttack
 * under attack}), and loots its storage (real items, carried visibly, dropped if the carrier dies). Then the survivors
 * head home, and what they carry goes into the camp's loot pile (or is kept for it until its pile is loaded).
 *
 * <p>A war party is the camp's war band, mustered for the raid: it isn't counted in the camp's roster, and the camp
 * doesn't lose (or replace) the raiders who fall. Raids aren't saved: raiders left over after a restart go home.
 *
 * <p>A barbarian camp's party is its raiders ({@link Folk}); an illager camp's is pillagers and vindicators
 * ({@link Illagers}: one vindicator to every {@link #PILLAGERS_PER_VINDICATOR} pillagers), who carry their plunder in
 * their off hands.
 */
final class Raids {
	/** How often a camp considers a raid (ticks), the chance it does when it can, and the wait after one (ticks: two days). */
	static final int CHECK_TICKS = 200;
	static final float RAID_CHANCE = 0.12F;
	/** (0.7.122) The raid chance's factor in summer and in winter (Kitchen's seasons). */
	static final float SUMMER_RAIDS = 1.5F, WINTER_RAIDS = 0.4F;
	static final long COOLDOWN_TICKS = 48000L;
	/** The same settlement isn't raided again for this long (ticks: a day), by any camp. */
	static final long TARGET_COOLDOWN_TICKS = 24000L;
	/** A camp raids settlements this far from its fire, when a player is this near their seat. */
	static final double REACH = 420.0, PLAYER_RANGE = 80.0;
	/** The party: a base, one more per this many raiders in the camp, at most this many. */
	static final int PARTY_BASE = 3, PARTY_PER = 2, PARTY_MAX = 8;
	/** Where it musters: this far from the seat, toward the camp (other distances tried if that ground isn't loaded). */
	static final int[] MUSTER_DISTANCE = {48, 40, 56, 32, 64};
	/** The raid is announced to players this near the target's seat. */
	static final double ANNOUNCE_RANGE = 160.0;
	/** How often the raid is driven (ticks); raiders march at this pace, walking to a waypoint this far ahead. */
	static final int TICK = 10;
	static final double MARCH_SPEED = 1.0, WAYPOINT = 16.0;
	/** Raiders keep within this of the seat while they're on the attack. */
	static final int RAID_HOME_RADIUS = 20;
	/** It has arrived when a raider is this near the seat, or after this long (ticks). */
	static final double ARRIVE_RANGE = 14.0;
	static final long MARCH_TICKS = 20 * 90;
	/** Plundering: how near the storage a raider must be, how much one carries away, and how long they keep at it. */
	static final double LOOT_REACH = 4.0;
	static final int LOOT_PER_RAIDER = 32;
	static final long PLUNDER_TICKS = 20 * 120;
	/** Golems this near a raider at the seat turn on him. */
	static final double GOLEM_RANGE = 20.0;
	/** Going home: a raider is home this near the camp's fire, or once no player is this near him (he's gone off over the land). */
	static final double HOME_REACH = 12.0, VANISH_RANGE = 48.0;
	/** A raid that drags on this long (ticks) is given up: its raiders go home. */
	static final long RAID_MAX_TICKS = 20 * 60 * 8;
	/** Reputation with the target for every raider a player kills during its raid. */
	static final int REP_RAIDER_SLAIN = 5;
	/** What raiders' {@link Folk#task} says. */
	static final String TASK = "raid";
	/** An illager war party: pillagers to each vindicator. */
	static final int PILLAGERS_PER_VINDICATOR = 2;
	/** (0.5.47) An orc war band: every this many, one is a ranger. */
	static final int ORCS_PER_RANGER = 3;
	/** (0.7.42) A pirate landing party: every this many, one is a gunner (pistols). */
	static final int PIRATES_PER_GUNNER = 3;
	/** (0.7.107) ...and every this many, one is a bombardier (dynamite), off the roster too. */
	static final int PIRATES_PER_BOMBARDIER = 4;

	enum Phase {
		MARCH, PLUNDER, RETURN
	}

	static final class Raid {
		final int camp, target;
		final List<UUID> party = new ArrayList<>();
		/** Raiders who found nothing left to take. */
		final Set<UUID> emptyHanded = new HashSet<>();
		Phase phase = Phase.MARCH;
		/** Raiders who made it home. */
		int home;
		final long started;
		long phaseAt;

		Raid(int camp, int target, long now) {
			this.camp = camp;
			this.target = target;
			this.started = now;
			this.phaseAt = now;
		}
	}

	/** Camp id → its raid under way. */
	private static final Map<Integer, Raid> ACTIVE = new HashMap<>();
	/** Settlement id → game time it was last raided. */
	private static final Map<Integer, Long> RAIDED = new HashMap<>();
	/** Camps whose raider fell (the roster's loss is undone next tick: raiders aren't on it). */
	private static final List<int[]> UNDO = new ArrayList<>();
	/** Raiders loaded with no raid to belong to (sent home next tick). */
	private static final List<Mob> STRAYS = new ArrayList<>();

	private Raids() {
	}

	static boolean raiding(int camp) {
		return ACTIVE.containsKey(camp);
	}

	static boolean isRaider(Folk folk) {
		return folk.task().equals(TASK);
	}

	/** A raider of either kind: a barbarian on a raid, or an illager mustered for one. */
	static boolean isRaider(Entity entity) {
		if (entity instanceof Folk folk) {
			return isRaider(folk);
		}
		Illagers.Tag tag = entity instanceof Mob ? Illagers.tag(entity) : null;
		return tag != null && tag.raid();
	}

	// ---- what raiders carry (a barbarian in his hands, an illager in his off hand) ---------------------------------------

	static boolean carrying(Mob raider) {
		return raider instanceof Folk folk ? folk.isCarrying() : !Illagers.carried(raider).isEmpty();
	}

	static void setCarried(Mob raider, ItemStack load) {
		if (raider instanceof Folk folk) {
			folk.setCarried(load);
		} else {
			Illagers.setCarried(raider, load);
		}
	}

	static ItemStack takeCarried(Mob raider) {
		return raider instanceof Folk folk ? folk.takeCarried() : Illagers.takeCarried(raider);
	}

	// ---- starting ------------------------------------------------------------------------------------------------

	/** (0.7.122) With Kitchen's seasons, raids are likelier in summer and rarer in winter, when camps sit by their fires. */
	static float seasonFactor(ServerLevel level) {
		String season = KitchenCompat.season(level);
		return "summer".equals(season) ? SUMMER_RAIDS : "winter".equals(season) ? WINTER_RAIDS : 1.0F;
	}

	/** Every {@link #CHECK_TICKS}: camps that may raid look for a settlement with a player near it. */
	private static void consider(MinecraftServer server, ServerLevel level) {
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			if (camp.fallen() || camp.leader().isEmpty() || raiding(camp.id())) {
				continue;
			}
			Camps.CampData data = Camps.data(server, camp.id());
			if (data == null || data.clearedAt() >= 0 || now - data.lastRaid() < COOLDOWN_TICKS
				|| (data.illager() && level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) || data.goblin()) {
				// (goblin villages don't raid: they're somewhere to go)
				continue;
			}
			Faction target = target(server, level, camp);
			if (target != null && random.nextFloat() < Moons.raidChance(server, RAID_CHANCE) * seasonFactor(level)) {
				start(level, camp, target);
			}
		}
	}

	/** The nearest standing settlement within {@link #REACH} of {@code camp} that has a player near it and hasn't been raided lately. */
	static @Nullable Faction target(MinecraftServer server, ServerLevel level, Faction camp) {
		long now = level.getGameTime();
		boolean pirates = Camps.pirate(server, camp.id());
		Faction best = null;
		double bestDist = REACH * REACH;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || now - RAIDED.getOrDefault(f.id(), -TARGET_COOLDOWN_TICKS) < TARGET_COOLDOWN_TICKS) {
				continue;
			}
			double d = horizontal(f.seat(), camp.seat());
			if (d >= bestDist || !level.isPositionEntityTicking(f.seat()) || !playerNear(level, f.seat(), PLAYER_RANGE)) {
				continue;
			}
			if (ACTIVE.values().stream().anyMatch(r -> r.target == f.id())) {
				continue;
			}
			if (pirates && !PirateCoves.coastal(level, f)) {
				// (0.7.42) pirates raid only from the sea
				continue;
			}
			best = f;
			bestDist = d;
		}
		return best;
	}

	/** The nearest standing settlement to {@code camp}, however far (for {@code /faction camp raid}). */
	static @Nullable Faction nearestTarget(MinecraftServer server, Faction camp) {
		Faction best = null;
		double bestDist = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			double d = horizontal(f.seat(), camp.seat());
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && d < bestDist) {
				best = f;
				bestDist = d;
			}
		}
		return best;
	}

	/** Musters {@code camp}'s war party on {@code target}'s land and sends it in. False if there was nowhere loaded to muster. */
	static boolean start(ServerLevel level, Faction camp, Faction target) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		boolean pirates = Camps.pirate(server, camp.id());
		BlockPos muster = pirates ? PirateCoves.landing(level, target) : muster(level, camp, target);
		if (muster == null || raiding(camp.id())) {
			return false;
		}
		long now = level.getGameTime();
		Raid raid = new Raid(camp.id(), target.id(), now);
		ACTIVE.put(camp.id(), raid);
		RAIDED.put(target.id(), now);
		Camps.noteRaid(server, camp.id(), now);
		boolean illagers = Camps.illager(server, camp.id()), orcs = Camps.orc(server, camp.id());
		int band = illagers ? Illagers.fighters(camp) : orcs ? OrcCamps.fighters(camp) : pirates ? PirateCoves.fighters(camp)
			: camp.holdings().count(Folk.BARBARIAN);
		int size = Math.min(PARTY_MAX, PARTY_BASE + band / PARTY_PER);
		for (int i = 0; i < size; i++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			if (illagers) {
				int role = i % (PILLAGERS_PER_VINDICATOR + 1) == PILLAGERS_PER_VINDICATOR ? Illagers.VINDICATOR : Illagers.PILLAGER;
				Mob raider = Illagers.spawn(level, camp, role, at, target.seat(), RAID_HOME_RADIUS, true);
				if (raider != null) {
					raid.party.add(raider.getUUID());
				}
				continue;
			}
			Folk raider = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (raider == null) {
				continue;
			}
			raider.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			// (0.5.47) an orc war band: grunts, every third one a ranger
			int role = pirates ? (i % PIRATES_PER_GUNNER == PIRATES_PER_GUNNER - 1 ? Pirates.GUNNER
					: i % PIRATES_PER_BOMBARDIER == 1 ? Pirates.BOMBARDIER : Pirates.DECKHAND)
				: !orcs ? Folk.BARBARIAN : i % ORCS_PER_RANGER == ORCS_PER_RANGER - 1 ? Orcs.RANGER : Orcs.GRUNT;
			raider.setup(role, camp, target.seat(), RAID_HOME_RADIUS);
			level.addFreshEntity(raider);
			// (after it's added: its load mustn't take it for a stray)
			raider.setTask(TASK);
			raid.party.add(raider.getUUID());
			// (0.5.47) some ride steppe ponies (not pirates: they come ashore from their ship)
			if (!pirates) {
				Stables.pony(level, raider, camp, i);
			}
		}
		tell(level, target.seat(), Component.translatable(illagers ? "message.jaymoskingdoms.raid.announce.illager"
				: orcs ? "message.jaymoskingdoms.raid.announce.orc" : pirates ? "message.jaymoskingdoms.raid.announce.pirate"
				: "message.jaymoskingdoms.raid.announce",
			camp.name(), target.name()).withStyle(ChatFormatting.RED));
		for (ServerPlayer player : players(level, target.seat(), ANNOUNCE_RANGE)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 0.8F);
		}
		if (pirates) {
			// (0.7.42) their ship stands off the landing while it's on
			PirateCoves.raidStarted(level, camp, muster);
		}
		JaymosKingdoms.LOGGER.info("{} raids {} with {} raiders from {}", camp.name(), target.name(), raid.party.size(), muster);
		return true;
	}

	/** Loaded open ground on {@code target}'s land toward {@code camp}, a march from its seat. */
	private static @Nullable BlockPos muster(ServerLevel level, Faction camp, Faction target) {
		double dx = camp.seat().getX() - target.seat().getX(), dz = camp.seat().getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		for (int distance : MUSTER_DISTANCE) {
			double d = Math.min(distance, Math.max(8.0, len - 8.0));
			BlockPos column = target.seat().offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(at.below()).isEmpty() && level.getFluidState(at).isEmpty()) {
				return at;
			}
		}
		return null;
	}

	// ---- driving ---------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (int[] lost : UNDO) {
			// (camp, role: a barbarian, or one of an orc war band)
			int role = lost[1];
			Factions.update(server, lost[0], f -> f.withHoldings(f.holdings().adjust(role, 1).adjustVacancy(role, -1)));
		}
		UNDO.clear();
		if (!STRAYS.isEmpty()) {
			List<Mob> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Mob stray : strays) {
				Raid raid = ACTIVE.get(campOf(stray));
				// (looked at a tick late: a new war party's illagers load before they're on its roll)
				if (stray.isAlive() && !stray.isRemoved() && (raid == null || !raid.party.contains(stray.getUUID()))) {
					goHome(level, Factions.get(server, campOf(stray)), stray);
				}
			}
		}
		long now = level.getGameTime();
		if (now % CHECK_TICKS == 0) {
			consider(server, level);
		}
		if (now % TICK != 0) {
			return;
		}
		for (Raid raid : List.copyOf(ACTIVE.values())) {
			drive(level, raid, now);
		}
	}

	private static void drive(ServerLevel level, Raid raid, long now) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, raid.camp);
		Faction target = Factions.get(server, raid.target);
		List<Mob> party = new ArrayList<>();
		for (UUID id : raid.party) {
			if (level.getEntity(id) instanceof Mob raider && raider.isAlive()) {
				party.add(raider);
			}
		}
		if (camp == null || camp.fallen()) {
			// (its chief is dead: see scatter)
			end(level, raid, party, null);
			return;
		}
		if (party.isEmpty()) {
			if (raid.phase != Phase.RETURN && target != null) {
				tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.beaten", camp.name()).withStyle(ChatFormatting.GREEN));
			}
			end(level, raid, party, camp);
			return;
		}
		if (now - raid.started > RAID_MAX_TICKS) {
			end(level, raid, party, camp);
			return;
		}
		if (target == null || target.fallen()) {
			raid.phase = Phase.RETURN;
		}
		switch (raid.phase) {
			case MARCH -> {
				boolean arrived = now - raid.phaseAt > MARCH_TICKS;
				for (Mob raider : party) {
					if (raider.getTarget() == null) {
						walk(level, raider, target.seat());
					}
					arrived |= raider.distanceToSqr(Vec3.atCenterOf(target.seat())) < ARRIVE_RANGE * ARRIVE_RANGE;
				}
				if (arrived) {
					raid.phase = Phase.PLUNDER;
					raid.phaseAt = now;
					Economy.noteAttack(server, target, party.getFirst());
					tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.arrive", camp.name(), target.name())
						.withStyle(ChatFormatting.RED));
				}
			}
			case PLUNDER -> {
				Economy.noteAttack(server, target, party.getFirst());
				BlockPos depot = Builders.depot(target);
				boolean done = true;
				for (Mob raider : party) {
					for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, raider.getBoundingBox().inflate(GOLEM_RANGE),
						g -> g.isAlive() && g.getTarget() == null)) {
						golem.setTarget(raider);
					}
					if (carrying(raider) || raid.emptyHanded.contains(raider.getUUID())) {
						continue;
					}
					done = false;
					if (raider.getTarget() != null) {
						continue;
					}
					if (raider.distanceToSqr(Vec3.atCenterOf(depot)) <= LOOT_REACH * LOOT_REACH || nearStorage(raider, target)) {
						plunder(level, raider, target, raid);
					} else {
						walk(level, raider, depot);
					}
				}
				if (done || now - raid.phaseAt > PLUNDER_TICKS) {
					raid.phase = Phase.RETURN;
					raid.phaseAt = now;
					for (Mob raider : party) {
						raider.setHomeTo(camp.seat(), Folk.HOME_RADIUS);
					}
				}
			}
			case RETURN -> {
				for (Mob raider : party) {
					if (raider.distanceToSqr(Vec3.atCenterOf(camp.seat())) < HOME_REACH * HOME_REACH || !playerNear(level, raider.blockPosition(), VANISH_RANGE)) {
						goHome(level, camp, raider);
						raid.party.remove(raider.getUUID());
						raid.home++;
					} else if (raider.getTarget() == null) {
						walk(level, raider, camp.seat());
					}
				}
				// (raiders who wandered out of loaded land are home: they'll be sent on when they load)
				int before = raid.party.size();
				raid.party.removeIf(id -> !(level.getEntity(id) instanceof Mob));
				raid.home += before - raid.party.size();
				if (raid.party.isEmpty() && target != null) {
					tell(level, target.seat(), raid.home > 0
						? Component.translatable("message.jaymoskingdoms.raid.withdraw", camp.name()).withStyle(ChatFormatting.GOLD)
						: Component.translatable("message.jaymoskingdoms.raid.beaten", camp.name()).withStyle(ChatFormatting.GREEN));
				}
			}
		}
	}

	private static boolean nearStorage(Mob raider, Faction target) {
		for (BlockPos pos : target.holdings().storage()) {
			if (raider.distanceToSqr(Vec3.atCenterOf(pos)) <= LOOT_REACH * LOOT_REACH) {
				return true;
			}
		}
		return false;
	}

	/** A raider at the storage takes what he can carry (the valuables first, half the time). */
	private static void plunder(ServerLevel level, Mob raider, Faction target, Raid raid) {
		List<ItemStack> contents = Storage.contents(level, target);
		if (contents.isEmpty()) {
			raid.emptyHanded.add(raider.getUUID());
			return;
		}
		RandomSource random = level.getRandom();
		List<ItemStack> valuables = contents.stream()
			.filter(s -> s.is(Items.EMERALD) || s.is(Items.GOLD_INGOT) || s.is(Items.IRON_INGOT) || s.is(Items.DIAMOND)).toList();
		ItemStack pick = !valuables.isEmpty() && random.nextBoolean() ? valuables.get(random.nextInt(valuables.size()))
			: contents.get(random.nextInt(contents.size()));
		ItemStack taken = Storage.take(level, target, s -> ItemStack.isSameItemSameComponents(s, pick), LOOT_PER_RAIDER);
		if (taken.isEmpty()) {
			raid.emptyHanded.add(raider.getUUID());
			return;
		}
		setCarried(raider, taken);
		level.playSound(null, raider.getX(), raider.getY(), raider.getZ(), SoundEvents.BARREL_OPEN, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** Sends {@code raider} toward {@code to}, a waypoint at a time (long paths fail). */
	private static void walk(ServerLevel level, Mob raider, BlockPos to) {
		if (!raider.getNavigation().isDone() && level.getGameTime() % (TICK * 4) != 0) {
			return;
		}
		double dx = to.getX() + 0.5 - raider.getX(), dz = to.getZ() + 0.5 - raider.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		BlockPos goal = to;
		if (len > WAYPOINT) {
			BlockPos column = BlockPos.containing(raider.getX() + dx / len * WAYPOINT, raider.getY(), raider.getZ() + dz / len * WAYPOINT);
			goal = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		}
		raider.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, MARCH_SPEED);
	}

	/** A raider is home: what he carries goes into the loot pile (or is kept for it), and he rejoins the camp. */
	private static void goHome(ServerLevel level, @Nullable Faction camp, Mob raider) {
		ItemStack load = takeCarried(raider);
		if (camp != null && !camp.fallen() && !load.isEmpty()) {
			Camps.deposit(level, camp, load);
		} else if (!load.isEmpty()) {
			raider.spawnAtLocation(level, load);
		}
		raider.discard();
	}

	private static void end(ServerLevel level, Raid raid, List<Mob> party, @Nullable Faction camp) {
		ACTIVE.remove(raid.camp);
		for (Mob raider : party) {
			goHome(level, camp, raider);
		}
	}

	// ---- events ------------------------------------------------------------------------------------------------------

	/**
	 * A raider of {@code camp} fell: off the party; the camp doesn't count the loss (a barbarian's death is undone on its
	 * roster: {@code folk}); a player who killed him earns the target's thanks.
	 */
	static void raiderDied(ServerLevel level, LivingEntity raider, int camp, DamageSource source, boolean folk) {
		if (folk && !(raider instanceof Folk sailor && Pirates.offRoster(sailor.role()))) {
			// ((0.7.42) a pirate gunner in a landing party was never on the roster)
			UNDO.add(new int[] {camp, raider instanceof Folk f ? f.role() : Folk.BARBARIAN});
		}
		Raid raid = ACTIVE.get(camp);
		if (raid == null) {
			return;
		}
		raid.party.remove(raider.getUUID());
		Faction target = Factions.get(level.getServer(), raid.target);
		if (target != null && source.getEntity() instanceof ServerPlayer player) {
			Reputation.change(player, target, REP_RAIDER_SLAIN);
		}
	}

	/** A raider loaded: if his raid is over (or was lost to a restart), he's sent home next tick. */
	static void loaded(Entity entity) {
		if (entity instanceof Mob raider && isRaider(entity)) {
			Raid raid = ACTIVE.get(campOf(raider));
			if (raid == null || !raid.party.contains(raider.getUUID())) {
				STRAYS.add(raider);
			}
		}
	}

	/** The camp a raider (of either kind) belongs to. */
	private static int campOf(Mob raider) {
		if (raider instanceof Folk folk) {
			return folk.factionId();
		}
		Illagers.Tag tag = Illagers.tag(raider);
		return tag == null ? -1 : tag.camp();
	}

	/** {@code camp}'s chief is dead: its raiders lose heart and flee, dropping their plunder. */
	static void scatter(ServerLevel level, int camp) {
		Raid raid = ACTIVE.remove(camp);
		if (raid == null) {
			return;
		}
		for (UUID id : raid.party) {
			if (level.getEntity(id) instanceof Mob raider && raider.isAlive()) {
				ItemStack load = takeCarried(raider);
				if (!load.isEmpty()) {
					raider.spawnAtLocation(level, load);
				}
				level.sendParticles(ParticleTypes.POOF, raider.getX(), raider.getY() + 1.0, raider.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				raider.discard();
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
		RAIDED.clear();
		UNDO.clear();
		STRAYS.clear();
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	static boolean playerNear(ServerLevel level, BlockPos pos, double range) {
		return !players(level, pos, range).isEmpty();
	}

	static List<ServerPlayer> players(ServerLevel level, BlockPos pos, double range) {
		double r2 = range * range;
		return level.players().stream().filter(p -> !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(pos)) <= r2).toList();
	}

	/** Tells the players near {@code pos}. */
	static void tell(ServerLevel level, BlockPos pos, Component message) {
		for (ServerPlayer player : players(level, pos, ANNOUNCE_RANGE)) {
			player.sendSystemMessage(message);
		}
	}
}
