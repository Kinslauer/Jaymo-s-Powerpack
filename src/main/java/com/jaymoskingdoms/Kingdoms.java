package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Every kingdom in the world, kept on the Overworld (saved with it). The one place kingdoms are read and written.
 */
public final class Kingdoms {
	/** All kingdoms, fallen ones included (their story isn't over: a ruin can be refounded). */
	static final AttachmentType<List<Kingdom>> ALL = AttachmentRegistry.create(JaymosKingdoms.id("kingdoms"),
		builder -> builder.persistent(Kingdom.CODEC.listOf()));

	private Kingdoms() {
	}

	private static ServerLevel store(MinecraftServer server) {
		return server.overworld();
	}

	public static List<Kingdom> all(MinecraftServer server) {
		List<Kingdom> all = store(server).getAttached(ALL);
		return all == null ? List.of() : all;
	}

	public static @Nullable Kingdom get(MinecraftServer server, int id) {
		for (Kingdom k : all(server)) {
			if (k.id() == id) {
				return k;
			}
		}
		return null;
	}

	public static @Nullable Kingdom byName(MinecraftServer server, String name) {
		for (Kingdom k : all(server)) {
			if (k.name().equalsIgnoreCase(name)) {
				return k;
			}
		}
		return null;
	}

	/** Adds or replaces (by id). */
	public static void put(MinecraftServer server, Kingdom kingdom) {
		List<Kingdom> all = new ArrayList<>(all(server));
		all.removeIf(k -> k.id() == kingdom.id());
		all.add(kingdom);
		all.sort(Comparator.comparingInt(Kingdom::id));
		store(server).setAttached(ALL, List.copyOf(all));
	}

	static int nextId(MinecraftServer server) {
		return all(server).stream().mapToInt(Kingdom::id).max().orElse(0) + 1;
	}

	/** The kingdom whose land this is (Overworld only), if any. */
	public static @Nullable Kingdom at(MinecraftServer server, ServerLevel level, BlockPos pos) {
		if (level != store(server)) {
			return null;
		}
		ChunkPos chunk = ChunkPos.containing(pos);
		for (Kingdom k : all(server)) {
			if (k.claims(chunk)) {
				return k;
			}
		}
		return null;
	}

	/** The kingdom a capital near {@code pos} (within {@code radius}) belongs to, if any. */
	static @Nullable Kingdom near(MinecraftServer server, BlockPos pos, double radius) {
		for (Kingdom k : all(server)) {
			if (k.capital().distSqr(pos) <= radius * radius) {
				return k;
			}
		}
		return null;
	}

	/** The kingdom {@code player} is pledged to, if any. */
	public static @Nullable Kingdom of(MinecraftServer server, UUID player) {
		for (Kingdom k : all(server)) {
			if (!k.fallen() && k.members().containsKey(player)) {
				return k;
			}
		}
		return null;
	}
}
