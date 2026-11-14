package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** The new kinds of work, for the board and for a tale's steps (docs/StoryQuests-Plan.md). */
final class QuestJobs {
	private QuestJobs() {
	}

	static void register() {
	}

	static Component title(Quest q) {
		return Component.empty();
	}

	static Component text(Quest q) {
		return Component.empty();
	}

	static Component objective(ServerLevel level, ServerPlayer player, Quest q) {
		return Component.empty();
	}

	/** {@code q} is off {@code player}'s log (done, failed or dropped): what it put in the world is cleared away. */
	static void over(ServerLevel level, ServerPlayer player, Quest q) {
	}
}
