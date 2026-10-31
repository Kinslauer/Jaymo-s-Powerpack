package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * (0.7.452, Andy: "the issue with chunky is we are not necessarily going to be spawning within the 500 or so blocks it
 * generates because of the distinct starts") Once a life story sets someone down, the land round them is made ahead of
 * them in the background, a few chunks at a time, from just past their view outwards to {@link #RADIUS}. So whichever
 * start they took (the wild 600-1200 blocks out, a far world, a peak), walking off doesn't make the Deck build new land
 * as they go. It holds back while the server is behind ({@link #BUSY_MS}), and each chunk is let go as soon as it's made
 * (saved, and unloaded unless someone is near). One arrival's land at a time; later arrivals wait their turn.
 */
public final class ArrivalGround {
	/** Chunks round the arrival made ahead (the square's half-width); those within {@link #SKIP} are already loaded by their view. */
	static final int RADIUS = 20;
	/** The server's view distance (chunks) on the Deck: the player's own view loads these. */
	static final int SKIP = 8;
	/** Chunks being made at once. */
	static final int IN_FLIGHT = 2;
	/** No new chunk is asked for while the server's average tick is over this (ms). */
	static final int BUSY_MS = 40;

	/** Holds a chunk while it's made: no timeout, loads it, never saved (so it needs no registry entry). */
	private static final TicketType AHEAD = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

	/** One arrival's land: where, and the chunks still to make (nearest first). */
	private record Job(ResourceKey<Level> dimension, Deque<ChunkPos> left) {
	}

	private record Making(ResourceKey<Level> dimension, ChunkPos pos, CompletableFuture<?> done) {
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static final List<Making> MAKING = new ArrayList<>();

	private ArrivalGround() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ArrivalGround::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Making m : MAKING) {
				ServerLevel level = server.getLevel(m.dimension());
				if (level != null) {
					level.getChunkSource().removeTicketWithRadius(AHEAD, m.pos(), 0);
				}
			}
			MAKING.clear();
			JOBS.clear();
		});
	}

	/** Queue the land round where {@code player} stands now to be made. */
	static void around(ServerPlayer player) {
		ChunkPos centre = player.chunkPosition();
		List<ChunkPos> ring = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) > SKIP) {
					ring.add(new ChunkPos(centre.x() + dx, centre.z() + dz));
				}
			}
		}
		ring.sort(Comparator.comparingInt(p -> Math.max(Math.abs(p.x() - centre.x()), Math.abs(p.z() - centre.z()))));
		JOBS.add(new Job(player.level().dimension(), new ArrayDeque<>(ring)));
	}

	private static void tick(MinecraftServer server) {
		if (MAKING.isEmpty() && JOBS.isEmpty()) {
			return;
		}
		for (int i = MAKING.size() - 1; i >= 0; i--) {
			Making m = MAKING.get(i);
			if (m.done().isDone()) {
				MAKING.remove(i);
				ServerLevel level = server.getLevel(m.dimension());
				if (level != null) {
					level.getChunkSource().removeTicketWithRadius(AHEAD, m.pos(), 0);
				}
			}
		}
		if (server.getAverageTickTimeNanos() > BUSY_MS * 1_000_000L) {
			return;
		}
		while (MAKING.size() < IN_FLIGHT && !JOBS.isEmpty()) {
			Job job = JOBS.peek();
			ServerLevel level = server.getLevel(job.dimension());
			if (level == null || job.left().isEmpty()) {
				JOBS.poll();
				continue;
			}
			ChunkPos pos = job.left().poll();
			if (level.getChunkSource().hasChunk(pos.x(), pos.z())) {
				continue;
			}
			MAKING.add(new Making(job.dimension(), pos, level.getChunkSource().addTicketAndLoadWithRadius(AHEAD, pos, 0)));
		}
	}
}
