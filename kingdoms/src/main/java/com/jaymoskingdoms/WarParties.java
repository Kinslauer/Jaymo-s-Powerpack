package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * NPC war parties, as the barbarians' {@link Raids} but between realms: while a war is on, each side now and then sends
 * a party of soldiers against a realm of the other side that has a player near its seat (so it's seen). The party
 * musters on the enemy's land, on its frontier toward the attacker where it can; its bearer plants their banner on the
 * first chunk it can claim and leaves {@link #BANNER_GUARDS} to hold it; the rest march on the seat, plant a banner
 * there (a siege) and fight round it for {@link #SIEGE_TICKS}; then the survivors withdraw. Its size comes from the
 * army of the realm that sends it. A party is mustered for the march: it isn't on the realm's roster, and its dead
 * aren't replaced. Parties aren't saved: soldiers left after a restart go home.
 */
final class WarParties {
	/** How often each side of a war considers sending a party (ticks), the chance it does, and the least time between two (ticks). */
	static final int CHECK_TICKS = 200;
	static final float PARTY_CHANCE = 0.15F;
	static final long COOLDOWN_TICKS = 6000L;
	/** A party goes against a realm with a player this near its seat. */
	static final double PLAYER_RANGE = 96.0;
	/** The party: a base, one more per this many of the army (guards and archers), at most this many. */
	static final int PARTY_BASE = 3, PARTY_PER = 3, PARTY_MAX = 9;
	/** Soldiers left to hold the frontier banner (the party keeps at least {@code MARCHERS_KEPT} marching). */
	static final int BANNER_GUARDS = 2, MARCHERS_KEPT = 2;
	/** Where it musters: this far from the enemy's seat toward the attacker (the first loaded place that's claimable, else any loaded one). */
	static final int[] MUSTER_DISTANCE = {104, 96, 88, 112, 80, 72, 64, 120, 56, 48};
	/** How often parties are driven (ticks); their pace; waypoints this far ahead. */
	static final int TICK = 10;
	static final double MARCH_SPEED = 1.0, WAYPOINT = 16.0;
	/** It has arrived at the seat this near it, or after this long (ticks). */
	static final double ARRIVE_RANGE = 16.0;
	static final long MARCH_TICKS = 20 * 120;
	/** The siege: soldiers keep within this of their banner, for this long (ticks). */
	static final int SIEGE_RADIUS = 14, HOLD_RADIUS = 6;
	static final long SIEGE_TICKS = 20 * 180;
	/** Withdrawing: a soldier is gone this near his own seat, or once no player is this near him; the whole party after this long (ticks). */
	static final double HOME_REACH = 16.0, VANISH_RANGE = 48.0;
	static final long PARTY_MAX_TICKS = 20 * 60 * 10;
	/** What soldiers' {@link Folk#task} says. */
	static final String TASK = "war";

	enum Phase {
		MARCH, SIEGE, RETURN
	}

	static final class Party {
		final int war, side, faction, target;
		final List<UUID> soldiers = new ArrayList<>();
		final Set<UUID> holders = new HashSet<>();
		Phase phase = Phase.MARCH;
		final long started;
		long phaseAt;
		boolean frontierPlanted, seatPlanted;

		Party(int war, int side, int faction, int target, long now) {
			this.war = war;
			this.side = side;
			this.faction = faction;
			this.target = target;
			this.started = now;
			this.phaseAt = now;
		}
	}

	/** "war|side" → its party in the field; and when each last sent one. */
	private static final Map<String, Party> ACTIVE = new HashMap<>();
	private static final Map<String, Long> LAST = new HashMap<>();
	/** Realms whose soldier fell (the roster's loss is undone next tick: parties aren't on it). */
	private static final List<Integer> UNDO = new ArrayList<>();
	/** Soldiers loaded with no party to belong to (sent home next tick). */
	private static final List<Folk> STRAYS = new ArrayList<>();

	private WarParties() {
	}

	private static String key(int war, int side) {
		return war + "|" + side;
	}

	static boolean isSoldier(Folk folk) {
		return folk.task().equals(TASK);
	}

	/** A party of {@code war} is in the field. */
	static boolean fighting(int war) {
		return ACTIVE.values().stream().anyMatch(p -> p.war == war);
	}

	// ---- sending -------------------------------------------------------------------------------------------------------

	private static void consider(MinecraftServer server, ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			for (int side : new int[] {war.attacker().principal(), war.defender().principal()}) {
				String key = key(war.id(), side);
				if (ACTIVE.containsKey(key) || now - LAST.getOrDefault(key, -COOLDOWN_TICKS) < COOLDOWN_TICKS || random.nextFloat() >= Tempers.warChance(server, side, PARTY_CHANCE)) {
					continue;
				}
				send(level, war, side, false);
			}
		}
	}

	/** {@code side} of {@code war} sends a party, if there's a target with a player near. Returns the party's target, or null. */
	static @Nullable Faction send(ServerLevel level, War war, int side, boolean anywhere) {
		MinecraftServer server = level.getServer();
		int enemy = Wars.otherSide(war, side);
		List<Faction> ours = Wars.members(server, side);
		if (ours.isEmpty()) {
			return null;
		}
		Faction target = null, from = null;
		double best = Double.MAX_VALUE;
		for (Faction t : Wars.members(server, enemy)) {
			if (!level.isPositionEntityTicking(t.seat()) || (!anywhere && !Raids.playerNear(level, t.seat(), PLAYER_RANGE))) {
				continue;
			}
			for (Faction f : ours) {
				double d = Raids.horizontal(f.seat(), t.seat());
				if (d < best) {
					best = d;
					target = t;
					from = f;
				}
			}
		}
		if (target == null || ACTIVE.containsKey(key(war.id(), side))) {
			return null;
		}
		return start(level, war, side, from, target) ? target : null;
	}

	private static boolean start(ServerLevel level, War war, int side, Faction from, Faction target) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos muster = muster(level, side, Wars.otherSide(war, side), from, target);
		if (muster == null) {
			return false;
		}
		long now = level.getGameTime();
		Party party = new Party(war.id(), side, from.id(), target.id(), now);
		ACTIVE.put(key(war.id(), side), party);
		LAST.put(key(war.id(), side), now);
		int army = from.holdings().count(Folk.GUARD) + from.holdings().count(Folk.ARCHER);
		int size = Math.min(PARTY_MAX, PARTY_BASE + army / PARTY_PER) + Tempers.warExtra(from);
		for (int i = 0; i < size; i++) {
			Folk soldier = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (soldier == null) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			soldier.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			soldier.setup(Folk.GUARD, from, target.seat(), Raids.RAID_HOME_RADIUS);
			level.addFreshEntity(soldier);
			soldier.setTask(TASK);
			// (0.5.14) a prosperous realm's soldiers ride, some of them
			if (i % Cavalry.PARTY_RIDER_ONE_IN == 0 && Wealth.atLeast(server, from, Wealth.Tier.PROSPEROUS)) {
				Cavalry.mount(level, soldier, from, true);
			}
			party.soldiers.add(soldier.getUUID());
		}
		Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party", from.name(), target.name())
			.withStyle(ChatFormatting.RED));
		for (ServerPlayer player : Raids.players(level, target.seat(), Raids.ANNOUNCE_RANGE)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.1F);
		}
		Treasuries.wages(server, from, party.soldiers.size());
		JaymosKingdoms.LOGGER.info("{} sends {} soldiers against {} from {}", from.name(), party.soldiers.size(), target.name(), muster);
		return true;
	}

	/** Loaded open ground on {@code target}'s land toward {@code from}: a claimable frontier chunk if there is one. */
	private static @Nullable BlockPos muster(ServerLevel level, int side, int enemy, Faction from, Faction target) {
		MinecraftServer server = level.getServer();
		double dx = from.seat().getX() - target.seat().getX(), dz = from.seat().getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		BlockPos fallback = null;
		for (int distance : MUSTER_DISTANCE) {
			double d = Math.min(distance, Math.max(8.0, len - 8.0));
			BlockPos column = target.seat().offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()) {
				continue;
			}
			ChunkPos chunk = ChunkPos.containing(at);
			Faction owner = Banners.owner(server, chunk);
			if (owner != null && Wars.onSide(server, owner.id(), enemy) && Banners.claimable(server, side, chunk)) {
				return at;
			}
			if (fallback == null) {
				fallback = at;
			}
		}
		return fallback;
	}

	// ---- driving -------------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server, ServerLevel level, long now) {
		for (int faction : UNDO) {
			Factions.update(server, faction, f -> f.withHoldings(f.holdings().adjust(Folk.GUARD, 1).adjustVacancy(Folk.GUARD, -1)));
		}
		UNDO.clear();
		if (!STRAYS.isEmpty()) {
			List<Folk> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Folk stray : strays) {
				if (stray.isAlive()) {
					vanish(level, stray);
				}
			}
		}
		if (now % CHECK_TICKS == 0) {
			consider(server, level, now);
		}
		if (now % TICK != 0 || ACTIVE.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Party> e : List.copyOf(ACTIVE.entrySet())) {
			drive(level, e.getKey(), e.getValue(), now);
		}
	}

	private static void drive(ServerLevel level, String key, Party party, long now) {
		MinecraftServer server = level.getServer();
		War war = WarData.war(server, party.war);
		Faction from = Factions.get(server, party.faction);
		Faction target = Factions.get(server, party.target);
		List<Folk> soldiers = new ArrayList<>();
		for (UUID id : party.soldiers) {
			if (level.getEntity(id) instanceof Folk folk && folk.isAlive()) {
				soldiers.add(folk);
			}
		}
		if (soldiers.isEmpty()) {
			ACTIVE.remove(key);
			if (party.phase != Phase.RETURN && target != null && war != null) {
				Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party_beaten", name(from), target.name())
					.withStyle(ChatFormatting.GREEN));
			}
			return;
		}
		if (war == null || !war.active() || from == null || from.fallen() || target == null || target.fallen() || now - party.started > PARTY_MAX_TICKS) {
			withdraw(party, soldiers, from, now);
		}
		switch (party.phase) {
			case MARCH -> {
				if (!party.frontierPlanted) {
					plantFrontier(level, war, party, soldiers, from, target);
				}
				boolean arrived = now - party.phaseAt > MARCH_TICKS;
				for (Folk soldier : soldiers) {
					if (party.holders.contains(soldier.getUUID())) {
						continue;
					}
					if (soldier.getTarget() == null) {
						walk(level, soldier, target.seat());
					}
					arrived |= soldier.distanceToSqr(Vec3.atCenterOf(target.seat())) < ARRIVE_RANGE * ARRIVE_RANGE;
				}
				if (arrived) {
					party.phase = Phase.SIEGE;
					party.phaseAt = now;
					Economy.noteAttack(server, target, soldiers.getFirst());
					Folk bearer = soldiers.stream().filter(s -> !party.holders.contains(s.getUUID()))
						.min((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(target.seat())), b.distanceToSqr(Vec3.atCenterOf(target.seat()))))
						.orElse(soldiers.getFirst());
					BlockPos at = bearer.blockPosition();
					if (Raids.horizontal(at, target.seat()) > Banners.SEAT_RANGE * Banners.SEAT_RANGE) {
						at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.seat().offset(4, 0, 4));
					}
					BlockPos planted = Banners.plantFor(level, war, from, target, true, at);
					party.seatPlanted = planted != null;
					BlockPos rally = planted != null ? planted : target.seat();
					for (Folk soldier : soldiers) {
						if (!party.holders.contains(soldier.getUUID())) {
							soldier.setHomeTo(rally, SIEGE_RADIUS);
						}
					}
					Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party_arrives", from.name(), target.name())
						.withStyle(ChatFormatting.RED));
				}
			}
			case SIEGE -> {
				Economy.noteAttack(server, target, soldiers.getFirst());
				if (now - party.phaseAt > SIEGE_TICKS) {
					withdraw(party, soldiers, from, now);
				}
			}
			case RETURN -> {
				for (Folk soldier : soldiers) {
					boolean home = from != null && soldier.distanceToSqr(Vec3.atCenterOf(from.seat())) < HOME_REACH * HOME_REACH;
					if (home || !Raids.playerNear(level, soldier.blockPosition(), VANISH_RANGE)) {
						vanish(level, soldier);
						party.soldiers.remove(soldier.getUUID());
					} else if (soldier.getTarget() == null && from != null) {
						walk(level, soldier, from.seat());
					}
				}
				party.soldiers.removeIf(id -> !(level.getEntity(id) instanceof Folk));
				if (party.soldiers.isEmpty()) {
					ACTIVE.remove(key);
				}
			}
		}
	}

	/** The bearer plants the party's banner on the first claimable chunk of the enemy's land he stands on, and some stay to hold it. */
	private static void plantFrontier(ServerLevel level, War war, Party party, List<Folk> soldiers, Faction from, Faction target) {
		MinecraftServer server = level.getServer();
		int enemy = Wars.otherSide(war, party.side);
		for (Folk bearer : soldiers) {
			ChunkPos chunk = ChunkPos.containing(bearer.blockPosition());
			Faction owner = Banners.owner(server, chunk);
			if (owner == null || !Wars.onSide(server, owner.id(), enemy) || !Banners.claimable(server, party.side, chunk)) {
				continue;
			}
			BlockPos planted = Banners.plantFor(level, war, from, owner, false, bearer.blockPosition());
			party.frontierPlanted = true;
			if (planted == null) {
				return;
			}
			int keep = Math.min(BANNER_GUARDS, soldiers.size() - MARCHERS_KEPT);
			for (Folk guard : soldiers) {
				if (keep <= 0) {
					break;
				}
				guard.setHomeTo(planted, HOLD_RADIUS);
				party.holders.add(guard.getUUID());
				keep--;
			}
			return;
		}
	}

	private static void withdraw(Party party, List<Folk> soldiers, @Nullable Faction from, long now) {
		if (party.phase == Phase.RETURN) {
			return;
		}
		party.phase = Phase.RETURN;
		party.phaseAt = now;
		for (Folk soldier : soldiers) {
			if (from != null) {
				soldier.setHomeTo(from.seat(), Folk.HOME_RADIUS);
			}
		}
	}

	private static void walk(ServerLevel level, Folk soldier, BlockPos to) {
		if (!soldier.getNavigation().isDone() && level.getGameTime() % (TICK * 4) != 0) {
			return;
		}
		double dx = to.getX() + 0.5 - soldier.getX(), dz = to.getZ() + 0.5 - soldier.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		BlockPos goal = to;
		if (len > WAYPOINT) {
			BlockPos column = BlockPos.containing(soldier.getX() + dx / len * WAYPOINT, soldier.getY(), soldier.getZ() + dz / len * WAYPOINT);
			goal = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		}
		soldier.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, MARCH_SPEED);
	}

	private static void vanish(ServerLevel level, Folk soldier) {
		Cavalry.dismiss(soldier);
		level.sendParticles(ParticleTypes.POOF, soldier.getX(), soldier.getY() + 1.0, soldier.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		soldier.discard();
	}

	private static String name(@Nullable Faction f) {
		return f == null ? "?" : f.name();
	}

	// ---- events ------------------------------------------------------------------------------------------------------

	/** A soldier fell: off his party, and his realm doesn't count the loss (he was never on its roster). */
	static void soldierDied(Folk folk) {
		if (!isSoldier(folk)) {
			return;
		}
		UNDO.add(folk.factionId());
		for (Party party : ACTIVE.values()) {
			party.soldiers.remove(folk.getUUID());
			party.holders.remove(folk.getUUID());
		}
	}

	/** A soldier loaded: if his party is over (or was lost to a restart), he goes home next tick. */
	static void loaded(Entity entity) {
		if (entity instanceof Folk folk && isSoldier(folk)) {
			for (Party party : ACTIVE.values()) {
				if (party.soldiers.contains(folk.getUUID())) {
					return;
				}
			}
			STRAYS.add(folk);
		}
	}

	static void clear() {
		ACTIVE.clear();
		LAST.clear();
		UNDO.clear();
		STRAYS.clear();
	}
}
