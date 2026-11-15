package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (am-sieges, medieval picks; Armed Mobs: Across the Ages' sieges, an idea only) A war party at a seat lays siege in
 * waves (Lore, "The realms' ways": a war on a seat comes in waves, with a lull between). It attacks for
 * {@link #WAVE_TICKS}; then its soldiers fall back to their camp and its engines go quiet for {@link #LULL_TICKS}, while
 * the besieged are resupplied out of their war chest ({@link WarChest#resupply}); then the next wave comes on, with
 * {@link #REINFORCE} fresh soldiers from home. After the {@link #WAVES}th the seat is sacked, as before. Beat every one of
 * them first and the siege is broken. A homestead is taken in {@link #HOME_WAVES} ({@link HomeRaids}).
 *
 * <p>When the siege begins, one of the besieged realm's people (its ruler, else a guard) comes to each player about the
 * seat who isn't sworn to either side and asks, on the talk card ({@link Menus}), which way they'll go: stand with the
 * seat (its guards count them a friend and they may spend its war chest), throw in with the attackers (who share their
 * spoils; the seat's guards go for them), or stay out of it (the attackers leave them be). A sworn player is on their
 * realm's side without asking. Striking one side settles it: you're with the other.
 */
public final class Sieges {
	/** Waves before the sack, at a seat and at a homestead; how long each lasts and the lull after it (ticks). */
	static final int WAVES = 5, HOME_WAVES = 1;
	static final long WAVE_TICKS = 20 * 75, LULL_TICKS = 20 * 40;
	/** The attackers' camp in a lull: this far back from their banner, and they keep within this of it (blocks). */
	static final double CAMP_BACK = 26.0;
	static final int CAMP_RADIUS = 6;
	/** Fresh soldiers the attackers bring up for each wave after the first. */
	static final int REINFORCE = 2;
	/** Players this near the seat are asked; the one who asks is found this near them, comes this close, and gives up after this long (ticks). */
	static final double ASK_RANGE = 64.0, ASKER_RANGE = 48.0, ASK_REACH = 5.0;
	static final long ASK_WALK_TICKS = 20 * 25;
	/** Good name with the besieged: standing with them, each wave held by one who stands with them, throwing in with the attackers. */
	static final int STAND_REP = 5, HELD_REP = 3, BETRAY_REP = -60;
	/** A defender who held the seat is thanked with this when the siege is broken. */
	static final int BROKEN_REP = 20;
	/** What the siege broke this near its seat is mended by the war chest ({@link WarChest}). */
	static final double BREACH_RANGE = 56.0;
	/** Lines for each (speech.jaymoskingdoms.siege.<key>.<n>). */
	static final int LINES = 2;

	/** The sides a player takes. */
	static final int DEFEND = 0, ATTACK = 1, OUT = 2;

	/** One of the besieged's people on their way to a player to ask which side they're on. */
	private record Ask(WarParties.Party party, UUID asker, long since) {
	}

	private static final Map<UUID, Ask> ASKS = new HashMap<>();

	private Sieges() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer p) {
				struck(p, victim);
			}
		});
	}

	static void clear() {
		ASKS.clear();
	}

	static int waves(WarParties.Party party) {
		return party.home != null ? HOME_WAVES : WAVES;
	}

	// ---- the waves ---------------------------------------------------------------------------------------------------

	/**
	 * One step of {@code party}'s siege of {@code goal} ({@code target}'s seat, or a homestead with no target): its waves
	 * and lulls. True once the last wave is over (the sack follows).
	 */
	static boolean drive(ServerLevel level, WarParties.Party party, List<PathfinderMob> soldiers, Faction from, @Nullable Faction target, BlockPos goal,
		long now) {
		if (party.siegeOver) {
			return false;
		}
		if (party.wave == 0) {
			begin(level, party, from, target, goal, now);
		}
		asks(level, party, target);
		if (!party.lull) {
			Sappers.drive(level, party, from, goal, now);
			if (now - party.waveAt < WAVE_TICKS) {
				return false;
			}
			if (party.wave >= waves(party)) {
				party.siegeOver = true;
				return true;
			}
			// the wave spent: back to the camp, and the besieged see to their walls
			party.lull = true;
			party.waveAt = now;
			for (PathfinderMob s : soldiers) {
				Comrades.stand(s);
				s.setHomeTo(party.camp != null ? party.camp : goal, CAMP_RADIUS);
			}
			horn(level, goal, 0.7F);
			if (target != null) {
				WarChest.resupply(level, party, target);
				for (ServerPlayer p : Raids.players(level, goal, ASK_RANGE)) {
					if (side(party, p) == DEFEND) {
						Reputation.change(p, target, HELD_REP);
					}
				}
			}
			return false;
		}
		BlockPos camp = party.camp != null ? party.camp : goal;
		for (PathfinderMob s : soldiers) {
			if (Rulers.target(s) == null && !SiegeTrain.pushing(level, party, s) && s.distanceToSqr(Vec3.atBottomCenterOf(camp)) > (CAMP_RADIUS + 2) * (CAMP_RADIUS + 2)
				&& s.getNavigation().isDone()) {
				s.getNavigation().moveTo(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5, WarParties.MARCH_SPEED);
			}
		}
		if (now - party.waveAt >= LULL_TICKS) {
			party.lull = false;
			party.wave++;
			party.waveAt = now;
			reinforce(level, party, from, camp, goal);
			for (UUID id : party.soldiers) {
				if (level.getEntity(id) instanceof PathfinderMob s && !party.holders.contains(id)) {
					s.setHomeTo(party.rally != null ? party.rally : goal, WarParties.SIEGE_RADIUS);
				}
			}
			horn(level, goal, 1.1F);
		}
		return false;
	}

	/** The first wave: the camp marked, the gate shut, and the players about asked which side they're on. */
	private static void begin(ServerLevel level, WarParties.Party party, Faction from, @Nullable Faction target, BlockPos goal, long now) {
		party.wave = 1;
		party.lull = false;
		party.waveAt = now;
		BlockPos rally = party.rally != null ? party.rally : goal;
		Vec3 back = Vec3.atBottomCenterOf(rally).subtract(Vec3.atBottomCenterOf(goal)).multiply(1.0, 0.0, 1.0);
		if (back.lengthSqr() < 1.0) {
			back = Vec3.atBottomCenterOf(from.seat()).subtract(Vec3.atBottomCenterOf(goal)).multiply(1.0, 0.0, 1.0);
		}
		if (back.lengthSqr() < 1.0) {
			back = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 at = Vec3.atBottomCenterOf(rally).add(back.normalize().scale(CAMP_BACK));
		BlockPos column = BlockPos.containing(at);
		party.camp = Loaded.made(level, column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : rally;
		if (target != null) {
			WarChest.shutGate(level, party, target);
		}
		horn(level, goal, 1.0F);
		if (target == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (ServerPlayer p : Raids.players(level, goal, ASK_RANGE)) {
			if (p.isCreative() || p.isSpectator() || party.sides.containsKey(p.getUUID())) {
				continue;
			}
			Faction mine = Factions.of(server, p.getUUID());
			if (mine != null && Wars.warTop(server, mine).id() == Wars.warTop(server, target).id()) {
				party.sides.put(p.getUUID(), DEFEND);
			} else if (mine != null && Wars.warTop(server, mine).id() == Wars.warTop(server, from).id() || party.players.contains(p.getUUID())) {
				party.sides.put(p.getUUID(), ATTACK);
			} else {
				Mob asker = asker(level, target, p);
				if (asker != null) {
					ASKS.put(p.getUUID(), new Ask(party, asker.getUUID(), now));
				}
			}
		}
	}

	/** The besieged's ruler, if they're near {@code p}; else the nearest of its guards who can see them. */
	private static @Nullable Mob asker(ServerLevel level, Faction target, ServerPlayer p) {
		PathfinderMob ruler = Rulers.of(level, target);
		if (ruler != null && ruler.distanceTo(p) <= ASKER_RANGE && !Downed.isDowned(ruler)) {
			return ruler;
		}
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(ASKER_RANGE), m -> m instanceof Worker w && m.isAlive()
			&& w.factionId() == target.id() && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER) && !Downed.isDowned(m) && Rulers.target(m) == null)) {
			double d = m.distanceToSqr(p);
			if (d < bestD && m.hasLineOfSight(p)) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	/** Those on their way to ask a player, walked over; once there, they ask. */
	private static void asks(ServerLevel level, WarParties.Party party, @Nullable Faction target) {
		if (ASKS.isEmpty() || target == null) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<UUID, Ask>> it = ASKS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Ask> e = it.next();
			Ask a = e.getValue();
			if (a.party() != party) {
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			Entity found = level.getEntity(a.asker());
			if (p == null || p.level() != level || !(found instanceof Mob asker) || !asker.isAlive() || now - a.since() > ASK_WALK_TICKS
				|| party.sides.containsKey(p.getUUID())) {
				it.remove();
				continue;
			}
			if (asker.distanceTo(p) > ASK_REACH) {
				if (asker instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, p, 0.6F, 2);
				} else if (asker instanceof PathfinderMob pm && pm.getNavigation().isDone()) {
					pm.getNavigation().moveTo(p, 0.9);
				}
				continue;
			}
			it.remove();
			ask(level, party, target, asker, p);
		}
	}

	/** {@code asker} puts it to {@code p}: which side are you on? */
	private static void ask(ServerLevel level, WarParties.Party party, Faction target, Mob asker, ServerPlayer p) {
		Faction from = Factions.get(level.getServer(), party.faction);
		String them = from == null ? "?" : from.name();
		asker.getLookControl().setLookAt(p, 30.0F, 30.0F);
		Component words = Component.translatable("speech.jaymoskingdoms.siege.ask." + level.getRandom().nextInt(LINES), them, target.name());
		Speech.tell(level, asker, p, words, Speech.GOLD);
		Menus.ask(p, asker, words, List.of(
			Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.siege.answer.defend", target.name()), sp -> {
				choose(party, sp, DEFEND);
				Reputation.change(sp, target, STAND_REP);
				Speech.tell(level, asker, sp, Component.translatable("speech.jaymoskingdoms.siege.reply.defend." + level.getRandom().nextInt(LINES)), Speech.GOLD);
			}),
			Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.siege.answer.attack", them), sp -> {
				choose(party, sp, ATTACK);
				Reputation.change(sp, target, BETRAY_REP);
				Speech.tell(level, asker, sp, Component.translatable("speech.jaymoskingdoms.siege.reply.attack." + level.getRandom().nextInt(LINES)), Speech.RED);
				Comrades.aim(asker, sp);
			}),
			Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.siege.answer.out"), sp -> {
				choose(party, sp, OUT);
				Speech.tell(level, asker, sp, Component.translatable("speech.jaymoskingdoms.siege.reply.out." + level.getRandom().nextInt(LINES)), Speech.WHITE);
			})));
	}

	/** {@code p} takes {@code side} in {@code party}'s siege: with the attackers, they march (and share the spoils) with them. */
	private static void choose(WarParties.Party party, ServerPlayer p, int side) {
		party.sides.put(p.getUUID(), side);
		if (side == ATTACK) {
			party.players.add(p.getUUID());
		} else {
			party.players.remove(p.getUUID());
		}
	}

	/** Each wave after the first, the attackers bring up fresh soldiers from home (paid from their treasury), a squad of their own. */
	private static void reinforce(ServerLevel level, WarParties.Party party, Faction from, BlockPos camp, BlockPos goal) {
		if (from.treasury() <= 0) {
			return;
		}
		List<UUID> squad = new ArrayList<>();
		for (int i = 0; i < REINFORCE; i++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.offset(level.getRandom().nextInt(5) - 2, 0,
				level.getRandom().nextInt(5) - 2));
			Worker s = WarParties.soldier(level, from, at, level.getRandom().nextFloat() * 360.0F, goal);
			if (s == null) {
				continue;
			}
			if (squad.isEmpty()) {
				Ranks.lead(s.mob());
			}
			squad.add(s.getUUID());
			party.soldiers.add(s.getUUID());
		}
		if (!squad.isEmpty()) {
			party.squads.add(squad);
			Treasuries.wages(level.getServer(), from, squad.size());
		}
	}

	private static void horn(ServerLevel level, BlockPos goal, float pitch) {
		for (ServerPlayer p : Raids.players(level, goal, Raids.ANNOUNCE_RANGE)) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5F, pitch);
		}
	}

	/**
	 * {@code party}'s siege is over, won or broken (once): the gate opened, the levies paid off, the sappers stood down; a
	 * broken siege thanks those who held the seat; and the lord, having been threatened, is minded to build ({@link Defences}).
	 */
	static void end(ServerLevel level, WarParties.Party party) {
		if (party.siegeEnded) {
			return;
		}
		party.siegeEnded = true;
		ASKS.values().removeIf(a -> a.party() == party);
		Sappers.end(level, party);
		if (party.wave == 0) {
			return;
		}
		WarChest.openGate(level, party);
		WarChest.payOff(level, party);
		Faction target = party.target >= 0 ? Factions.get(level.getServer(), party.target) : null;
		if (target == null || target.fallen()) {
			return;
		}
		if (!party.siegeOver) {
			for (UUID id : party.sides.keySet()) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (p != null && party.sides.get(id) == DEFEND && p.distanceToSqr(Vec3.atCenterOf(target.seat())) < ASK_RANGE * ASK_RANGE) {
					Reputation.change(p, target, BROKEN_REP);
				}
			}
		}
		Defences.afterThreat(level, target);
	}

	// ---- sides ---------------------------------------------------------------------------------------------------------

	/** {@code p}'s side in {@code party}'s siege, or -1 if they've taken none. */
	static int side(WarParties.Party party, ServerPlayer p) {
		Integer side = party.sides.get(p.getUUID());
		return side == null ? -1 : side;
	}

	/** The siege of {@code faction}'s seat under way (marching on it or at it), or null. */
	static WarParties.@Nullable Party of(int faction) {
		for (WarParties.Party party : WarParties.active()) {
			if (party.target == faction && party.phase != WarParties.Phase.RETURN) {
				return party;
			}
		}
		return null;
	}

	/** {@code faction} is the besieged realm of {@code party}, or one of its own side. */
	private static boolean besieged(MinecraftServer server, WarParties.Party party, int faction) {
		if (faction == party.target) {
			return true;
		}
		Faction f = Factions.get(server, faction), t = Factions.get(server, party.target);
		return f != null && t != null && Wars.warTop(server, f).id() == Wars.warTop(server, t).id();
	}

	/**
	 * Whether {@code fighter} counts {@code target} a foe for a siege's sake ({@link McaCompat#foe}, the folk's own
	 * targeting, {@link #spares}): the attackers go for one who stands with the seat and leave be one who stayed out or
	 * is with them; the besieged go for one who threw in with the attackers and spare one who stands with them. Null
	 * when no siege has a say.
	 */
	static @Nullable Boolean foe(LivingEntity fighter, LivingEntity target) {
		if (!(target instanceof ServerPlayer p) || !(fighter instanceof Worker w) || WarParties.active().isEmpty()) {
			return null;
		}
		MinecraftServer server = p.level().getServer();
		for (WarParties.Party party : WarParties.active()) {
			int side = party.phase == WarParties.Phase.RETURN ? -1 : side(party, p);
			if (side < 0) {
				continue;
			}
			if (party.soldiers.contains(fighter.getUUID())) {
				return side == DEFEND;
			}
			if (party.target >= 0 && w.factionId() >= 0 && besieged(server, party, w.factionId())) {
				return side == ATTACK ? Boolean.TRUE : side == DEFEND ? Boolean.FALSE : null;
			}
		}
		return null;
	}

	/** (ThrallTargetMixin, McaGuardFoesMixin) {@code attacker} leaves {@code target} be for a siege's sake. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		return foe(attacker, target) == Boolean.FALSE;
	}

	/** {@code p} struck {@code victim}: one who hadn't taken a side (or stayed out) is now against whoever they struck. */
	private static void struck(ServerPlayer p, LivingEntity victim) {
		if (!(victim instanceof Worker w) || WarParties.active().isEmpty()) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		for (WarParties.Party party : WarParties.active()) {
			if (party.phase != WarParties.Phase.SIEGE) {
				continue;
			}
			int side = side(party, p);
			if (side == ATTACK || side == DEFEND && !party.soldiers.contains(victim.getUUID())) {
				continue;
			}
			if (party.soldiers.contains(victim.getUUID())) {
				choose(party, p, DEFEND);
				ASKS.remove(p.getUUID());
			} else if (party.target >= 0 && w.factionId() >= 0 && besieged(server, party, w.factionId()) && side != DEFEND) {
				choose(party, p, ATTACK);
				ASKS.remove(p.getUUID());
			}
		}
	}

	// ---- breaches ------------------------------------------------------------------------------------------------------

	/**
	 * (Bombardment, {@link Sappers}) {@code state} at {@code pos} was broken by a shot or a keg: if it was about a besieged
	 * seat, it's kept for the war chest's masons to put back ({@link WarChest#repair}).
	 */
	public static void broken(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || WarParties.active().isEmpty() || level != Factions.level(level.getServer())) {
			return;
		}
		for (WarParties.Party party : WarParties.active()) {
			if (party.phase != WarParties.Phase.SIEGE || party.target < 0) {
				continue;
			}
			BlockPos goal = WarParties.goal(level.getServer(), party);
			if (goal != null && goal.distSqr(pos) <= Mth.square(BREACH_RANGE) && !party.gate.containsKey(pos.asLong())) {
				party.breaches.putIfAbsent(pos.asLong(), state);
			}
		}
	}

	/** The fighters of {@code party}'s besieged realm near {@code goal}: its guards and archers, and the levies. */
	static List<Mob> defenders(ServerLevel level, WarParties.Party party, Faction target, BlockPos goal) {
		MinecraftServer server = level.getServer();
		return level.getEntitiesOfClass(Mob.class, new AABB(goal).inflate(ASK_RANGE), m -> m.isAlive() && m instanceof Worker w
			&& (w.role() == Folk.GUARD || w.role() == Folk.ARCHER) && w.factionId() >= 0 && !party.soldiers.contains(m.getUUID())
			&& besieged(server, party, w.factionId()));
	}
}
