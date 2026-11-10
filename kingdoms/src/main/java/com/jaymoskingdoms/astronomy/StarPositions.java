package com.jaymoskingdoms.astronomy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * The star catalogue's places for the server, which has no resource manager for assets: right ascension and declination
 * (degrees) of every catalogue star by Hipparcos number, read once from the mod's own
 * {@code assets/jaymoskingdoms/astronomy/stars.json} (the file the client's sky draws, HYG v4.1, CC BY-SA 4.0). The
 * armillary sphere's readings ({@link Measurements}) work out where a discovered star stands from these.
 */
public final class StarPositions {
	static final String FILE = "assets/" + JaymosKingdoms.MOD_ID + "/astronomy/stars.json";

	/** A star's catalogue place (degrees). */
	public record Place(double ra, double dec) {
	}

	private static @Nullable Map<Integer, Place> places;

	private StarPositions() {
	}

	/** The place of the star with Hipparcos number {@code hip}, or null if the catalogue has no such star. */
	public static synchronized @Nullable Place of(int hip) {
		if (places == null) {
			places = load();
		}
		return places.get(hip);
	}

	private static Map<Integer, Place> load() {
		Path path = FabricLoader.getInstance().getModContainer(JaymosKingdoms.MOD_ID).flatMap(mod -> mod.findPath(FILE))
			.orElseThrow(() -> new IllegalStateException("missing " + FILE));
		try {
			Map<Integer, Place> out = new HashMap<>();
			for (JsonElement element : JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonArray()) {
				JsonObject row = element.getAsJsonObject();
				// the file gives right ascension in hours, as HYG does
				out.put(row.get("hip").getAsInt(), new Place(row.get("ra").getAsDouble() * Celestial.DEG_PER_HOUR, row.get("dec").getAsDouble()));
			}
			return out;
		} catch (IOException e) {
			throw new IllegalStateException("failed to load " + FILE, e);
		}
	}
}
