package com.AdamNew.dynamicwaters.client;

import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.FlowZones;
import com.AdamNew.dynamicwaters.block.FlowOverrideBlock;
import com.AdamNew.dynamicwaters.item.FlowWandItem;
import com.AdamNew.dynamicwaters.network.SyncFlowZonesPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FlowZoneClient {
    private static final double DRAW_RANGE = 160.0;

    private static final int ARROWS_PER_SIDE = 12;

    private static final int SURFACE_SEARCH = 8;

    private static final double LIFT = 0.05;

    private static final int FLOWING = 0xFF38C8FF;
    private static final int STANDING = 0xFFDCDCDC;
    private static final int MARKING = 0xFFFFD23F;
    private static final int MARKING_DIM = 0x99FFD23F;

    private FlowZoneClient() {
    }

    private static final float LINE_WIDTH = 2.0f;

    public static void receive(SyncFlowZonesPacket packet) {
        final List<FlowZones.Zone> before = FlowZones.clientZones();
        FlowZones.setClient(packet.zones());
        final Set<FlowZones.Zone> was = new HashSet<>(before), now = new HashSet<>(packet.zones());
        for (FlowZones.Zone zone : before) {
            if (!now.contains(zone)) redraw(zone);
        }
        for (FlowZones.Zone zone : packet.zones()) {
            if (!was.contains(zone)) redraw(zone);
        }
    }

    private static void redraw(FlowZones.Zone zone) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.levelRenderer == null) return;
        for (int sx = zone.minX() >> 4; sx <= zone.maxX() >> 4; sx++) {
            for (int sy = zone.minY() >> 4; sy <= zone.maxY() >> 4; sy++) {
                for (int sz = zone.minZ() >> 4; sz <= zone.maxZ() >> 4; sz++) {
                    mc.levelExtractor.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    public static void onLoggingOut() {
        FlowZones.clearClient();
    }

    public static void onClone() {
        FlowZones.clearClient();
    }

    public static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        final Vec3 camera = context.levelState().cameraRenderState.pos;
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) return;
        final ItemStack wand = heldWand(player);
        if (wand == null) return;
        final boolean marking = FlowWandItem.isZoneMode(wand) && FlowWandItem.corner(wand, level, false) != null;
        if (FlowZones.clientZones().isEmpty() && !marking) return;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
        final Drawing d = new Drawing(level, pose, buffer, camera);
        for (FlowZones.Zone zone : FlowZones.clientZones()) {
            if (d.far(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ())) continue;
            final int colour = zone.direction() == FlowZones.STILL ? STANDING : FLOWING;
            d.rectangle(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ(), zone.maxY(), colour);
            d.arrows(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ(), zone.minY(), zone.maxY(), zone.direction(), colour);
        }
        if (marking) drawMarking(d, player, level, wand);
        });
    }

    private static void drawMarking(Drawing d, LocalPlayer player, ClientLevel level, ItemStack wand) {
        final BlockPos a = FlowWandItem.corner(wand, level, false);
        final BlockPos b = FlowWandItem.corner(wand, level, true);
        d.box(a, MARKING);
        if (b == null) {
            final BlockHitResult hit = FlowWandItem.target(level, player);
            if (hit.getType() == HitResult.Type.MISS) return;
            final BlockPos at = hit.getBlockPos();
            d.rectangle(Math.min(a.getX(), at.getX()), Math.min(a.getZ(), at.getZ()),
                    Math.max(a.getX(), at.getX()), Math.max(a.getZ(), at.getZ()), Math.max(a.getY(), at.getY()) + 1, MARKING_DIM);
            return;
        }
        d.box(b, MARKING);
        final int minX = Math.min(a.getX(), b.getX()), minZ = Math.min(a.getZ(), b.getZ());
        final int maxX = Math.max(a.getX(), b.getX()), maxZ = Math.max(a.getZ(), b.getZ());
        final int top = Math.max(a.getY(), b.getY()) + 1, bottom = Math.min(a.getY(), b.getY()) - FlowZones.DEPTH;
        d.rectangle(minX, minZ, maxX, maxZ, top, MARKING);
        int direction = FlowZones.STILL;
        if (!player.isShiftKeyDown()) {
            final Vec3 look = player.getLookAngle();
            if (look.x * look.x + look.z * look.z < FlowWandItem.LEVEL_LOOK * FlowWandItem.LEVEL_LOOK) return;
            direction = FlowOverrideBlock.vec3ToDirection(look.x, look.z);
        }
        d.arrows(minX, minZ, maxX, maxZ, bottom, top, direction, MARKING);
    }

    private static ItemStack heldWand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FlowWandItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FlowWandItem) return player.getOffhandItem();
        return null;
    }

    private static final class Drawing {
        private final ClientLevel level;
        private final PoseStack.Pose pose;
        private final VertexConsumer out;
        private final Vec3 cam;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        Drawing(ClientLevel level, PoseStack.Pose pose, VertexConsumer out, Vec3 cam) {
            this.level = level;
            this.pose = pose;
            this.out = out;
            this.cam = cam;
        }

        boolean far(int minX, int minZ, int maxX, int maxZ) {
            final double dx = Math.max(0.0, Math.max(minX - cam.x, cam.x - (maxX + 1)));
            final double dz = Math.max(0.0, Math.max(minZ - cam.z, cam.z - (maxZ + 1)));
            return dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE;
        }

        void line(double x1, double y1, double z1, double x2, double y2, double z2, int colour) {
            final float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            final float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-4F) return;
            out.addVertex(pose, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z))
                    .setColor(colour).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(LINE_WIDTH);
            out.addVertex(pose, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z))
                    .setColor(colour).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(LINE_WIDTH);
        }

        void rectangle(int minX, int minZ, int maxX, int maxZ, int top, int colour) {
            final double x0 = minX, z0 = minZ, x1 = maxX + 1, z1 = maxZ + 1, y = top + LIFT;
            line(x0, y, z0, x1, y, z0, colour);
            line(x1, y, z0, x1, y, z1, colour);
            line(x1, y, z1, x0, y, z1, colour);
            line(x0, y, z1, x0, y, z0, colour);
        }

        void box(BlockPos p, int colour) {
            final double e = 0.004;
            final double x0 = p.getX() - e, y0 = p.getY() - e, z0 = p.getZ() - e;
            final double x1 = p.getX() + 1 + e, y1 = p.getY() + 1 + e, z1 = p.getZ() + 1 + e;
            for (double y : new double[] {y0, y1}) {
                line(x0, y, z0, x1, y, z0, colour);
                line(x1, y, z0, x1, y, z1, colour);
                line(x1, y, z1, x0, y, z1, colour);
                line(x0, y, z1, x0, y, z0, colour);
            }
            line(x0, y0, z0, x0, y1, z0, colour);
            line(x1, y0, z0, x1, y1, z0, colour);
            line(x1, y0, z1, x1, y1, z1, colour);
            line(x0, y0, z1, x0, y1, z1, colour);
        }

        void arrows(int minX, int minZ, int maxX, int maxZ, int bottom, int top, int direction, int colour) {
            final int sizeX = maxX - minX + 1, sizeZ = maxZ - minZ + 1;
            final int step = Math.max(3, Mth.ceil(Math.max(sizeX, sizeZ) / (double) ARROWS_PER_SIDE));
            final double len = Math.min(step * 0.8, 3.0), head = len * 0.35;
            final Vec3 way = direction == FlowZones.STILL ? null : FlowOverrideBlock.directionToVec3(direction);

            final double c = Math.cos(Math.toRadians(150.0)), s = Math.sin(Math.toRadians(150.0));
            for (int gx = minX + (sizeX - 1) % step / 2; gx <= maxX; gx += step) {
                for (int gz = minZ + (sizeZ - 1) % step / 2; gz <= maxZ; gz += step) {
                    final double surface = surface(gx, gz, bottom, top);
                    if (Double.isNaN(surface)) continue;
                    final double cx = gx + 0.5, cz = gz + 0.5, y = surface + LIFT;
                    if (way == null) {
                        line(cx - 0.3, y, cz - 0.3, cx + 0.3, y, cz + 0.3, colour);
                        line(cx - 0.3, y, cz + 0.3, cx + 0.3, y, cz - 0.3, colour);
                        continue;
                    }
                    final double hx = way.x * len / 2.0, hz = way.z * len / 2.0;
                    final double tipX = cx + hx, tipZ = cz + hz;
                    line(cx - hx, y, cz - hz, tipX, y, tipZ, colour);
                    for (int side = -1; side <= 1; side += 2) {
                        final double bx = way.x * c - way.z * s * side, bz = way.x * s * side + way.z * c;
                        line(tipX, y, tipZ, tipX + bx * head, y, tipZ + bz * head, colour);
                    }
                }
            }
        }

        private double surface(int x, int z, int bottom, int top) {
            for (int y = top; y >= Math.max(bottom, top - SURFACE_SEARCH); y--) {
                probe.set(x, y, z);
                final FluidState fluid = level.getFluidState(probe);
                if (fluid.is(FluidTags.WATER)) return y + fluid.getHeight(level, probe);
            }
            return Double.NaN;
        }
    }
}
