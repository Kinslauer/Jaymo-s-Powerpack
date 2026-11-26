package com.jaymospowers.core.reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.wounds.BodyPlan;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * What an op has set by hand for how a mob or a weapon is read, kept in {@code config/jaymospowers-gore-overrides.json}
 * on the server and read once at start. Each value has one lookup here ({@link #blood}, {@link #weight}, {@link #body},
 * {@link #weapon}); the code that guesses the value asks it first and uses its own guess only when it answers null.
 * <pre>{"blood": {"mod:mob": "#RRGGBB" or "none"}, "weight": {"mod:mob": times a man's bulk},
 * "body": {"mod:mob": "QUADRUPED"}, "weapon": {"mod:item": "AXE" or "none"}}</pre>
 */
public final class GoreOverrides {
	public static final String FILE = "jaymospowers-gore-overrides.json";
	/** A blood override of no blood at all. */
	public static final int NO_BLOOD = -1;
	/** The lightest a weight override may be, as times a man's bulk. */
	public static final float MIN_WEIGHT = 0.05F;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<Identifier, Integer> BLOOD = new ConcurrentHashMap<>();
	private static final Map<Identifier, Float> WEIGHT = new ConcurrentHashMap<>();
	private static final Map<Identifier, BodyPlan> BODY = new ConcurrentHashMap<>();
	private static final Map<Identifier, Optional<WeaponKind>> WEAPON = new ConcurrentHashMap<>();

	private GoreOverrides() {
	}

	// ---------------------------------------------------------------- the lookups

	/** The blood colour (RGB) set for this kind of creature, {@link #NO_BLOOD} for none, or null if nothing is set. */
	public static @Nullable Integer blood(EntityType<?> type) {
		return BLOOD.isEmpty() ? null : BLOOD.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** The weight set for this kind of creature, as times a man's bulk, or null. */
	public static @Nullable Float weight(EntityType<?> type) {
		return WEIGHT.isEmpty() ? null : WEIGHT.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** The body plan set for this kind of creature, or null. */
	public static @Nullable BodyPlan body(EntityType<?> type) {
		return BODY.isEmpty() ? null : BODY.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** The weapon kind set for this item (an empty answer: not a weapon), or null if nothing is set. */
	public static @Nullable Optional<WeaponKind> weapon(Item item) {
		return WEAPON.isEmpty() ? null : WEAPON.get(BuiltInRegistries.ITEM.getKey(item));
	}

	// ---------------------------------------------------------------- the edits

	/** Sets (or, with null, clears) a creature's blood: an RGB colour or {@link #NO_BLOOD}. */
	public static void setBlood(Identifier id, @Nullable Integer color) {
		put(BLOOD, id, color);
	}

	/** Sets (or, with null, clears) a creature's weight, as times a man's bulk. */
	public static void setWeight(Identifier id, @Nullable Float times) {
		if (times != null && !Float.isFinite(times)) {
			return;
		}
		put(WEIGHT, id, times == null ? null : Math.max(MIN_WEIGHT, times));
	}

	/** Sets (or, with null, clears) a creature's body plan. */
	public static void setBody(Identifier id, @Nullable BodyPlan plan) {
		put(BODY, id, plan);
		BodyPlan.forget();
	}

	/** Sets (or, with null, clears) a weapon's kind; an empty one says it isn't a weapon. */
	public static void setWeapon(Identifier id, @Nullable Optional<WeaponKind> kind) {
		put(WEAPON, id, kind);
	}

	private static <V> void put(Map<Identifier, V> map, Identifier id, @Nullable V value) {
		if (value == null) {
			map.remove(id);
		} else {
			map.put(id, value);
		}
	}

	// ---------------------------------------------------------------- the file

	/** Whether the file was there but couldn't be read (then it is never written over). */
	private static boolean unreadable;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	/** Reads the file (if there is one), in place of what was read before. A bad entry is skipped, not the rest. */
	public static void load() {
		BLOOD.clear();
		WEIGHT.clear();
		BODY.clear();
		WEAPON.clear();
		unreadable = false;
		Path file = file();
		if (!Files.exists(file)) {
			BodyPlan.forget();
			return;
		}
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(file)) {
			root = GSON.fromJson(reader, JsonObject.class);
		} catch (IOException | RuntimeException e) {
			// (kept as it is: an edit won't write over a file a hand can still mend)
			unreadable = true;
			JaymosPowers.LOGGER.warn("Could not read {}; edits won't be saved until it's fixed: {}", FILE, e.toString());
			BodyPlan.forget();
			return;
		}
		if (root != null) {
			for (var e : section(root, "blood")) {
				entry(e, (id, v) -> {
					String c = v.getAsString().trim().toLowerCase(Locale.ROOT);
					BLOOD.put(id, c.equals("none") ? NO_BLOOD : Integer.parseInt(c.replace("#", ""), 16) & 0xFFFFFF);
				});
			}
			for (var e : section(root, "weight")) {
				entry(e, (id, v) -> {
					float w = v.getAsFloat();
					if (Float.isFinite(w)) {
						WEIGHT.put(id, Math.max(MIN_WEIGHT, w));
					}
				});
			}
			for (var e : section(root, "body")) {
				entry(e, (id, v) -> BODY.put(id, BodyPlan.valueOf(v.getAsString().toUpperCase(Locale.ROOT))));
			}
			for (var e : section(root, "weapon")) {
				entry(e, (id, v) -> {
					String k = v.getAsString().toUpperCase(Locale.ROOT);
					WEAPON.put(id, k.equals("NONE") ? Optional.empty() : Optional.of(WeaponKind.valueOf(k)));
				});
			}
		}
		BodyPlan.forget();
	}

	/** Reads one entry; a bad one is warned about and left out. */
	private static void entry(Map.Entry<String, JsonElement> e, java.util.function.BiConsumer<Identifier, JsonElement> read) {
		Identifier id = Identifier.tryParse(e.getKey());
		if (id == null) {
			return;
		}
		try {
			read.accept(id, e.getValue());
		} catch (RuntimeException ex) {
			JaymosPowers.LOGGER.warn("Skipping {} in {}: {}", e.getKey(), FILE, ex.toString());
		}
	}

	private static Iterable<Map.Entry<String, JsonElement>> section(JsonObject root, String name) {
		return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name).entrySet() : java.util.List.of();
	}

	/** Writes everything set to the file. */
	public static void save() {
		JsonObject root = new JsonObject();
		JsonObject blood = new JsonObject(), weight = new JsonObject(), body = new JsonObject(), weapon = new JsonObject();
		BLOOD.forEach((id, c) -> blood.addProperty(id.toString(), c == NO_BLOOD ? "none" : String.format("#%06X", c)));
		WEIGHT.forEach((id, w) -> weight.addProperty(id.toString(), w));
		BODY.forEach((id, p) -> body.addProperty(id.toString(), p.name()));
		WEAPON.forEach((id, k) -> weapon.addProperty(id.toString(), k.map(Enum::name).orElse("none")));
		root.add("blood", blood);
		root.add("weight", weight);
		root.add("body", body);
		root.add("weapon", weapon);
		if (unreadable) {
			JaymosPowers.LOGGER.warn("Not saving {}: it couldn't be read, so writing it would lose what's in it", FILE);
			return;
		}
		Path file = file(), temp = file.resolveSibling(FILE + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}
}
