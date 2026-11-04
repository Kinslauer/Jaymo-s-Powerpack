package com.jaymospowers.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.20.28) The pack's default stack for One Dimension (CodxIO; shipped unchanged with CustomChunks, Planeshift and CodxLib):
 * the Aether on top, the Overworld, the Nether underneath, so you fly up into the islands and dig down into the Nether's
 * sky, with no portal and no loading screen. The End stays a dimension of its own (its portal, the dragon, Enderscape), and
 * so does every world of ours you reach some other way (the future, the moons, the Arsenal's pocket...).
 * <p>
 * One Dimension reads {@code config/one-dimension.json} when a world is made (the create-world screen starts from it; a
 * dedicated server takes it as it is) and writes its own defaults if the file isn't there. So this writes ours first, once:
 * a file that is already there (One Dimension's, or one someone edited) is left alone. Worlds made before, or with One
 * Dimension turned off on that screen, stay as they were. Our side of a stacked world is {@link Stacked}.
 */
public final class OneDimensionDefaults {
	/** Top to bottom. */
	static final List<String> ORDER = List.of("jaymospowers:aether", "minecraft:overworld", "minecraft:the_nether");
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
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("_about", "Jaymo's Powers' defaults: the Aether over the Overworld over the Nether. Each world keeps the order"
			+ " it was made with; changing this file never changes a world that exists.");
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
			JaymosPowers.LOGGER.info("Wrote the pack's One Dimension stack (Aether, Overworld, Nether) to {}", path);
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
