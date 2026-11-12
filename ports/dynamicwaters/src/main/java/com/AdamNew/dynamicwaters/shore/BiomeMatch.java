package com.AdamNew.dynamicwaters.shore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class BiomeMatch {
    private static final Logger LOG = LogUtils.getLogger();

    final Set<Identifier> biomes;

    final List<TagKey<Biome>> tags;
    final Set<Identifier> notBiomes;
    final List<TagKey<Biome>> notTags;

    final float[] climate;

    private BiomeMatch(Set<Identifier> biomes, List<TagKey<Biome>> tags, Set<Identifier> notBiomes,
            List<TagKey<Biome>> notTags, float[] climate) {
        this.biomes = biomes;
        this.tags = tags;
        this.notBiomes = notBiomes;
        this.notTags = notTags;
        this.climate = climate;
    }

    static <T> T choose(List<T> all, Function<T, BiomeMatch> where, Holder<Biome> biome) {
        final ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        for (T s : all) {
            final BiomeMatch m = where.apply(s);
            if (m.namesBiome(key) && !m.excluded(biome)) return s;
        }
        for (T s : all) {
            final BiomeMatch m = where.apply(s);
            if (m.hasAnyTag(biome) && m.inClimate(biome) && !m.excluded(biome)) return s;
        }
        T best = null;
        for (T s : all) {
            final BiomeMatch m = where.apply(s);
            if (m.byClimateOnly() && m.inClimate(biome) && !m.excluded(biome)
                    && (best == null || m.climateWindow() < where.apply(best).climateWindow())) {
                best = s;
            }
        }
        if (best != null) return best;
        for (T s : all) {
            final BiomeMatch m = where.apply(s);
            if (m.isDefault() && !m.excluded(biome)) return s;
        }
        return null;
    }

    boolean namesBiome(ResourceKey<Biome> key) {
        return key != null && biomes.contains(key.identifier());
    }

    boolean hasAnyTag(Holder<Biome> biome) {
        for (TagKey<Biome> tag : tags) {
            if (biome.is(tag)) return true;
        }
        return false;
    }

    boolean inClimate(Holder<Biome> biome) {
        if (climate == null) return true;
        final Biome.ClimateSettings c = biome.value().climateSettings;
        return c.temperature() >= climate[0] && c.temperature() <= climate[1]
                && c.downfall() >= climate[2] && c.downfall() <= climate[3];
    }

    double climateWindow() {
        return climate == null ? Double.MAX_VALUE : (climate[1] - climate[0]) * (climate[3] - climate[2]);
    }

    boolean byClimateOnly() {
        return biomes.isEmpty() && tags.isEmpty() && climate != null;
    }

    boolean isDefault() {
        return biomes.isEmpty() && tags.isEmpty() && climate == null;
    }

    boolean excluded(Holder<Biome> biome) {
        final ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key != null && notBiomes.contains(key.identifier())) return true;
        for (TagKey<Biome> tag : notTags) {
            if (biome.is(tag)) return true;
        }
        return false;
    }

    static BiomeMatch parse(String what, String source, JsonObject file) {
        final JsonObject where = file.has("where") && file.get("where").isJsonObject() ? file.getAsJsonObject("where") : new JsonObject();
        final Set<Identifier> notBiomes = new HashSet<>();
        final List<TagKey<Biome>> notTags = new ArrayList<>();
        for (String raw : strings(where.get("not"))) {
            final String s = raw.trim();
            if (s.startsWith("#")) {
                final Identifier rl = Identifier.tryParse(s.substring(1));
                if (rl != null) notTags.add(TagKey.create(Registries.BIOME, rl));
                else LOG.warn("Dynamic Waters: {} {}: \"{}\" is not a biome tag", what, source, raw);
            } else {
                final Identifier rl = Identifier.tryParse(s);
                if (rl != null) notBiomes.add(rl);
                else LOG.warn("Dynamic Waters: {} {}: \"{}\" is not a biome id", what, source, raw);
            }
        }
        return new BiomeMatch(ids(what, source, where, "biomes"), tags(what, source, where, "tags"), notBiomes, notTags,
                climate(what, source, where.get("climate")));
    }

    private static float[] climate(String what, String source, JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        final JsonObject c = e.getAsJsonObject();
        final float[] out = { -10f, 10f, 0f, 1f };
        range(what, source, c, "temperature", out, 0);
        range(what, source, c, "downfall", out, 2);
        return out;
    }

    private static void range(String what, String source, JsonObject c, String field, float[] into, int at) {
        if (!c.has(field)) return;
        try {
            final JsonArray a = c.getAsJsonArray(field);
            into[at] = a.get(0).getAsFloat();
            into[at + 1] = a.get(1).getAsFloat();
        } catch (RuntimeException ex) {
            LOG.warn("Dynamic Waters: {} {}: climate {} should be [from, to]", what, source, field);
        }
    }

    private static Set<Identifier> ids(String what, String source, JsonObject o, String field) {
        final Set<Identifier> out = new HashSet<>();
        for (String s : strings(o.get(field))) {
            final Identifier rl = Identifier.tryParse(s.trim());
            if (rl != null) out.add(rl);
            else LOG.warn("Dynamic Waters: {} {}: \"{}\" is not a biome id", what, source, s);
        }
        return out;
    }

    private static List<TagKey<Biome>> tags(String what, String source, JsonObject o, String field) {
        final List<TagKey<Biome>> out = new ArrayList<>();
        for (String s : strings(o.get(field))) {
            final String t = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
            final Identifier rl = Identifier.tryParse(t);
            if (rl != null) out.add(TagKey.create(Registries.BIOME, rl));
            else LOG.warn("Dynamic Waters: {} {}: \"{}\" is not a biome tag", what, source, s);
        }
        return out;
    }

    static List<String> strings(JsonElement e) {
        final List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        } else if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
        }
        return out;
    }

    static boolean flag(JsonObject o, String field, boolean def) {
        try {
            return o.has(field) ? o.get(field).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    static int whole(JsonObject o, String field, int def) {
        try {
            return o.has(field) ? o.get(field).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    static double number(JsonObject o, String field, double def) {
        try {
            return o.has(field) ? o.get(field).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
}
