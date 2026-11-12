package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * They know the way (docs/Pawn-Plan.md row B2; after the pawns of Dragon's Dogma, a commercial game, who have been
 * somewhere with another master and lead you there: ideas only). A companion remembers the places it has been: the
 * settlements it walked through and the untouched chests it saw ({@link #KNOWN}). Told Go out of a fight
 * ({@link QuickOrders}), if one of those places lies the way you're looking, it walks ahead and leads you there,
 * saying where; otherwise it scouts as before.
 * <p>
 * The foe side: a band knows its own ground. Within {@link #HOME} of its camp, one that loses sight of you cuts across
 * the land it knows to come at you again, quicker than you'd think.
 */
public final class Wayfinding {
	/** Places remembered at most; looked for this often (ticks); a chest this near (blocks) is noticed. */
	static final int KEEP = 32, LEARN_EVERY = 100;
	static final double CHEST_NEAR = 12.0;
	/** A place this far at least and at most (blocks), within this much of the look (cosine), is led to. */
	static final double LEAD_MIN = 24.0, LEAD_MAX = 800.0, LOOK_COS = 0.85;
	/** How far ahead of you a guide walks (blocks), how long it leads at most (ticks), how near counts as there. */
	static final double AHEAD = 10.0, THERE = 12.0, LOST = 40.0;
	static final int LEAD_TICKS = 6000, EVERY = 20;
	/** A band's home ground (blocks from its camp) and how long its short cut lasts (ticks). */
	static final double HOME = 64.0;
	static final int CUT_TICKS = 60, CUT_SAY_EVERY = 400;

	/** What a companion remembers: where (packed), what ("town:<name>" or "chest"), and in which world. Saved. */
	public record Known(List<Long> at, List<String> what, List<String> dim) {
		static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.listOf().fieldOf("at").forGetter(Known::at),
			Codec.STRING.listOf().fieldOf("what").forGetter(Known::what),
			Codec.STRING.listOf().fieldOf("dim").forGetter(Known::dim)).apply(i, Known::new));

		static final Known NONE = new Known(List.of(), List.of(), List.of());
	}

	public static final AttachmentType<Known> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("comrade_known_places"),
		builder -> builder.persistent(Known.CODEC));
	/** On the overworld: where each camp's raiders got into each town ("camp:town" -> packed). Saved. */
	static final AttachmentType<Map<String, Long>> WAY_IN = AttachmentRegistry.create(JaymosKingdoms.id("raid_way_in"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** On a player: quests a band saw them take (quest id -> camp id; negative once the band is waiting). Saved. */
	static final AttachmentType<Map<String, Integer>> WATCHED = AttachmentRegistry.create(JaymosKingdoms.id("quest_watched"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** A raider this near the seat has got in (blocks). */
	static final double WAY_IN_NEAR = 24.0;
	/** A band member this near (blocks) sees you take a quest; the band waits this many, and sets out when you're this near the place. */
	static final double SEES_QUEST = 32.0, WAIT_FROM = 128.0, WAIT_NOT_NEARER = 48.0;
	static final int WAITERS = 3;
	/** A road this near you (blocks) when a companion leads off it: they know a shorter way across. */
	static final double ON_ROAD = 4.0, SHORT_WAY = 64.0;

	private record Guide(UUID player, BlockPos to, long until) {
	}

	private static final Map<Villager, Guide> GUIDES = new java.util.IdentityHashMap<>();
	private static final Map<UUID, Long> CUT_SAID = new HashMap<>();

	private Wayfinding() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Wayfinding::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GUIDES.clear();
			CUT_SAID.clear();
		});
	}

	private static String dim(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/** {@code m} remembers {@code what} at {@code at} (once). */
	private static void remember(Mob m, BlockPos at, String what, String dim) {
		Known k = m.getAttachedOrElse(KNOWN, Known.NONE);
		for (int i = 0; i < k.at().size(); i++) {
			if (k.at().get(i) == at.asLong() || k.what().get(i).equals(what) && what.startsWith("town:")) {
				return;
			}
		}
		List<Long> a = new ArrayList<>(k.at());
		List<String> w = new ArrayList<>(k.what()), d = new ArrayList<>(k.dim());
		a.add(at.asLong());
		w.add(what);
		d.add(dim);
		while (a.size() > KEEP) {
			a.removeFirst();
			w.removeFirst();
			d.removeFirst();
		}
		m.setAttached(KNOWN, new Known(a, w, d));
	}

	private static void forget(Mob m, int i) {
		Known k = m.getAttachedOrElse(KNOWN, Known.NONE);
		List<Long> a = new ArrayList<>(k.at());
		List<String> w = new ArrayList<>(k.what()), d = new ArrayList<>(k.dim());
		a.remove(i);
		w.remove(i);
		d.remove(i);
		m.setAttached(KNOWN, new Known(a, w, d));
	}

	/** (QuickOrders, Go out of a fight) {@code v} leads {@code player} to a place it knows the way they look. False if none. */
	static boolean lead(ServerLevel level, Villager v, ServerPlayer player, long now) {
		Known k = v.getAttached(KNOWN);
		if (k == null) {
			return false;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return false;
		}
		flat = flat.normalize();
		String here = dim(level);
		int best = -1;
		double bestD = LEAD_MAX;
		for (int i = 0; i < k.at().size(); i++) {
			if (!k.dim().get(i).equals(here)) {
				continue;
			}
			BlockPos p = BlockPos.of(k.at().get(i));
			Vec3 to = new Vec3(p.getX() + 0.5 - player.getX(), 0.0, p.getZ() + 0.5 - player.getZ());
			double d = to.length();
			if (d < LEAD_MIN || d > bestD || to.normalize().dot(flat) < LOOK_COS) {
				continue;
			}
			if (k.what().get(i).equals("chest") && level.isLoaded(p) && !unlooted(level.getBlockEntity(p))) {
				// opened since: nothing to lead to
				forget(v, i);
				return lead(level, v, player, now);
			}
			best = i;
			bestD = d;
		}
		// a quest's next step it knows the way to (row B3)
		BlockPos questAt = null;
		Component giver = null;
		List<String> known = v.getAttachedOrElse(Remarks.QUESTS, List.of());
		for (Quest q : Quests.log(player)) {
			if (q.objective().pos().isEmpty() || !known.contains(q.template())) {
				continue;
			}
			BlockPos p = q.objective().pos().get();
			Vec3 to = new Vec3(p.getX() + 0.5 - player.getX(), 0.0, p.getZ() + 0.5 - player.getZ());
			double d = to.length();
			if (d >= LEAD_MIN && d < bestD && to.normalize().dot(flat) >= LOOK_COS) {
				bestD = d;
				questAt = p;
				giver = Component.literal(q.giver().name());
			}
		}
		if (best < 0 && questAt == null) {
			return false;
		}
		BlockPos to = questAt != null ? questAt : BlockPos.of(k.at().get(best));
		String what = questAt != null ? "quest" : k.what().get(best);
		McaBridge.moveState(v, 0, player);
		GUIDES.put(v, new Guide(player.getUUID(), to, now + LEAD_TICKS));
		if (onRoad(level, player.blockPosition()) && bestD > SHORT_WAY) {
			// on a road that winds a long way round: they know a shorter way across country
			Callouts.say(v, "way.short", player, true);
		} else if (what.equals("quest")) {
			Callouts.say(v, "way.quest", player, true, giver);
		} else if (what.startsWith("town:")) {
			Callouts.say(v, "way.town", player, true, what.substring(5));
		} else {
			Callouts.say(v, "way.chest", player, true);
		}
		return true;
	}

	/** {@code at} is on (or beside) a road between settlements. */
	private static boolean onRoad(ServerLevel level, BlockPos at) {
		for (Roads.Road r : Roads.network(level.getServer()).roads()) {
			for (long packed : r.points()) {
				if (BlockPos.of(packed).distSqr(at) < ON_ROAD * ON_ROAD) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- the foe side: the way into a town, and waiting where you're headed -----------------------------------------------

	/** (Raids) where {@code camp}'s raiders got into {@code town} before, or null. */
	static @Nullable BlockPos wayIn(MinecraftServer server, int camp, int town) {
		Long p = Factions.level(server).getAttachedOrElse(WAY_IN, Map.of()).get(camp + ":" + town);
		return p == null ? null : BlockPos.of(p);
	}

	/** (Raids) {@code camp}'s raiders got into {@code town} at {@code at}. */
	static void noteWayIn(MinecraftServer server, int camp, int town, BlockPos at) {
		ServerLevel level = Factions.level(server);
		Map<String, Long> ways = new HashMap<>(level.getAttachedOrElse(WAY_IN, Map.of()));
		ways.put(camp + ":" + town, at.asLong());
		level.setAttached(WAY_IN, ways);
	}

	/** (Quests) {@code player} took {@code q}: a camp's band member who saw it remembers where they're headed. */
	static void questTaken(ServerLevel level, ServerPlayer player, Quest q) {
		if (q.objective().pos().isEmpty()) {
			return;
		}
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEES_QUEST), m -> m.isAlive() && !Downed.isDowned(m)
			&& Comrades.side(m).startsWith("f:") && m.hasLineOfSight(player))) {
			int camp;
			try {
				camp = Integer.parseInt(Comrades.side(m).substring(2));
			} catch (NumberFormatException e) {
				continue;
			}
			if (Camps.data(level.getServer(), camp) == null) {
				continue;
			}
			Map<String, Integer> w = new HashMap<>(player.getAttachedOrElse(WATCHED, Map.of()));
			w.put(q.id(), camp);
			player.setAttached(WATCHED, w);
			return;
		}
	}

	/** A band that saw you take a quest is waiting there when you come (set out before you're in sight of the place). */
	private static void waiting(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Integer> w = player.getAttached(WATCHED);
			if (w == null || w.isEmpty()) {
				continue;
			}
			Map<String, Integer> next = new HashMap<>(w);
			boolean changed = next.keySet().retainAll(Quests.log(player).stream().map(Quest::id).toList());
			for (Quest q : Quests.log(player)) {
				Integer camp = next.get(q.id());
				if (camp == null || camp < 0 || q.objective().pos().isEmpty()) {
					continue;
				}
				BlockPos at = q.objective().pos().get();
				double d = Math.sqrt(player.blockPosition().distSqr(at));
				Faction f = Factions.get(server, camp);
				if (d > WAIT_FROM || d < WAIT_NOT_NEARER || f == null || f.fallen() || !player.level().isLoaded(at)) {
					continue;
				}
				ServerLevel level = player.level();
				for (int i = 0; i < WAITERS; i++) {
					BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(level.getRandom().nextInt(9) - 4, 0,
						level.getRandom().nextInt(9) - 4));
					Mob m = Raids.member(level, f, i, spot, at, 10, false);
					if (m != null && i == 0) {
						Callouts.say(m, "foe.waiting", player, true, player.getDisplayName());
					}
				}
				next.put(q.id(), -camp - 1);
				changed = true;
			}
			if (changed) {
				player.setAttached(WATCHED, next);
			}
		}
	}

	private static boolean unlooted(BlockEntity be) {
		return be instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null;
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		guide(server, now);
		cutAcross(server, now);
		if (t % LEARN_EVERY == 0) {
			waiting(server);
		}
		if (t % LEARN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			String dim = dim(level);
			for (Mob m : Bonds.companions(player, 32.0)) {
				Faction f = Factions.at(server, level, m.blockPosition());
				if (f != null && !f.fallen()) {
					remember(m, f.seat(), "town:" + f.name(), dim);
				}
				BlockPos at = m.blockPosition();
				for (BlockEntity be : level.getChunkAt(at).getBlockEntities().values()) {
					if (unlooted(be) && be.getBlockPos().distSqr(at) < CHEST_NEAR * CHEST_NEAR) {
						remember(m, be.getBlockPos(), "chest", dim);
					}
				}
			}
		}
	}

	/** Guides walk ahead of their player toward the place, and say when it's reached. */
	private static void guide(MinecraftServer server, long now) {
		for (var it = GUIDES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Villager, Guide> e = it.next();
			Villager v = e.getKey();
			Guide g = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(g.player());
			if (player == null || !v.isAlive() || v.level() != player.level() || now > g.until() || player.distanceTo(v) > LOST
				|| Comrades.target(v) != null || Downed.isDowned(v)) {
				it.remove();
				if (player != null && v.isAlive() && Followers.takesOrders(v, player)) {
					Followers.take(v, player, Followers.FOLLOW);
				}
				continue;
			}
			Vec3 target = Vec3.atBottomCenterOf(g.to());
			Vec3 flat = new Vec3(target.x - player.getX(), 0.0, target.z - player.getZ());
			if (flat.length() < THERE) {
				it.remove();
				Callouts.say(v, "way.here", player, true);
				if (Followers.takesOrders(v, player)) {
					Followers.take(v, player, Followers.FOLLOW);
				}
				continue;
			}
			Vec3 ahead = flat.length() <= AHEAD ? target : player.position().add(flat.normalize().scale(AHEAD));
			ServerLevel level = player.level();
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead));
			Vec3 point = Math.abs(top.getY() - player.getY()) <= 6.0 ? Vec3.atBottomCenterOf(top) : ahead;
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(point, 0.6F, 1));
			v.getLookControl().setLookAt(point.x, point.y + 1.0, point.z);
		}
	}

	/** A band on its own ground cuts across to whoever it lost sight of. */
	private static void cutAcross(MinecraftServer server, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			ServerLevel level = player.level();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> Comrades.target(m) == player
				&& !Downed.isDowned(m) && Comrades.side(m).startsWith("f:"))) {
				if (m.hasLineOfSight(player) || !onHomeGround(server, m)) {
					continue;
				}
				m.addEffect(new MobEffectInstance(MobEffects.SPEED, CUT_TICKS, 1));
				m.getNavigation().moveTo(player, 1.2);
				Long said = CUT_SAID.get(m.getUUID());
				if (said == null || now - said > CUT_SAY_EVERY) {
					CUT_SAID.put(m.getUUID(), now);
					Callouts.say(m, "way.cut", player, false);
				}
			}
		}
		if (CUT_SAID.size() > 256) {
			CUT_SAID.entrySet().removeIf(e -> now - e.getValue() > CUT_SAY_EVERY);
		}
	}

	private static boolean onHomeGround(MinecraftServer server, LivingEntity m) {
		String side = Comrades.side(m);
		try {
			Camps.CampData camp = Camps.data(server, Integer.parseInt(side.substring(2)));
			return camp != null && camp.center().distSqr(m.blockPosition()) < HOME * HOME;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
