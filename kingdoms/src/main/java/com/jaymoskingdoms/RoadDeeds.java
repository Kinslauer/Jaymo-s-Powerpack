package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;

/**
 * (0.7.418) The server-wide record of road deeds: ids of things done (a hard road's aim finished is {@code rise.<road>}),
 * kept on the Overworld with the realms and saved with the world. Anyone doing one does it for everyone. Patch 5 reads it
 * to unlock a richer road; for now it only records. Reached as {@link Deeds#done} and {@link Deeds#has}, which is where
 * the rest of the deeds code looks.
 */
public final class RoadDeeds {
	/** On the Overworld: the deed ids done so far. Saved. */
	static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("road_deeds"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	private RoadDeeds() {
	}

	/** Whether deed {@code id} has been done in this world. */
	public static boolean has(MinecraftServer server, String id) {
		return Factions.level(server).getAttachedOrElse(DONE, List.of()).contains(id);
	}

	/** Records deed {@code id} as done. True if it is new. */
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

	/** Every deed done, in the order they were. */
	public static List<String> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(DONE, List.of());
	}
}
