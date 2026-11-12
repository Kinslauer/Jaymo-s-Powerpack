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
	/** (0.7.123) The raid chance's factor in summer and in winter (Kitchen's seasons). */
	static final float SUMMER_RAIDS = 1.5F, WINTER_RAIDS = 0.4F;
	/** (0.7.490) The raid chance's factor for a camp that can't feed its folk ({@link HordeEconomy#hungry}). */
	static final float HUNGRY_RAIDS = 3.0F;
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
	/** (0.7.490) How far (blocks) from the seat a party looks for loose chests and barrels to break into. */
	static final int LOOSE_SCAN = 28;
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
		/** (0.7.490) How many items and coins the party has taken so far. */
		int looted;
		/** (0.7.490) Loose chests and barrels on the target's land, looked for once when the party arrives (null until then). */
		@Nullable List<BlockPos> loose;
		final long started;
		long phaseAt;
		/** Raiders past the way in their camp remembers into this town; whether this raid has noted its own way in. */
		final Set<UUID> pastWay = new HashSet<>();
		boolean wayNoted;

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

	/** (0.7.490) The settlement camp {@code camp} is raiding just now (its id), or -1 if it isn't. */
	static int targetOf(int camp) {
		Raid raid = ACTIVE.get(camp);
		return raid == null ? -1 : raid.target;
	}

	/** (0.7.490) Camp {@code a} is raiding camp {@code b} just now, or the other way about. */
	static boolean between(int a, int b) {
		Raid ab = ACTIVE.get(a), ba = ACTIVE.get(b);
		return ab != null && ab.target == b || ba != null && ba.target == a;
	}

	/** (0.7.443) Settlement {@code target} has a raid on it just now. */
	static boolean raided(int target) {
		return ACTIVE.values().stream().anyMatch(r -> r.target == target);
	}

	static boolean isRaider(Folk folk) {
		return folk.task().equals(TASK);
	}

	/** A raider of either kind: a barbarian on a raid (a folk or (0.7.295) an MCA villager), or an illager mustered for one. */
	static boolean isRaider(Entity entity) {
		if (entity instanceof Worker folk) {
			return TASK.equals(folk.task());
		}
		Illagers.Tag tag = entity instanceof Mob ? Illagers.tag(entity) : null;
		return tag != null && tag.raid();
	}

	// ---- what raiders carry (a barbarian in his hands, an illager in his off hand) ---------------------------------------

	static boolean carrying(Mob raider) {
		return raider instanceof Worker folk ? folk.isCarrying() : !Illagers.carried(raider).isEmpty();
	}

	static void setCarried(Mob raider, ItemStack load) {
		if (raider instanceof Worker folk) {
			folk.setCarried(load);
		} else {
			Illagers.setCarried(raider, load);
		}
	}

	static ItemStack takeCarried(Mob raider) {
		return raider instanceof Worker folk ? folk.takeCarried() : Illagers.takeCarried(raider);
	}

	// ---- starting ------------------------------------------------------------------------------------------------

	/** (0.7.123) With Kitchen's seasons, raids are likelier in summer and rarer in winter, when camps sit by their fires. */
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
			// ((0.7.490) a hungry greenskin camp raids twice as often, and a hungry goblin village raids at all)
			boolean hungry = data != null && (data.orc() || data.goblin()) && HordeEconomy.hungry(server, camp.id());
			// ((0.7.491) a greenskin realm on the warpath: it and its vassals all go for the one target, often)
			Faction warpath = data == null ? null : warpathTarget(server, level, camp);
			if (data == null || data.clearedAt() >= 0
				|| now - data.lastRaid() < (warpath != null ? COOLDOWN_TICKS / 4 : hungry ? COOLDOWN_TICKS / 2 : COOLDOWN_TICKS)
				|| (data.illager() && level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) || data.goblin() && !hungry && warpath == null) {
				// (goblin villages don't raid: they're somewhere to go, unless they're starving or their king calls)
				continue;
			}
			Faction target = warpath != null ? warpath : target(server, level, camp);
			// ((0.7.530) a wall and a watchtower put raiders off; a horde on the warpath isn't)
			float chance = warpath != null ? HordeCity.RAID_CHANCE
				: Moons.raidChance(server, RAID_CHANCE) * seasonFactor(level) * (hungry ? HUNGRY_RAIDS : 1.0F) * TownProjects.raidFactor(server, target);
			if (target != null && random.nextFloat() < chance) {
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
			if (f.fallen() || f.type() == FactionType.BARBARIAN && !Brutality.rival(server, camp, f) || now - RAIDED.getOrDefault(f.id(), -TARGET_COOLDOWN_TICKS) < TARGET_COOLDOWN_TICKS) {
				continue;
			}
			double d = horizontal(f.seat(), camp.seat());
			if (d >= bestDist || !level.isPositionEntityTicking(f.seat()) || !playerNear(level, f.seat(), PLAYER_RANGE)) {
				continue;
			}
			if (ACTIVE.values().stream().anyMatch(r -> r.target == f.id())) {
				continue;
			}
			if ((Camps.orc(server, camp.id()) || Camps.goblin(server, camp.id())) && TribePacts.spared(server, camp, f)) {
				// (0.7.441) a haven that lives alongside the tribes isn't raided by them; (0.7.443) nor one that pays them
				continue;
			}
			if (HordeCity.spared(server, camp, f)) {
				// (0.7.491) nor a realm that pays the greenskin king tribute
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

	/**
	 * (0.7.491) The target of the warpath {@code camp}'s greenskin realm is on ({@link HordeCity#warpath}), if it can be
	 * raided now: loaded, a player near its seat, and this camp not already at it.
	 */
	private static @Nullable Faction warpathTarget(MinecraftServer server, ServerLevel level, Faction camp) {
		Faction f = HordeCity.warpath(server, camp);
		if (f == null || !level.isPositionEntityTicking(f.seat()) || !playerNear(level, f.seat(), PLAYER_RANGE)) {
			return null;
		}
		return f;
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
		// (0.7.530) a watchtower warns players near the town where the party musters
		TownProjects.watch(level, target, muster);
		// (0.7.443) a tribe that favours one of the raided realm's own may send them a war band
		TribePacts.called(level, target, camp);
		Camps.noteRaid(server, camp.id(), now);
		boolean illagers = Camps.illager(server, camp.id()), orcs = Camps.orc(server, camp.id()), goblins = Camps.goblin(server, camp.id());
		int band = illagers ? Illagers.fighters(camp) : orcs ? OrcCamps.fighters(camp) : goblins ? GoblinVillages.goblins(camp) : pirates ? PirateCoves.fighters(camp)
			: camp.holdings().count(Folk.BARBARIAN);
		// ((0.7.491) a warpath's bands are bigger)
		Faction path = HordeCity.warpath(server, camp);
		boolean warpath = path != null && path.id() == target.id();
		int size = warpath ? Math.min(HordeCity.PARTY_MAX, PARTY_BASE + HordeCity.PARTY_EXTRA + band / PARTY_PER) : Math.min(PARTY_MAX, PARTY_BASE + band / PARTY_PER);
		for (int i = 0; i < size; i++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			Mob member = member(level, camp, i, at, target.seat(), RAID_HOME_RADIUS, true);
			if (member == null) {
				continue;
			}
			if (member instanceof Worker w) {
				// (after it's added: its load mustn't take it for a stray)
				w.setTask(TASK);
				// (0.5.47) some ride steppe ponies (not pirates: they come ashore from their ship)
				if (!pirates) {
					Stables.pony(level, w, camp, i);
				}
			}
			raid.party.add(member.getUUID());
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
		// its praised fighters and bought swords go too (docs/Pawn-Plan.md rows A3, A6)
		BandTallies.raidStarted(level, camp, target, muster);
		JaymosKingdoms.LOGGER.info("{} raids {} with {} raiders from {}", camp.name(), target.name(), raid.party.size(), muster);
		return true;
	}

	/**
	 * One of {@code camp}'s fighters, the {@code i}th of a band, made at {@code at} and kept within {@code radius} of
	 * {@code home}: a pillager or vindicator for an illager camp, else its own kind (an MCA villager in a raider's place
	 * with MCA). {@code raid}: on a raid (an illager's tag says so; a raider's task is the caller's to set).
	 */
	static @Nullable Mob member(ServerLevel level, Faction camp, int i, BlockPos at, BlockPos home, int radius, boolean raid) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		boolean pirates = Camps.pirate(server, camp.id());
		boolean illagers = Camps.illager(server, camp.id()), orcs = Camps.orc(server, camp.id()), goblins = Camps.goblin(server, camp.id());
		if (illagers) {
			int role = i % (PILLAGERS_PER_VINDICATOR + 1) == PILLAGERS_PER_VINDICATOR ? Illagers.VINDICATOR : Illagers.PILLAGER;
			return Illagers.spawn(level, camp, role, at, home, radius, raid);
		}
		Folk raider = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (raider == null) {
			return null;
		}
		raider.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		// (0.5.47) an orc war band: grunts, every third one a ranger
		int role = pirates ? (i % PIRATES_PER_GUNNER == PIRATES_PER_GUNNER - 1 ? Pirates.GUNNER
				: i % PIRATES_PER_BOMBARDIER == 1 ? Pirates.BOMBARDIER : Pirates.DECKHAND)
			: goblins ? i % ORCS_PER_RANGER == ORCS_PER_RANGER - 1 ? Goblins.SLINGER : Goblins.GOBLIN
			: !orcs ? Folk.BARBARIAN : i % ORCS_PER_RANGER == ORCS_PER_RANGER - 1 ? Orcs.RANGER : Orcs.GRUNT;
		raider.setup(role, camp, home, radius);
		level.addFreshEntity(raider);
		// (0.7.295) with MCA, an MCA villager in his place (Andy, retiring the folk: "all mca please")
		if (McaCompat.raider(level, raider) instanceof Mob v && v instanceof Worker) {
			return v;
		}
		return raider;
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
				// a camp that has got into this town before comes the same way in (docs/Pawn-Plan.md row B2)
				BlockPos way = Wayfinding.wayIn(server, raid.camp, raid.target);
				for (Mob raider : party) {
					if (way != null && !raid.pastWay.contains(raider.getUUID()) && raider.blockPosition().distSqr(way) < 6 * 6) {
						raid.pastWay.add(raider.getUUID());
					}
					if (target(raider) == null) {
						walk(level, raider, way != null && !raid.pastWay.contains(raider.getUUID()) ? way : target.seat());
					}
					if (!raid.wayNoted && raider.distanceToSqr(Vec3.atCenterOf(target.seat())) < Wayfinding.WAY_IN_NEAR * Wayfinding.WAY_IN_NEAR) {
						raid.wayNoted = true;
						Wayfinding.noteWayIn(server, raid.camp, raid.target, raider.blockPosition());
						if (way != null) {
							Callouts.say(raider, "raid.way", null, false);
						}
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
				if (raid.loose == null) {
					raid.loose = new ArrayList<>(Plunder.looseAll(level, target, LOOSE_SCAN));
				}
				// (0.7.490) once the storehouse is bare they break into the chests and barrels standing about the land
				boolean bare = !raid.loose.isEmpty() && Storage.contents(level, target).isEmpty();
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
					if (target(raider) != null) {
						continue;
					}
					// (0.7.490) the last loose chest may have gone to an earlier raider this pass: back to the depot then
					bare &= !raid.loose.isEmpty();
					BlockPos aim = bare ? raid.loose.get(Math.floorMod(raider.getUUID().hashCode(), raid.loose.size())) : depot;
					if (raider.distanceToSqr(Vec3.atCenterOf(aim)) <= LOOT_REACH * LOOT_REACH || !bare && nearStorage(raider, target)) {
						plunder(level, raider, target, raid);
						if (bare && raid.emptyHanded.remove(raider.getUUID())) {
							// (that one was bare or smashed already: on to the next)
							raid.loose.remove(aim);
						}
					} else {
						walk(level, raider, aim);
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
					} else if (target(raider) == null) {
						walk(level, raider, camp.seat());
					}
				}
				// (raiders who wandered out of loaded land are home: they'll be sent on when they load)
				int before = raid.party.size();
				raid.party.removeIf(id -> !(level.getEntity(id) instanceof Mob));
				raid.home += before - raid.party.size();
				if (raid.party.isEmpty() && target != null) {
					if (raid.looted > 0) {
						tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.looted", camp.name(), raid.looted).withStyle(ChatFormatting.GOLD));
					}
					tell(level, target.seat(), raid.home > 0
						? Component.translatable("message.jaymoskingdoms.raid.withdraw", camp.name()).withStyle(ChatFormatting.GOLD)
						: Component.translatable("message.jaymoskingdoms.raid.beaten", camp.name()).withStyle(ChatFormatting.GREEN));
				}
			}
		}
	}

	/** (0.7.295) A raider's foe: a villager's is its brain's. */
	private static @Nullable LivingEntity target(Mob raider) {
		return raider instanceof net.minecraft.world.entity.PathfinderMob m ? Rulers.target(m) : raider.getTarget();
	}

	/**
	 * (0.7.295) A raider who's an MCA villager ({@code Workers.step}): true while he marches, makes for the storehouse or
	 * goes home as his raid sends him (in place of his brain); false for a fight (a foe on him or near).
	 */
	static boolean villagerStep(ServerLevel level, Worker w) {
		net.minecraft.world.entity.PathfinderMob mob = w.mob();
		if (mob instanceof Folk || !isRaider(mob) || Rulers.target(mob) != null || McaCompat.busy(mob) || Downed.isDowned(mob)) {
			return false;
		}
		Raid raid = ACTIVE.get(w.factionId());
		return raid != null && raid.party.contains(mob.getUUID()) && !Rulers.foeNear(level, mob);
	}

	private static boolean nearStorage(Mob raider, Faction target) {
		for (BlockPos pos : target.holdings().storage()) {
			if (raider.distanceToSqr(Vec3.atCenterOf(pos)) <= LOOT_REACH * LOOT_REACH) {
				return true;
			}
		}
		return false;
	}

	/** (0.7.490) What a raider (or a war's victor) takes first: coin of any realm, anything gold, iron and diamonds. */
	static boolean valuable(ItemStack s) {
		return Plunder.valuable(s);
	}

	/**
	 * A raider at the settlement takes what he can carry ({@link Plunder#take}): coin now and then, valuables from its
	 * storage first, then the chests and barrels standing loose on its land, which he breaks into (0.7.490).
	 */
	private static void plunder(ServerLevel level, Mob raider, Faction target, Raid raid) {
		ItemStack taken = Plunder.take(level, raider, target, LOOT_PER_RAIDER, Plunder.SMASH_CHANCE, PirateCoves.is(level.getServer(), raid.camp));
		if (taken.isEmpty()) {
			raid.emptyHanded.add(raider.getUUID());
			return;
		}
		raid.looted += taken.getCount();
		setCarried(raider, taken);
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
			// ((0.7.490) a greenskin camp bends what it brings home)
			Camps.deposit(level, camp, Camps.orc(level.getServer(), camp.id()) || Camps.goblin(level.getServer(), camp.id()) ? Plunder.bend(load) : load);
		} else if (!load.isEmpty()) {
			raider.spawnAtLocation(level, load);
		}
		// ((0.7.295) a villager out of MCA's family tree too)
		McaCompat.vanish(raider);
	}

	private static void end(ServerLevel level, Raid raid, List<Mob> party, @Nullable Faction camp) {
		ACTIVE.remove(raid.camp);
		// (0.7.530) the town is left to pick up the pieces
		TownTroubles.event(level.getServer(), raid.target, TownTroubles.RAID, Integer.toString(raid.camp));
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
		if (folk && !(raider instanceof Worker sailor && Pirates.offRoster(sailor.role()))) {
			// ((0.7.42) a pirate gunner in a landing party was never on the roster)
			UNDO.add(new int[] {camp, raider instanceof Worker f ? f.role() : Folk.BARBARIAN});
		}
		Raid raid = ACTIVE.get(camp);
		if (raid == null) {
			return;
		}
		raid.party.remove(raider.getUUID());
		Faction target = Factions.get(level.getServer(), raid.target);
		if (target != null && target.type() != FactionType.BARBARIAN && source.getEntity() instanceof ServerPlayer player) {
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
		if (raider instanceof Worker folk) {
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
				McaCompat.vanish(raider);
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

	/** What {@link #horizontal} answers for two places in different bands of a stacked world: further than anything reaches. */
	static final double NO_ROAD = 1.0e18;

	/**
	 * How far apart two places are over the ground (squared). In a stacked world a place in the Aether and one below it
	 * have no road between them ({@link Stacked#band}), so they are {@link #NO_ROAD} apart: nothing that goes by this
	 * (wars, raids, rivalries, spacing) reaches from one to the other.
	 */
	static double horizontal(BlockPos a, BlockPos b) {
		if (a.getY() != b.getY()) {
			MinecraftServer server = Rulers.server();
			if (server != null && Stacked.band(server, a.getY()) != Stacked.band(server, b.getY())) {
				return NO_ROAD;
			}
		}
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
