package com.AdamNew.dynamicwaters;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class Config {
        public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
        public static final ModConfigSpec SPEC;

        public static final ModConfigSpec.DoubleValue PLAYER_SPEED;
        public static final ModConfigSpec.DoubleValue ANIMAL_SPEED;
        public static final ModConfigSpec.DoubleValue WATER_MOB_SPEED;
        public static final ModConfigSpec.DoubleValue ITEM_SPEED;
        public static final ModConfigSpec.DoubleValue DEFAULT_SPEED;
        public static final ModConfigSpec.DoubleValue SABLE_SHIP_SPEED;
        public static final ModConfigSpec.DoubleValue RAIN_FLOW_BOOST;
        public static final ModConfigSpec.DoubleValue THUNDERSTORM_FLOW_BOOST;
        public static final ModConfigSpec.BooleanValue VANILLA_RIVER_FLOW;
        public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CUSTOM_SPEEDS;

        public static final ModConfigSpec.BooleanValue ENABLE_RIVER_PARTICLES;
        public static final ModConfigSpec.IntValue RIVER_PARTICLE_DENSITY;
        public static final ModConfigSpec.BooleanValue ENABLE_RIVER_VISUAL_FLOW;
        public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BIOME_BLACKLIST;
        public static final ModConfigSpec.BooleanValue ENABLE_RIVER_SOUND;
        public static final ModConfigSpec.BooleanValue CUSTOM_MAIN_MENU_PANORAMA;
        public static final ModConfigSpec.BooleanValue SHOW_WELCOME_MESSAGE;
        public static final ModConfigSpec.BooleanValue DISABLE_TECTONIC_UNDERGROUND_RIVERS;
        public static final ModConfigSpec.BooleanValue SHOW_MAIN_MENU_BUTTON;

        public enum DensityLevel {
                VERY_LOW, LOW, MEDIUM, HIGH, ULTRA, EXTREME, INSANE
        }

        public enum RiverSplits {
                OFF(0.0),
                RARE(0.1),
                ENOUGH(0.2),
                NORMAL(0.3),
                OFTEN(0.6);

                public final double chance;

                RiverSplits(double chance) {
                        this.chance = chance;
                }
        }

        public enum FallenLogs {
                OFF(0.0),
                RARE(0.15),
                NORMAL(0.4),
                OFTEN(0.85);

                public final double chance;

                FallenLogs(double chance) {
                        this.chance = chance;
                }
        }

        public enum TunnelFrequency {
                RARE(0.05),
                NORMAL(0.15),
                OFTEN(1.0);

                public final double chance;

                TunnelFrequency(double chance) {
                        this.chance = chance;
                }
        }

        public enum IceFloes {
                OFF,
                RARE,
                NORMAL,
                OFTEN,
                MANY
        }

        public enum MountainRiverIce {
                OPEN,

                CALM,

                FULL
        }

        public enum WaterfallLevel {
                CLIFFS,

                FEW,

                NORMAL,

                MANY
        }

        public enum NormalRiverMode {
                DYNAMIC,

                VANILLA,

                NONE
        }

        public static final ModConfigSpec.BooleanValue ENABLE_watersheds;
        public static final ModConfigSpec.EnumValue<NormalRiverMode> NORMAL_RIVER_MODE;
        public static final ModConfigSpec.EnumValue<DensityLevel> MOUNTAIN_RIVER_DENSITY;
        public static final ModConfigSpec.BooleanValue ENABLE_MOUNTAIN_LAKES;
        public static final ModConfigSpec.DoubleValue NORMAL_RIVER_CANYON_HEIGHT;
        public static final ModConfigSpec.BooleanValue NORMAL_RIVER_TUNNELS;
        public static final ModConfigSpec.IntValue NORMAL_TUNNEL_HEIGHT;
        public static final ModConfigSpec.IntValue NORMAL_TUNNEL_LEDGE;
        public static final ModConfigSpec.BooleanValue NORMAL_TUNNEL_PILLARS;
        public static final ModConfigSpec.BooleanValue NORMAL_TUNNEL_FORMATIONS;
        public static final ModConfigSpec.IntValue DELTA_INLAND_REACH;
        public static final ModConfigSpec.EnumValue<RiverSplits> RIVER_SPLITS;
        public static final ModConfigSpec.EnumValue<FallenLogs> RIVER_FALLEN_LOGS;
        public static final ModConfigSpec.EnumValue<DensityLevel> NORMAL_RIVER_DENSITY;
        public static final ModConfigSpec.BooleanValue MOUNTAIN_RIVER_TUNNELS;
        public static final ModConfigSpec.EnumValue<TunnelFrequency> MOUNTAIN_TUNNEL_FREQUENCY;
        public static final ModConfigSpec.IntValue MOUNTAIN_TUNNEL_LENGTH;
        public static final ModConfigSpec.IntValue MOUNTAIN_TUNNEL_DEPTH;
        public static final ModConfigSpec.IntValue MOUNTAIN_TUNNEL_HEIGHT;
        public static final ModConfigSpec.IntValue MOUNTAIN_TUNNEL_LEDGE;
        public static final ModConfigSpec.EnumValue<WaterfallLevel> MOUNTAIN_RIVER_WATERFALLS;
        public static final ModConfigSpec.EnumValue<MountainRiverIce> MOUNTAIN_RIVER_ICE;
        public static final ModConfigSpec.EnumValue<IceFloes> RIVER_ICE_FLOES;
        public static final ModConfigSpec.BooleanValue MOUNTAIN_RIVER_BIOME;
        public static final ModConfigSpec.BooleanValue RIVERBANK_PALETTES;
        public static final ModConfigSpec.BooleanValue WATER_PLANTS;
        public static final ModConfigSpec.BooleanValue RIVER_BOULDERS;

        static {
                BUILDER.comment("How strongly river water pushes players, animals, items and boats.").push("River Flow Speeds");
                PLAYER_SPEED = BUILDER.comment("RiverFlow speed multiplier for players in the river.").defineInRange(
                                "playerSpeed",
                                0.2, 0.0, 10.0);
                ANIMAL_SPEED = BUILDER.comment(
                                "RiverFlow speed multiplier for animals. Note: Animals have strong swimming AI, you may want to decrease this.")
                                .defineInRange("animalSpeed", 0.1, 0.0, 10.0);
                WATER_MOB_SPEED = BUILDER.comment(
                                "RiverFlow speed multiplier for water animals (squids, fishes, dolphins). Set to 0 to prevent them from being swept away when their AI pauses.")
                                .defineInRange("waterMobSpeed", 0.0, 0.0, 10.0);
                ITEM_SPEED = BUILDER.comment("RiverFlow speed multiplier for dropped items.").defineInRange("itemSpeed",
                                0.2,
                                0.0,
                                10.0);
                DEFAULT_SPEED = BUILDER.comment("RiverFlow speed multiplier for generic unidentified entities.")
                                .defineInRange("defaultSpeed", 0.1, 0.0, 10.0);
                SABLE_SHIP_SPEED = BUILDER.comment("RiverFlow speed multiplier for physics ships: Sable / Aeronautics, Valkyrien Skies.")
                                .defineInRange("sableShipSpeed", 2.0, 0.0, 10.0);
                RAIN_FLOW_BOOST = BUILDER.comment("Flat speed boost applied to river flow when it is raining.")
                                .defineInRange("rainFlowBoost", 0.5, 0.0, 10.0);
                THUNDERSTORM_FLOW_BOOST = BUILDER
                                .comment("Flat speed boost applied to river flow during a thunderstorm.")
                                .defineInRange("thunderstormFlowBoost", 0.7, 0.0, 10.0);
                VANILLA_RIVER_FLOW = BUILDER
                                .comment("Give Minecraft's own rivers a current too, when they are the world's lowland rivers\n"
                                                + "(normalRiverMode = VANILLA): along the river, toward the sea. The server works the direction\n"
                                                + "out and sends it to players with the chunks, so it works in multiplayer.")
                                .define("vanillaRiverFlow", true);
                CUSTOM_SPEEDS = BUILDER.comment(
                                "Custom flow speeds for specific entities, or for every entity in a tag.\n"
                                                + "Format: modid:name=speed or #modid:tag=speed (Example: minecraft:pig=0.8, alexmobs:bird=0.0, #minecraft:aquatic=0.0)")
                                .defineListAllowEmpty(java.util.Collections.singletonList("customEntitySpeeds"),
                                                () -> java.util.List.of("minecraft:warden=1.0"),
                                                o -> o instanceof String);
                BUILDER.pop();

                BUILDER.comment("How rivers look, and the mod's touches on the main menu.").push("Visuals");
                ENABLE_RIVER_PARTICLES = BUILDER.comment("Enable custom bubble particles in rivers.")
                                .define("enableRiverParticles", true);
                RIVER_PARTICLE_DENSITY = BUILDER
                                .comment("Spawn rate of river particles. Higher = fewer particles (1 in X blocks per tick).")
                                .defineInRange("riverParticleDensity", 50, 1, 100);
                ENABLE_RIVER_VISUAL_FLOW = BUILDER.comment(
                                "Show the flowing-water texture on rivers. Off = the water looks still but still pushes entities\n"
                                                + "(for compatibility with Physics Mod). Press F3+T after changing it.")
                                .define("enableRiverVisualFlow", true);
                CUSTOM_MAIN_MENU_PANORAMA = BUILDER.comment("Enable the custom main menu panorama for Dynamic Waters.")
                                .define("customMainMenuPanorama", true);
                SHOW_MAIN_MENU_BUTTON = BUILDER.comment("Show the Dynamic Waters 'D' button on the main menu.")
                                .define("showMainMenuButton", true);
                BUILDER.pop();

                BUILDER.comment("How rivers and lakes are generated. Changes apply to newly generated chunks only.")
                                .push("Generation");
                NORMAL_RIVER_MODE = BUILDER
                                .comment("Which long lowland rivers a world gets. Only one system can own them.\n"
                                                + "DYNAMIC = Dynamic Waters' own rivers (default): wide channels with canyons and tunnels,\n"
                                                + "          carrying the minecraft:river biome along their course.\n"
                                                + "VANILLA = Minecraft's own rivers, exactly as in an unmodded world, with their normal\n"
                                                + "          minecraft:river biome. Dynamic Waters stops touching lowland river terrain.\n"
                                                + "NONE    = no lowland rivers at all. Mountain rivers are unaffected.\n"
                                                + "Takes effect when a world is loaded, not while one is running.")
                                .defineEnum("normalRiverMode", NormalRiverMode.DYNAMIC);
                NORMAL_RIVER_DENSITY = BUILDER
                                .comment("How many lowland rivers a world gets, against the default (HIGH): VERY_LOW about 1/9,\n"
                                                + "LOW about 4/9, MEDIUM about 2/3, ULTRA about 1.8x, EXTREME about 2.8x, INSANE about 7x.")
                                .defineEnum("normalRiverDensity", DensityLevel.HIGH);
                NORMAL_RIVER_CANYON_HEIGHT = BUILDER
                                .comment("Terrain height (Y level) above which a lowland river runs as a closed tunnel under the ground\n"
                                                + "instead of an open canyon. Higher = more open canyons, fewer tunnels. Only with normalRiverTunnels on.")
                                .defineInRange("normalRiverCanyonHeight", 105.0, 60.0, 2000.0);
                NORMAL_RIVER_TUNNELS = BUILDER
                                .comment("Let lowland rivers run under high ground as closed tunnels (above normalRiverCanyonHeight).\n"
                                                + "Off = no lowland tunnels at all: where the ground rises high over a lowland river it cuts an\n"
                                                + "open gorge instead - steep rock walls stepping back in terraces as they rise, wider at the top\n"
                                                + "than at the water, the river filling its floor. Mountain rivers end in the gorge.\n"
                                                + "Off by default. New chunks only.")
                                .define("normalRiverTunnels", false);
                NORMAL_TUNNEL_HEIGHT = BUILDER
                                .comment("Height of a lowland river tunnel's roof over the water at its middle, in blocks, give or take\n"
                                                + "a few. Only with normalRiverTunnels on. New chunks only.")
                                .defineInRange("normalTunnelHeight", 16, 8, 40);
                NORMAL_TUNNEL_LEDGE = BUILDER
                                .comment("Width of a dry ledge along each side of the water in a lowland river tunnel, in blocks.\n"
                                                + "0 = the water reaches the walls. Only with normalRiverTunnels on. New chunks only.")
                                .defineInRange("normalTunnelLedge", 0, 0, 12);
                NORMAL_TUNNEL_PILLARS = BUILDER
                                .comment("Rare single pillars of rock standing in a lowland river tunnel.\n"
                                                + "Only with normalRiverTunnels on. New chunks only.")
                                .define("normalTunnelPillars", true);
                NORMAL_TUNNEL_FORMATIONS = BUILDER
                                .comment("Rock rising from the bed and hanging from the roof of a lowland river tunnel, as in a cave.\n"
                                                + "Only with normalRiverTunnels on. New chunks only.")
                                .define("normalTunnelFormations", true);
                DELTA_INLAND_REACH = BUILDER
                                .comment("How far inland a big lowland river's delta may begin, in blocks up the river from the coast.\n"
                                                + "Its arms and creeks spread from there to the sea, so lower = deltas nearer the coast.\n"
                                                + "0 = no deltas at all; under 90 counts as 90; 512 = as far as the river's size allows.\n"
                                                + "New chunks only - best set before creating a world, or a delta may not line up with\n"
                                                + "the terrain already generated.")
                                .defineInRange("deltaInlandReach", 256, 0, 512);
                RIVER_SPLITS = BUILDER
                                .comment("How often a lowland river divides for a stretch: its water runs in two or three arms round\n"
                                                + "low islands, then comes together again. Mountain rivers never do.\n"
                                                + "OFF = never. RARE = here and there. ENOUGH = on one stretch in five (default).\n"
                                                + "NORMAL = on many a stretch. OFTEN = on more stretches than not. New chunks only.")
                                .defineEnum("riverSplits", RiverSplits.ENOUGH);
                RIVER_FALLEN_LOGS = BUILDER
                                .comment("How often a fallen trunk lies over the water between a lowland river's islands, of the wood\n"
                                                + "of the trees around it. Only with riverSplits on and normalRiverTunnels off. New chunks only.")
                                .defineEnum("riverFallenLogs", FallenLogs.NORMAL);
                ENABLE_watersheds = BUILDER
                                .comment("Generate mountain rivers: rivers that follow the terrain's own drainage down from the mountains.\n"
                                                + "Off = faster world generation.")
                                .define("enableWatersheds", true);
                MOUNTAIN_RIVER_DENSITY = BUILDER
                                .comment("Density of mountain rivers. Mountain rivers follow the terrain's real drainage: a stream starts wherever enough terrain drains into one spot.\n"
                                                + "Each step down roughly halves the streams: VERY_LOW = only the largest valleys carry a river, MEDIUM = half\n"
                                                + "of the default, HIGH = default, INSANE = a dense network of small streams.")
                                .defineEnum("mountainRiverDensity", DensityLevel.HIGH);
                MOUNTAIN_RIVER_TUNNELS = BUILDER
                                .comment("Let mountain rivers go underground. A river coming down a slope may go into the hillside,\n"
                                                + "run through the rock and come out further down; see the mountainTunnel options. Where one\n"
                                                + "meets a lowland river (or the sea) far below the surface it descends gradually and joins it\n"
                                                + "through a tunnel, instead of dropping in down a vertical shaft from the surface; and any\n"
                                                + "stretch running deep under the ground gets a roof rather than an open canyon. A lowland river\n"
                                                + "running as a tunnel under a mountain drains the mountain streams into itself; they reach it\n"
                                                + "underground. Off = the old sheer drop, and mountain rivers ignore lowland tunnels.\n"
                                                + "New chunks only.")
                                .define("mountainRiverTunnels", false);
                MOUNTAIN_TUNNEL_FREQUENCY = BUILDER
                                .comment("How often a mountain river goes into a hillside, on slopes with room for a tunnel.\n"
                                                + "OFTEN = wherever the ground has room for one. Only with mountainRiverTunnels on. New chunks only.")
                                .defineEnum("mountainTunnelFrequency", TunnelFrequency.NORMAL);
                MOUNTAIN_TUNNEL_LENGTH = BUILDER
                                .comment("Longest a mountain river runs level through the rock, in blocks, before its way in.\n"
                                                + "The tunnel ends sooner where the ground lets it. Only with mountainRiverTunnels on. New chunks only.")
                                .defineInRange("mountainTunnelLength", 256, 64, 640);
                MOUNTAIN_TUNNEL_DEPTH = BUILDER
                                .comment("How far under its old level a mountain river goes in a tunnel, in blocks. Deeper = more rock\n"
                                                + "over it and longer tunnels, on steeper ground. Only with mountainRiverTunnels on. New chunks only.")
                                .defineInRange("mountainTunnelDepth", 32, 16, 64);
                MOUNTAIN_TUNNEL_HEIGHT = BUILDER
                                .comment("Height of a mountain river tunnel's roof over the water, in blocks. A wide river's tunnel\n"
                                                + "is taller than this. Only with mountainRiverTunnels on. New chunks only.")
                                .defineInRange("mountainTunnelHeight", 8, 5, 24);
                MOUNTAIN_TUNNEL_LEDGE = BUILDER
                                .comment("Width of the dry ledge along each side of the water in a mountain river tunnel, in blocks.\n"
                                                + "Only with mountainRiverTunnels on. New chunks only.")
                                .defineInRange("mountainTunnelLedge", 3, 1, 8);
                MOUNTAIN_RIVER_WATERFALLS = BUILDER
                                .comment("How often a steep mountain stream breaks into a flat pool and a vertical waterfall.\n"
                                                + "Wherever the ground itself breaks off there is a waterfall at every setting; the rest are\n"
                                                + "cut into the stream's slope, never built up on it.\n"
                                                + "CLIFFS = only where the ground breaks off; elsewhere a steep stream runs down as rapids.\n"
                                                + "FEW    = a waterfall every 50-90 blocks of steep stream.\n"
                                                + "NORMAL = every 20-40 blocks (default).\n"
                                                + "MANY   = wherever the slope has room for one, every few blocks on a steep stream.\n"
                                                + "New chunks only.")
                                .defineEnum("mountainRiverWaterfalls", WaterfallLevel.NORMAL);
                MOUNTAIN_RIVER_ICE = BUILDER
                                .comment("How mountain rivers freeze in cold land. Lakes, the sea and the lowland rivers in their\n"
                                                + "frozen river biome freeze either way.\n"
                                                + "OPEN = none of their running water freezes.\n"
                                                + "CALM = their calm stretches freeze like still water; waterfalls and rapids stay open, so\n"
                                                + "       the falling water does not run out over the ice (default).\n"
                                                + "FULL = all of it, waterfalls and rapids too: their running water freezes and stops running.")
                                .defineEnum("mountainRiverIce", MountainRiverIce.CALM);
                RIVER_ICE_FLOES = BUILDER
                                .comment("How often a group of thin ice floes comes floating down a mountain river's open water in\n"
                                                + "cold land, carried by the current - over a waterfall they drop - until they crack apart a few\n"
                                                + "minutes later, the small ones first. A blow breaks one; a player goes through it into the water.\n"
                                                + "OFF = never. RARE = now and then. NORMAL = every few seconds by such a river (default).\n"
                                                + "OFTEN = more of them. MANY = a steady drift.")
                                .defineEnum("riverIceFloes", IceFloes.NORMAL);
                MOUNTAIN_RIVER_BIOME = BUILDER
                                .comment("Give the middle of a mountain river the river biome (the frozen river biome in cold land).\n"
                                                + "Banks, beds and plants stay those of the land round the river; it shows in the weather, the\n"
                                                + "mobs, the colours and the F3 screen. Only with dynamic lowland rivers. New chunks only.")
                                .define("mountainRiverBiome", true);
                ENABLE_MOUNTAIN_LAKES = BUILDER
                                .comment("Fill natural terrain depressions in the mountains with lakes, at whatever height the basin sits.")
                                .define("enableMountainLakes", true);
                BIOME_BLACKLIST = BUILDER.comment(
                                "List of biome IDs that will be treated like oceans by Dynamic Waters: no river flow and no river carving.\n"
                                                + "Use the full biome resource location, e.g. minecraft:warm_ocean or mymod:dead_sea.\n"
                                                + "Supports wildcard suffixes when the entry ends with '*', e.g. mymod:*")
                                .defineListAllowEmpty(
                                                java.util.Collections.singletonList("biomeBlacklist"),
                                                () -> java.util.List.of(),
                                                o -> o instanceof String);
                RIVER_BOULDERS = BUILDER
                                .comment("Rounded boulders in the lowland rivers, along their shallows, and very rarely a rock in a\n"
                                                + "mountain stream's rapids. New chunks only.")
                                .define("riverBoulders", true);
                RIVERBANK_PALETTES = BUILDER
                                .comment("Lay riverbank palettes on the ground rivers (lowland and mountain) and lakes cut: the bed\n"
                                                + "sorted by the current - gravel and cobbles where the water runs hard, sand, clay and silt\n"
                                                + "where it slackens - spots of shingle, sand, roots or mud along the shore, sand and gravel bars\n"
                                                + "inside the bends; each laid in spots on one ground, by the biome. A lowland river keeps its\n"
                                                + "river biome but takes the palette of the land it runs through. The palettes are JSON files in\n"
                                                + "config/dynamicwaters/riverbanks/; edit them in game (Riverbank palettes) or see the README\n"
                                                + "there. New chunks only.")
                                .define("riverbankPalettes", true);
                WATER_PLANTS = BUILDER
                                .comment("Plants in and along the rivers (lowland and mountain) and lakes: kelp in the lakes, seagrass\n"
                                                + "in the mountain rivers and lakes, lily pads and reeds along calm water, a few sea pickles in\n"
                                                + "warm waters - by the biome, a list for the lowland rivers, one for the mountain rivers and one\n"
                                                + "for the lakes. The sets are JSON files in config/dynamicwaters/water_plants/; edit them in game\n"
                                                + "(Water plants) or see the README there. Off = none of them. New chunks only.")
                                .define("waterPlants", true);
                BUILDER.pop();

                BUILDER.comment("River ambience.").push("Sounds");
                ENABLE_RIVER_SOUND = BUILDER.comment("Enable the ambient flowing sound for rivers.").define(
                                "enableRiverSound",
                                true);
                BUILDER.pop();

                BUILDER.comment("The welcome message, and compatibility with other mods.").push("General");
                SHOW_WELCOME_MESSAGE = BUILDER.comment("Show the welcome message when joining the world.")
                                .define("showWelcomeMessage", true);
                DISABLE_TECTONIC_UNDERGROUND_RIVERS = BUILDER.comment(
                                "Automatically disable underground rivers from the Tectonic mod to improve performance and compatibility with Dynamic Waters.")
                                .define("disableTectonicUndergroundRivers", true);
                BUILDER.pop();

                SPEC = BUILDER.build();
        }

        public static NormalRiverMode normalRiverMode() {
                try {
                        return NORMAL_RIVER_MODE.get();
                } catch (IllegalStateException notLoadedYet) {
                        return NormalRiverMode.DYNAMIC;
                }
        }

        public static WaterfallLevel waterfalls() {
                try {
                        return MOUNTAIN_RIVER_WATERFALLS.get();
                } catch (IllegalStateException notLoadedYet) {
                        return WaterfallLevel.NORMAL;
                }
        }

        public static boolean dynamicNormalRivers() {
                return normalRiverMode() == NormalRiverMode.DYNAMIC;
        }

        public static boolean vanillaNormalRivers() {
                return normalRiverMode() == NormalRiverMode.VANILLA;
        }

        private static java.util.List<? extends String> lastCustomSpeedsStr = null;
        private static final java.util.Map<String, Double> parsedCustomSpeeds = new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>, Double> parsedTagSpeeds = new java.util.concurrent.ConcurrentHashMap<>();

        private static volatile java.util.List<? extends String> lastBiomeBlacklistRaw = null;
        private static volatile java.util.Set<String> parsedBiomeExact = java.util.Collections.emptySet();
        private static volatile java.util.List<String> parsedBiomeWildcardPrefixes = java.util.Collections.emptyList();

        private static void updateBiomeBlacklist() {
                java.util.List<? extends String> current = BIOME_BLACKLIST.get();
                if (current == lastBiomeBlacklistRaw)
                        return;
                lastBiomeBlacklistRaw = current;
                java.util.Set<String> exact = new java.util.HashSet<>();
                java.util.List<String> prefixes = new java.util.ArrayList<>();
                if (current != null) {
                        for (String entry : current) {
                                String trimmed = entry.trim();
                                if (trimmed.endsWith("*")) {
                                        prefixes.add(trimmed.substring(0, trimmed.length() - 1));
                                } else if (!trimmed.isEmpty()) {
                                        exact.add(trimmed);
                                }
                        }
                }
                parsedBiomeExact = java.util.Collections.unmodifiableSet(exact);
                parsedBiomeWildcardPrefixes = java.util.Collections.unmodifiableList(prefixes);
        }

        public static boolean isBiomeBlacklisted(net.minecraft.resources.Identifier biomeId) {
                if (biomeId == null)
                        return false;
                updateBiomeBlacklist();
                String idStr = biomeId.toString();
                if (parsedBiomeExact.contains(idStr))
                        return true;
                for (String prefix : parsedBiomeWildcardPrefixes) {
                        if (idStr.startsWith(prefix))
                                return true;
                }
                return false;
        }

        public static boolean isBiomeBlacklisted(
                        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
                if (biomeHolder == null)
                        return false;
                net.minecraft.resources.Identifier id = biomeHolder.unwrapKey()
                                .map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
                return isBiomeBlacklisted(id);
        }

        private static void updateCustomSpeeds() {
                java.util.List<? extends String> currentList = CUSTOM_SPEEDS.get();
                if (currentList != lastCustomSpeedsStr) {
                        parsedCustomSpeeds.clear();
                        parsedTagSpeeds.clear();
                        if (currentList != null) {
                                for (String entry : currentList) {
                                        String[] split = entry.split("=");
                                        if (split.length == 2) {
                                                try {
                                                        String key = split[0].trim();
                                                        double val = Double.parseDouble(split[1].trim());
                                                        if (key.startsWith("#")) {
                                                                String tagName = key.substring(1);
                                                                net.minecraft.resources.Identifier resLoc = net.minecraft.resources.Identifier
                                                                                .parse(tagName);
                                                                net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag = net.minecraft.tags.TagKey
                                                                                .create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                                                                                                resLoc);
                                                                parsedTagSpeeds.put(tag, val);
                                                        } else {
                                                                parsedCustomSpeeds.put(key, val);
                                                        }
                                                } catch (Exception ignored) {
                                                }
                                        }
                                }
                        }
                        lastCustomSpeedsStr = currentList;
                }
        }

        public static double getFlowMultiplierForEntity(net.minecraft.world.entity.Entity entity) {
                if (entity == null)
                        return DEFAULT_SPEED.get();

                updateCustomSpeeds();

                double baseSpeed = DEFAULT_SPEED.get();

                net.minecraft.resources.Identifier rl = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(entity.getType());
                if (rl != null) {
                        String entityId = rl.toString();
                        if (parsedCustomSpeeds.containsKey(entityId)) {
                                baseSpeed = parsedCustomSpeeds.get(entityId);
                                return applyWeatherBoost(baseSpeed, entity);
                        }
                }

                for (java.util.Map.Entry<net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>, Double> entry : parsedTagSpeeds
                                .entrySet()) {
                        if (entity.typeHolder().is(entry.getKey())) {
                                baseSpeed = entry.getValue();
                                return applyWeatherBoost(baseSpeed, entity);
                        }
                }

                if (entity instanceof net.minecraft.world.entity.player.Player) {
                        baseSpeed = PLAYER_SPEED.get();
                } else if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                        baseSpeed = ITEM_SPEED.get();
                } else if (entity instanceof net.minecraft.world.entity.animal.fish.WaterAnimal
                                || entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl) {
                        baseSpeed = WATER_MOB_SPEED.get();
                } else if (entity instanceof net.minecraft.world.entity.animal.Animal) {
                        baseSpeed = ANIMAL_SPEED.get();
                }

                return applyWeatherBoost(baseSpeed, entity);
        }

        private static double applyWeatherBoost(double baseSpeed, net.minecraft.world.entity.Entity entity) {
                if (entity != null && entity.level() != null) {
                        if (entity.level().isThundering()) {
                                return baseSpeed + THUNDERSTORM_FLOW_BOOST.get();
                        } else if (entity.level().isRaining()) {
                                return baseSpeed + RAIN_FLOW_BOOST.get();
                        }
                }
                return baseSpeed;
        }
}
