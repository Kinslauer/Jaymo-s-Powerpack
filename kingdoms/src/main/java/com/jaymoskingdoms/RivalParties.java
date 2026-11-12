package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import java.util.UUID;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rival parties (docs/Pawn-Plan.md row H4; after the other Arisen and their pawns of Dragon's Dogma, a commercial game:
 * ideas only). Now and then a party of sellswords takes the road: a leader and two or three more, built from the same
 * parts as your own companions (leanings, {@link Veterans grades}, callouts, what they learn of you, {@link FoeLore}).
 * They go after camps ({@link Camps}) and fight the bands there, rest at a settlement, and move on.
 * <p>
 * They're rivals, not foes: they leave you be unless you hit one of them (then the whole party turns on you), and they
 * let you know when you're after the same camp. Or hire them: coin used on the leader takes the lot on for a day
 * ({@link #DAY}), at their price, and they follow you and fight as yours; then they go back to the road.
 * <p>
 * Built out to the whole row: a party resting in a town takes a bounty off its court's board ({@link Bounties}), goes
 * after the one it's on (a crook, or you if it's you), and carries them in downed to the court's seat. A party can be
 * befriended: gifts of food, help in its fights and a hire that ended well build its goodwill ({@link #FRIEND}), and a
 * friend's party greets them as one, hires out at half price, comes to their aid and won't take a bounty on them. And a
 * party that lost to you remembers it ({@link Memory}): it may come back down the road, harder for it (the leader a
 * knight, more health for each loss), knowing your ways ({@link FoeLore}, kept by its side), and either wanting it out
 * with you (a bold leader) or giving you a wide berth.
 */
public final class RivalParties {
	/** Looked at this often (ticks); a party may set out this often (ticks), at this chance, with at most this many about. */
	static final int EVERY = 40, SPAWN_EVERY = 2400, MAX = 2;
	static final float CHANCE = 0.15F;
	/** A settlement this near a player (blocks) may send one out; a party lasts this long (ticks), then goes when nobody's near. */
	static final double SPAWN_NEAR = 160.0, CAMP_RANGE = 320.0, TOWN_RANGE = 400.0;
	static final long LIFETIME = 72000L, DAY = 24000L, REST = 2400L;
	/** Walks this far at a time toward its goal (blocks); members keep this near their leader; arrived this near. */
	static final double HOP = 20.0, KEEP_UP = 6.0, AT_GOAL = 20.0, CAMP_FIGHT = 32.0, GREET_NEAR = 8.0;
	static final int SAY_EVERY = 1200;

	/** On a party member: the party, whether it leads, who's hired them (and until when), when it set out. Saved. */
	public record Party(int id, boolean leader, String hiredBy, long until, long born) {
		static final Codec<Party> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Party::id),
			Codec.BOOL.optionalFieldOf("leader", false).forGetter(Party::leader),
			Codec.STRING.optionalFieldOf("hired_by", "").forGetter(Party::hiredBy),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(Party::until),
			Codec.LONG.optionalFieldOf("born", 0L).forGetter(Party::born)).apply(i, Party::new));

		boolean hired(long now) {
			return !this.hiredBy.isEmpty() && now < this.until;
		}
	}

	public static final AttachmentType<Party> PARTY = AttachmentRegistry.create(JaymosKingdoms.id("rival_party"),
		builder -> builder.persistent(Party.CODEC));

	/** Goodwill: friends from this; what a gift of food, a blow struck in their fight and a hire ended well give; the most. */
	static final int FRIEND = 10, GIFT_WILL = 2, HELP_WILL = 1, HIRE_WILL = 3, WILL_MOST = 30, HELP_GAP = 200;
	/** A party comes to a friend's aid this near (blocks); sees the one its bounty's on this near; sets them down this near the court's seat. */
	static final double AID_NEAR = 32.0, HUNT_SEE = 48.0, COURT_NEAR = 10.0, HUNT_FIND = 160.0;
	/** The least bounty a party takes (emeralds). */
	static final int BOUNTY_LEAST = 5;
	/** A remembered party comes back at this chance when one sets out, unless it's been this long (ticks); health it gains per loss, losses counted. */
	static final float RETURN_CHANCE = 0.5F;
	static final long FORGET = 24000L * 30, LOSS_GAP = 2400L;
	static final double HEALTH_PER_LOSS = 4.0;
	static final int MOST_LOSSES = 3;

	/** A party remembered after it leaves the road: how many, whether a knight led it, last seen, losses to and goodwill of each player. */
	public record Memory(int size, boolean knight, long last, Map<String, Integer> beaten, Map<String, Integer> will, long lost) {
		static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("size", 3).forGetter(Memory::size),
			Codec.BOOL.optionalFieldOf("knight", false).forGetter(Memory::knight),
			Codec.LONG.optionalFieldOf("last", 0L).forGetter(Memory::last),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("beaten", Map.of()).forGetter(Memory::beaten),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("will", Map.of()).forGetter(Memory::will),
			Codec.LONG.optionalFieldOf("lost", 0L).forGetter(Memory::lost)).apply(i, Memory::new));

		int will(Player p) {
			return this.will.getOrDefault(p.getUUID().toString(), 0);
		}

		int beaten(Player p) {
			return this.beaten.getOrDefault(p.getUUID().toString(), 0);
		}
	}

	/** Every party remembered (id → memory), kept on the Overworld. Saved. */
	static final AttachmentType<Map<String, Memory>> MEMORY = AttachmentRegistry.create(JaymosKingdoms.id("rival_party_memory"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Memory.CODEC)));

	/** A bounty a party has taken: the board's key, the court, its seat. */
	private record Hunt(String key, int court, BlockPos seat) {
	}

	private static final Map<Integer, Hunt> HUNTS = new HashMap<>();
	private static final Map<String, Long> HELPED = new HashMap<>();

	private static final Set<Villager> LOADED = new HashSet<>();
	/** Where each party is headed, and whether it's a camp; when it may leave a settlement. */
	private record Goal(BlockPos at, boolean camp, long restUntil) {
	}

	private static final Map<Integer, Goal> GOALS = new HashMap<>();
	private static final Map<Integer, Long> SAID = new HashMap<>();

	private RivalParties() {
	}

	static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(RivalParties::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(PARTY)) {
				LOADED.add(v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				LOADED.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			GOALS.clear();
			SAID.clear();
			HUNTS.clear();
			HELPED.clear();
		});
		UseEntityCallback.EVENT.register(RivalParties::use);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> helped(victim, source));
	}

	/** (BandLeaders) {@code m} leads a rival party. */
	static boolean leads(LivingEntity m) {
		Party p = m.getAttached(PARTY);
		return p != null && p.leader();
	}

	/** (Followers.takesOrders) {@code player} has hired {@code v}'s party. */
	static boolean hiredBy(Entity v, Player player) {
		Party p = v.getAttached(PARTY);
		return p != null && p.hired(v.level().getGameTime()) && p.hiredBy().equals(player.getUUID().toString());
	}

	/** (Loans.bandSide) a party's side: its hirer's while hired. */
	static String side(Entity e, Party p) {
		return p.hired(e.level().getGameTime()) ? "p:" + p.hiredBy() : "party:" + p.id();
	}

	// ---- setting out ---------------------------------------------------------------------------------------------------

	private static void spawn(MinecraftServer server, long now) {
		ServerLevel level = Factions.level(server);
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= CHANCE || LOADED.stream().map(v -> v.getAttached(PARTY)).filter(p -> p != null && p.leader()).count() >= MAX) {
			return;
		}
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer near = players.get(random.nextInt(players.size()));
		List<Faction> towns = Factions.all(server).stream().filter(f -> !f.fallen() && f.type() != FactionType.BARBARIAN
			&& f.seat().distSqr(near.blockPosition()) < SPAWN_NEAR * SPAWN_NEAR).toList();
		if (towns.isEmpty()) {
			return;
		}
		Faction from = towns.get(random.nextInt(towns.size()));
		BlockPos at = Drifters.onRoad(level, from, random);
		if (at == null || !level.isPositionEntityTicking(at)) {
			return;
		}
		int id = random.nextInt(Integer.MAX_VALUE);
		int size = 3 + random.nextInt(2);
		// a party that's met someone may come back down the road: the same party (its side keeps what it learned), changed
		Memory back = null;
		if (random.nextFloat() < RETURN_CHANCE) {
			Set<Integer> out = new HashSet<>();
			for (Villager v : LOADED) {
				Party p = v.getAttached(PARTY);
				if (p != null) {
					out.add(p.id());
				}
			}
			List<Map.Entry<String, Memory>> known = memories(server).entrySet().stream().filter(e -> now - e.getValue().last() < FORGET
				&& (!e.getValue().beaten().isEmpty() || !e.getValue().will().isEmpty()) && !out.contains(Integer.parseInt(e.getKey()))).toList();
			if (!known.isEmpty()) {
				Map.Entry<String, Memory> e = known.get(random.nextInt(known.size()));
				id = Integer.parseInt(e.getKey());
				back = e.getValue();
				size = Math.max(3, back.size());
			}
		}
		int losses = back == null ? 0 : Math.min(MOST_LOSSES, back.beaten().values().stream().mapToInt(Integer::intValue).sum());
		for (int i = 0; i < size; i++) {
			Villager v = McaCompat.visitor(level, Vec3.atBottomCenterOf(at).add(random.nextDouble() * 2.0 - 1.0, 0.0, random.nextDouble() * 2.0 - 1.0),
				null, false);
			if (v == null) {
				continue;
			}
			McaBridge.toGuard(v);
			boolean knight = back != null ? back.knight() || losses > 0 : random.nextBoolean();
			Veterans.make(v, i == 0 && knight ? Veterans.KNIGHT : Veterans.VETERAN);
			if (losses > 0) {
				// harder for having lost
				AttributeInstance health = v.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("party_beaten"), losses * HEALTH_PER_LOSS,
						AttributeModifier.Operation.ADD_VALUE));
					v.setHealth(v.getMaxHealth());
				}
			}
			v.setAttached(PARTY, new Party(id, i == 0, "", 0L, now));
			v.setPersistenceRequired();
			LOADED.add(v);
			// some set out with the Murmur (docs/Pawn-Plan.md H2)
			Murmur.carrier(v);
		}
		JaymosKingdoms.LOGGER.debug("A rival party set out from {}{}", from.name(), back != null ? " (back again)" : "");
	}

	// ---- what a party remembers ----------------------------------------------------------------------------------------

	static Map<String, Memory> memories(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(MEMORY, Map.of());
	}

	static Memory memory(MinecraftServer server, int id) {
		Memory m = memories(server).get(Integer.toString(id));
		return m != null ? m : new Memory(3, false, 0L, Map.of(), Map.of(), 0L);
	}

	private static void remember(MinecraftServer server, int id, Memory m) {
		Map<String, Memory> all = new HashMap<>(memories(server));
		all.put(Integer.toString(id), m);
		server.overworld().setAttached(MEMORY, Map.copyOf(all));
	}

	/** {@code player}'s goodwill with party {@code id} changes by {@code by} (or is set to 0 when {@code by} is negative and large). */
	private static void will(MinecraftServer server, int id, Player player, int by) {
		Memory m = memory(server, id);
		Map<String, Integer> will = new HashMap<>(m.will());
		String key = player.getUUID().toString();
		int was = will.getOrDefault(key, 0);
		int now = Math.max(0, Math.min(WILL_MOST, was + by));
		if (now == 0) {
			will.remove(key);
		} else {
			will.put(key, now);
		}
		remember(server, id, new Memory(m.size(), m.knight(), m.last(), m.beaten(), Map.copyOf(will), m.lost()));
	}

	/** {@code player} counts party {@code id} as friends. */
	static boolean friends(MinecraftServer server, int id, Player player) {
		return memory(server, id).will(player) >= FRIEND;
	}

	/** The party has gone down, all of it: the players who beat it are remembered. */
	private static void lost(ServerLevel level, int id, List<Villager> members, long now) {
		Memory m = memory(level.getServer(), id);
		if (now - m.lost() < LOSS_GAP) {
			return;
		}
		Set<String> by = new HashSet<>();
		for (Villager v : members) {
			LivingEntity hit = v.getLastHurtByMob();
			if (hit == null || now - v.getLastHurtByMobTimestamp() > 400) {
				continue;
			}
			String side = Comrades.side(hit);
			if (side.startsWith("p:")) {
				by.add(side.substring(2));
			}
		}
		if (by.isEmpty()) {
			return;
		}
		Map<String, Integer> beaten = new HashMap<>(m.beaten());
		Map<String, Integer> will = new HashMap<>(m.will());
		for (String p : by) {
			beaten.merge(p, 1, Integer::sum);
			will.remove(p);
		}
		boolean knight = members.stream().anyMatch(v -> Veterans.grade(v) >= Veterans.KNIGHT);
		remember(level.getServer(), id, new Memory(members.size(), knight, now, Map.copyOf(beaten), Map.copyOf(will), now));
	}

	/** A player's blow on someone a party is fighting: goodwill (once a while). */
	private static void helped(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(victim.level() instanceof ServerLevel level) || LOADED.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Villager v : LOADED) {
			Party p = v.getAttached(PARTY);
			if (p != null && v.level() == level && !p.hired(now) && Comrades.target(v) == victim && v.distanceTo(player) < AID_NEAR) {
				String key = p.id() + "|" + player.getUUID();
				if (now - HELPED.getOrDefault(key, -HELP_GAP * 2L) > HELP_GAP) {
					HELPED.put(key, now);
					will(level.getServer(), p.id(), player, HELP_WILL);
				}
				return;
			}
		}
	}

	// ---- on the road ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		long now = server.overworld().getGameTime();
		if (t % SPAWN_EVERY == 0) {
			spawn(server, now);
		}
		if (t % EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		Map<Integer, List<Villager>> parties = new HashMap<>();
		for (Villager v : new ArrayList<>(LOADED)) {
			Party p = v.getAttached(PARTY);
			if (p == null || !v.isAlive() || v.isRemoved()) {
				LOADED.remove(v);
				continue;
			}
			parties.computeIfAbsent(p.id(), k -> new ArrayList<>()).add(v);
		}
		for (Map.Entry<Integer, List<Villager>> e : parties.entrySet()) {
			step(e.getKey(), e.getValue(), now);
		}
		GOALS.keySet().retainAll(parties.keySet());
	}

	private static void step(int id, List<Villager> members, long now) {
		Villager leader = members.stream().filter(v -> v.getAttached(PARTY).leader() && !Downed.isDowned(v)).findFirst()
			.orElse(members.stream().filter(v -> !Downed.isDowned(v)).findFirst().orElse(null));
		if (leader == null) {
			// all of them down: whoever beat them is remembered
			if (members.get(0).level() instanceof ServerLevel level) {
				lost(level, id, members, now);
			}
			return;
		}
		if (!(leader.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Party lp = leader.getAttached(PARTY);
		if (!lp.leader()) {
			// the leader's gone: the next one leads
			leader.setAttached(PARTY, new Party(lp.id(), true, lp.hiredBy(), lp.until(), lp.born()));
			lp = leader.getAttached(PARTY);
		}
		// a hire runs out: back to the road
		if (!lp.hiredBy().isEmpty() && !lp.hired(now)) {
			// a hire that ended well is remembered kindly
			if (level.getPlayerByUUID(UUID.fromString(lp.hiredBy())) instanceof ServerPlayer hirer) {
				will(server, id, hirer, HIRE_WILL);
			}
			for (Villager v : members) {
				Party p = v.getAttached(PARTY);
				v.setAttached(PARTY, new Party(p.id(), p.leader(), "", 0L, p.born()));
				McaBridge.stopFollowing(v);
			}
			Callouts.say(leader, "party.done", null, true);
			return;
		}
		if (lp.hired(now)) {
			return;
		}
		if (now - lp.born() > LIFETIME && level.getNearestPlayer(leader, 64.0) == null) {
			// off the road, but remembered
			Memory m = memory(server, id);
			remember(server, id, new Memory(members.size(), Veterans.grade(leader) >= Veterans.KNIGHT, now, m.beaten(), m.will(), m.lost()));
			HUNTS.remove(id);
			for (Villager v : members) {
				LOADED.remove(v);
				v.discard();
			}
			return;
		}
		// hit by a player: the whole party turns on them
		for (Villager v : members) {
			if (v.getLastHurtByMob() instanceof Player p && now - v.getLastHurtByMobTimestamp() < 100 && !p.isCreative()) {
				for (Villager m : members) {
					if (Comrades.target(m) == null && !Downed.isDowned(m)) {
						Comrades.aim(m, p);
					}
				}
				Callouts.say(leader, "party.warn", p, false, p.getDisplayName());
				will(server, id, p, -WILL_MOST);
				return;
			}
		}
		// a friend in a fight near: they come to help
		for (ServerPlayer p : level.getPlayers(p -> p.distanceTo(leader) < AID_NEAR && !p.isSpectator())) {
			LivingEntity foe = p.getLastHurtByMob();
			if (foe != null && foe.isAlive() && !Downed.isDowned(foe) && now - p.getLastHurtByMobTimestamp() < 100 && friends(server, id, p)
				&& !(foe instanceof Villager fv && fv.getAttached(PARTY) != null)) {
				for (Villager m : members) {
					if (Comrades.target(m) == null && !Downed.isDowned(m)) {
						Comrades.aim(m, foe);
					}
				}
				Callouts.say(leader, "party.aid", p, false, p.getDisplayName());
				return;
			}
		}
		// a bounty taken: after the one it's on
		Hunt hunt = HUNTS.get(id);
		if (hunt != null && hunt(level, id, leader, members, hunt, now)) {
			return;
		}
		// back again: the one who beat them
		Memory mem = memory(server, id);
		if (!mem.beaten().isEmpty()) {
			for (ServerPlayer p : level.getPlayers(p -> p.distanceTo(leader) < HUNT_SEE && !p.isSpectator() && !p.isCreative())) {
				if (mem.beaten(p) <= 0 || now - mem.lost() < LOSS_GAP) {
					continue;
				}
				if (Callouts.voice(leader) == Callouts.Voice.BOLD) {
					if (leader.distanceTo(p) < 16.0 && leader.hasLineOfSight(p)) {
						for (Villager m : members) {
							if (Comrades.target(m) == null && !Downed.isDowned(m)) {
								Comrades.aim(m, p);
							}
						}
						Callouts.say(leader, "party.rematch", p, false, p.getDisplayName());
						return;
					}
				} else if (leader.distanceTo(p) < 24.0) {
					// a wide berth
					Vec3 away = leader.position().subtract(p.position()).normalize().scale(HOP);
					BlockPos to = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(leader.position().add(away)));
					leader.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.7);
					Callouts.say(leader, "party.wary", p, false, p.getDisplayName());
					follow(leader, members);
					return;
				}
			}
		}
		Goal goal = GOALS.get(id);
		if (goal == null) {
			goal = choose(level, leader, null);
			if (goal == null) {
				return;
			}
			GOALS.put(id, goal);
		}
		boolean there = leader.blockPosition().distSqr(goal.at()) < AT_GOAL * AT_GOAL;
		if (there && goal.camp()) {
			List<Mob> band = level.getEntitiesOfClass(Mob.class, leader.getBoundingBox().inflate(CAMP_FIGHT), m -> m.isAlive()
				&& !Downed.isDowned(m) && Illagers.isCampIllager(m));
			if (band.isEmpty()) {
				Callouts.say(leader, "party.won", null, true);
				GOALS.remove(id);
				return;
			}
			for (Villager v : members) {
				if (Comrades.target(v) == null && !Downed.isDowned(v)) {
					Comrades.aim(v, band.get(v.getRandom().nextInt(band.size())));
				}
			}
			// someone else after the same camp
			for (ServerPlayer p : level.getPlayers(p -> p.distanceTo(leader) < CAMP_FIGHT && !p.isSpectator())) {
				if (now - SAID.getOrDefault(id, -SAY_EVERY * 2L) > SAY_EVERY) {
					SAID.put(id, now);
					Callouts.say(leader, "party.race", p, true, p.getDisplayName());
				}
				break;
			}
			return;
		}
		if (there) {
			if (goal.restUntil() == 0L) {
				GOALS.put(id, new Goal(goal.at(), false, now + REST));
				takeBounty(level, id, leader, goal.at(), now);
			} else if (now > goal.restUntil()) {
				Goal next = choose(level, leader, goal.at());
				if (next != null) {
					GOALS.put(id, next);
				}
			}
		} else if (Comrades.target(leader) == null && leader.getNavigation().isDone()) {
			hop(level, leader, goal.at(), 0.6);
		}
		follow(leader, members);
		// a word for a player who comes up to them
		ServerPlayer near = (ServerPlayer)level.getNearestPlayer(leader, GREET_NEAR);
		if (near != null && !near.isSpectator() && now - SAID.getOrDefault(id, -SAY_EVERY * 2L) > SAY_EVERY * 2L) {
			SAID.put(id, now);
			if (friends(server, id, near)) {
				Callouts.say(leader, "party.friend", near, true, near.getDisplayName(), price(members, server, id, near));
			} else if (mem.beaten(near) > 0) {
				Callouts.say(leader, "party.back", near, true, near.getDisplayName());
			} else {
				Callouts.say(leader, "party.greet", near, true, price(members));
			}
		}
	}

	/** {@code m} walks a hop at a time toward {@code at}. */
	private static void hop(ServerLevel level, Mob m, BlockPos at, double speed) {
		Vec3 to = Vec3.atBottomCenterOf(at).subtract(m.position());
		Vec3 hop = to.length() <= HOP ? Vec3.atBottomCenterOf(at) : m.position().add(to.normalize().scale(HOP));
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
		m.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, speed);
	}

	/** The rest keep up with {@code leader}. */
	private static void follow(Mob leader, List<Villager> members) {
		for (Villager v : members) {
			if (v != leader && Comrades.target(v) == null && !Downed.isDowned(v) && v.distanceTo(leader) > KEEP_UP && v.getNavigation().isDone()) {
				v.getNavigation().moveTo(leader, 0.7);
			}
		}
	}

	// ---- bounties ------------------------------------------------------------------------------------------------------

	/** Resting in a town: the party takes the best open bounty off its court's board, if there's one worth it. */
	private static void takeBounty(ServerLevel level, int id, Villager leader, BlockPos seat, long now) {
		if (HUNTS.containsKey(id)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction town = Factions.all(server).stream().filter(f -> f.seat().equals(seat)).findFirst().orElse(null);
		Faction court = Bounties.court(server, town);
		if (court == null) {
			return;
		}
		Bounties.Bounty best = null;
		for (Bounties.Bounty b : Bounties.all(server).values()) {
			// (not a warlord in his own city, nor a miniboss: arming one waits on a player taking it)
			if (b.dead() || b.court() != court.id() || b.takenByAnyone(now) || b.price() < BOUNTY_LEAST || b.boss() || "warlord".equals(b.crime())
				|| best != null && b.price() <= best.price()) {
				continue;
			}
			// not on a friend
			if (b.player() && level.getServer().getPlayerList().getPlayers().stream().anyMatch(p -> Bounties.targets(p).contains(b.target())
				&& friends(server, id, p))) {
				continue;
			}
			best = b;
		}
		if (best == null) {
			return;
		}
		Bounties.takenByParty(server, best.key(), "party:" + id, now);
		HUNTS.put(id, new Hunt(best.key(), court.id(), court.seat()));
		GOALS.remove(id);
		Callouts.say(leader, "party.bounty.took", null, true, Component.literal(best.name()), best.price());
	}

	/** After the one its bounty's on: found, fought, carried in. False if the hunt's over (on to the usual). */
	private static boolean hunt(ServerLevel level, int id, Villager leader, List<Villager> members, Hunt hunt, long now) {
		MinecraftServer server = level.getServer();
		Bounties.Bounty b = Bounties.all(server).get(hunt.key());
		String me = "party:" + id;
		if (b == null || b.dead() || b.hunter().isEmpty() || !b.hunter().get().equals(me) || !b.takenByAnyone(now)) {
			// off the board, or too long at it
			for (Villager v : members) {
				if (PowersCompat.npcDragging(v) != null) {
					PowersCompat.npcLetGo(v);
				}
			}
			HUNTS.remove(id);
			return false;
		}
		LivingEntity target = huntFor(level, b, leader);
		// carried in
		for (Villager v : members) {
			if (target != null && PowersCompat.npcDragging(v) == target) {
				if (v.blockPosition().distSqr(hunt.seat()) < COURT_NEAR * COURT_NEAR) {
					PowersCompat.npcLetGo(v);
					if (Bounties.broughtIn(level, target, hunt.court())) {
						Callouts.say(leader, "party.claimed", target, true, target.getDisplayName());
					}
					HUNTS.remove(id);
				} else if (v.getNavigation().isDone()) {
					hop(level, v, hunt.seat(), 0.8);
				}
				follow(v, members);
				return true;
			}
		}
		if (target == null) {
			// to where they were last seen (the board hears fresher word now and then)
			if (leader.getNavigation().isDone() && leader.blockPosition().distSqr(b.last()) > AT_GOAL * AT_GOAL) {
				hop(level, leader, b.last(), 0.7);
			}
			follow(leader, members);
			return true;
		}
		if (Downed.isDowned(target)) {
			Villager carrier = members.stream().filter(v -> !Downed.isDowned(v)).min((a, c) -> Double.compare(a.distanceTo(target), c.distanceTo(target)))
				.orElse(leader);
			if (carrier.distanceTo(target) > 2.5) {
				carrier.getNavigation().moveTo(target, 0.9);
			} else if (PowersCompat.npcLift(carrier, target) || PowersCompat.npcDrag(carrier, target)) {
				Callouts.say(carrier, "party.caught", target, true, target.getDisplayName());
				hop(level, carrier, hunt.seat(), 0.8);
			} else if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				// (no carrying without Jaymo's Powers: they settle it where they stand)
				if (Bounties.broughtIn(level, target, hunt.court())) {
					Callouts.say(leader, "party.claimed", target, true, target.getDisplayName());
				}
				HUNTS.remove(id);
			}
			return true;
		}
		if (leader.distanceTo(target) < HUNT_SEE && leader.hasLineOfSight(target)) {
			for (Villager m : members) {
				if (Comrades.target(m) != target && !Downed.isDowned(m)) {
					Comrades.aim(m, target);
				}
			}
			if (now - SAID.getOrDefault(id, -SAY_EVERY * 2L) > SAY_EVERY) {
				SAID.put(id, now);
				Callouts.say(leader, "party.bounty", target, true, target.getDisplayName(), b.price());
			}
			return true;
		}
		if (leader.getNavigation().isDone()) {
			hop(level, leader, target.blockPosition(), 0.8);
		}
		follow(leader, members);
		return true;
	}

	/** The one bounty {@code b} is on, if they're about. */
	private static @Nullable LivingEntity huntFor(ServerLevel level, Bounties.Bounty b, Villager leader) {
		if (b.player()) {
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && p.distanceTo(leader) < HUNT_FIND && Bounties.targets(p).contains(b.target())) {
					return p;
				}
			}
			return null;
		}
		try {
			return level.getEntity(UUID.fromString(b.target())) instanceof LivingEntity e && e.isAlive() && e.distanceTo(leader) < HUNT_FIND ? e : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Where a party goes next: a camp in reach, else a settlement other than {@code not}. */
	private static @Nullable Goal choose(ServerLevel level, Villager leader, @Nullable BlockPos not) {
		BlockPos best = null;
		double bestD = CAMP_RANGE * CAMP_RANGE;
		for (Camps.CampData c : Camps.all(level.getServer()).values()) {
			double d = c.center().distSqr(leader.blockPosition());
			if (d < bestD && !c.center().equals(not)) {
				bestD = d;
				best = c.center();
			}
		}
		if (best != null) {
			return new Goal(best, true, 0L);
		}
		List<Faction> towns = Factions.all(level.getServer()).stream().filter(f -> !f.fallen() && f.type() != FactionType.BARBARIAN
			&& !f.seat().equals(not) && f.seat().distSqr(leader.blockPosition()) < TOWN_RANGE * TOWN_RANGE).toList();
		return towns.isEmpty() ? null : new Goal(towns.get(leader.getRandom().nextInt(towns.size())).seat(), false, 0L);
	}

	/** What the party asks of {@code player}: half to a friend. */
	private static int price(List<Villager> members, MinecraftServer server, int id, Player player) {
		int full = price(members);
		return friends(server, id, player) ? Math.max(1, full / 2) : full;
	}

	private static int price(List<Villager> members) {
		int sum = 0;
		for (Villager v : members) {
			if (v.isAlive() && !Downed.isDowned(v)) {
				sum += Veterans.price(v);
			}
		}
		return sum;
	}

	// ---- hiring --------------------------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isSpectator()
			|| !(entity instanceof Villager leader)) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getMainHandItem();
		Party gp = leader.getAttached(PARTY);
		long now = server.getGameTime();
		// a gift of food or drink to any of them: goodwill
		if (gp != null && !gp.hired(now) && !sp.isShiftKeyDown() && held.has(DataComponents.FOOD) && Comrades.target(leader) != sp) {
			held.shrink(1);
			will(server.getServer(), gp.id(), sp, GIFT_WILL);
			Callouts.say(leader, friends(server.getServer(), gp.id(), sp) ? "party.gift.friend" : "party.gift", sp, true, sp.getDisplayName());
			return InteractionResult.SUCCESS;
		}
		if (!Coinage.isMoney(held)) {
			return InteractionResult.PASS;
		}
		Party lp = gp;
		if (lp == null || !lp.leader() || lp.hired(now)) {
			return InteractionResult.PASS;
		}
		List<Villager> members = new ArrayList<>();
		for (Villager v : LOADED) {
			Party p = v.getAttached(PARTY);
			if (p != null && p.id() == lp.id() && v.isAlive() && !Downed.isDowned(v)) {
				members.add(v);
			}
		}
		if (members.stream().anyMatch(v -> Comrades.target(v) == sp)) {
			return InteractionResult.PASS;
		}
		int cost = price(members, server.getServer(), lp.id(), sp);
		if (!Money.canPay(sp, Money.worth(cost))) {
			Callouts.say(leader, "party.short", sp, true, Money.text(sp, Money.worth(cost)));
			return InteractionResult.SUCCESS;
		}
		Money.charge(sp, Money.worth(cost));
		for (Villager v : members) {
			Party p = v.getAttached(PARTY);
			v.setAttached(PARTY, new Party(p.id(), p.leader(), sp.getUUID().toString(), now + DAY, p.born()));
			McaBridge.moveState(v, 2, sp);
		}
		GOALS.remove(lp.id());
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.party.hired", leader.getDisplayName(), members.size(), cost)
			.withStyle(ChatFormatting.GOLD));
		Callouts.say(leader, "party.hired", sp, true, sp.getDisplayName());
		return InteractionResult.SUCCESS;
	}
}
