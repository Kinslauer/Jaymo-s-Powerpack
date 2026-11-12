package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.WaterPlant;
import com.AdamNew.dynamicwaters.shore.WaterPlantSet;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WaterPlantDraft {
    static final String[] KINDS = WaterPlantSet.KINDS;
    static final String[] PLACES = { "bed", "surface", "shore" };

    static final int ANY_DEPTH = 64;

    static final class Plant {
        String id;
        String at = "bed";
        double chance = 0.05;
        int minDepth = 1, maxDepth = ANY_DEPTH;
        boolean calm;
        int patchSize;
        double patchShare = 0.3;
        boolean alone;
        int minHeight = 1, maxHeight = 1;

        Integer salt;

        final Map<String, JsonElement> extra = new LinkedHashMap<>();

        Plant(String id) {
            this.id = id;
        }

        String blockId() {
            final int b = id.indexOf('[');
            return b < 0 ? id : id.substring(0, b);
        }
    }

    String name;

    final boolean builtIn;

    boolean replacesBuiltIn;

    final List<String> biomes = new ArrayList<>();
    final List<String> tags = new ArrayList<>();

    final List<String> not = new ArrayList<>();

    JsonElement climate;
    boolean enabled = true;
    @SuppressWarnings("unchecked")
    final List<Plant>[] plants = new List[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };

    private final Map<String, JsonElement> extra = new LinkedHashMap<>();
    private final Map<String, JsonElement> whereExtra = new LinkedHashMap<>();

    WaterPlantDraft(String name, boolean builtIn) {
        this.name = name;
        this.builtIn = builtIn;
    }

    static WaterPlantDraft startingFrom(String name, WaterPlantDraft base) {
        final WaterPlantDraft d = fromJson(name, base.toJson(), false);
        d.biomes.clear();
        d.tags.clear();
        d.not.clear();
        d.climate = null;
        return d;
    }

    WaterPlantDraft copy() {
        final WaterPlantDraft d = fromJson(name, toJson(), builtIn);
        d.replacesBuiltIn = replacesBuiltIn;
        return d;
    }

    static Plant plantOf(String id) {
        final Plant p = new Plant(id);
        p.at = usualPlace(id);
        if ("shore".equals(p.at)) {
            p.calm = true;
            p.maxHeight = 3;
        } else if ("surface".equals(p.at)) {
            p.calm = true;
            p.maxDepth = 4;
            p.alone = true;
        }
        return p;
    }

    static String usualPlace(String id) {
        final Block b = BlockSwatch.block(id.indexOf('[') < 0 ? id : id.substring(0, id.indexOf('[')));
        return b == null ? "bed" : WaterPlant.usualPlace(b.defaultBlockState()).id();
    }

    static WaterPlantDraft fromJson(String name, String text, boolean builtIn) {
        JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        return fromJson(name, o, builtIn);
    }

    static WaterPlantDraft fromJson(String name, JsonObject o, boolean builtIn) {
        final WaterPlantDraft d = new WaterPlantDraft(name, builtIn);
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            final String k = e.getKey();
            final JsonElement v = e.getValue();
            try {
                final int kind = kindIndex(k);
                if (kind >= 0 && v.isJsonArray()) {
                    for (JsonElement p : v.getAsJsonArray()) {
                        final Plant plant = readPlant(p);
                        if (plant != null) d.plants[kind].add(plant);
                    }
                    continue;
                }
                switch (k) {
                    case "where" -> d.readWhere(v.getAsJsonObject());
                    case "enabled" -> d.enabled = v.getAsBoolean();
                    default -> d.extra.put(k, v);
                }
            } catch (RuntimeException ignored) {
                d.extra.put(k, v);
            }
        }
        return d;
    }

    private void readWhere(JsonObject w) {
        for (Map.Entry<String, JsonElement> e : w.entrySet()) {
            switch (e.getKey()) {
                case "biomes" -> biomes.addAll(strings(e.getValue()));
                case "tags" -> tags.addAll(strings(e.getValue()));
                case "not" -> not.addAll(strings(e.getValue()));
                case "climate" -> climate = e.getValue();
                default -> whereExtra.put(e.getKey(), e.getValue());
            }
        }
    }

    private static int kindIndex(String key) {
        for (int k = 0; k < KINDS.length; k++) {
            if (KINDS[k].equals(key)) return k;
        }
        return -1;
    }

    private static Plant readPlant(JsonElement e) {
        if (e.isJsonPrimitive()) {
            final Plant p = new Plant(normal(e.getAsString()));
            p.at = usualPlace(p.id);
            return p;
        }
        if (!e.isJsonObject()) return null;
        final JsonObject o = e.getAsJsonObject();
        if (!o.has("block") || !o.get("block").isJsonPrimitive()) return null;
        final Plant p = new Plant(normal(o.get("block").getAsString()));
        p.at = usualPlace(p.id);
        for (Map.Entry<String, JsonElement> f : o.entrySet()) {
            final JsonElement v = f.getValue();
            try {
                switch (f.getKey()) {
                    case "block" -> {
                    }
                    case "at" -> p.at = v.getAsString().trim().toLowerCase(Locale.ROOT);
                    case "chance" -> p.chance = v.getAsDouble();
                    case "depth" -> {
                        final int[] r = range(v);
                        p.minDepth = r[0];
                        p.maxDepth = r[1];
                    }
                    case "calm" -> p.calm = v.getAsBoolean();
                    case "patch_size" -> p.patchSize = v.getAsInt();
                    case "patch_share" -> p.patchShare = v.getAsDouble();
                    case "alone" -> p.alone = v.getAsBoolean();
                    case "height" -> {
                        final int[] r = range(v);
                        p.minHeight = r[0];
                        p.maxHeight = r[1];
                    }
                    case "salt" -> p.salt = v.getAsInt();
                    default -> p.extra.put(f.getKey(), v);
                }
            } catch (RuntimeException ignored) {
                p.extra.put(f.getKey(), v);
            }
        }
        if (indexOf(PLACES, p.at) < 0) p.at = usualPlace(p.id);
        return p;
    }

    private static String normal(String raw) {
        final String id = raw.trim();
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static int[] range(JsonElement v) {
        if (v.isJsonArray()) {
            final JsonArray a = v.getAsJsonArray();
            final int from = a.get(0).getAsInt(), to = a.get(a.size() > 1 ? 1 : 0).getAsInt();
            return new int[] { Math.min(from, to), Math.max(from, to) };
        }
        final int n = v.getAsInt();
        return new int[] { n, n };
    }

    private static List<String> strings(JsonElement v) {
        final List<String> out = new ArrayList<>();
        if (v == null || v.isJsonNull()) return out;
        if (v.isJsonArray()) {
            for (JsonElement x : v.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        } else if (v.isJsonPrimitive()) {
            out.add(v.getAsString());
        }
        return out;
    }

    JsonObject toJson() {
        final JsonObject o = new JsonObject();
        final JsonObject where = new JsonObject();
        if (!biomes.isEmpty()) where.add("biomes", array(biomes));
        if (!tags.isEmpty()) where.add("tags", array(tags));
        if (climate != null) where.add("climate", climate);
        if (!not.isEmpty()) where.add("not", array(not));
        for (Map.Entry<String, JsonElement> e : whereExtra.entrySet()) {
            if (!where.has(e.getKey())) where.add(e.getKey(), e.getValue());
        }
        if (where.size() > 0) o.add("where", where);
        if (!enabled) o.addProperty("enabled", false);
        for (int k = 0; k < KINDS.length; k++) {
            final JsonArray a = new JsonArray();
            for (Plant p : plants[k]) a.add(plantJson(p));
            o.add(KINDS[k], a);
        }
        for (Map.Entry<String, JsonElement> e : extra.entrySet()) {
            if (!o.has(e.getKey())) o.add(e.getKey(), e.getValue());
        }
        return o;
    }

    private static JsonObject plantJson(Plant p) {
        final JsonObject o = new JsonObject();
        o.addProperty("block", p.id);
        o.addProperty("at", p.at);
        if (p.calm) o.addProperty("calm", true);
        if (!"shore".equals(p.at)) o.add("depth", pair(p.minDepth, p.maxDepth));
        if (p.patchSize > 0) {
            o.addProperty("patch_size", p.patchSize);
            o.addProperty("patch_share", p.patchShare);
        }
        o.addProperty("chance", p.chance);
        if (p.alone) o.addProperty("alone", true);
        if (p.minHeight != 1 || p.maxHeight != 1) o.add("height", pair(p.minHeight, p.maxHeight));
        if (p.salt != null) o.addProperty("salt", p.salt);
        for (Map.Entry<String, JsonElement> e : p.extra.entrySet()) {
            if (!o.has(e.getKey())) o.add(e.getKey(), e.getValue());
        }
        return o;
    }

    String toJsonText() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(toJson()) + "\n";
    }

    private static JsonArray pair(int a, int b) {
        final JsonArray r = new JsonArray();
        r.add(a);
        r.add(b);
        return r;
    }

    private static JsonArray array(List<String> list) {
        final JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) return i;
        }
        return -1;
    }

    boolean isDefault() {
        return biomes.isEmpty() && tags.isEmpty() && climate == null;
    }

    int plantCount() {
        int n = 0;
        for (List<Plant> l : plants) n += l.size();
        return n;
    }
}
