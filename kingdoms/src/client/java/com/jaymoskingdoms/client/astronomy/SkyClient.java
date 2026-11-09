package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The real night sky over the overworld: the HYG catalog's stars down to
 * magnitude 6.5, turning with the world's time and the observer's position,
 * plus the 88 constellation figures whose lines connect as their stars are
 * discovered ({@link Discovery}). Stars dim near settlements (light pollution)
 * and in rain.
 * <p>
 * Drawn as additive, camera-centred geometry during the world render: the
 * vanilla sky renderer is never replaced, so shader packs (Iris, Sodium) keep
 * their own sky and these stars simply ride alongside it.
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

    private SkyClient() {
    }

    public static void register() {
        Discovery.register();
        LevelRenderEvents.COLLECT_SUBMITS.register(SkyClient::render);
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
        float pollution = pollution(level, player);
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
                RenderTypes.entityTranslucent(WHITE),
                (pose2, buffer) -> {
                    clock++;
                    for (Star star : stars) {
                        Vec3 dir = SkyMath.direction(star, time, partial, lonDeg, days);
                        if (dir.y <= 0.0) {
                            continue; // below the horizon
                        }
                        double depth = dir.dot(forward);
                        if (depth <= 0.05) {
                            continue; // behind the camera
                        }
                        double nx = -dir.dot(vec(camera.leftVector())) / depth / (fovTan * aspect);
                        double ny = dir.dot(vec(camera.upVector())) / depth / fovTan;
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
                                    double daDepth = da.dot(forward), dbDepth = db.dot(forward);
                                    if (daDepth <= 0.05 || dbDepth <= 0.05) {
                                        continue;
                                    }
                                    line(buffer, pose2, da.scale(DOME), db.scale(DOME), colour);
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Each tick: discover any star the player keeps in view, and pollute the
     * sky's model so the render side stays cheap.
     */
    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (SkyMath.night(level, partial) <= 0.0F) {
            return;
        }
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
        int days = (int) (level.getGameTime() / 24000L);
        Vector3fc forward = minecraft.gameRenderer.mainCamera().forwardVector();
        double lookCos = Math.cos(Math.toRadians(LOOK_DEG));
        Discovery discovery = Discovery.getOrCreate(minecraft);
        if (discovery == null) {
            return;
        }
        for (Star star : StarCatalog.stars()) {
            if (discovery.isDiscovered(star.hip())) {
                continue;
            }
            Vec3 dir = SkyMath.direction(star, time, partial, lonDeg, days);
            if (dir.y <= 0.0 || forward.dot((float) dir.x, (float) dir.y, (float) dir.z) <= lookCos) {
                discovery.stopLook(star.hip());
                continue;
            }
            if (discovery.noteLook(star.hip()) && JaymosKingdoms.LOGGER.isDebugEnabled()) {
                JaymosKingdoms.LOGGER.debug("astronomy: {} discovered {}", player.getName().getString(), star.hip());
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    private static float starBrightness(float mag) {
        return Mth.clamp((6.5F - mag) / 6.0F, 0.2F, 1.0F);
    }

    private static float starSize(float mag) {
        return 0.12F + 0.30F * Mth.clamp((6.5F - mag) / 5.0F, 0.0F, 1.0F);
    }

    private static float twinkle(int hip) {
        return 1.0F - 0.15F * (0.5F + 0.5F * Mth.sin((clock + hip * 1.7F) * 0.07F));
    }

    /**
     * Light pollution: nearby villagers mark a settlement, and its lights dim
     * the sky. Reads as a place feeling different at night, not as a number.
     */
    private static float pollution(ClientLevel level, LocalPlayer player) {
        double best = Double.MAX_VALUE;
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(SETTLEMENT_RADIUS))) {
            best = Math.min(best, player.distanceToSqr(villager));
        }
        if (best == Double.MAX_VALUE) {
            return 0.0F;
        }
        return Mth.clamp(1.0F - (float) (Math.sqrt(best) / SETTLEMENT_RADIUS), 0.0F, 1.0F);
    }

    /** A point of light at dome distance, facing the camera, full bright. */
    private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                                  float size, int colour, float u0, float v0, float u1, float v1) {
        Vec3 n = new Vec3(x, y, z).normalize();
        Vec3 side = Math.abs(n.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 up = side.cross(n).normalize();
        float h = size * 0.5F;
        float sx = (float) side.x * h, sy = (float) side.y * h, sz = (float) side.z * h;
        float ux = (float) up.x * h, uy = (float) up.y * h, uz = (float) up.z * h;
        quad(buffer, pose,
                new float[] {x - sx + ux, y - sy + uy, z - sz + uz}, colour, u0, v0,
                new float[] {x + sx + ux, y + sy + uy, z + sz + uz}, colour, u1, v0,
                new float[] {x + sx - ux, y + sy - uy, z + sz - uz}, colour, u1, v1,
                new float[] {x - sx - ux, y + sy - uy, z - sz - uz}, colour, u0, v1);
    }

    /** A constellation line between two dome points: a thin camera-facing quad. */
    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, int colour) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 dir = b.subtract(a).normalize();
        Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6F) {
            side = new Vec3(0.0, 0.0, 1.0);
        }
        side = side.normalize();
        float mx = (float) mid.x, my = (float) mid.y, mz = (float) mid.z;
        float half = (float) a.distanceTo(b) * 0.5F;
        float sx = (float) side.x * LINE_WIDTH, sy = (float) side.y * LINE_WIDTH, sz = (float) side.z * LINE_WIDTH;
        float dx = (float) dir.x * half, dy = (float) dir.y * half, dz = (float) dir.z * half;
        quad(buffer, pose,
                new float[] {mx + dx + sx, my + dy + sy, mz + dz + sz}, colour, 0.0F, 0.0F,
                new float[] {mx - dx + sx, my - dy + sy, mz - dz + sz}, colour, 1.0F, 0.0F,
                new float[] {mx - dx - sx, my - dy - sy, mz - dz - sz}, colour, 1.0F, 1.0F,
                new float[] {mx + dx - sx, my + dy - sy, mz + dz - sz}, colour, 0.0F, 1.0F);
    }

    /** One quad, drawn both ways round so it shows from either side. */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float[] p0, int k0, float u0, float v0,
                             float[] p1, int k1, float u1, float v1,
                             float[] p2, int k2, float u2, float v2,
                             float[] p3, int k3, float u3, float v3) {
        vertex(buffer, pose, p0, k0, u0, v0);
        vertex(buffer, pose, p1, k1, u1, v1);
        vertex(buffer, pose, p2, k2, u2, v2);
        vertex(buffer, pose, p3, k3, u3, v3);
        vertex(buffer, pose, p3, k3, u3, v3);
        vertex(buffer, pose, p2, k2, u2, v2);
        vertex(buffer, pose, p1, k1, u1, v1);
        vertex(buffer, pose, p0, k0, u0, v0);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] p, int colour, float u, float v) {
        buffer.addVertex(pose, p[0], p[1], p[2]).setColor(colour).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static Vec3 vec(org.joml.Vector3fc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
