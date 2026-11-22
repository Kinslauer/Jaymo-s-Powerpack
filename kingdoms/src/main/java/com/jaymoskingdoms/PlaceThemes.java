package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Which place a player is in, for the music of the places (the client's {@code PlaceMusic} plays it, from the Medieval
 * Music pack's tracks). This side only says "town", "tavern", "festival", "marked" (an illager camp), "grundar" (any
 * other camp), "road" or "war" to the player, and "" when none, a few times a second. Nothing is played on the server.
 * First match wins: a camp, a war, a tavern or the cafe, a festival square, a King's Road under a rider, a realm's town.
 */
public final class PlaceThemes {
	/** How often each player's place is looked at (ticks), and how often a place is told again so the client can tell it has ended. */
	public static final int SCAN_EVERY = 20, REPEAT_TICKS = 60;
	/** How near a camp's fire (blocks), the seat of a realm at war, a festival's square, and its fire. */
	static final double CAMP_RADIUS = 48.0, WAR_RADIUS = 96.0, FESTIVAL_RADIUS = 40.0, FIRE_RADIUS = 10.0;
	/** How near a town's seat (blocks) counts as in the town, and how near a King's Road (blocks) as on it. */
	static final double TOWN_RADIUS = 56.0, ROAD_REACH = 6.0;
	/** How near the cafe's board (blocks) counts as in the cafe. */
	static final double CAFE_RADIUS = 12.0;

	/** "You are in this place", or an empty one: none. */
	public record Play(String place) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("place_theme"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Play::place, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	private record Told(String place, long at) {
	}

	private static final Map<UUID, Told> TOLD = new HashMap<>();

	private PlaceThemes() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(PlaceThemes::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> TOLD.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOLD.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String place = place(server, player);
			Told told = TOLD.get(player.getUUID());
			String before = told == null ? "" : told.place();
			if (!place.equals(before) || !place.isEmpty() && now - told.at() >= REPEAT_TICKS) {
				ServerPlayNetworking.send(player, new Play(place));
				TOLD.put(player.getUUID(), new Told(place, now));
			}
		}
	}

	/** The place {@code player} is in, or "". */
	static String place(MinecraftServer server, ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || level != Factions.level(server) || player.isSpectator()) {
			return "";
		}
		BlockPos pos = player.blockPosition();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = camp.fallen() ? null : Camps.data(server, camp.id());
			if (data != null && data.clearedAt() < 0 && data.center().distSqr(pos) <= CAMP_RADIUS * CAMP_RADIUS) {
				return data.illager() ? "marked" : "grundar";
			}
		}
		Faction near = Factions.around(server, level, pos, WAR_RADIUS);
		if (near != null && near.type().isRealm() && !near.fallen() && near.seat().distSqr(pos) <= WAR_RADIUS * WAR_RADIUS
			&& !Wars.warsOf(server, near.id()).isEmpty()) {
			return "war";
		}
		if (Remedies.tavernAt(level, player.position()) || cafeNear(level, pos)) {
			return "tavern";
		}
		Faction town = Factions.around(server, level, pos, TOWN_RADIUS);
		if (town != null && !town.fallen() && town.type() != FactionType.BARBARIAN && town.seat().distSqr(pos) <= TOWN_RADIUS * TOWN_RADIUS) {
			if (Festivals.keeping(level, town) && town.seat().distSqr(pos) <= FESTIVAL_RADIUS * FESTIVAL_RADIUS) {
				return town.seat().distSqr(pos) <= FIRE_RADIUS * FIRE_RADIUS ? "festival_fire" : "festival";
			}
			return "town";
		}
		return onRoad(server, player) ? "road" : "";
	}

	/** {@code player} rides along a King's Road. */
	private static boolean onRoad(MinecraftServer server, ServerPlayer player) {
		if (!player.isPassenger()) {
			return false;
		}
		for (Roads.Road road : Roads.network(server).roads()) {
			Roads.Line line = Roads.line(road);
			if (line.near(player.getX(), player.getZ(), ROAD_REACH) && line.project(player.getX(), player.getZ())[1] <= ROAD_REACH) {
				return true;
			}
		}
		return false;
	}

	/** A cafe's board stands within {@link #CAFE_RADIUS} of {@code pos}. */
	private static boolean cafeNear(ServerLevel level, BlockPos pos) {
		ChunkPos centre = ChunkPos.containing(pos);
		int reach = (int) Math.ceil(CAFE_RADIUS / 16.0);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof CafeBoard.Entity board && board.getBlockPos().distSqr(pos) <= CAFE_RADIUS * CAFE_RADIUS) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
