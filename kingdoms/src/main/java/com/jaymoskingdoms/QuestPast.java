package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The quests a player is done with, for the journal's Past page: each one's name, how it went (done, failed, given
 * up), a line on how it ended, and the day. Errands, board work and tales all end up here ({@link Quests},
 * {@link Tales}). Kept through death; gone on reincarnation ({@link #reborn}).
 */
public final class QuestPast {
	/** How a quest went. */
	public static final int DONE = 0, FAILED = 1, GIVEN_UP = 2;
	/** How many are kept (the oldest go first). */
	static final int KEPT = 80;

	/** One quest that's over. */
	public record Entry(Component title, int how, Component text, long day) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				ComponentSerialization.CODEC.fieldOf("title").forGetter(Entry::title),
				Codec.INT.optionalFieldOf("how", DONE).forGetter(Entry::how),
				ComponentSerialization.CODEC.optionalFieldOf("text", Component.empty()).forGetter(Entry::text),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Entry::day))
			.apply(i, Entry::new));
	}

	static final AttachmentType<List<Entry>> PAST = AttachmentRegistry.create(JaymosKingdoms.id("quest_past"),
		builder -> builder.persistent(Entry.CODEC.listOf()).copyOnDeath());

	private QuestPast() {
	}

	/** {@code player}'s finished quests, newest first. */
	static List<Entry> of(ServerPlayer player) {
		return player.getAttachedOrElse(PAST, List.of());
	}

	/** A quest of {@code player}'s is over: {@code title}, {@code how} it went, and {@code text} on how it ended. */
	static void add(ServerPlayer player, Component title, int how, Component text) {
		List<Entry> list = new ArrayList<>(of(player));
		list.add(0, new Entry(title, how, text, player.level() instanceof ServerLevel level ? Quests.day(level) : 0L));
		while (list.size() > KEPT) {
			list.remove(list.size() - 1);
		}
		player.setAttached(PAST, List.copyOf(list));
	}

	static void reborn(ServerPlayer player) {
		player.removeAttached(PAST);
	}
}
