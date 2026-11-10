package com.jaymoskingdoms.client.astronomy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The 88 IAU constellation figures, loaded from
 * {@code assets/jaymoskingdoms/astronomy/constellations.json}. Segment endpoints
 * are Hipparcos numbers; stars fainter than magnitude 6.5 are not in the
 * catalog, so any segment touching one is dropped at load time.
 */
public final class Constellations {
    private static List<Constellation> constellations;

    private Constellations() {
    }

    public static List<Constellation> all() {
        if (constellations == null) {
            constellations = load();
        }
        return constellations;
    }

    private static List<Constellation> load() {
        StarCatalog.stars(); // index the catalog before resolving segments
        Minecraft minecraft = Minecraft.getInstance();
        Identifier id = JaymosKingdoms.id("astronomy/constellations.json");
        try {
            try (InputStream in = minecraft.getResourceManager().getResource(id)
                    .orElseThrow(() -> new IllegalStateException("missing " + id)).open()) {
                JsonArray array = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonArray();
                List<Constellation> out = new ArrayList<>();
                for (JsonElement element : array) {
                    JsonObject row = element.getAsJsonObject();
                    List<int[]> segments = new ArrayList<>();
                    JsonArray raw = row.get("segments").getAsJsonArray();
                    for (JsonElement segment : raw) {
                        JsonArray pair = segment.getAsJsonArray();
                        int a = StarCatalog.indexOf(pair.get(0).getAsInt());
                        int b = StarCatalog.indexOf(pair.get(1).getAsInt());
                        if (a < 0 || b < 0 || a == b) {
                            continue;
                        }
                        segments.add(new int[] {a, b});
                    }
                    if (!segments.isEmpty()) {
                        out.add(new Constellation(row.get("id").getAsString(), row.get("name").getAsString(),
                                row.get("abbr").getAsString(), segments));
                    }
                }
                return out;
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to load " + id, e);
        }
    }
}
