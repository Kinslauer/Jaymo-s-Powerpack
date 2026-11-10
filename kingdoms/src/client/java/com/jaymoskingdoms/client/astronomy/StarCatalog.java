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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The star catalog: every HYG v4.1 star down to magnitude 6.5 (8,870 of them
 * in v4.1), loaded from {@code assets/jaymoskingdoms/astronomy/stars.json}.
 * Star positions are right ascension and declination in degrees; magnitudes
 * are visual magnitudes.
 */
public final class StarCatalog {
    private static List<Star> stars;
    private static Map<Integer, Star> byHip;
    private static Map<Integer, Integer> hipToIndex;

    private StarCatalog() {
    }

    public static synchronized List<Star> stars() {
        if (stars == null) {
            stars = load();
            byHip = new HashMap<>();
            hipToIndex = new HashMap<>();
            int index = 0;
            for (Star star : stars) {
                byHip.put(star.hip(), star);
                hipToIndex.put(star.hip(), index++);
            }
        }
        return stars;
    }

    @Nullable
    public static Star byHip(int hip) {
        stars();
        return byHip.get(hip);
    }

    /** The index of the star with {@code hip} in {@link #stars()}, or -1. */
    public static int indexOf(int hip) {
        stars();
        Integer index = hipToIndex.get(hip);
        return index == null ? -1 : index;
    }

    private static List<Star> load() {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier id = JaymosKingdoms.id("astronomy/stars.json");
        try {
            try (InputStream in = minecraft.getResourceManager().getResource(id)
                    .orElseThrow(() -> new IllegalStateException("missing " + id)).open()) {
                JsonArray array = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonArray();
                List<Star> out = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    JsonObject row = element.getAsJsonObject();
                    out.add(new Star(
                            row.get("hip").getAsInt(),
                            row.get("ra").getAsFloat(),
                            row.get("dec").getAsFloat(),
                            row.get("mag").getAsFloat(),
                            row.get("con").getAsString(),
                            row.has("name") && !row.get("name").isJsonNull() ? row.get("name").getAsString() : null));
                }
                return out;
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to load " + id, e);
        }
    }
}
