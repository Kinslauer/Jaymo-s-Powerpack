package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Villagers' scenes with each other, and a town's talk about a player's tales (docs/StoryQuests-Plan.md). */
final class Overheard {
	private Overheard() {
	}

	static void register() {
	}

	/** The folk of settlement {@code faction} have {@code lines} to say about how a tale of {@code player}'s ended, when they next see them. */
	static void townTalk(ServerPlayer player, int faction, List<Component> lines) {
	}
}
