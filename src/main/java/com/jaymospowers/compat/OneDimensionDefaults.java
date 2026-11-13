package com.jaymospowers.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * a file that is already there (One Dimension's, or one someone edited) is left alone. Worlds made before, or with One
 * Dimension turned off on that screen, stay as they were. Our side of a stacked world is {@link Stacked}.
 */
public final class OneDimensionDefaults {
	/** Top to bottom. (0.20.36, Andy) The high sky on top: the thin air at the edge of space, over the Aether's islands. */
	static final List<String> ORDER = List.of("jaymospowers:high_sky", "jaymospowers:aether", "jaymospowers:cloud_sea", "minecraft:overworld",
		"minecraft:the_nether");
	/** The stacks this wrote before: a file still holding exactly one of these orders is ours, unedited, and is brought up to date. */
	static final List<List<String>> OLD_ORDERS = List.of(
		List.of("jaymospowers:aether", "minecraft:overworld", "minecraft:the_nether"),
		List.of("jaymospowers:high_sky", "jaymospowers:aether", "minecraft:overworld", "minecraft:the_nether"));
	private static final String ABOUT = "Jaymo's Powers' defaults: the high sky over the Aether over the Cloud Sea over the Overworld over the Nether."
		+ " Each world keeps the order it was made with; changing this file never changes a world that exists.";
	/** Not stacked: reached as before. */
	static final List<String> LEFT_OUT = List.of("minecraft:the_end", "jaymospowers:future", "jaymospowers:moon", "jaymospowers:verdant_world",
		"jaymospowers:rustfall", "jaymospowers:cinder", "jaymospowers:glacia", "jaymospowers:maw", "jaymospowers:time_chamber",
		"jaymospowers:arsenal_pocket");

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
	 * A file this wrote before (its order exactly one of {@link #OLD_ORDERS}) gets today's order, for the worlds made from now
	 * on; anything else in it, and any order someone changed, is left as it is.
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
				if (!entry.isJsonObject() || !entry.getAsJsonObject().has("dimension")) {
					return;
				}
				ids.add(entry.getAsJsonObject().get("dimension").getAsString());
			}
			if (!OLD_ORDERS.contains(ids)) {
				return;
			}
			// (the entries are kept as they are, so whatever One Dimension holds beside each dimension's id stays)
			JsonArray next = new JsonArray();
			for (String id : ORDER) {
				JsonElement kept = null;
				for (JsonElement entry : order) {
					if (id.equals(entry.getAsJsonObject().get("dimension").getAsString())) {
						kept = entry;
					}
				}
				if (kept == null) {
					JsonObject added = new JsonObject();
					added.addProperty("dimension", id);
					kept = added;
				}
				next.add(kept);
			}
			root.add("order", next);
			if (root.has("_about")) {
				root.addProperty("_about", ABOUT);
			}
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
			JaymosPowers.LOGGER.info("Brought the One Dimension stack in {} up to date: high sky, Aether, Cloud Sea, Overworld, Nether (new worlds only)", path);
		} catch (IOException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Could not update {}", path, e);
		}
	}
}
