package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.blindness.BlindnessEchoPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
        // Packet handling is in BlindnessNet
    }

    public static void onEcho(BlindnessEchoPayload payload) {
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
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || camera.getEntity() == null) return;

        DeltaTracker delta = minecraft.getDeltaTracker();
        Vec3 camPos = camera.getPosition().add(camera.getEntity().getViewVector(delta.getGameTimeDeltaTicks()).scale(0)); // current position
        Matrix4f projection = new Matrix4f(minecraft.gameRenderer.getProjectionMatrix());

        for (ClientRing ring : RINGS) {
            // Project 3D origin to 2D screen
            Vec3 rel = ring.origin.subtract(camPos);
            // Simple orthographic projection for screen-space rings
            // In a real implementation, we'd use the view/projection matrix
            // For now, draw at screen center with radius based on currentRadius
            int cx = graphics.guiWidth() / 2;
            int cy = graphics.guiHeight() / 2;
            float screenRadius = ring.currentRadius * 4.0F; // scale factor for visibility
            if (screenRadius < 1.0F) continue;

            int alpha = (int) (255 * ring.alpha());
            if (alpha <= 0) continue;
            int colour = alpha << 24 | 0x00FFFF; // cyan rings

            // Draw ring as hollow circle
            int segments = 32;
            float prevX = cx + screenRadius;
            float prevY = cy;
            for (int i = 1; i <= segments; i++) {
                float angle = (float) (2.0 * Math.PI * i / segments);
                float x = cx + screenRadius * Mth.cos(angle);
                float y = cy + screenRadius * Mth.sin(angle);
                graphics.hLine((int) prevX, (int) x, (int) prevY, colour);
                graphics.hLine((int) prevX, (int) x, (int) y, colour);
                prevX = x;
                prevY = y;
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