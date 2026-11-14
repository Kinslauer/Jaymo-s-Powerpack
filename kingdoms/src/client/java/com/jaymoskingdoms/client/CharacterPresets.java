package com.jaymoskingdoms.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Andy: a character made in the character creator (the life story's skip, {@link LifeStoryScreen}) "can also be saved as a
 * preset". The presets are kept on this computer ({@code config/jaymoskingdoms-characters.json}), by name, so one made in
 * one world can be loaded in any other, and on any server. Everything is kept by key or by name (never by an index or a
 * realm's id), so a preset still loads after answers are added, and in a world whose realms are other ones: what a world
 * doesn't have (a realm of that name, a blood not woken there) is left as the creator had it.
 */
final class CharacterPresets {
	/** Presets kept at most, and a name's length. */
	static final int MAX = 32, MAX_NAME = 24;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static @Nullable List<Preset> presets;

	/** One saved character. The fields are the file's: don't rename them. */
	static final class Preset {
		String name = "";
		/** 0 male, 1 female, -1 not picked. */
		int gender = -1;
		/** The heritage's key, and the answers' keys: born, raised, childhood, youth and turn (the affinity). */
		String heritage = "", born = "", raised = "", childhood = "", youth = "", turn = "";
		/** The class's key and its option (a first spell, an element, a patron), and the realm named for a noble house, by name. */
		String cls = "", option = "", raisedRealm = "";
		int look, flaws, switches;
		int[] sparks = new int[3];
	}

	private CharacterPresets() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms-characters.json");
	}

	/** Every preset, in the order they were first saved. */
	static List<Preset> all() {
		if (presets == null) {
			presets = new ArrayList<>();
			Path file = file();
			if (Files.exists(file)) {
				try {
					List<Preset> read = GSON.fromJson(Files.readString(file), new TypeToken<List<Preset>>() { }.getType());
					if (read != null) {
						for (Preset p : read) {
							if (p != null && p.name != null && !p.name.isBlank()) {
								presets.add(p);
							}
						}
					}
				} catch (IOException | RuntimeException e) {
					JaymosKingdoms.LOGGER.warn("Couldn't read the character presets", e);
				}
			}
		}
		return presets;
	}

	static @Nullable Preset named(String name) {
		for (Preset p : all()) {
			if (p.name.equalsIgnoreCase(name.strip())) {
				return p;
			}
		}
		return null;
	}

	/** Saves {@code preset} under its name, over one of that name; false if there's no room for another. */
	static boolean save(Preset preset) {
		List<Preset> list = all();
		Preset old = named(preset.name);
		if (old != null) {
			list.set(list.indexOf(old), preset);
		} else if (list.size() >= MAX) {
			return false;
		} else {
			list.add(preset);
		}
		write();
		return true;
	}

	static void delete(String name) {
		Preset old = named(name);
		if (old != null) {
			all().remove(old);
			write();
		}
	}

	private static void write() {
		try {
			Files.writeString(file(), GSON.toJson(all()));
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't save the character presets", e);
		}
	}
}
