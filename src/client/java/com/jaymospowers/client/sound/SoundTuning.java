package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * The sound browser's settings (Andy's ImmersiveFX pick, built as our own; see {@link SoundBrowserScreen}): a volume for
 * any single sound (0 mutes it), and the thinning of repeats: when one sound starts more than {@link #THIN_MAX} times in
 * {@link #THIN_TICKS} ticks (a hundred items poured out, a flock's every step), the extra starts are dropped. That also
 * keeps the engine's channels free for the sounds that matter. Music, records, voices, menus and loops are never thinned.
 * Kept in {@code config/jaymospowers-sounds.txt}, one {@code id=volume} a line.
 * <p>It also notes every sound that starts, so the browser can list what you heard in the last {@link #HEARD_TICKS}.
 */
public final class SoundTuning {
	/** A sound may start this many times in this many ticks; past that, thinning drops it. */
	static final int THIN_MAX = 6, THIN_TICKS = 10;
	/** The browser lists what started in this many ticks. */
	static final long HEARD_TICKS = 1200L;
	static final String FILE = "jaymospowers-sounds.txt";

	/** A sound's starts: the last, how many in the listing's window, and the tick each recent one began (a ring). */
	static final class Heard {
		long last, windowStart;
		int count;
		final long[] ring = new long[THIN_MAX];
		int next;
	}

	private static final Map<Identifier, Float> LEVELS = new HashMap<>();
	private static final Map<Identifier, Heard> HEARD = new HashMap<>();
	private static boolean thin = true;
	private static boolean loaded;

	private SoundTuning() {
	}

	/** (SoundEngine mixin) {@code sound} is about to start: note it, and whether it may (not muted, not one too many). */
	public static boolean starts(SoundInstance sound) {
		load();
		Identifier id = sound.getIdentifier();
		long now = ImmersiveSounds.tick();
		Heard heard = HEARD.computeIfAbsent(id, k -> new Heard());
		if (now - heard.windowStart > HEARD_TICKS) {
			heard.windowStart = now;
			heard.count = 0;
		}
		heard.count++;
		heard.last = now;
		if (level(id) <= 0.0F) {
			return false;
		}
		if (!thin || exempt(sound)) {
			return true;
		}
		// the oldest of the last THIN_MAX starts: if it's this recent, this one is one too many
		long oldest = heard.ring[heard.next];
		if (oldest != 0 && now - oldest < THIN_TICKS) {
			return false;
		}
		heard.ring[heard.next] = now == 0 ? 1 : now;
		heard.next = (heard.next + 1) % THIN_MAX;
		return true;
	}

	private static boolean exempt(SoundInstance sound) {
		SoundSource source = sound.getSource();
		return sound.isLooping() || sound.isRelative() || source == SoundSource.MUSIC || source == SoundSource.RECORDS
			|| source == SoundSource.UI || source == SoundSource.VOICE || source == SoundSource.MASTER;
	}

	/** The volume set for {@code id}: 1 untouched, 0 muted. */
	public static float level(Identifier id) {
		load();
		Float level = LEVELS.get(id);
		return level == null ? 1.0F : level;
	}

	public static void set(Identifier id, float level) {
		level = Math.round(Math.max(0.0F, Math.min(1.0F, level)) * 20.0F) / 20.0F;
		if (level >= 1.0F) {
			LEVELS.remove(id);
		} else {
			LEVELS.put(id, level);
		}
		save();
	}

	public static boolean thinning() {
		load();
		return thin;
	}

	public static void setThinning(boolean on) {
		thin = on;
		save();
	}

	public static void resetAll() {
		LEVELS.clear();
		save();
	}

	/** The sounds set to anything but full. */
	static List<Identifier> changed() {
		load();
		return new ArrayList<>(new TreeMap<>(LEVELS).keySet());
	}

	/** What started in the last {@link #HEARD_TICKS}, newest first, with how often. */
	static List<Map.Entry<Identifier, Integer>> heard() {
		long now = ImmersiveSounds.tick();
		List<Map.Entry<Identifier, Heard>> recent = new ArrayList<>();
		for (Map.Entry<Identifier, Heard> e : HEARD.entrySet()) {
			if (now - e.getValue().last <= HEARD_TICKS) {
				recent.add(e);
			}
		}
		recent.sort((a, b) -> Long.compare(b.getValue().last, a.getValue().last));
		List<Map.Entry<Identifier, Integer>> out = new ArrayList<>();
		for (Map.Entry<Identifier, Heard> e : recent) {
			out.add(Map.entry(e.getKey(), e.getValue().count));
		}
		return out;
	}

	/** How many times {@code id} started in the listing's window (0 if not lately). */
	static int count(Identifier id) {
		Heard heard = HEARD.get(id);
		return heard == null || ImmersiveSounds.tick() - heard.last > HEARD_TICKS ? 0 : heard.count;
	}

	// ---- the file ----

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				int eq = line.lastIndexOf('=');
				if (line.isBlank() || line.startsWith("#") || eq <= 0) {
					continue;
				}
				String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
				if (key.equals("thin")) {
					thin = Boolean.parseBoolean(value);
					continue;
				}
				Identifier id = Identifier.tryParse(key);
				if (id != null) {
					try {
						float level = Float.parseFloat(value);
						if (level < 1.0F) {
							LEVELS.put(id, Math.max(0.0F, level));
						}
					} catch (NumberFormatException ignored) {
						// a bad line is skipped
					}
				}
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't read {}", path, e);
		}
	}

	private static void save() {
		StringBuilder out = new StringBuilder("# Jaymo's Powers sound browser: id=volume (0 mutes), thin=true thins out repeats\n");
		out.append("thin=").append(thin).append('\n');
		for (Map.Entry<Identifier, Float> e : new TreeMap<>(LEVELS).entrySet()) {
			out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
		}
		try {
			Files.writeString(file(), out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't save {}", file(), e);
		}
	}
}
