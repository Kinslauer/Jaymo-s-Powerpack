package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.RiverbankPalette;
import com.AdamNew.dynamicwaters.shore.Patches;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RiverbankPaletteDraft {
    static final String[] PLACES = RiverbankPalette.PLACES;
    static final int RAPIDS = RiverbankPalette.RAPIDS, SILT = RiverbankPalette.SILT, SHORE = RiverbankPalette.SHORE,
            SLOPE = RiverbankPalette.SLOPE, BARS = RiverbankPalette.BARS, FLATS = RiverbankPalette.FLATS;

    static final String KEEP = "keep";
    static final int MAX_WEIGHT = 20;

    static final class Entry {
        String id;
        int weight;

        Entry(String id, int weight) {
            this.id = id;
            this.weight = weight;
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
    final List<Entry>[] places = new List[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };
    int patchSize = 5;
    int shoreHeight = 1;

    private final Map<String, JsonElement> extra = new LinkedHashMap<>();
    private final Map<String, JsonElement> whereExtra = new LinkedHashMap<>();

    RiverbankPaletteDraft(String name, boolean builtIn) {
        this.name = name;
        this.builtIn = builtIn;
    }

    static RiverbankPaletteDraft blank(String name) {
        final RiverbankPaletteDraft d = new RiverbankPaletteDraft(name, false);
        d.places[RAPIDS].add(new Entry("minecraft:gravel", 3));
        d.places[RAPIDS].add(new Entry("minecraft:cobblestone", 1));
        d.places[SILT].add(new Entry("minecraft:sand", 2));
        d.places[SILT].add(new Entry("minecraft:clay", 1));
        d.places[SHORE].add(new Entry(KEEP, 3));
        d.places[SHORE].add(new Entry("minecraft:mud", 1));
        d.places[BARS].add(new Entry("minecraft:sand", 1));
        return d;
    }

    RiverbankPaletteDraft copy() {
        final RiverbankPaletteDraft d = fromJson(name, toJson(), builtIn);
        d.replacesBuiltIn = replacesBuiltIn;
        return d;
    }

    static RiverbankPaletteDraft fromJson(String name, String text, boolean builtIn) {
        JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        return fromJson(name, o, builtIn);
    }

    static RiverbankPaletteDraft fromJson(String name, JsonObject o, boolean builtIn) {
        final RiverbankPaletteDraft d = new RiverbankPaletteDraft(name, builtIn);
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            final String k = e.getKey();
            final JsonElement v = e.getValue();
            try {
                final int place = placeIndex(k);
                if (place >= 0) {
                    readPlace(d.places[place], v);
                    continue;
                }
                switch (k) {
                    case "where" -> d.readWhere(v.getAsJsonObject());
                    case "enabled" -> d.enabled = v.getAsBoolean();
                    case "patch_size" -> d.patchSize = v.getAsInt();
                    case "shore_height" -> d.shoreHeight = v.getAsInt();
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

    private static int placeIndex(String key) {
        for (int p = 0; p < PLACES.length; p++) {
            if (PLACES[p].equals(key)) return p;
        }
        return -1;
    }

    private static void readPlace(List<Entry> into, JsonElement v) {
        for (String raw : strings(v)) {
            String id = raw.trim();
            int w = 1;
            final int star = id.lastIndexOf('*');
            if (star > 0) {
                try {
                    w = Math.max(1, Integer.parseInt(id.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
                id = id.substring(0, star).trim();
            }
            if (id.equals("dynamicwaters:keep")) id = KEEP;
            if (!id.equals(KEEP) && !id.contains(":")) id = "minecraft:" + id;
            Entry same = null;
            for (Entry e : into) {
                if (e.id.equals(id)) same = e;
            }
            if (same != null) same.weight = Math.min(MAX_WEIGHT, same.weight + w);
            else into.add(new Entry(id, Math.min(MAX_WEIGHT, w)));
        }
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
        for (int p = 0; p < PLACES.length; p++) {
            if (places[p].isEmpty()) continue;
            final JsonArray a = new JsonArray();
            for (Entry e : places[p]) a.add(e.weight > 1 ? e.id + "*" + e.weight : e.id);
            o.add(PLACES[p], a);
        }
        o.addProperty("patch_size", patchSize);
        o.addProperty("shore_height", shoreHeight);
        for (Map.Entry<String, JsonElement> e : extra.entrySet()) {
            if (!o.has(e.getKey())) o.add(e.getKey(), e.getValue());
        }
        return o;
    }

    String toJsonText() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(toJson()) + "\n";
    }

    private static JsonArray array(List<String> list) {
        final JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }

    List<Entry> effective(int place) {
        if (!places[place].isEmpty()) return places[place];
        if (place == FLATS && !places[BARS].isEmpty()) return places[BARS];
        if (place == FLATS || place == BARS) return places[SILT];
        return places[place];
    }

    int totalWeight(int place) {
        int t = 0;
        for (Entry e : places[place]) t += e.weight;
        return t;
    }

    String pick(int place, int x, int z, int size, int salt) {
        final List<Entry> mix = effective(place);
        if (mix.isEmpty()) return null;
        final int[] w = new int[mix.size()];
        int total = 0;
        for (int k = 0; k < w.length; k++) {
            w[k] = Math.max(1, mix.get(k).weight);
            total += w[k];
        }
        return mix.get(Patches.spot(w, total, x, z, size, salt)).id;
    }

    boolean isDefault() {
        return biomes.isEmpty() && tags.isEmpty() && climate == null;
    }

    static List<String> parseList(String text, boolean tagList) {
        final List<String> out = new ArrayList<>();
        for (String s : text.split("[,\\s]+")) {
            String t = s.trim();
            if (tagList && t.startsWith("#")) t = t.substring(1);
            if (t.isEmpty()) continue;
            if (!t.contains(":")) t = "minecraft:" + t;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    static List<String> parseNot(String text) {
        final List<String> out = new ArrayList<>();
        for (String s : text.split("[,\\s]+")) {
            String t = s.trim();
            final boolean tag = t.startsWith("#");
            if (tag) t = t.substring(1);
            if (t.isEmpty()) continue;
            if (!t.contains(":")) t = "minecraft:" + t;
            if (tag) t = "#" + t;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    static String joinList(List<String> list) {
        return String.join(", ", list);
    }
}
