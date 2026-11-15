package com.jaymospowers.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fantasy Armor's lore paragraphs stay off its tooltips (Andy, 2026-10-29: no screen text states lore): once per install
 * its {@code showDescriptions} is switched off in {@code config/fantasy_armor-common.json} before it reads the file. A
 * new file gets all of Fantasy Armor's own defaults but that one. After that, a player's or the server's own change is
 * never touched.
 * <p>
 * Its durability is on too (Andy, 2026-11-15: "turn on durability. but still nothing breaks, it becomes useless and
 * requires repair"): {@code enableDurability} is switched on, and each piece's durability in
 * {@code config/fantasy_armor-attributes.json} is set to {@link #DURABILITY} of Fantasy Armor's own, the share every
 * other armour keeps ({@link ArmourWear#DURABILITY}; Fantasy Armor sets its own number on each piece, so {@link ArmourWear}
 * leaves its items out of the item-wide cut). A piece someone gave another number keeps it. A worn-out piece stays on doing nothing until
 * repaired ({@link ArmourWear}), and a set with one gives no set effects ({@code FantasyArmorSetMixin}).
 */
public final class FantasyArmorPreset implements PreLaunchEntrypoint {
	/** Bump to switch the descriptions off again. */
	private static final String MARKER = "jaymospowers-fantasy-armor-preset-v2";
	/** Share of Fantasy Armor's own durability each piece gets (a constant, so reading it loads nothing). */
	static final double DURABILITY = ArmourWear.DURABILITY;
	/** Fantasy Armor 1.3's own defaults, with its descriptions off. */
	private static final String DEFAULTS = """
		{
		  "applyArmorEffects": true,
		  "applyModifiers": true,
		  "showDescriptions": false,
		  "showCapes": true,
		  "showParticles": false,
		  "effectsInterval": 241,
		  "descriptionsLength": 250,
		  "showEffectIcon": true,
		  "enableDurability": true
		}
		""";
	private static final Pattern DESCRIPTIONS = Pattern.compile("(\"showDescriptions\"\\s*:\\s*)true");
	private static final Pattern WEAR = Pattern.compile("(\"enableDurability\"\\s*:\\s*)false");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("fantasy_armor")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("fantasy_armor-common.json");
		try {
			if (Files.exists(config)) {
				String text = Files.readString(config);
				Matcher matcher = DESCRIPTIONS.matcher(text);
				if (matcher.find()) {
					text = matcher.replaceFirst("$1false");
				}
				matcher = WEAR.matcher(text);
				if (matcher.find()) {
					text = matcher.replaceFirst("$1true");
				} else if (!text.contains("\"enableDurability\"")) {
					text = text.replaceFirst("\\{", "{\n  \"enableDurability\": true,");
				}
				Files.writeString(config, text);
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, DEFAULTS);
			}
			try {
				wear(loader);
			} catch (RuntimeException e) {
				// (a hand-edited attributes file it can't read: left as it is)
				com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Fantasy Armor's durability in its attributes config", e);
			}
			Files.writeString(marker, "Jaymo's Powers switched Fantasy Armor's tooltip descriptions off and its durability on. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Fantasy Armor's descriptions and durability in {}", config, e);
		}
	}

	/** Each piece's durability in its attributes config, as {@link #DURABILITY} of Fantasy Armor's own (unless someone changed it). */
	private static void wear(FabricLoader loader) throws IOException {
		Optional<Path> defaults = loader.getModContainer("fantasy_armor").flatMap(mod -> mod.findPath("armor_defaults.json"));
		if (defaults.isEmpty()) {
			return;
		}
		JsonObject own = JsonParser.parseString(Files.readString(defaults.get())).getAsJsonObject();
		Path config = loader.getConfigDir().resolve("fantasy_armor-attributes.json");
		JsonObject root = Files.exists(config) ? JsonParser.parseString(Files.readString(config)).getAsJsonObject() : new JsonObject();
		if (!root.has("sets") || !root.get("sets").isJsonObject()) {
			root.add("sets", new JsonObject());
		}
		JsonObject sets = root.getAsJsonObject("sets");
		for (Map.Entry<String, JsonElement> set : own.entrySet()) {
			if (!sets.has(set.getKey()) || !sets.get(set.getKey()).isJsonObject()) {
				sets.add(set.getKey(), new JsonObject());
			}
			JsonObject pieces = sets.getAsJsonObject(set.getKey());
			for (Map.Entry<String, JsonElement> piece : set.getValue().getAsJsonObject().entrySet()) {
				JsonElement base = piece.getValue().getAsJsonObject().get("durability");
				if (base == null || !base.isJsonPrimitive()) {
					continue;
				}
				if (!pieces.has(piece.getKey()) || !pieces.get(piece.getKey()).isJsonObject()) {
					pieces.add(piece.getKey(), new JsonObject());
				}
				JsonObject mine = pieces.getAsJsonObject(piece.getKey());
				JsonElement now = mine.get("durability");
				if (now == null || now.isJsonNull() || now.getAsDouble() == base.getAsDouble()) {
					mine.addProperty("durability", (double) Math.max(1, Math.round(base.getAsDouble() * DURABILITY)));
				}
			}
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Files.writeString(config, gson.toJson(root));
	}
}
