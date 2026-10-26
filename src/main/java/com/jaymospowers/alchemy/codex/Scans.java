package com.jaymospowers.alchemy.codex;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What a player has already read with the Scrying Lens: one entry for each essence of each thing ({@code
 * "item:minecraft:iron_ingot/metal"}). Kept per essence, not per thing, so a thing scanned before the player could read
 * all of it (a compound whose parents they hadn't found yet) teaches the rest on a later scan, and nothing is paid
 * twice. Kept through death, server side only.
 */
public final class Scans {
	public static final AttachmentType<Set<String>> SCANNED = AttachmentRegistry.create(JaymosPowers.id("alchemy_scans"),
		builder -> builder.persistent(Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf)).copyOnDeath().initializer(() -> Set.of()));

	private Scans() {
	}

	private static String entry(String thing, Essence essence) {
		return thing + "/" + essence.id();
	}

	/** Whether this essence of this thing has been read before. */
	public static boolean has(Player player, String thing, Essence essence) {
		Set<String> set = player.getAttached(SCANNED);
		return set != null && set.contains(entry(thing, essence));
	}

	/** Whether the player has read anything at all. */
	public static boolean any(Player player) {
		Set<String> set = player.getAttached(SCANNED);
		return set != null && !set.isEmpty();
	}

	public static void add(ServerPlayer player, String thing, Essence essence) {
		Set<String> now = new HashSet<>(player.getAttachedOrCreate(SCANNED));
		now.add(entry(thing, essence));
		player.setAttached(SCANNED, Set.copyOf(now));
	}
}
