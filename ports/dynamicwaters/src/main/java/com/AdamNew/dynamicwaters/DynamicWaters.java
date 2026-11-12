package com.AdamNew.dynamicwaters;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import com.AdamNew.dynamicwaters.platform.Platform;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import com.mojang.serialization.MapCodec;

public class DynamicWaters {
    public static final String MODID = "dynamicwaters";

    public static final Registrar<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = Registrar.create(Registries.DENSITY_FUNCTION_TYPE);

    public static final Supplier<MapCodec<FractalRiverFunction>> FRACTAL_RIVER = DENSITY_FUNCTIONS
            .register("fractal_river", () -> FractalRiverFunction.CODEC);

    public static void init() {
        if (Platform.isModLoaded("mr_lithosphere")) {
            com.mojang.logging.LogUtils.getLogger().warn("[Dynamic Waters] Compatibility with mod Lithosphere is still beta.");
            com.mojang.logging.LogUtils.getLogger().warn("[Dynamic Waters] Warning: The world may take a very long time to load.");
        }
        if (Platform.isModLoaded("larion")) {
            com.mojang.logging.LogUtils.getLogger().warn("[Dynamic Waters] Compatibility with mod Larion is still beta.");
            com.mojang.logging.LogUtils.getLogger().warn("[Dynamic Waters] Warning: The world may take a very long time to load.");
        }
        if (Platform.isModLoaded("sable")) {
            com.mojang.logging.LogUtils.getLogger().warn("[Dynamic Waters] Warning: Sable mod detected. The game/server may crash upon first joining the world, but should work fine afterwards.");
        }
    }

    public static void onConfigLoaded() {
        if (Config.DISABLE_TECTONIC_UNDERGROUND_RIVERS.get()) {
            disableTectonicUndergroundRivers();
        }
    }

    public static void onServerAboutToStart() {
        HydrologyManager.resetForServer();
    }

    public static void onLevelLoad(net.minecraft.server.level.ServerLevel level) {
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            HydrologyManager.setSeed(level.getSeed());
            com.AdamNew.dynamicwaters.compat.OneDimension.onOverworldLoad(level);
        }
    }

    public static void onServerStarting() {
        if (Config.DISABLE_TECTONIC_UNDERGROUND_RIVERS.get()) {
            disableTectonicUndergroundRivers();
        }
        com.AdamNew.dynamicwaters.async.MountainRiverScheduler.start();

        com.AdamNew.dynamicwaters.shore.RiverbankPalettes.reload();
        com.AdamNew.dynamicwaters.shore.WaterPlantSets.reload();

        VanillaRiverFlow.clearServer();
    }

    public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        com.AdamNew.dynamicwaters.async.MountainRiverScheduler.startPreWarm(server, server.overworld());
        final String labTiles = System.getProperty("dynamicwaters.labTiles");
        if (labTiles != null) HydrologyManager.labBuildTiles(labTiles, server.overworld());
    }

    public static void onChunkSent(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.chunk.LevelChunk chunk) {
        final byte[] angles = VanillaRiverFlow.chunkAngles(level, chunk);
        if (angles != null) {
            Platform.sendToPlayer(player,
                    new com.AdamNew.dynamicwaters.network.SyncVanillaFlowPacket(chunk.getPos().x(), chunk.getPos().z(), angles));
        }
    }

    public static void onServerStopped() {
        com.AdamNew.dynamicwaters.async.MountainRiverScheduler.stop();
    }

    private static void disableTectonicUndergroundRivers() {
        try {
            java.nio.file.Path configPath = Platform.configDir()
                    .resolve("tectonic.json");
            if (java.nio.file.Files.exists(configPath)) {
                String jsonContent = new String(java.nio.file.Files.readAllBytes(configPath),
                        java.nio.charset.StandardCharsets.UTF_8);
                com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(jsonContent)
                        .getAsJsonObject();

                if (jsonObject.has("continents")) {
                    com.google.gson.JsonObject continents = jsonObject.getAsJsonObject("continents");
                    if (continents.has("underground_rivers") && continents.get("underground_rivers").getAsBoolean()) {
                        continents.addProperty("underground_rivers", false);

                        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                        java.nio.file.Files.write(configPath,
                                gson.toJson(jsonObject).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        System.out.println(
                                "[Dynamic Waters] Successfully disabled Tectonic underground rivers for performance.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Dynamic Waters] Failed to modify tectonic config: " + e.getMessage());
        }
    }

    public static class ModEvents {
        private static final java.util.Map<java.util.UUID, java.util.Set<Long>> SENT_REGIONS = new java.util.concurrent.ConcurrentHashMap<>();

        private static final java.util.Map<java.util.UUID, java.util.Set<Long>> SENT_LOWLAND_REGIONS = new java.util.concurrent.ConcurrentHashMap<>();

        public static void onPlayerLogin(net.minecraft.world.entity.player.Player player) {
            syncPlayerRegions(player);
            onPlayerLoginMessages(player);
            onPlayerJoin(player);
        }

        public static void onPlayerRespawn(net.minecraft.world.entity.player.Player player) {
            syncPlayerRegions(player);
        }

        public static void onPlayerChangeDimension(net.minecraft.world.entity.player.Player player) {
            SENT_REGIONS.put(player.getUUID(), java.util.concurrent.ConcurrentHashMap.newKeySet());
            SENT_LOWLAND_REGIONS.put(player.getUUID(), java.util.concurrent.ConcurrentHashMap.newKeySet());
            syncPlayerRegions(player);
        }

        private static void syncPlayerRegions(net.minecraft.world.entity.player.Player playerEntity) {
            SENT_REGIONS.putIfAbsent(playerEntity.getUUID(), java.util.concurrent.ConcurrentHashMap.newKeySet());
            if (playerEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                if (!HydrologyManager.riversIn(serverPlayer.level())) {
                    return;
                }

                if (Platform.isOwnPlayer(serverPlayer)) {
                    return;
                }
                long seed = serverPlayer.level().getSeed();
                com.AdamNew.dynamicwaters.HydrologyManager.setSeed(seed);

                int seaLevel = serverPlayer.level().getSeaLevel();
                Platform.sendToPlayer(
                        serverPlayer,
                        new com.AdamNew.dynamicwaters.network.SyncSeedPacket(seed, seaLevel));

                int chunkX = serverPlayer.chunkPosition().x();
                int chunkZ = serverPlayer.chunkPosition().z();
                int rx = (int) Math.floor((chunkX * 16.0) / com.AdamNew.dynamicwaters.HydrologyManager.WATERSHED_REGION_SIZE);
                int rz = (int) Math.floor((chunkZ * 16.0) / com.AdamNew.dynamicwaters.HydrologyManager.WATERSHED_REGION_SIZE);
                java.util.Set<Long> sent = SENT_REGIONS.computeIfAbsent(serverPlayer.getUUID(),
                        k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int nrx = rx + dx;
                        int nrz = rz + dz;
                        long key = ((long) nrx << 32) | (nrz & 0xFFFFFFFFL);
                        if (sent.add(key)) {
                            com.AdamNew.dynamicwaters.HydrologyManager.WatershedRegionData regionData = com.AdamNew.dynamicwaters.HydrologyManager.WATERSHED_REGION_CACHE
                                    .get(com.AdamNew.dynamicwaters.HydrologyManager.getRegionKey(nrx, nrz));
                            if (regionData != null) {
                                Platform.sendToPlayer(
                                        serverPlayer,
                                        new com.AdamNew.dynamicwaters.network.SyncWatershedsPacket(nrx, nrz, regionData.allPaths, regionData.lakes));
                            }
                        }
                    }
                }

                final int mrx = Math.floorDiv(serverPlayer.blockPosition().getX(), com.AdamNew.dynamicwaters.MainRivers.REGION_SIZE);
                final int mrz = Math.floorDiv(serverPlayer.blockPosition().getZ(), com.AdamNew.dynamicwaters.MainRivers.REGION_SIZE);
                final java.util.Set<Long> sentLowland = SENT_LOWLAND_REGIONS.computeIfAbsent(serverPlayer.getUUID(),
                        k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        final long key = ((long) (mrx + dx) << 32) | ((mrz + dz) & 0xFFFFFFFFL);
                        if (!sentLowland.add(key)) continue;
                        java.util.List<com.AdamNew.dynamicwaters.river.RiverGraphEdge> mainRivers = com.AdamNew.dynamicwaters.MainRivers.getRegion(
                            mrx + dx, mrz + dz, com.AdamNew.dynamicwaters.compat.OneDimension.generator(serverPlayer.level()), serverPlayer.level(),
                            com.AdamNew.dynamicwaters.compat.OneDimension.randomState(serverPlayer.level()));
                        Platform.sendToPlayer(
                            serverPlayer,
                            new com.AdamNew.dynamicwaters.network.SyncMainRiversPacket(mrx + dx, mrz + dz, mainRivers));
                    }
                }
            }
        }

        private static void onPlayerLoginMessages(net.minecraft.world.entity.player.Player player) {
            if (Platform.isModLoaded("tectonic")) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("§e[Dynamic Waters] Compatibility with mod Tectonic is still beta."));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a[Dynamic Waters] Tectonic underground rivers were automatically disabled for better performance."));
            }
        }

        public static void onPlayerLogout(net.minecraft.world.entity.player.Player player) {
            SENT_REGIONS.remove(player.getUUID());
            SENT_LOWLAND_REGIONS.remove(player.getUUID());
            com.AdamNew.dynamicwaters.async.MountainRiverScheduler.forget(player.getUUID());
        }

        public static void onChunkWatch(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level,
                int chunkX, int chunkZ) {
            com.AdamNew.dynamicwaters.HydrologyManager.setSeed(level.getSeed());
            com.AdamNew.dynamicwaters.async.MountainRiverScheduler.onChunkWatched(player, level, chunkX, chunkZ);
        }
    }

    public record BuiltInPack(String folder, String title) {
    }

    public static java.util.List<BuiltInPack> builtInPacks() {
        if (Config.vanillaNormalRivers()) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Dynamic Waters: normalRiverMode=VANILLA - leaving river terrain and the river biome to the world generator.");
            return java.util.List.of();
        }
        final java.util.List<BuiltInPack> packs = new java.util.ArrayList<>();
        packs.add(new BuiltInPack("dynamicwaters_rivers", "Dynamic Waters Rivers Override"));
        if (Platform.isModLoaded("tectonic")) packs.add(new BuiltInPack("dynamicwaters_tectonic", "Dynamic Waters Tectonic Compat"));
        if (Platform.isModLoaded("mr_lithosphere")) packs.add(new BuiltInPack("dynamicwaters_lithosphere", "Dynamic Waters Lithosphere Compat"));

        if (Platform.isModLoaded("abridged")) packs.add(new BuiltInPack("dynamicwaters_abridged", "Dynamic Waters Abridged Compat"));
        if (Platform.isModLoaded("larion")) packs.add(new BuiltInPack("dynamicwaters_larion", "Dynamic Waters Larion Compat"));
        return packs;
    }

    public static void onRegisterCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        com.AdamNew.dynamicwaters.command.LocateRiverTerrainCommand.register(dispatcher);
        dispatcher.register(net.minecraft.commands.Commands.literal("dynamicwaters")
                .then(net.minecraft.commands.Commands.literal("disableWelcome")
                        .executes(context -> {
                            Config.SHOW_WELCOME_MESSAGE.set(false);
                            Config.SHOW_WELCOME_MESSAGE.save();
                            context.getSource()
                                    .sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "§aDynamic Waters welcome message has been disabled for future worlds."),
                                            false);
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("stats")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                    .literal(com.AdamNew.dynamicwaters.terrain.WatershedStats.summary()), false);
                            HydrologyManager.labDumpFlow(context.getSource().getLevel());
                            return 1;
                        })
                        .then(net.minecraft.commands.Commands.literal("reset")
                                .executes(context -> {
                                    com.AdamNew.dynamicwaters.terrain.WatershedStats.reset();
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                            .literal("Dynamic Waters stats reset."), false);
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("trace")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            final String report = traceNearestRiver(context.getSource().getLevel(),
                                    context.getSource().getPosition().x, context.getSource().getPosition().z);
                            com.mojang.logging.LogUtils.getLogger().info(report);
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(report), false);
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("bench")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands
                                .argument("chunks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8192))
                                .executes(context -> {
                                    runBench(context.getSource(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunks"));
                                    return 1;
                                })))
                .then(com.AdamNew.dynamicwaters.command.FlowZonesCommand.build()));
    }

    private static String traceNearestRiver(net.minecraft.server.level.ServerLevel level, double px, double pz) {
        if (!HydrologyManager.riversIn(level)) return "Dynamic Waters' rivers only run in the Overworld.";
        HydrologyManager.WatershedContext ctx = HydrologyManager.evaluateWatershedContext(px, pz, null, null, null, true);
        if (ctx.bestPath == null) return "No mountain river within reach of this position.";
        final int seaLevel = level.getSeaLevel();
        final StringBuilder sb = new StringBuilder();
        final java.util.Set<WatershedPath> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        WatershedPath p = ctx.bestPath;
        for (int hop = 0; hop < 64 && p != null; hop++) {
            seen.add(p);
            final int n = p.nodeCount;
            final double mx = p.segmentsX[0], mz = p.segmentsZ[0];
            final int my = p.segmentsY[0];
            sb.append(String.format("#%d %d nodes, w=%.1f, from (%d,%d) to mouth (%d,%d,%d)%n",
                    hop, n, p.segmentWidths[0], (int) p.segmentsX[n - 1], (int) p.segmentsZ[n - 1], (int) mx, my, (int) mz));
            if (p.hitMainRiver) {
                sb.append("  ends: MAIN RIVER");
                break;
            }
            com.AdamNew.dynamicwaters.terrain.WatershedLake lake = HydrologyManager.findLake(mx, mz, null, null, null, true);
            if (lake != null) {
                sb.append("  ends: LAKE at y=").append(lake.waterY);
                break;
            }
            int terrainY = Integer.MIN_VALUE;
            if (level.hasChunk(((int) mx) >> 4, ((int) mz) >> 4)) {
                terrainY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, (int) mx, (int) mz);
                if (terrainY < seaLevel) {
                    sb.append("  ends: SEA (terrain y=").append(terrainY).append(")");
                    break;
                }
            }

            WatershedPath next = null;
            double best = 12.0 * 12.0;
            final int rx = Math.floorDiv((int) mx, HydrologyManager.WATERSHED_REGION_SIZE);
            final int rz = Math.floorDiv((int) mz, HydrologyManager.WATERSHED_REGION_SIZE);
            int cachedRegions = 0, pathsNearby = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    HydrologyManager.WatershedRegionData data = HydrologyManager.WATERSHED_REGION_CACHE
                            .get(HydrologyManager.getRegionKey(rx + dx, rz + dz));
                    if (data == null) continue;
                    cachedRegions++;
                    for (WatershedPath q : data.allPaths) {
                        pathsNearby++;
                        if (seen.contains(q)) continue;
                        final int qn = q.nodeCount;
                        final double ddx = q.segmentsX[qn - 1] - mx, ddz = q.segmentsZ[qn - 1] - mz;
                        final double d2 = ddx * ddx + ddz * ddz;
                        if (d2 < best) {
                            best = d2;
                            next = q;
                        }
                    }
                }
            }
            if (next == null) {
                sb.append(String.format("  ends: BREAK - nothing continues from this mouth. regions cached %d/9, paths nearby %d, terrain y=%s, sea %d, region (%d,%d)",
                        cachedRegions, pathsNearby, terrainY == Integer.MIN_VALUE ? "?" : String.valueOf(terrainY), seaLevel, rx, rz));
                break;
            }
            p = next;
        }
        return sb.toString();
    }

    private static void runBench(net.minecraft.commands.CommandSourceStack source, int chunks) {
        final net.minecraft.server.level.ServerLevel level = source.getLevel();

        if (!HydrologyManager.riversIn(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "Dynamic Waters' rivers only run in the Overworld - run the bench there."));
            return;
        }
        final net.minecraft.server.MinecraftServer server = source.getServer();
        final net.minecraft.world.level.chunk.ChunkGenerator generator = com.AdamNew.dynamicwaters.compat.OneDimension.generator(level);
        final net.minecraft.world.level.levelgen.RandomState randomState = com.AdamNew.dynamicwaters.compat.OneDimension.randomState(level);
        final int seaLevel = level.getSeaLevel();
        final int originChunkX = ((int) source.getPosition().x >> 4) + 4096;
        final int originChunkZ = ((int) source.getPosition().z >> 4) + 4096;

        HydrologyManager.setSeed(level.getSeed());
        HydrologyManager.setSeaLevel(seaLevel);
        if (HydrologyManager.getGlobalGenerator() == null) {
            HydrologyManager.setGlobalGenerator(generator, randomState);
        }
        HydrologyManager.clearCaches();
        MainRivers.clearCaches();
        com.AdamNew.dynamicwaters.terrain.WatershedStats.reset();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Dynamic Waters bench: " + chunks + " chunks at chunk " + originChunkX + "," + originChunkZ + " (caches cleared)..."), false);

        Thread t = new Thread(() -> {
            final net.minecraft.world.level.LevelHeightAccessor accessor = HydrologyManager.getDummyAccessor();
            final long t0 = System.nanoTime();
            int x = 0, z = 0, dx = 0, dz = -1;
            for (int i = 0; i < chunks; i++) {
                final int cx = originChunkX + x, cz = originChunkZ + z;
                final int startX = cx << 4, startZ = cz << 4;
                HydrologyManager.prefetchWatershedsAround(startX + 8, startZ + 8, generator, accessor, randomState);
                MainRivers.prefetchAround(startX + 8, startZ + 8, generator, accessor, randomState);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        final int worldX = startX + lx, worldZ = startZ + lz;
                        final int originalY = com.AdamNew.dynamicwaters.terrain.CoarseHeightField.sample(
                                worldX >> com.AdamNew.dynamicwaters.terrain.CoarseHeightField.CELL_SHIFT,
                                worldZ >> com.AdamNew.dynamicwaters.terrain.CoarseHeightField.CELL_SHIFT,
                                generator, randomState);
                        HydrologyManager.calculateRiverCarving(worldX, worldZ, originalY, seaLevel, generator, accessor, randomState);
                    }
                }
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int tmp = dx;
                    dx = -dz;
                    dz = tmp;
                }
                x += dx;
                z += dz;
            }
            final long ms = (System.nanoTime() - t0) / 1_000_000L;
            final String msg = String.format("Dynamic Waters bench: %d chunks in %d ms (%.2f ms/chunk)\n%s",
                    chunks, ms, (double) ms / chunks, com.AdamNew.dynamicwaters.terrain.WatershedStats.summary());
            com.mojang.logging.LogUtils.getLogger().info(msg);
            server.execute(() -> source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false));
        }, "DynamicWaters-Bench");
        t.setDaemon(true);
        t.start();
    }

    private static void onPlayerJoin(net.minecraft.world.entity.player.Player entity) {
        if (Config.SHOW_WELCOME_MESSAGE.get()
                && entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§3===================================="));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b§lWelcome to Dynamic Waters!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Thank you for installing the mod. I hope you enjoy the new, sprawling river networks as they carve their way through your world!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bDynamic Waters is highly configurable! §7You can turn 2D and 3D rivers off or change them - their density, waterfalls, lakes, banks and much more - in the mod's config (Mods → Dynamic Waters → Config)."));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bRiver tunnels are off by default. §7You can turn them on at the top of the config: the Lowland River Tunnels tab for 2D rivers and the Mountain River Tunnels tab for 3D rivers, where you can also set where they appear and how big they are."));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fIf you encounter any bugs, crashes, or have suggestions, please join our Discord server:"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§9§nhttps://discord.gg/s6XMKJtEN5")
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create("https://discord.gg/s6XMKJtEN5")))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("§9Click to join Discord")))));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e§oNote: Dynamic Waters is under active development. Some bugs may still occur."));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6After updating to a new version, creating a new world is highly recommended."));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aEnjoy your adventure!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§3===================================="));
            player.sendSystemMessage(net.minecraft.network.chat.Component.empty());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Click here to disable this message]")
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/dynamicwaters disableWelcome"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("§cDisable welcome message")))));
        }
    }
}
