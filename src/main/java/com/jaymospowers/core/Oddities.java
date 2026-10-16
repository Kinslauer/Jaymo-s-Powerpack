package com.jaymospowers.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Strange sights, marked for anyone watching: an entity tag {@link #PREFIX}{@code <kind>} on whatever the world's
 * people would stare at or run from. Jaymo's Kingdoms reads these tags (it never loads Powers' classes), so its
 * townsfolk gawk at a power in use, point at a meteor and flee a symbiote.
 *
 * <p>Things that come and go with the entity ({@link #METEOR}, {@link #STAR}, a symbiote's host) are {@link #mark}ed
 * and {@link #unmark}ed. A player's state is {@link #hold}ed: the tag stays for a while and lapses unless held again,
 * so a tag left on a player by a crash or a lost power clears itself.
 */
public final class Oddities {
	public static final String PREFIX = "jaymospowers_odd:";
	/** A Gravity meteor or a falling star coming down (the rock in the sky). */
	public static final String METEOR = "meteor", STAR = "star";
	/** A symbiote: a player in the black suit or the big body, or a mob it's poured itself into. */
	public static final String SYMBIOTE = "symbiote";
	/** A Darktide user walking as their shadow. */
	public static final String SHADE = "shade";
	/** (0.11.103) A villager raised by Darktide's Necromancy as itself, back at its life (saved: it stays marked). */
	public static final String RISEN = "risen";
	/** A player who just used their power: {@code power/<power id path>}, e.g. {@code power/wind}. */
	public static final String POWER = "power/";

	// ---- tuning ----
	/** How long a used power keeps its user marked (ticks), and how often lapsed marks are taken off. */
	public static final int POWER_SEEN_TICKS = 60;
	public static final int EVERY = 10;
	/** A held state (the suit, the shade) lapses this long after its power last held it (ticks). */
	public static final int HELD_TICKS = 20;

	private static final Map<UUID, Map<String, Long>> HELD = new HashMap<>();

	private Oddities() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Oddities::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
	}

	public static void mark(Entity entity, String kind) {
		entity.addTag(PREFIX + kind);
	}

	public static void unmark(Entity entity, String kind) {
		entity.removeTag(PREFIX + kind);
	}

	/** {@code player} shows {@code kind} for at least the next {@code ticks}. */
	public static void hold(ServerPlayer player, String kind, int ticks) {
		long until = player.level().getGameTime() + ticks;
		HELD.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).merge(kind, until, Math::max);
		mark(player, kind);
	}

	public static void hold(ServerPlayer player, String kind) {
		hold(player, kind, HELD_TICKS);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Long> held = HELD.get(player.getUUID());
			long now = player.level().getGameTime();
			for (String tag : new ArrayList<>(player.entityTags())) {
				if (!tag.startsWith(PREFIX)) {
					continue;
				}
				String kind = tag.substring(PREFIX.length());
				Long until = held == null ? null : held.get(kind);
				if (until == null || until < now) {
					player.removeTag(tag);
					if (held != null) {
						held.remove(kind);
					}
				}
			}
		}
	}
}
