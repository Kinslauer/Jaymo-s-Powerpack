package com.jaymoskingdoms.spirits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

/**
 * (0.7.245) What a spirit says. Andy: "make them speak like wise wizards from ancient times looking after a young child or
 * grumpy old wizards that are tired of looking after said young child". A line goes to its keeper's chat only, with the
 * spirit's name in its colour, in italics, so it reads as a voice and not as a game message. Every spirit has three lines
 * for each event ({@code spirit.jaymoskingdoms.<id>.<event>.<n>}): wake, summon, dismiss, tired, kill, hurt, low, idle, bond,
 * level, act, release, move, sick.1 to sick.5 (one line each), and advice.&lt;foe&gt; for the foes {@link Spirits#advice}
 * knows. The Quiet One (kind QUIET) says none of them: only its sick dots and, once at bond 100, {@code word.1} ({@link QuietOne}).
 * The name is {@code spirit.jaymoskingdoms.<id>.name}. Not too chatty: one line per {@link #SAY_GAP} ticks.
 */
public final class SpiritVoice {
	/** How many lines each event has for each spirit. */
	public static final int LINES = 3;
	/** The least time between two lines (ticks). */
	static final int SAY_GAP = 60;

	/** Game time each player may be spoken to again. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	static void forget(UUID id) {
		NEXT.remove(id);
	}

	private SpiritVoice() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT.clear());
	}

	/** The line for {@code event} from {@code player}'s own spirit, if it isn't mid-sentence; whether it was said. */
	public static boolean say(ServerPlayer player, String event) {
		SpiritState state = Spirits.state(player);
		return state != null && say(player, state.kind(), event, false);
	}

	/** {@link #say(ServerPlayer, String)} for the spirit {@code kind} (the one stirring in a sickness has no state yet). */
	public static boolean say(ServerPlayer player, int kind, String event) {
		return say(player, kind, event, false);
	}

	/** A line that can't be skipped for a recent one (waking, parting): it speaks over the gap. */
	public static boolean sayNow(ServerPlayer player, int kind, String event) {
		return say(player, kind, event, true);
	}

	private static boolean say(ServerPlayer player, int kind, String event, boolean force) {
		long now = player.level().getGameTime();
		if (!force && now < NEXT.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		if (kind == Spirits.QUIET) {
			// (the Quiet One says nothing, but for the one word at the end and the dots of its sickness)
			if ("bond".equals(event)) {
				event = "word";
			}
			if (!QuietOne.mayWhisper(player, event)) {
				return false;
			}
		}
		Spirit spirit = Spirits.of(kind);
		int n = 1 + player.getRandom().nextInt(LINES);
		// (the sick days have a single line each: "sick.3", not "sick.3.2")
		String key = event.equals("word") ? "spirit.jaymoskingdoms." + spirit.id() + ".word.1"
			: event.startsWith("sick.") ? "spirit.jaymoskingdoms." + spirit.id() + "." + event
			: "spirit.jaymoskingdoms." + spirit.id() + "." + event + "." + n;
		TextColor colour = TextColor.fromRgb(spirit.color() & 0xFFFFFF);
		MutableComponent name = Component.translatable("spirit.jaymoskingdoms." + spirit.id() + ".name").withStyle(s -> s.withColor(colour).withItalic(true));
		MutableComponent line = Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		player.sendSystemMessage(Component.empty().append(name).append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(line));
		NEXT.put(player.getUUID(), now + SAY_GAP);
		if (kind == Spirits.QUIET && event.equals("word")) {
			QuietOne.spoke(player);
		}
		return true;
	}
}
