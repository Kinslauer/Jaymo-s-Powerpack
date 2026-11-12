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
		});
		UseEntityCallback.EVENT.register(RivalParties::use);
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
		for (int i = 0; i < size; i++) {
			Villager v = McaCompat.visitor(level, Vec3.atBottomCenterOf(at).add(random.nextDouble() * 2.0 - 1.0, 0.0, random.nextDouble() * 2.0 - 1.0),
				null, false);
			if (v == null) {
				continue;
			}
			McaBridge.toGuard(v);
			Veterans.make(v, i == 0 ? (random.nextBoolean() ? Veterans.KNIGHT : Veterans.VETERAN) : Veterans.VETERAN);
			v.setAttached(PARTY, new Party(id, i == 0, "", 0L, now));
			v.setPersistenceRequired();
			LOADED.add(v);
		}
		JaymosKingdoms.LOGGER.debug("A rival party set out from {}", from.name());
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
		if (leader == null || !(leader.level() instanceof ServerLevel level)) {
			return;
		}
		Party lp = leader.getAttached(PARTY);
		if (!lp.leader()) {
			// the leader's gone: the next one leads
			leader.setAttached(PARTY, new Party(lp.id(), true, lp.hiredBy(), lp.until(), lp.born()));
			lp = leader.getAttached(PARTY);
		}
		// a hire runs out: back to the road
		if (!lp.hiredBy().isEmpty() && !lp.hired(now)) {
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
				return;
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
			} else if (now > goal.restUntil()) {
				Goal next = choose(level, leader, goal.at());
				if (next != null) {
					GOALS.put(id, next);
				}
			}
		} else if (Comrades.target(leader) == null && leader.getNavigation().isDone()) {
			Vec3 to = Vec3.atBottomCenterOf(goal.at()).subtract(leader.position());
			Vec3 hop = to.length() <= HOP ? Vec3.atBottomCenterOf(goal.at()) : leader.position().add(to.normalize().scale(HOP));
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
			leader.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.6);
		}
		for (Villager v : members) {
			if (v != leader && Comrades.target(v) == null && v.distanceTo(leader) > KEEP_UP && v.getNavigation().isDone()) {
				v.getNavigation().moveTo(leader, 0.7);
			}
		}
		// a word for a player who comes up to them
		ServerPlayer near = (ServerPlayer)level.getNearestPlayer(leader, GREET_NEAR);
		if (near != null && !near.isSpectator() && now - SAID.getOrDefault(id, -SAY_EVERY * 2L) > SAY_EVERY * 2L) {
			SAID.put(id, now);
			Callouts.say(leader, "party.greet", near, true, price(members));
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
			|| !(entity instanceof Villager leader) || !Coinage.isMoney(sp.getMainHandItem())) {
			return InteractionResult.PASS;
		}
		Party lp = leader.getAttached(PARTY);
		long now = server.getGameTime();
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
		int cost = price(members);
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
