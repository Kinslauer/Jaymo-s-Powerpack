package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.418) The record of road deeds: ids of things done (a hard road's aim finished is {@code rise.<road>}), kept on the
 * Overworld with the realms and saved with the world. (0.7.419) {@link RoadLocks} reads it to open the roads the deeds earn.
 * <p>
 * (0.7.466) Andy: "starts are now per group / player". A deed is no longer done for the whole world: it is done by a
 * player ({@link #OWN}, by UUID, so an order delivered while they're away still counts) and by their group, the handshake
 * party they're in ({@link Parties#team}, kept by team name in {@link #GROUPS}). A road a deed opens is open to the one who
 * did it, to everyone in their group then, and to everyone who later joins that group; and a player's own deeds are shared
 * with every group member who is on while they are. The old world-wide list ({@link #DONE}) now only holds the ops' forced
 * {@code open.<road>}/{@code shut.<road>} and the deeds done before 0.7.466; those old deeds were handed, once, to every
 * player who had already told their story ({@link #handOver}), and to nobody new.
 */
public final class RoadDeeds {
	/** On the Overworld: the ops' forced roads, and the deeds done world-wide before 0.7.466. Saved. */
	static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("road_deeds"),
		builder -> builder.persistent(Codec.STRING.listOf()));
	/** (0.7.466) On the Overworld: each player's own deeds, by UUID. Saved. */
	static final AttachmentType<Map<String, List<String>>> OWN = AttachmentRegistry.create(JaymosKingdoms.id("road_deeds_own"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())));
	/** (0.7.466) On the Overworld: each group's deeds (done by a member while in it), by its team's name. Saved. */
	static final AttachmentType<Map<String, List<String>>> GROUPS = AttachmentRegistry.create(JaymosKingdoms.id("road_deeds_groups"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())));
	/** (0.7.466) On the Overworld: the players already checked for the old world-wide deeds ({@link #handOver}). Saved. */
	static final AttachmentType<List<String>> HANDED = AttachmentRegistry.create(JaymosKingdoms.id("road_deeds_handed"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	/** How far off (blocks) a player in the same world sees a star come down, and so counts it. */
	static final double STAR_SEEN = 384.0;

	private RoadDeeds() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("road deed attachments: {} {} {} {}", DONE, OWN, GROUPS, HANDED);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handOver(server, handler.getPlayer()));
	}

	// ---- the world-wide list (ops, and the deeds from before 0.7.466) -------------------------------------------------

	/** Whether {@code id} is on the world-wide list (an op's forced road, or an old deed). */
	public static boolean has(MinecraftServer server, String id) {
		return Factions.level(server).getAttachedOrElse(DONE, List.of()).contains(id);
	}

	/** Puts {@code id} on the world-wide list (only the ops' forced roads now). True if it is new. */
	public static boolean done(MinecraftServer server, String id) {
		List<String> now = Factions.level(server).getAttachedOrElse(DONE, List.of());
		if (now.contains(id)) {
			return false;
		}
		List<String> more = new ArrayList<>(now);
		more.add(id);
		Factions.level(server).setAttached(DONE, List.copyOf(more));
		return true;
	}

	/** (0.7.419) Takes {@code id} off the world-wide list (ops testing the road locks). True if it was there. */
	public static boolean undo(MinecraftServer server, String id) {
		List<String> now = Factions.level(server).getAttachedOrElse(DONE, List.of());
		if (!now.contains(id)) {
			return false;
		}
		List<String> less = new ArrayList<>(now);
		less.remove(id);
		Factions.level(server).setAttached(DONE, List.copyOf(less));
		return true;
	}

	/** Everything on the world-wide list, in the order it came. */
	public static List<String> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(DONE, List.of());
	}

	// ---- a player's and a group's deeds (0.7.466) --------------------------------------------------------------------

	private static boolean add(ServerLevel level, AttachmentType<Map<String, List<String>>> type, String key, String id) {
		Map<String, List<String>> all = level.getAttachedOrElse(type, Map.of());
		List<String> had = all.getOrDefault(key, List.of());
		if (had.contains(id)) {
			return false;
		}
		List<String> more = new ArrayList<>(had);
		more.add(id);
		Map<String, List<String>> next = new HashMap<>(all);
		next.put(key, List.copyOf(more));
		level.setAttached(type, Map.copyOf(next));
		return true;
	}

	/** {@code player} did deed {@code id}: it is theirs, and their group's. True if it is new to them or the group. */
	public static boolean done(ServerPlayer player, String id) {
		ServerLevel level = Factions.level(player.level().getServer());
		boolean fresh = add(level, OWN, player.getUUID().toString(), id);
		PlayerTeam team = Parties.team(player);
		if (team != null) {
			fresh |= add(level, GROUPS, team.getName(), id);
		}
		return fresh;
	}

	/** The player {@code who} (maybe away) did deed {@code id}: on, it goes to their group too. True if it is new to them. */
	public static boolean done(MinecraftServer server, UUID who, String id) {
		ServerPlayer on = server.getPlayerList().getPlayer(who);
		if (on != null) {
			return done(on, id);
		}
		return add(Factions.level(server), OWN, who.toString(), id);
	}

	/** Everyone on in {@code level} within {@code range} blocks of {@code at} did deed {@code id} (they saw it). */
	public static void doneNear(ServerLevel level, Vec3 at, double range, String id) {
		for (ServerPlayer p : List.copyOf(level.players())) {
			if (!p.isSpectator() && p.position().distanceToSqr(at) <= range * range) {
				done(p, id);
			}
		}
	}

	/** Everyone on in {@code level} lived through deed {@code id} (a blood moon). */
	public static void doneAll(ServerLevel level, String id) {
		for (ServerPlayer p : List.copyOf(level.players())) {
			if (!p.isSpectator()) {
				done(p, id);
			}
		}
	}

	/**
	 * Whether {@code player} may count deed {@code id}: they did it, their group did it, or someone in their group who is on
	 * did it (whenever, alone or elsewhere). An op's forced road is read separately ({@link RoadLocks}).
	 */
	public static boolean has(ServerPlayer player, String id) {
		return of(player).contains(id);
	}

	/** Every deed {@code player} may count (see {@link #has(ServerPlayer, String)}). */
	public static Set<String> of(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		Map<String, List<String>> own = level.getAttachedOrElse(OWN, Map.of());
		Set<String> out = new LinkedHashSet<>(own.getOrDefault(player.getUUID().toString(), List.of()));
		PlayerTeam team = Parties.team(player);
		if (team != null) {
			out.addAll(level.getAttachedOrElse(GROUPS, Map.of()).getOrDefault(team.getName(), List.of()));
			for (ServerPlayer mate : Parties.members(player)) {
				out.addAll(own.getOrDefault(mate.getUUID().toString(), List.of()));
			}
		}
		return out;
	}

	/** {@code player}'s own deeds, whatever group they're in (for the ops' readout). */
	static List<String> own(MinecraftServer server, @Nullable UUID who) {
		return who == null ? List.of() : Factions.level(server).getAttachedOrElse(OWN, Map.of()).getOrDefault(who.toString(), List.of());
	}

	/**
	 * Once for each player: one who had told their story before 0.7.466 keeps the deeds the world had done by then (the
	 * roads they could take stay open to them). Anyone new starts with their own deeds only.
	 */
	private static void handOver(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = Factions.level(server);
		List<String> handed = level.getAttachedOrElse(HANDED, List.of());
		String key = player.getUUID().toString();
		if (handed.contains(key)) {
			return;
		}
		List<String> more = new ArrayList<>(handed);
		more.add(key);
		level.setAttached(HANDED, List.copyOf(more));
		if (!player.hasAttached(LifeStory.STORY)) {
			return;
		}
		for (String id : all(server)) {
			if (!id.startsWith(RoadLocks.FORCE_OPEN) && !id.startsWith(RoadLocks.FORCE_SHUT)) {
				add(level, OWN, key, id);
			}
		}
	}
}
