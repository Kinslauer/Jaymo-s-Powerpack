package com.jaymospowers.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.20.30) The pack's default stack for One Dimension (CodxIO; shipped unchanged with CustomChunks, Planeshift and CodxLib):
 * the high sky on top (0.20.36), then the Aether, the Cloud Sea ({@link com.jaymospowers.aether.cloudsea.CloudSea}), the
 * Overworld, the Nether underneath, so you fly up through the clouds into the islands and on up to the edge of space
 * ({@link com.jaymospowers.power.zfighter.ZFighterHighSky}), and dig down into the Nether's sky, with no portal and no
 * loading screen. The End stays a dimension of its own (its portal, the dragon, Enderscape), and
 * so does every world of ours you reach some other way (the future, the moons, the Arsenal's pocket...).
 * <p>
 * One Dimension reads {@code config/one-dimension.json} when a world is made (the create-world screen starts from it; a
 * dedicated server takes it as it is) and writes its own defaults if the file isn't there. So this writes ours first, once:
 * a file that is already there is brought back to this stack by {@link #update}. Worlds made before, or with One
 * Dimension turned off on that screen, stay as they were. Our side of a stacked world is {@link Stacked}.
 */
public final class OneDimensionDefaults {
	/** Top to bottom. (0.20.36, Andy) The high sky on top: the thin air at the edge of space, over the Aether's islands. */
	static final List<String> ORDER = List.of("jaymospowers:high_sky", "jaymospowers:aether", "jaymospowers:cloud_sea", "minecraft:overworld",
		"minecraft:the_nether");
	private static final String ABOUT = "Jaymo's Powers' defaults: the high sky over the Aether over the Cloud Sea over the Overworld over the Nether."
		+ " Each world keeps the order it was made with; changing this file never changes a world that exists.";
	/** Not stacked: reached as before (Lost Cities is dropped from the stack; the proving grounds are reached by teleport). */
	static final List<String> LEFT_OUT = List.of("minecraft:the_end", "jaymospowers:future", "jaymospowers:moon", "jaymospowers:verdant_world", "jaymospowers:giant_ring",
		"jaymospowers:rustfall", "jaymospowers:cinder", "jaymospowers:glacia", "jaymospowers:maw", "jaymospowers:time_chamber",
		"jaymospowers:arsenal_pocket", "lostcities:lostcity", "jaymoskingdoms:proving_grounds");

	private OneDimensionDefaults() {
	}

	/** Called from {@code JaymosPowers.onInitialize}, before any world loads. */
	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("one-dimension")) {
			return;
		}
		Path path = FabricLoader.getInstance().getConfigDir().resolve("one-dimension.json");
		if (Files.exists(path)) {
			update(path);
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("_about", ABOUT);
		root.addProperty("one_dimension_on_new_worlds", true);
		JsonArray order = new JsonArray();
		for (String id : ORDER) {
			JsonObject entry = new JsonObject();
			entry.addProperty("dimension", id);
			order.add(entry);
		}
		root.add("order", order);
		root.addProperty("order_version", 2);
		JsonArray leftOut = new JsonArray();
		LEFT_OUT.forEach(leftOut::add);
		root.add("left_out", leftOut);
		// the Nether's floor is the bottom of the world: nothing under it to fall into
		root.addProperty("loop", false);
		root.addProperty("gap", 0);
		root.addProperty("trim", true);
		root.addProperty("separate_skies", true);
		root.addProperty("bedrock_becomes", "minecraft:obsidian");
		JsonArray kept = new JsonArray();
		kept.add("minecraft:the_end");
		root.add("bedrock_kept_in", kept);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
			JaymosPowers.LOGGER.info("Wrote the pack's One Dimension stack (high sky, Aether, Cloud Sea, Overworld, Nether) to {}", path);
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Could not write {}", path, e);
		}
	}

	/**
	 * One Dimension rewrites the file itself once it has run (plain ids, every dimension it knows added to the order), so a
	 * hand-edited order can't be told apart from One Dimension's own rewrite. The pack's order therefore wins: a file whose
	 * order isn't exactly {@link #ORDER} gets the five ORDER ids on top in ORDER's order (each entry kept as it is), every
	 * other id moved to {@code left_out} (deduped, after what was there). Used for new worlds only.
	 */
	private static void update(Path path) {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!root.has("order") || !root.get("order").isJsonArray()) {
				return;
			}
			JsonArray order = root.getAsJsonArray("order");
			List<String> ids = new ArrayList<>();
			for (JsonElement entry : order) {
				String id = id(entry);
				if (id == null) {
					return;
				}
				ids.add(id);
			}
			if (ids.isEmpty() || ids.equals(ORDER)) {
				return;
			}
			// (the entries are kept as they are, so whatever One Dimension holds beside each dimension's id stays)
			JsonArray next = new JsonArray();
			for (String id : ORDER) {
				int at = ids.indexOf(id);
				next.add(at >= 0 ? order.get(at) : order.isEmpty() ? new JsonPrimitive(id) : like(order.get(0), id));
			}
			Set<String> out = new LinkedHashSet<>();
			if (root.has("left_out") && root.get("left_out").isJsonArray()) {
				for (JsonElement e : root.getAsJsonArray("left_out")) {
					String left = id(e);
					if (left != null) {
						out.add(left);
					}
				}
			}
			out.addAll(ids);
			out.addAll(LEFT_OUT);
			out.removeAll(ORDER);
			JsonArray leftOut = new JsonArray();
			out.forEach(leftOut::add);
			root.add("order", next);
			root.add("left_out", leftOut);
			if (root.has("_about")) {
				root.addProperty("_about", ABOUT);
			}
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
			JaymosPowers.LOGGER.info("Put the One Dimension stack in {} back to the pack's (high sky, Aether, Cloud Sea, Overworld, Nether); was {}, left out now {} (new worlds only)",
				path, ids, out);
		} catch (IOException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Could not update {}", path, e);
		}
	}

	/** An order entry's dimension id: a plain id (One Dimension's own file) or {"dimension": id} (ours), else null. */
	private static String id(JsonElement entry) {
		if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
			return entry.getAsString();
		}
		if (entry.isJsonObject() && entry.getAsJsonObject().has("dimension")) {
			return entry.getAsJsonObject().get("dimension").getAsString();
		}
		return null;
	}

	/** A new entry for this id, written the way {@code sample} is. */
	private static JsonElement like(JsonElement sample, String id) {
		if (sample.isJsonPrimitive()) {
			return new JsonPrimitive(id);
		}
		JsonObject added = new JsonObject();
		added.addProperty("dimension", id);
		return added;
	}
}
