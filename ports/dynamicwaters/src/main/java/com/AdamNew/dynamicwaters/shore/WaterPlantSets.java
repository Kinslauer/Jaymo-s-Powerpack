package com.AdamNew.dynamicwaters.shore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class WaterPlantSets {
    private static final Logger LOG = LogUtils.getLogger();

    private static final String[] BUILT_IN = { "default.json", "warm_waters.json" };
    private static final String RESOURCE_DIR = "/dynamicwaters/water_plants/";

    private static volatile List<WaterPlantSet> builtIn;
    private static volatile List<WaterPlantSet> user;
    private static final ConcurrentHashMap<Holder<Biome>, Optional<WaterPlantSet>> BY_BIOME = new ConcurrentHashMap<>();

    private WaterPlantSets() {
    }

    public static void reload() {
        synchronized (WaterPlantSets.class) {
            builtIn = null;
            user = null;
            BY_BIOME.clear();
        }
    }

    public static List<String> builtInFileNames() {
        return List.of(BUILT_IN);
    }

    public static String builtInText(String fileName) {
        return resource(fileName);
    }

    public static Path userFolder() {
        final Path dir = folder();
        seed(dir);
        return dir;
    }

    public static WaterPlantSet setFor(Holder<Biome> biome) {
        ensureLoaded();
        return BY_BIOME.computeIfAbsent(biome, b -> Optional.ofNullable(resolve(b))).orElse(null);
    }

    private static void ensureLoaded() {
        if (builtIn != null && user != null) return;
        synchronized (WaterPlantSets.class) {
            if (user == null || builtIn == null) {
                final Set<String> mine = new HashSet<>();
                user = loadUser(mine);
                builtIn = loadBuiltIn(mine);
            }
        }
    }

    private static WaterPlantSet resolve(Holder<Biome> biome) {
        final List<WaterPlantSet> u = user, b = builtIn;
        final List<WaterPlantSet> all = new ArrayList<>();
        if (u != null) all.addAll(u);
        if (b != null) all.addAll(b);
        return BiomeMatch.choose(all, s -> s.where, biome);
    }

    private static List<WaterPlantSet> loadBuiltIn(Set<String> replaced) {
        final List<WaterPlantSet> out = new ArrayList<>();
        for (String name : BUILT_IN) {
            if (replaced.contains(name)) continue;
            final String text = resource(name);
            if (text == null) continue;
            final WaterPlantSet s = parse("built-in " + name, text);
            if (s != null) out.add(s);
        }
        LOG.info("Dynamic Waters: {} built-in set(s) of water plants", out.size());
        return out;
    }

    private static Path folder() {
        return com.AdamNew.dynamicwaters.platform.Platform.configDir().resolve("dynamicwaters").resolve("water_plants");
    }

    private static List<WaterPlantSet> loadUser(Set<String> names) {
        final List<WaterPlantSet> out = new ArrayList<>();
        final Path dir = folder();
        seed(dir);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isRegularFile(p))
                    .sorted()
                    .forEach(p -> {
                        try {
                            final WaterPlantSet s = parse(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                            if (s != null) {
                                out.add(s);
                                names.add(p.getFileName().toString());
                            }
                        } catch (IOException e) {
                            LOG.warn("Dynamic Waters: could not read water plants {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Dynamic Waters: could not list water plants in {}", dir, e);
        }
        if (!out.isEmpty()) LOG.info("Dynamic Waters: {} set(s) of water plants of your own from {}", out.size(), dir);
        return out;
    }

    private static void seed(Path dir) {
        try {
            Files.createDirectories(dir.resolve("examples"));
            writeIfMissing(dir.resolve("README.md"), README);
            for (String name : BUILT_IN) {
                final String text = resource(name);
                if (text != null) writeIfMissing(dir.resolve("examples").resolve(name), text);
            }
        } catch (IOException e) {
            LOG.warn("Dynamic Waters: could not set up {}", dir, e);
        }
    }

    private static void writeIfMissing(Path file, String text) throws IOException {
        if (!Files.exists(file)) Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String resource(String name) {
        try (InputStream in = WaterPlantSets.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) {
                LOG.warn("Dynamic Waters: built-in water plants {} are missing from the jar", name);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Dynamic Waters: could not read built-in water plants {}", name, e);
            return null;
        }
    }

    static WaterPlantSet parse(String source, String text) {
        final JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            LOG.warn("Dynamic Waters: water plants {} are not a JSON object: {}", source, e.getMessage());
            return null;
        }
        final List<List<WaterPlant>> plants = new ArrayList<>();
        for (String kind : WaterPlantSet.KINDS) {
            final List<WaterPlant> list = new ArrayList<>();
            final JsonElement e = o.get(kind);
            if (e != null && e.isJsonArray()) {
                for (JsonElement p : e.getAsJsonArray()) {
                    final WaterPlant plant = WaterPlant.parse(source, kind, p);
                    if (plant != null) list.add(plant);
                }
            } else if (e != null && !e.isJsonNull()) {
                LOG.warn("Dynamic Waters: water plants {}: \"{}\" should be a list of plants", source, kind);
            }
            plants.add(List.copyOf(list));
        }
        return new WaterPlantSet(source, BiomeMatch.parse("water plants", source, o), BiomeMatch.flag(o, "enabled", true),
                List.copyOf(plants));
    }

    private static final String README = """
            # Dynamic Waters - water plants

            A set of water plants says what grows in and along a biome's waters - in a list for each
            kind of water:

            | List | Water |
            |---|---|
            | `lowland` | the lowland (2D) rivers and their deltas |
            | `mountain` | the mountain (3D) rivers and streams |
            | `lake` | the lakes |

            A list is a list of plants, tried in order: the first that grows at a place takes it.
            The built-in sets have the mod's kelp in the lakes, seagrass in the mountain rivers and
            the lakes, and lily pads and reeds along calm water; `warm_waters` adds a few sea pickles
            in the warm biomes' lakes and slow rivers.

            Needs `waterPlants = true` in `config/dynamicwaters-common.toml` (it is on by default).
            Applies to newly generated chunks. Files are read again every time a world is opened.

            ## How to change a set

            1. Copy a file from `examples/` up into this folder (the one with this README).
            2. Edit the copy. Only the files in THIS folder are loaded, never `examples/`.
            3. Open the world again and explore new land.

            A file here named like a built-in set replaces it.

            ## A plant

            | Field | Example | Meaning |
            |---|---|---|
            | `block` | `"minecraft:sea_pickle"` | The block. With its properties - `"minecraft:sea_pickle[pickles=2]"` - it is laid exactly so. |
            | `at` | `"bed"` | `bed`: on the bottom, under the water. `surface`: on still water, as a lily pad. `shore`: on the bank, on the ground at the water's level, as reeds. Left out: lily pads and frogspawn on the surface, sugar cane on the shore, the rest on the bed. |
            | `chance` | `0.002` | Of the places it could grow: 1 is everywhere, 0.002 one in 500. |
            | `depth` | `[1, 5]` | Blocks of water over the bed, from and to. For `surface`, the water under it. |
            | `calm` | `true` | Only on calm water: a lake, a river's slack margins, a delta, a mountain river where it hardly falls. |
            | `patch_size` | `7` | Grows only in patches about this many blocks across... |
            | `patch_share` | `0.3` | ...that cover this share of the water. `chance` is then how thick a patch is. |
            | `alone` | `true` | Never next to another of it. |
            | `height` | `[1, 3]` | How many blocks tall, for a plant that stacks: reeds. |
            | `salt` | `1234` | Any number: it shuffles where the plant grows. |

            A plant can be just its block id - `"minecraft:seagrass"` - with everything else as
            the table's defaults: on its usual place, `chance` 0.05, any depth.

            Kelp grows in a stalk from the bed up toward the surface; tall seagrass takes two blocks
            of water; sea pickles come one to four to a block. A block that can hold water is laid
            holding it, under the water. Nothing grows on the surface or the shore where it snows:
            the water freezes over there.

            ## Which set a biome gets

            The same as for the riverbank palettes, your files ahead of the built-in ones:

            | Field | Example | Meaning |
            |---|---|---|
            | `where.biomes` | `["minecraft:jungle"]` | Use this set in these biomes. |
            | `where.tags` | `["c:is_hot/overworld"]` | ...or in a biome with ANY of these tags. |
            | `where.climate` | `{"temperature": [1.0, 2.5], "downfall": [0.0, 0.3]}` | ...or in a biome whose climate falls in this window. |
            | `where.not` | `["minecraft:stony_peaks", "#c:is_snowy"]` | Never in these biomes, nor in a biome with these `#tags`. |
            | `enabled` | `false` | Nothing grows wherever this set applies. |

            1. A set that names the biome in `where.biomes`.
            2. Otherwise the first one with one of the biome's tags in `where.tags`.
            3. Otherwise one whose `where.climate` the biome fits - the narrowest window.
            4. Otherwise the one that names nothing - the default.

            ONE set per biome: a set for some biomes has to list the default set's plants too, or
            its waters lose them. A lowland river runs in the `minecraft:river` biome, but it takes
            the set of the land around it.

            ## Example

            Sea pickles here and there in the lakes of the jungles, the kelp kept:

            ```json
            { "where": { "tags": ["c:is_jungle"] },
              "lake": [
                { "block": "minecraft:kelp", "depth": [4, 64], "patch_size": 7, "patch_share": 0.3, "chance": 0.7, "salt": 27441 },
                { "block": "minecraft:sea_pickle", "calm": true, "depth": [1, 6], "chance": 0.004 }
              ] }
            ```
            """;
}
