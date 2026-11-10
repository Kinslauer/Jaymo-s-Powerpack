package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.ArmillaryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The armillary sphere's rings, turning slowly above the block. Drawn as taut
 * cords between points on three rings, so it moves even when nothing else does.
 */
public final class ArmillaryBlockEntityRenderer implements BlockEntityRenderer<ArmillaryBlockEntity, ArmillaryBlockEntityRenderer.State> {
    private static final int SEGMENTS = 24;
    private static final double RING_R = 0.42;
    /** How many degrees the rings turn per tick. */
    private static final float SPIN = 0.5F;

    public static final class State extends BlockEntityRenderState {
        BlockPos pos;
        float angle;
        int blockLight;
        int skyLight;
    }

    public ArmillaryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArmillaryBlockEntity blockEntity, State state, float partialTicks,
                                   Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        Level level = blockEntity.getLevel();
        state.pos = blockEntity.getBlockPos();
        state.blockLight = level == null ? 0 : level.getBrightness(LightLayer.BLOCK, blockEntity.getBlockPos());
        state.skyLight = level == null ? 0 : level.getBrightness(LightLayer.SKY, blockEntity.getBlockPos());
        double t = (level == null ? 0.0 : level.getGameTime()) + partialTicks;
        state.angle = (float) ((t * SPIN) % 360.0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        double cx = state.pos.getX() + 0.5, cy = state.pos.getY() + 0.75, cz = state.pos.getZ() + 0.5;
        float spin = (float) Math.toRadians(state.angle);
        ring(collector, poseStack, cx, cy, cz, RING_R, spin, 0.0F, 0xA47C36, state.blockLight, state.skyLight);
        ring(collector, poseStack, cx, cy, cz, RING_R, spin + (float) Math.toRadians(80.0F), (float) Math.toRadians(40.0F), 0x8D7351, state.blockLight, state.skyLight);
        ring(collector, poseStack, cx, cy, cz, RING_R * 0.7F, spin - (float) Math.toRadians(50.0F), (float) Math.toRadians(-30.0F), 0x755239, state.blockLight, state.skyLight);
        // the gnomon, pointing up
        leash(collector, poseStack, cx, cy - 0.30F, cz, cx, cy + 0.30F, cz, 0x584333, state.blockLight, state.skyLight);
    }

    /** One ring: a circle in a tilted plane, spun by {@code phase}. */
    private static void ring(SubmitNodeCollector collector, PoseStack pose, double cx, double cy, double cz,
                             double r, float phase, float tilt, int colour, int blockLight, int skyLight) {
        Vec3 prev = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double ang = 2.0 * Math.PI * i / SEGMENTS + phase;
            double x = cx + Math.cos(ang) * r;
            double y = cy + Math.sin(ang) * r * Math.cos(tilt);
            double z = cz + Math.sin(ang) * r * Math.sin(tilt);
            if (prev != null) {
                leash(collector, pose, prev.x, prev.y, prev.z, x, y, z, colour, blockLight, skyLight);
            }
            prev = new Vec3(x, y, z);
        }
    }

    private static void leash(SubmitNodeCollector collector, PoseStack pose, double x0, double y0, double z0,
                              double x1, double y1, double z1, int colour, int blockLight, int skyLight) {
        EntityRenderState.LeashState line = new EntityRenderState.LeashState();
        line.offset = Vec3.ZERO;
        line.start = new Vec3(x0, y0, z0);
        line.end = new Vec3(x1, y1, z1);
        line.startBlockLight = blockLight;
        line.endBlockLight = blockLight;
        line.startSkyLight = skyLight;
        line.endSkyLight = skyLight;
        line.slack = false;
        collector.submitLeash(pose, line);
    }
}
