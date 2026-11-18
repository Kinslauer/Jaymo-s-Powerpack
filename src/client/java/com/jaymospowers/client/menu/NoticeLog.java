package com.jaymospowers.client.menu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The last {@link #MOST} notices, newest first, for the menu's Notices page ({@link NoticesScreen}): what the server said in
 * chat or over the hotbar (rewards, level ups, Jaymo's Kingdoms' word and tidings) and what a tip card said, kept so they
 * can be read again after they were missed. Kept on this client only; cleared when you leave the world. Jaymo's Kingdoms
 * needs no feed of its own, as its lines arrive as the same messages.
 */
public final class NoticeLog {
	/** How many notices are kept, and how close in time (ms) a repeat of the newest one is taken for the same notice. */
	public static final int MOST = 50;
	static final long REPEAT_MS = 3000L;

	/** One notice and when it came (the system clock, ms). */
	public record Notice(Component text, long at) {
	}

	private static final Deque<Notice> LOG = new ArrayDeque<>();

	private NoticeLog() {
	}

	public static void init() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> add(message));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> LOG.clear());
	}

	/** Adds a notice (also called by a tip card as it is queued). */
	public static void add(Component text) {
		String words = text.getString();
		if (words.isBlank()) {
			return;
		}
		long now = Util.getMillis();
		Notice newest = LOG.peekFirst();
		if (newest != null && newest.text().getString().equals(words) && now - last < REPEAT_MS) {
			return;
		}
		last = now;
		LOG.addFirst(new Notice(text, System.currentTimeMillis()));
		while (LOG.size() > MOST) {
			LOG.removeLast();
		}
	}

	/** The clock (ms) when the newest notice came, for telling a repeat. */
	private static long last;

	/** The notices, newest first. */
	static List<Notice> all() {
		return new ArrayList<>(LOG);
	}
}
