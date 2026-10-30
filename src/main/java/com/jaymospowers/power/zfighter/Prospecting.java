package com.jaymospowers.power.zfighter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.18.52, Andy's SevTech picks: the Prospector career) The far worlds' first finds, for whoever needs to hear of them. The
 * world's ore code (Track 5, "Prospecting First") calls {@link #found} when a player reveals an ore kind for the first time
 * on a world, and when they knap a world's first metal; Jaymo's Kingdoms' Prospector career is one listener (added here, through
 * {@link ZFighterKingdoms}), and counts each kind once per world per player itself. Anything else may add a listener to
 * {@link #FIRST_FIND_LISTENERS}.
 */
public final class Prospecting {
	/** Hears of a find: {@code world} is the world's key (moon, rustfall, glacia, cinder, maw, verdant); {@code kind} an ore's block id, or {@code metal:<item id>}. */
	@FunctionalInterface
	public interface Listener {
		void found(ServerPlayer player, String world, String kind);
	}

	public static final List<Listener> FIRST_FIND_LISTENERS = new CopyOnWriteArrayList<>(List.of(ZFighterKingdoms::firstFind));

	private Prospecting() {
	}

	/** {@code player} found {@code kind} on {@code world}: a first reveal of an ore, or a first metal knapped ({@code "metal:" + item id}). */
	public static void found(ServerPlayer player, String world, String kind) {
		for (Listener listener : FIRST_FIND_LISTENERS) {
			listener.found(player, world, kind);
		}
	}
}
