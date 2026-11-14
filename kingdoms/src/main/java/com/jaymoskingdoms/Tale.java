package com.jaymoskingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/**
 * A tale as its data file wrote it ({@code data/<namespace>/tales/<id>.json}; the format is docs/StoryQuests-Plan.md):
 * how it starts, what's rolled when it does, its cast and places, its steps and its endings. Nothing here knows about
 * the world; {@link Tales} runs a tale for a player and {@link TaleCast} puts its people in the world.
 *
 * <p>A field may be a switch: an object whose keys are {@code ?<condition>}, tried in order ({@code ?} catches all).
 * {@link #pick} reads through them.
 */
final class Tale {
	/** Every tale loaded, by id (ours: the file's name; another namespace's: {@code namespace:name}). */
	private static Map<String, Tale> all = Map.of();
	/** Every overheard scene loaded ({@link Overheard}): its id and its file. */
	private static Map<String, JsonObject> scenes = Map.of();

	final String id;
	final JsonObject json;

	private Tale(String id, JsonObject json) {
		this.id = id;
		this.json = json;
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKingdoms.id("tales"),
			new SimpleJsonResourceReloadListener<>(ExtraCodecs.JSON, FileToIdConverter.json("tales")) {
				@Override
				protected void apply(Map<Identifier, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler) {
					Map<String, Tale> read = new LinkedHashMap<>();
					loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
						JsonElement file = e.getValue();
						if (file.isJsonObject() && file.getAsJsonObject().has("steps") && file.getAsJsonObject().has("first")) {
							String id = key(e.getKey());
							read.put(id, new Tale(id, file.getAsJsonObject()));
						} else {
							JaymosKingdoms.LOGGER.warn("Tale {} has no steps; left out", e.getKey());
						}
					});
					all = read;
				}
			});
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKingdoms.id("scenes"),
			new SimpleJsonResourceReloadListener<>(ExtraCodecs.JSON, FileToIdConverter.json("scenes")) {
				@Override
				protected void apply(Map<Identifier, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler) {
					Map<String, JsonObject> read = new LinkedHashMap<>();
					loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
						if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("lines")) {
							read.put(key(e.getKey()), e.getValue().getAsJsonObject());
						}
					});
					scenes = read;
				}
			});
	}

	private static String key(Identifier id) {
		return id.getNamespace().equals(JaymosKingdoms.MOD_ID) ? id.getPath() : id.toString();
	}

	static @Nullable Tale get(String id) {
		return all.get(id);
	}

	static Iterable<Tale> all() {
		return all.values();
	}

	static Map<String, JsonObject> scenes() {
		return scenes;
	}

	// ---- its parts ----------------------------------------------------------------------------------------------------

	/** Every mark this tale can set (a reply's or a step's {@code mark}, an ending's {@code marks}). */
	Set<String> marks() {
		Set<String> out = new HashSet<>();
		marks(this.json, out);
		return out;
	}

	private static void marks(JsonElement e, Set<String> out) {
		if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> marks(x, out));
		} else if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
				if (x.getKey().equals("mark") || x.getKey().equals("marks")) {
					if (x.getValue().isJsonObject()) {
						// (a switch: whichever arm)
						x.getValue().getAsJsonObject().asMap().values().forEach(arm -> out.addAll(strings(arm)));
					} else {
						out.addAll(strings(x.getValue()));
					}
				} else {
					marks(x.getValue(), out);
				}
			}
		}
	}

	String title() {
		return string(json, "title", id);
	}

	JsonObject start() {
		return object(json, "start");
	}

	/** How it starts: giver, rumour, book, letter or companion. */
	String by() {
		return string(start(), "by", "giver");
	}

	String first() {
		return string(json, "first", "");
	}

	@Nullable JsonObject step(String id) {
		return child(object(json, "steps"), id);
	}

	@Nullable JsonObject ending(String id) {
		return child(object(json, "endings"), id);
	}

	@Nullable JsonObject role(String role) {
		return child(object(json, "cast"), role);
	}

	@Nullable JsonObject place(String key) {
		return child(object(json, "places"), key);
	}

	/** What's rolled when it starts: each key gets one of its values. */
	Map<String, String> roll(RandomSource random) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : object(json, "roll").entrySet()) {
			List<String> values = strings(e.getValue());
			if (!values.isEmpty()) {
				out.put(e.getKey(), values.get(random.nextInt(values.size())));
			}
		}
		return out;
	}

	// ---- reading the file -----------------------------------------------------------------------------------------------

	static JsonObject object(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	private static @Nullable JsonObject child(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static String string(JsonObject in, String key, String or) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : or;
	}

	static int number(JsonObject in, String key, int or) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : or;
	}

	static double decimal(JsonObject in, String key, double or) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : or;
	}

	static boolean flag(JsonObject in, String key) {
		JsonElement e = in.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	/** A string, or a list of them, as a list. */
	static List<String> strings(@Nullable JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e == null) {
			return out;
		}
		if (e.isJsonPrimitive()) {
			out.add(e.getAsString());
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonPrimitive()) {
					out.add(x.getAsString());
				}
			}
		}
		return out;
	}

	/** A list's objects. */
	static List<JsonObject> objects(@Nullable JsonElement e) {
		List<JsonObject> out = new ArrayList<>();
		if (e instanceof JsonArray array) {
			for (JsonElement x : array) {
				if (x.isJsonObject()) {
					out.add(x.getAsJsonObject());
				}
			}
		}
		return out;
	}

	/** A {@code [least, most]} pair (or one number), rolled. */
	static int between(@Nullable JsonElement e, int or, RandomSource random) {
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		if (e instanceof JsonArray a && a.size() == 2) {
			int least = a.get(0).getAsInt(), most = a.get(1).getAsInt();
			return most <= least ? least : least + random.nextInt(most - least + 1);
		}
		return or;
	}

	private static boolean isSwitch(JsonElement e) {
		if (!e.isJsonObject() || e.getAsJsonObject().isEmpty()) {
			return false;
		}
		for (String k : e.getAsJsonObject().keySet()) {
			if (!k.startsWith("?")) {
				return false;
			}
		}
		return true;
	}

	/** {@code e} with its switches read through: the first arm whose condition {@code holds}; null if none does. */
	static @Nullable JsonElement pick(@Nullable JsonElement e, Predicate<String> holds) {
		while (e != null && isSwitch(e)) {
			JsonElement next = null;
			for (Map.Entry<String, JsonElement> arm : e.getAsJsonObject().entrySet()) {
				String cond = arm.getKey().substring(1);
				if (cond.isEmpty() || holds.test(cond)) {
					next = arm.getValue();
					break;
				}
			}
			e = next;
		}
		return e;
	}

	/** A field that's a string (or a switch to one). */
	static String pickString(JsonObject in, String key, Predicate<String> holds, String or) {
		JsonElement e = pick(in.get(key), holds);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : or;
	}

	/** A field that's a number (or a switch to one). */
	static int pickNumber(JsonObject in, String key, Predicate<String> holds, int or) {
		JsonElement e = pick(in.get(key), holds);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : or;
	}
}
