package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.jaymospowers.power.blindness.BlindnessEcho;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;


import java.util.ArrayList;
import java.util.List;

/**
 * Client-side echolocation rings: receives echo packets and draws expanding sonar rings.
 * Rings ripple outward, revealing blocks and entities in their path for a brief moment.
 */
public final class BlindnessEcholocationClient {
    private static final int RING_DURATION = 60; // ticks
    private static final int MAX_RINGS = 16;

    private static final List<ClientRing> RINGS = new ArrayList<>();

    private BlindnessEcholocationClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void onEcho(BlindnessEcho payload) {
        Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());
        RINGS.add(new ClientRing(origin, 0.0F, payload.distance(), payload.volume(), payload.soundId()));
        if (RINGS.size() > MAX_RINGS) {
            RINGS.remove(0);
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            RINGS.clear();
            return;
        }
        // Update ring radii
        for (ClientRing ring : RINGS) {
            ring.age++;
            float progress = ring.age / (float) RING_DURATION;
            ring.currentRadius = Mth.lerp(progress, 0.0F, ring.maxRadius);
        }
        RINGS.removeIf(ring -> ring.age >= RING_DURATION);
    }

    /** Renders all active echolocation rings as expanding circles on screen. */
    public static void render(GuiGraphicsExtractor graphics) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        for (ClientRing ring : RINGS) {
            float screenRadius = ring.currentRadius * 4.0F; // scale factor for visibility
            if (screenRadius < 1.0F) continue;

            int alpha = (int) (255 * ring.alpha());
            if (alpha <= 0) continue;
            int colour = alpha << 24 | 0x00FFFF; // cyan rings

            // Draw the ring as a dotted circle at the screen centre
            int segments = 32;
            for (int i = 0; i < segments; i++) {
                float angle = (float) (2.0 * Math.PI * i / segments);
                float x = cx + screenRadius * Mth.cos(angle);
                float y = cy + screenRadius * Mth.sin(angle);
                graphics.fill((int) x - 1, (int) y - 1, (int) x + 1, (int) y + 1, colour);
            }
        }
    }

    public static void clear() {
        RINGS.clear();
    }

    public static List<ClientRing> rings() {
        return RINGS;
    }

    /** A client-side echolocation ring. */
    public static final class ClientRing {
        public final Vec3 origin;
        public float currentRadius;
        public final float maxRadius;
        public int age;
        public final float volume;
        public final String soundId;

        public ClientRing(Vec3 origin, float currentRadius, float maxRadius, float volume, String soundId) {
            this.origin = origin;
            this.currentRadius = currentRadius;
            this.maxRadius = maxRadius;
            this.age = 0;
            this.volume = volume;
            this.soundId = soundId;
        }

        public float progress() {
            return age / (float) RING_DURATION;
        }

        public float alpha() {
            float p = progress();
            return (float) Math.sin(p * Math.PI) * volume; // peaks in middle, fades at ends
        }
    }
}