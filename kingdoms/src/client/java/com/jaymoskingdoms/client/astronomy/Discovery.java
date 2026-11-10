package com.jaymoskingdoms.client.astronomy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which stars a player has discovered, kept per player in the client config
 * directory ({@code config/jaymoskingdoms/astronomy/discovery-<uuid>.json}).
 * A star is discovered by keeping it in view at night for {@link #LOOK_TICKS}
 * ticks; constellation figures connect once all their stars are discovered.
 * Client side only: nothing is sent to or kept by the server.
 */
public final class Discovery {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms").resolve("astronomy");
    private static final Gson GSON = new Gson();
    static final int SAVE_EVERY_TICKS = 600;
    /** Ticks of steady looking at a star before it is discovered (~4 seconds at 20 tps). */
    static final int LOOK_TICKS = 80;

    private static final Map<UUID, Discovery> BY_PLAYER = new HashMap<>();
    private static int saveClock;

    static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LocalPlayer player = client.player;
            if (player != null) {
                Discovery d = BY_PLAYER.remove(player.getUUID());
                if (d != null) {
                    d.save();
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(Discovery::tick);
    }

    static Discovery of(LocalPlayer player) {
        return player == null ? null : BY_PLAYER.get(player.getUUID());
    }

    /** The discovery state for the player in the game, creating it on first use. */
    static Discovery getOrCreate(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        return BY_PLAYER.computeIfAbsent(player.getUUID(), uuid -> new Discovery(uuid));
    }

    private final UUID player;
    private final Path file;
    private final Set<Integer> stars = new HashSet<>();
    /** Star hip -> ticks the player has looked at it this session (transient). */
    private final Map<Integer, Integer> looking = new HashMap<>();
    private boolean dirty;

    private Discovery(UUID player) {
        this.player = player;
        this.file = DIR.resolve("discovery-" + player + ".json");
        load();
    }

    boolean isDiscovered(int hip) {
        return stars.contains(hip);
    }

    /**
     * Call once per tick while the player keeps {@code hip} in view at night.
     * Returns true the tick the star becomes discovered.
     */
    boolean noteLook(int hip) {
        if (stars.contains(hip)) {
            return false;
        }
        int ticks = looking.getOrDefault(hip, 0) + 1;
        looking.put(hip, ticks);
        if (ticks >= LOOK_TICKS) {
            stars.add(hip);
            looking.remove(hip);
            dirty = true;
            return true;
        }
        return false;
    }

    /** The player is no longer looking at {@code hip}: drop its look counter. */
    void stopLook(int hip) {
        looking.remove(hip);
    }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonObject root = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), JsonObject.class);
            JsonArray array = root.getAsJsonArray("stars");
            for (JsonElement element : array) {
                stars.add(element.getAsInt());
            }
        } catch (IOException | IllegalStateException e) {
            JaymosKingdoms.LOGGER.warn("astronomy: failed to read discovery for {}", player, e);
        }
    }

    void save() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(DIR);
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (int hip : stars) {
                array.add(hip);
            }
            root.add("stars", array);
            Files.writeString(file, GSON.toJson(root));
            dirty = false;
        } catch (IOException e) {
            JaymosKingdoms.LOGGER.warn("astronomy: failed to save discovery for {}", player, e);
        }
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        Discovery d = BY_PLAYER.get(player.getUUID());
        if (d == null) {
            return;
        }
        if (++saveClock >= SAVE_EVERY_TICKS) {
            saveClock = 0;
            d.save();
        }
    }
}
