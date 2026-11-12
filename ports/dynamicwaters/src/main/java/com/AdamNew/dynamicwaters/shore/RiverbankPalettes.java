package com.AdamNew.dynamicwaters.shore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

public final class RiverbankPalettes {
    private static final Logger LOG = LogUtils.getLogger();

    private static final String[] BUILT_IN = {
            "default.json",

            "tundra.json", "wetland.json", "mangrove.json", "dark_forest.json", "red_canyons.json", "hot_desert.json",
            "rainforest.json", "dry_grassland.json", "alpine_meadow.json", "highlands.json", "conifer_forest.json",
            "broadleaf_forest.json", "grassland.json", "mushroom_isle.json", "rocky_coast.json", "sea_coast.json",
            "river_valley.json", "lush_cave.json", "dripstone_cave.json",

            "warm_dry.json", "cold_climate.json", "humid_climate.json",
    };
    private static final String RESOURCE_DIR = "/dynamicwaters/riverbanks/";

    static final String KEEP = "keep";
    private static final Identifier KEEP_ID = Identifier.fromNamespaceAndPath("dynamicwaters", "keep");

    private static volatile List<RiverbankPalette> builtIn;
    private static volatile List<RiverbankPalette> user;
    private static final ConcurrentHashMap<Holder<Biome>, Optional<RiverbankPalette>> BY_BIOME = new ConcurrentHashMap<>();

    private RiverbankPalettes() {
    }

    public static void reload() {
        synchronized (RiverbankPalettes.class) {
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

    public static RiverbankPalette styleFor(Holder<Biome> biome) {
        ensureLoaded();
        return BY_BIOME.computeIfAbsent(biome, b -> Optional.ofNullable(resolve(b))).orElse(null);
    }

    private static void ensureLoaded() {
        if (builtIn != null && user != null) return;
        synchronized (RiverbankPalettes.class) {
            if (user == null || builtIn == null) {
                final Set<String> mine = new HashSet<>();
                user = loadUser(mine);
                builtIn = loadBuiltIn(mine);
            }
        }
    }

    private static RiverbankPalette resolve(Holder<Biome> biome) {
        final List<RiverbankPalette> u = user, b = builtIn;
        final List<RiverbankPalette> all = new ArrayList<>();
        if (u != null) all.addAll(u);
        if (b != null) all.addAll(b);
        return BiomeMatch.choose(all, s -> s.where, biome);
    }

    private static List<RiverbankPalette> loadBuiltIn(Set<String> replaced) {
        final List<RiverbankPalette> out = new ArrayList<>();
        for (String name : BUILT_IN) {
            if (replaced.contains(name)) continue;
            final String text = resource(name);
            if (text == null) continue;
            final RiverbankPalette s = parse("built-in " + name, text);
            if (s != null) out.add(s);
        }
        LOG.info("Dynamic Waters: {} built-in riverbank palette(s)", out.size());
        return out;
    }

    private static Path folder() {
        return com.AdamNew.dynamicwaters.platform.Platform.configDir().resolve("dynamicwaters").resolve("riverbanks");
    }

    private static List<RiverbankPalette> loadUser(Set<String> names) {
        final List<RiverbankPalette> out = new ArrayList<>();
        final Path dir = folder();
        seed(dir);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isRegularFile(p))
                    .sorted()
                    .forEach(p -> {
                        try {
                            final RiverbankPalette s = parse(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                            if (s != null) {
                                out.add(s);
                                names.add(p.getFileName().toString());
                            }
                        } catch (IOException e) {
                            LOG.warn("Dynamic Waters: could not read riverbank palette {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Dynamic Waters: could not list riverbank palettes in {}", dir, e);
        }
        if (!out.isEmpty()) LOG.info("Dynamic Waters: {} riverbank palette(s) of your own from {}", out.size(), dir);
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
        try (InputStream in = RiverbankPalettes.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) {
                LOG.warn("Dynamic Waters: built-in riverbank palette {} is missing from the jar", name);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Dynamic Waters: could not read built-in riverbank palette {}", name, e);
            return null;
        }
    }

    static RiverbankPalette parse(String source, String text) {
        final JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            LOG.warn("Dynamic Waters: riverbank palette {} is not a JSON object: {}", source, e.getMessage());
            return null;
        }
        final RiverbankPalette.Mix[] places = new RiverbankPalette.Mix[RiverbankPalette.PLACES.length];
        for (int p = 0; p < places.length; p++) places[p] = mix(source, o, RiverbankPalette.PLACES[p]);
        return new RiverbankPalette(source, BiomeMatch.parse("riverbank palette", source, o), BiomeMatch.flag(o, "enabled", true),
                places, Math.max(1, Math.min(16, BiomeMatch.whole(o, "patch_size", 5))),
                Math.max(0, Math.min(6, BiomeMatch.whole(o, "shore_height", 1))));
    }

    private static RiverbankPalette.Mix mix(String source, JsonObject o, String field) {
        if (!o.has(field)) return null;
        final List<BlockState> blocks = new ArrayList<>();
        final List<Integer> weights = new ArrayList<>();
        boolean anyBlock = false;
        for (String raw : BiomeMatch.strings(o.get(field))) {
            String id = raw.trim();
            int weight = 1;
            final int star = id.lastIndexOf('*');
            if (star > 0) {
                try {
                    weight = Math.max(1, Integer.parseInt(id.substring(star + 1).trim()));
                } catch (NumberFormatException e) {
                    LOG.warn("Dynamic Waters: riverbank palette {}: bad weight in \"{}\"", source, raw);
                }
                id = id.substring(0, star).trim();
            }
            BlockState state = null;
            if (!id.equals(KEEP)) {
                final Identifier rl = Identifier.tryParse(id);
                if (rl == null) {
                    LOG.warn("Dynamic Waters: riverbank palette {}: \"{}\" is not a block id", source, raw);
                    continue;
                }
                if (!rl.equals(KEEP_ID)) {
                    final Block block = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
                    if (block == null) {
                        LOG.warn("Dynamic Waters: riverbank palette {}: unknown block {}", source, rl);
                        continue;
                    }
                    state = block.defaultBlockState();
                    if (state.isAir()) state = null;
                }
            }
            if (state != null) anyBlock = true;
            blocks.add(state);
            weights.add(weight);
        }
        return anyBlock ? new RiverbankPalette.Mix(blocks, weights) : null;
    }

    private static final String README = """
            # Dynamic Waters - riverbank palettes

            A palette is what a river (lowland or mountain) or a lake lays on the ground it cut. It
            does not paint bands of height: it sorts the ground by what the water does there.

            | Place | Where | Typically |
            |---|---|---|
            | `rapids` | the bed where the water runs hard: falls, mountain streams, a river's deep line | pebbles, cobbles, boulders |
            | `silt` | the bed where the water slackens: lakes, deep pools, a river's margins, delta channels | sand, clay, mud |
            | `shore` | the wet margin, the first block or two over the water | pebbles, sand, roots, mud |
            | `slope` | cut ground above the shore (the built-in palettes leave it as it is) | rock |
            | `bars` | the shelves inside the bends where they break the surface | sand, gravel |
            | `flats` | a delta's low land between its channels | sand, mud, grass |

            How hard the water runs is worked out from the river itself: a mountain stream, a step
            in the water, the middle of a river run hard; a lake, a deep pool, the edges of a river,
            a delta's channels lie slack. The faster the water, the more of the bed is `rapids`.

            Nothing is laid block by block, which reads as a spray: the commonest block of a list is
            the ground, and every other lies on it in rounded spots - a spread of sand, a bed of
            pebbles, a hollow of mud - the way water sorts what it carries.

            Needs `riverbankPalettes = true` in `config/dynamicwaters-common.toml` (it is on by default).
            Applies to newly generated chunks. Files are read again every time a world is opened.

            ## How to change a palette

            1. Copy a file from `examples/` up into this folder (the one with this README).
            2. Edit the copy. Only the files in THIS folder are loaded, never `examples/`.
            3. Open the world again and explore new land.

            A file here named like a built-in palette replaces it, so a copy of
            `examples/broadleaf_forest.json` edited here is the forest palette from then on.

            ## Fields (all optional)

            | Field | Example | Meaning |
            |---|---|---|
            | `where.biomes` | `["minecraft:forest"]` | Use this palette in these biomes. |
            | `where.tags` | `["c:is_forest", "c:is_taiga"]` | ...or in a biome with ANY of these tags. |
            | `where.climate` | `{"temperature": [1.0, 2.5], "downfall": [0.0, 0.3]}` | ...or in a biome whose climate falls in this window. |
            | `where.not` | `["minecraft:dark_forest", "#c:is_swamp"]` | Never in these biomes, nor in a biome with these `#tags`. |
            | `enabled` | `false` | Leave the cut ground as it is wherever this palette applies. |
            | `rapids`, `silt`, `shore`, `slope`, `bars`, `flats` | `["minecraft:gravel*3", "keep"]` | The blocks of each place (see above). |
            | `patch_size` | `5` | How wide a spot is, in blocks (1 = block by block). The beds of fast water break finer. |
            | `shore_height` | `1` | How many blocks over the water the shore reaches, give or take one. |

            A place that is not given keeps its ground - but for `bars`, which are made of the
            palette's `silt` when not given, and `flats`, made of its `bars`.

            ## Mixing blocks

            `*N` gives a block N shares of the ground. The block with the most shares is the ground,
            the others lie on it in spots, each covering as much as its shares say:

            ```json
            "rapids": ["minecraft:gravel*4", "minecraft:cobblestone*2", "minecraft:mossy_cobblestone"]
            ```

            is a gravel bed with spots of cobblestone (2 in 7) and of mossy cobblestone (1 in 7).
            `keep` in a list keeps the ground for its shares - `["keep*2", "minecraft:mud"]` leaves
            the ground as it was with spots of mud on a third of it.

            ## Which palette a biome gets

            One palette per biome, asked for in this order, your files ahead of the built-in ones:

            1. A palette that names the biome in `where.biomes`.
            2. Otherwise the first one with one of the biome's tags in `where.tags`.
            3. Otherwise one whose `where.climate` the biome fits - the narrowest window.
            4. Otherwise the one that names nothing - the default.

            A lowland river runs in the `minecraft:river` biome, but it is dressed in the palette of
            the land around it - a forest river in the forest one, a desert river in the desert one.

            ## Examples

            Boulder-strewn streams in every taiga that is not snowy:

            ```json
            { "where": { "tags": ["c:is_taiga"], "not": ["#c:is_snowy"] },
              "rapids": ["minecraft:cobblestone*3", "minecraft:mossy_cobblestone*2", "minecraft:gravel"],
              "silt": ["minecraft:gravel*2", "minecraft:sand"],
              "shore": ["keep*2", "minecraft:podzol", "minecraft:mossy_cobblestone"],
              "patch_size": 4 }
            ```

            Red mud and clay in any hot, wet biome a mod adds:

            ```json
            { "where": { "climate": { "temperature": [0.9, 2.0], "downfall": [0.8, 1.0] } },
              "silt": ["minecraft:mud*3", "minecraft:clay"], "shore": ["minecraft:mud", "keep"] }
            ```

            Leave swamp water as it is:

            ```json
            { "where": { "biomes": ["minecraft:swamp"] }, "enabled": false }
            ```
            """;
}
