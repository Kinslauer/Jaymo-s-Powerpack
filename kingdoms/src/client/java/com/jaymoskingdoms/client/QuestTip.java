package com.jaymoskingdoms.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27): "add a tip that activates when you interact with a villager with a quest that tells you how to get
 * to that menu". Talking to one with a gold ! or a ? over their head ({@link QuestClient#mark}), the talk screen shows a
 * tip where your line goes (bottom right, while no spoke is pointed at): pick Quests on the wheel. It stops once you've
 * picked Quests from the wheel {@link #LEARNED} times (counted on this computer), and never shows with hints turned off
 * (Jaymo's Powers' /hints or /tips off, kept in its client settings file).
 */
public final class QuestTip {
	/** Quests picked from the wheel this many times: the tip has done its job. */
	static final int LEARNED = 3;
	/** Our count, and Powers' client settings (where /hints off is kept, as {@code tips.off}). */
	static final String FILE = "jaymoskingdoms-tips.properties", KEY = "quests.picked";
	static final String POWERS_FILE = "jaymospowers-client.properties", POWERS_OFF = "tips.off";

	/** How many times Quests has been picked from the wheel (-1: not read yet). */
	private static int picked = -1;
	/** Whether hints were off when last read, and when that was (ms); how often it's read again. */
	private static boolean off;
	private static long checked = Long.MIN_VALUE / 2;
	static final long RECHECK_MS = 2000L;

	private QuestTip() {
	}

	/** The tip for the talk with {@code villager}, or null for none. */
	static @Nullable Component tip(Entity villager) {
		char mark = QuestClient.mark(villager);
		if (mark == 0 || times() >= LEARNED || hintsOff()) {
			return null;
		}
		return Component.translatable(mark == '?' ? "gui.jaymoskingdoms.talk.quest_tip.yours" : "gui.jaymoskingdoms.talk.quest_tip.offer");
	}

	/** Quests was picked from the wheel: one nearer to the tip going away for good. */
	static void picked() {
		if (times() >= LEARNED) {
			return;
		}
		picked++;
		Path file = config(FILE);
		Properties p = read(file);
		p.setProperty(KEY, Integer.toString(picked));
		try (OutputStream out = Files.newOutputStream(file)) {
			p.store(out, "Jaymo's Kingdoms: tips already learned");
		} catch (IOException | RuntimeException e) {
			// (not kept: the tip shows again next time the game starts)
		}
	}

	private static int times() {
		if (picked < 0) {
			try {
				picked = Math.max(0, Integer.parseInt(read(config(FILE)).getProperty(KEY, "0").strip()));
			} catch (NumberFormatException e) {
				picked = 0;
			}
		}
		return picked;
	}

	/** Hints turned off in Jaymo's Powers: read again every {@link #RECHECK_MS} (/hints can be switched while the game runs). */
	private static boolean hintsOff() {
		long now = net.minecraft.util.Util.getMillis();
		if (now - checked >= RECHECK_MS) {
			checked = now;
			off = "true".equals(read(config(POWERS_FILE)).getProperty(POWERS_OFF));
		}
		return off;
	}

	private static Path config(String name) {
		return FabricLoader.getInstance().getConfigDir().resolve(name);
	}

	private static Properties read(Path file) {
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				p.load(in);
			} catch (IOException | RuntimeException e) {
				// (unreadable: as if empty)
			}
		}
		return p;
	}
}
