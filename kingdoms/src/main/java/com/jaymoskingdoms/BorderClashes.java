package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.439) Havens patch 6, second half: clashes on the border (ideas from the havens pick list; our own code).
 * <ul>
 * <li><b>Purist raids:</b> now and then the nearest human purist realm ({@link Purists}) sends a few of its guards
 * against a haven a player is near: they muster on the road from their realm, march on the haven's seat and fight its
 * guards there, then go home. If they aren't all cut down, they may carry off one of the haven's children, left bound
 * at their muster on the road ({@link Captives#hold}); one of the haven's folk asks a player to bring the child back.</li>
 * <li><b>The militia strike back:</b> a haven that's been raided owes its raiders one: once a player is near that
 * realm's seat, the haven's militia march on it the same way.</li>
 * <li><b>Taking a side:</b> cutting down a raider (or a militiaman) earns the side being raided
 * {@link #REP_DEFENDED}; cutting down one of the defenders earns the raiders {@link #REP_SIDED}. The usual price of
 * killing a realm's guard is paid to the other side as always, so each side remembers.</li>
 * </ul>
 * Clashes aren't saved: a party left after a restart goes home (its men vanish when loaded).
 */
public final class BorderClashes {
	/** How often havens are looked at (ticks), the chance one is raided when it can be, and the least time between two clashes on it (ticks). */
	static final int CHECK_TICKS = 600;
	static final float CLASH_CHANCE = 0.06F, STRIKE_BACK_CHANCE = 0.2F;
	static final long COOLDOWN_TICKS = 24000L * 2;
	/** A purist realm raids havens this far off (blocks); a clash comes when a player is this near the seat it falls on. */
	static final double REACH = 1500.0, PLAYER_RANGE = 96.0;
	/** The party: a base, one more per this many of the sender's guards, at most this many. */
	static final int PARTY_BASE = 3, PARTY_PER = 3, PARTY_MAX = 6;
	/** Where it musters: this far from the seat it marches on, toward the sender (other distances tried if that's not loaded). */
	static final int[] MUSTER_DISTANCE = {64, 56, 72, 48, 80};
	/** How often parties are driven (ticks); their pace; waypoints this far ahead. */
	static final int TICK = 10;
	static final double MARCH_SPEED = 1.0, WAYPOINT = 16.0;
	/** It has arrived this near the seat, or after this long (ticks); it fights this long there, then goes home. */
	static final double ARRIVE_RANGE = 16.0;
	static final long MARCH_TICKS = 20 * 120, FIGHT_TICKS = 20 * 120;
	/** Raiders look for defenders this far from the seat; defenders come to it from this far. */
	static final double FIGHT_RANGE = 40.0, DEFEND_RANGE = 64.0;
	/** Going home: a man is gone this near the muster, or once no player is this near him; the whole party after this long (ticks). */
	static final double HOME_REACH = 10.0, VANISH_RANGE = 48.0;
	static final long PARTY_MAX_TICKS = 20 * 60 * 8;
	/** Taking a side: reputation per raider cut down, with those raided; per defender cut down, with the raiders. */
	static final int REP_DEFENDED = 8, REP_SIDED = 8;
	/** The chance raiders not all cut down carry a child off; children are looked for this near the seat. */
	static final float KIDNAP_CHANCE = 0.5F;
	static final double CHILD_RANGE = 48.0;
	/** What the party's men's {@link Worker#task} says. */
	static final String TASK = "clash";

	/** Haven id → the child carried off from it, as "uuid|name|x|y|z|realm name". On the Overworld; kept until they're home. */
	static final AttachmentType<Map<String, String>> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("haven_taken"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** Haven id → the purist realm that last raided it, while the haven owes it a strike back. */
	static final AttachmentType<Map<String, Integer>> OWED = AttachmentRegistry.create(JaymosKingdoms.id("haven_owed"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	enum Phase { MARCH, FIGHT, RETURN }

	static final class Party {
		final int from, target;
		/** A haven's militia striking back (else purist raiders). */
		final boolean militia;
		final BlockPos muster;
		final List<UUID> men = new ArrayList<>();
		Phase phase = Phase.MARCH;
		final long started;
		long phaseAt;

		Party(int from, int target, boolean militia, BlockPos muster, long now) {
			this.from = from;
			this.target = target;
			this.militia = militia;
			this.muster = muster;
			this.started = now;
			this.phaseAt = now;
		}
	}

	/** The seat being marched on → its party. */
	private static final Map<Integer, Party> ACTIVE = new HashMap<>();
	/** Haven id → game time of its last clash (either way). */
	private static final Map<Integer, Long> LAST = new HashMap<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	private BorderClashes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BorderClashes::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Worker w && TASK.equals(w.task()) && partyOf(entity.getUUID()) == null) {
				STRAYS.add(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			LAST.clear();
			STRAYS.clear();
		});
	}

	// ---- who's who -------------------------------------------------------------------------------------------------

	private static @Nullable Party partyOf(UUID id) {
		for (Party p : ACTIVE.values()) {
			if (p.men.contains(id)) {
				return p;
			}
		}
		return null;
	}

	/** {@code entity} is one of a clash party's men. */
	static boolean member(Entity entity) {
		return partyOf(entity.getUUID()) != null;
	}

	/** (0.7.442) Settlement {@code target} has a clash party on it just now. */
	static boolean under(int target) {
		return ACTIVE.containsKey(target);
	}

	/** {@code entity} is one of a purist raiding party's men (for haven bounties). */
	static boolean puristRaider(Entity entity) {
		Party p = partyOf(entity.getUUID());
		return p != null && !p.militia;
	}

	/** The nearest human purist realm within reach of haven {@code f}, or null. */
	static @Nullable Faction foe(MinecraftServer server, Faction f) {
		Faction best = null;
		double bestD = REACH * REACH;
		for (Faction p : Factions.all(server)) {
			if (!p.type().isRealm() || !Purists.is(p) || Prejudice.degree(Purists.blood(p)) > 0) {
				continue;
			}
			double d = p.seat().distSqr(f.seat());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	// ---- sending ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (!STRAYS.isEmpty()) {
			List<Entity> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Entity stray : strays) {
				if (stray.isAlive()) {
					McaCompat.vanish(stray);
				}
			}
		}
		if (now % CHECK_TICKS == 0) {
			consider(level, now);
		}
		if (now % TICK == 0 && !ACTIVE.isEmpty()) {
			for (Party party : List.copyOf(ACTIVE.values())) {
				drive(level, party, now);
			}
		}
	}

	private static void consider(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Map<String, Integer> owed = level.getAttachedOrElse(OWED, Map.of());
		for (Faction h : Factions.all(server)) {
			if (h.fallen() || !Havens.is(server, h) || now - LAST.getOrDefault(h.id(), -COOLDOWN_TICKS) < COOLDOWN_TICKS) {
				continue;
			}
			Faction p = foe(server, h);
			if (p == null || ACTIVE.containsKey(h.id()) || ACTIVE.containsKey(p.id())) {
				continue;
			}
			Integer due = owed.get(Integer.toString(h.id()));
			if (due != null && due == p.id() && level.isPositionEntityTicking(p.seat()) && Raids.playerNear(level, p.seat(), PLAYER_RANGE)
				&& random.nextFloat() < STRIKE_BACK_CHANCE) {
				if (start(level, h, p, true)) {
					Map<String, Integer> next = new HashMap<>(owed);
					next.remove(Integer.toString(h.id()));
					level.setAttached(OWED, Map.copyOf(next));
					owed = next;
				}
				continue;
			}
			if (level.isPositionEntityTicking(h.seat()) && Raids.playerNear(level, h.seat(), PLAYER_RANGE) && random.nextFloat() < CLASH_CHANCE
				&& start(level, p, h, false)) {
				Map<String, Integer> next = new HashMap<>(owed);
				next.put(Integer.toString(h.id()), p.id());
				level.setAttached(OWED, Map.copyOf(next));
				owed = next;
			}
		}
	}

	/** {@code from} sends a party against {@code target}'s seat (a haven's militia if {@code militia}). True if it set out. */
	static boolean start(ServerLevel level, Faction from, Faction target, boolean militia) {
		BlockPos muster = muster(level, from, target);
		if (muster == null) {
			return false;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		Party party = new Party(from.id(), target.id(), militia, muster, now);
		int guards = from.holdings().count(Folk.GUARD);
		int size = Math.min(PARTY_MAX, PARTY_BASE + guards / PARTY_PER);
		for (int i = 0; i < size; i++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			Worker man = McaCompat.escort(level, from, at, random.nextFloat() * 360.0F, TASK, Raids.RAID_HOME_RADIUS);
			if (man == null) {
				continue;
			}
			man.mob().setHomeTo(target.seat(), Raids.RAID_HOME_RADIUS);
			// of their own blood: a purist's own kind, a haven's
			Heritage.Kind blood = militia ? Havens.folk(level.getServer(), from.id(), random) : Purists.blood(from);
			if (blood != null && VillagerHeritage.of(man.mob()) != blood) {
				VillagerHeritage.born(man.mob(), blood, null);
			}
			party.men.add(man.mob().getUUID());
		}
		if (party.men.isEmpty()) {
			return false;
		}
		ACTIVE.put(target.id(), party);
		if (!militia) {
			// (0.7.442) a tribe that favours one of the haven's own may send them a war band
			TribePacts.called(level, target, from);
		}
		LAST.put(militia ? from.id() : target.id(), now);
		Raids.tell(level, target.seat(), Component.translatable(militia ? "message.jaymoskingdoms.clash.militia" : "message.jaymoskingdoms.clash.raid",
			from.name(), target.name()).withStyle(ChatFormatting.RED));
		for (ServerPlayer player : Raids.players(level, target.seat(), Raids.ANNOUNCE_RANGE)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, militia ? 1.3F : 0.9F);
		}
		JaymosKingdoms.LOGGER.info("{} sends {} against {} ({})", from.name(), party.men.size(), target.name(), militia ? "striking back" : "a border raid");
		return true;
	}

	/** Where a party from {@code from} gathers against {@code target}: on loaded ground toward {@code from}; null if none is. */
	private static @Nullable BlockPos muster(ServerLevel level, Faction from, Faction target) {
		Vec3 dir = Vec3.atCenterOf(from.seat()).subtract(Vec3.atCenterOf(target.seat())).multiply(1.0, 0.0, 1.0);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
		for (int d : MUSTER_DISTANCE) {
			BlockPos at = target.seat().offset((int) Math.round(dir.x * d), 0, (int) Math.round(dir.z * d));
			if (level.isPositionEntityTicking(at)) {
				return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			}
		}
		return null;
	}

	// ---- driving ---------------------------------------------------------------------------------------------------

	private static void drive(ServerLevel level, Party party, long now) {
		MinecraftServer server = level.getServer();
		Faction target = Factions.get(server, party.target);
		List<PathfinderMob> men = new ArrayList<>();
		for (UUID id : List.copyOf(party.men)) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive()) {
				men.add(m);
			}
		}
		if (men.isEmpty() || target == null || now - party.started > PARTY_MAX_TICKS) {
			if (men.isEmpty() && target != null && party.phase != Phase.RETURN) {
				Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.clash.driven_off", name(server, party.from), target.name())
					.withStyle(ChatFormatting.GREEN));
			}
			end(party, men);
			return;
		}
		switch (party.phase) {
			case MARCH -> {
				boolean there = men.stream().anyMatch(m -> Raids.horizontal(m.blockPosition(), target.seat()) < ARRIVE_RANGE);
				if (there || now - party.phaseAt > MARCH_TICKS) {
					party.phase = Phase.FIGHT;
					party.phaseAt = now;
				}
			}
			case FIGHT -> {
				for (PathfinderMob m : men) {
					m.setHomeTo(target.seat(), Raids.RAID_HOME_RADIUS);
					LivingEntity foe = Rulers.target(m);
					if (foe == null || !foe.isAlive()) {
						foe = defender(level, m, target);
						if (foe != null) {
							Rulers.charge(m, foe);
						}
					}
				}
				// the seat's own come out to meet them
				for (Worker w : Workers.near(level, new AABB(target.seat()).inflate(DEFEND_RANGE),
					x -> x.isAlive() && x.factionId() == target.id() && x.role() == Folk.GUARD && !member(x.mob()))) {
					LivingEntity foe = Rulers.target(w.mob());
					if (foe == null || !foe.isAlive()) {
						men.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(w.mob()))).ifPresent(m -> Rulers.charge(w.mob(), m));
					}
				}
				if (now - party.phaseAt > FIGHT_TICKS) {
					party.phase = Phase.RETURN;
					party.phaseAt = now;
					if (!party.militia && level.getRandom().nextFloat() < KIDNAP_CHANCE) {
						kidnap(level, party, target);
					}
				}
			}
			case RETURN -> {
				for (PathfinderMob m : men) {
					m.setHomeTo(party.muster, Raids.RAID_HOME_RADIUS);
					if (Raids.horizontal(m.blockPosition(), party.muster) < HOME_REACH
						&& level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), VANISH_RANGE, false) == null) {
						party.men.remove(m.getUUID());
						McaCompat.vanish(m);
					}
				}
			}
		}
		if (party.phase != Phase.FIGHT) {
			BlockPos goal = party.phase == Phase.MARCH ? target.seat() : party.muster;
			for (PathfinderMob m : men) {
				if (m.isAlive() && Rulers.target(m) == null && !Downed.isDowned(m)) {
					walk(m, goal);
				}
			}
		}
	}

	private static void walk(PathfinderMob m, BlockPos to) {
		Vec3 at = m.position();
		Vec3 dir = Vec3.atCenterOf(to).subtract(at);
		double dist = dir.horizontalDistance();
		if (dist < 2.0) {
			return;
		}
		Vec3 step = dist <= WAYPOINT ? Vec3.atCenterOf(to) : at.add(dir.scale(WAYPOINT / dist));
		if (m.getNavigation().isDone() || m.tickCount % 40 == 0) {
			m.getNavigation().moveTo(step.x, step.y, step.z, MARCH_SPEED);
		}
	}

	/** The nearest of {@code target}'s guards (else any of its grown men under arms) for raider {@code m} to fight. */
	private static @Nullable LivingEntity defender(ServerLevel level, PathfinderMob m, Faction target) {
		return Workers.near(level, new AABB(target.seat()).inflate(FIGHT_RANGE),
				w -> w.isAlive() && w.factionId() == target.id() && w.role() == Folk.GUARD && !member(w.mob()) && !Downed.isDowned(w.mob()))
			.stream().map(Worker::mob).min(Comparator.comparingDouble(g -> g.distanceToSqr(m))).orElse(null);
	}

	private static void end(Party party, List<PathfinderMob> men) {
		ACTIVE.remove(party.target);
		for (PathfinderMob m : men) {
			McaCompat.vanish(m);
		}
	}

	/** {@code party}, going home, carries off one of haven {@code target}'s children, bound at its muster. */
	private static void kidnap(ServerLevel level, Party party, Faction target) {
		MinecraftServer server = level.getServer();
		if (!level.isPositionEntityTicking(party.muster)) {
			return;
		}
		Villager child = level.getEntitiesOfClass(Villager.class, new AABB(target.seat()).inflate(CHILD_RANGE),
				v -> v.isAlive() && v.isBaby() && !Captives.bound(v) && Havens.home(v) != null && Havens.home(v).id() == target.id())
			.stream().findAny().orElse(null);
		if (child == null) {
			return;
		}
		BlockPos at = Construction.standable(level, party.muster, 4);
		child.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Captives.hold(level, child, target);
		Map<String, String> next = new HashMap<>(Factions.level(server).getAttachedOrElse(TAKEN, Map.of()));
		next.put(Integer.toString(target.id()), child.getUUID() + "|" + child.getName().getString() + "|" + at.getX() + "|" + at.getY() + "|" + at.getZ()
			+ "|" + name(server, party.from));
		Factions.level(server).setAttached(TAKEN, Map.copyOf(next));
		// one of the haven's grown folk cries out, and asks for the child back
		Villager crier = level.getEntitiesOfClass(Villager.class, new AABB(target.seat()).inflate(CHILD_RANGE),
				v -> v.isAlive() && !v.isBaby() && !Captives.bound(v) && Havens.home(v) != null && Havens.home(v).id() == target.id())
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(target.seat())))).orElse(null);
		if (crier != null) {
			Speech.say(level, crier, Component.translatable("speech.jaymoskingdoms.clash.taken." + level.getRandom().nextInt(2), child.getName()), Speech.RED,
				32.0);
			Quest q = QuestBoard.havenRescue(level, crier, target, level.getRandom());
			if (q != null) {
				Quests.offerNow(level, crier, q);
			}
		}
		JaymosKingdoms.LOGGER.info("raiders from {} carried off {} from {} to {}", name(server, party.from), child.getName().getString(), target.name(), at);
	}

	/** The child carried off from haven {@code f}: "uuid|name|x|y|z|realm", or null. */
	static @Nullable String[] taken(MinecraftServer server, Faction f) {
		String entry = Factions.level(server).getAttachedOrElse(TAKEN, Map.of()).get(Integer.toString(f.id()));
		if (entry == null) {
			return null;
		}
		String[] parts = entry.split("\\|", 6);
		return parts.length == 6 ? parts : null;
	}

	/** A captive child is home: no longer carried off. */
	static void home(MinecraftServer server, UUID child) {
		Map<String, String> map = Factions.level(server).getAttachedOrElse(TAKEN, Map.of());
		Map<String, String> next = new HashMap<>(map);
		next.values().removeIf(entry -> entry.startsWith(child.toString()));
		if (next.size() != map.size()) {
			Factions.level(server).setAttached(TAKEN, Map.copyOf(next));
		}
	}

	private static String name(MinecraftServer server, int id) {
		Faction f = Factions.get(server, id);
		return f == null ? "?" : f.name();
	}

	// ---- workers ---------------------------------------------------------------------------------------------------

	/** One of a party's men ({@code Workers.step}): true while he marches or goes home (in place of his brain). */
	static boolean villagerStep(ServerLevel level, Worker w) {
		PathfinderMob m = w.mob();
		Party party = partyOf(m.getUUID());
		if (party == null) {
			return false;
		}
		return party.phase != Phase.FIGHT && Rulers.target(m) == null && !m.isSleeping() && !Downed.isDowned(m) && !Rulers.foeNear(level, m);
	}

	static boolean isMan(Worker w) {
		return TASK.equals(w.task());
	}

	/**
	 * A faction's villager died ({@code KingdomEvents.workerDied}): off its party; and whoever cut them down took a side.
	 * True if they were one of a party's men (never on a roster).
	 */
	static boolean died(ServerLevel level, Worker w, @Nullable ServerPlayer killer) {
		MinecraftServer server = level.getServer();
		Party party = partyOf(w.getUUID());
		if (party != null) {
			party.men.remove(w.getUUID());
			Faction raided = Factions.get(server, party.target);
			if (killer != null && raided != null) {
				Reputation.change(killer, raided, REP_DEFENDED);
			}
			return true;
		}
		Party raid = ACTIVE.get(w.factionId());
		if (raid != null && killer != null && raid.phase == Phase.FIGHT) {
			Faction raiders = Factions.get(server, raid.from);
			if (raiders != null) {
				Reputation.change(killer, raiders, REP_SIDED);
			}
		}
		return false;
	}
}
