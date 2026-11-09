package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The real night sky over the overworld: the HYG catalog's stars down to
 * magnitude 6.5, turning with the world's time and the observer's position,
 * plus the 88 constellation figures whose lines connect as their stars are
 * discovered ({@link Discovery}). Stars dim near settlements (light pollution)
 * and in rain.
 * <p>
 * Drawn during the sky pass: rendered after terrain so they appear behind
 * distant landmarks, and coexist with shader packs (Iris, Sodium).
 */
public final class SkyClient {
    /** Radius of the camera-centred dome the stars are drawn on (blocks). */
    static final float DOME = 60.0F;
    /** Constellation line thickness (blocks at dome distance). */
    static final float LINE_WIDTH = 0.05F;
    /** Brightness a settlement's lights can take away at most. */
    static final float POLLUTION_MAX = 0.85F;
    /** Settlement light reach (blocks). */
    static final float SETTLEMENT_RADIUS = 96.0F;
    /** Angular radius (degrees) within which looking at a star discovers it. */
    static final double LOOK_DEG = 2.5;

    private static final Identifier WHITE = JaymosKingdoms.id("textures/astronomy/white.png");
        private static long clock;
        private static float clientPollution = 0.0F;

    private SkyClient() {
    }

    public static void register() {
        Discovery.register();
        LevelRenderEvents.SKY.register(SkyClient::render);
        ClientTickEvents.END.subscribe(SkyClient::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null) {
            clientPollution = 0.0F;
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            clientPollution = 0.0F;
            return;
        }
        double pollution = 0.0;
        for (var entity : level.getEntitiesOfClass(Villager.class,
                entity -> entity.distanceTo(minecraft.player) < 192.0)) {
            pollution += 1.0;
        }
        clientPollution = (float) (pollution / 10.0); // scale down
        if (clientPollution > POLLUTION_MAX) {
            clientPollution = POLLUTION_MAX;
        }
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float night = SkyMath.night(level, partial);
        if (night <= 0.0F) {
            return;
        }
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
        int days = (int) (level.getGameTime() / 24000L);
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 forward = vec(camera.forwardVector());
        float pollution = clientPollution; // computed in tick()
                float scale = 1.0F - POLLUTION_MAX * pollution;
                int width = minecraft.getWindow().getGuiScaledWidth();
                int height = minecraft.getWindow().getGuiScaledHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }
                double aspect = width / (double) height;
                double fovTan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
                List<Star> stars = StarCatalog.stars();
                Discovery discovery = Discovery.getOrCreate(minecraft);
                PoseStack.Pose pose = context.poseStack().last();
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        RenderType.entityTranslucent(WHITE),
                        (pose2, buffer) -> {
                            clock++;
                            double fovTan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
                            double aspect = (double) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getGuiScaledHeight();
                            double halfFovTan = fovTan * aspect;
                            Vec3 left = vec(camera.leftVector());
                            Vec3 up = vec(camera.upVector());
                            for (Star star : stars) {
                                Vec3 dir = SkyMath.direction(star, time, partial, lonDeg, days);
                                if (dir.y <= 0.0) {
                                    continue; // below the horizon
                                }
                                // depth removed: stars are in the sky pass, behind terrain
                                double nx = -dir.dot(left) / halfFovTan;
                                double ny = dir.dot(up) / fovTan;
                        if (Math.abs(nx) > 1.05 || Math.abs(ny) > 1.05) {
                            continue; // outside the field of view
                        }
                        float brightness = starBrightness(star.mag()) * night * scale * twinkle(star.hip());
                        int alpha = Math.round(255.0F * brightness);
                        if (alpha < 6) {
                            continue;
                        }
                        float size = starSize(star.mag());
                        int colour = alpha << 24 | 0xFFFFFF;
                        billboard(buffer, pose2, (float) (dir.x * DOME), (float) (dir.y * DOME),
                                (float) (dir.z * DOME), size, colour, 0.0F, 0.0F, 1.0F, 1.0F);
                    }
                    if (discovery != null) {
                        int lineAlpha = Math.round(255.0F * night * 0.45F * scale);
                        if (lineAlpha >= 6) {
                            int colour = lineAlpha << 24 | 0xD0D8FF;
                            for (Constellation figure : Constellations.all()) {
                                for (int[] segment : figure.segments()) {
                                    Star a = StarCatalog.byHip(segment[0]);
                                    Star b = StarCatalog.byHip(segment[1]);
                                    if (a == null || b == null || !discovery.isDiscovered(a.hip())
                                            || !discovery.isDiscovered(b.hip())) {
                                        continue;
                                    }
                                    Vec3 da = SkyMath.direction(a, time, partial, lonDeg, days);
                                    Vec3 db = SkyMath.direction(b, time, partial, lonDeg, days);
                                    if (da.y <= 0.0 || db.y <= 0.0) {
                                        continue;
                                    }
                                    // depth removed for lines too: they are in the sky pass
                                    line(buffer, pose2, da.scale(DOME), db.scale(DOME), colour);
                                }
                            }
                        }
                    }
                });
    }
