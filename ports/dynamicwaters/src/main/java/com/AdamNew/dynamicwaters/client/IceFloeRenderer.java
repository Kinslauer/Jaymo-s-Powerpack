package com.AdamNew.dynamicwaters.client;

import com.AdamNew.dynamicwaters.entity.IceFloe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws an ice floe as one or two flattened ice blocks. In 26.3 entity renderers fill a render state on the main
 * thread and submit it later, so the floe's size, turn and shape seed are copied into {@link State} first.
 */
public class IceFloeRenderer extends EntityRenderer<IceFloe, IceFloeRenderer.State> {
    private static final float TWO_PLATES_FROM = 0.5f;
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();

    private final BlockModelResolver blocks;

    public IceFloeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockModelResolver();
        this.shadowRadius = 0.0f;
    }

    public static class State extends EntityRenderState {
        public float size, yRot;
        public long shape;
        public final BlockModelRenderState ice = new BlockModelRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IceFloe floe, State state, float partialTick) {
        super.extractRenderState(floe, state, partialTick);
        state.size = floe.size();
        state.yRot = floe.getYRot();
        state.shape = it.unimi.dsi.fastutil.HashCommon.mix(floe.getUUID().getLeastSignificantBits());
        blocks.update(state.ice, Blocks.ICE.defaultBlockState(), DISPLAY);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector submit, CameraRenderState camera) {
        final float size = state.size, high = IceFloe.thickness(size);
        final long shape = state.shape;
        final float stretch = 1.0f + 0.3f * share(shape, 0);
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, -state.yRot);
        plate(pose, submit, state, size * stretch, high, size / stretch, 0.0f, 0.0f, 0.0f, 0.0f);
        if (size >= TWO_PLATES_FROM) {
            final float side = size * (0.5f + 0.3f * share(shape, 1));
            final float low = high * 0.75f;
            final float angle = share(shape, 2) * (float) (Math.PI * 2.0);
            final float out = size * 0.45f;
            plate(pose, submit, state, side, low, side * 0.8f, (float) Math.cos(angle) * out, high - low,
                    (float) Math.sin(angle) * out, 15.0f + 50.0f * share(shape, 3));
        }
        pose.popPose();
        super.submit(state, pose, submit, camera);
    }

    private static float share(long shape, int slot) {
        return ((shape >>> (slot * 8)) & 0xFF) / 255.0f;
    }

    private static void plate(PoseStack pose, SubmitNodeCollector submit, State state, float wide, float high, float deep,
            float x, float y, float z, float turn) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.rotateDegrees(Axis.YP, turn);
        pose.scale(wide, high, deep);
        pose.translate(-0.5, 0.0, -0.5);
        state.ice.submit(pose, submit, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
