package com.jaymospowers.client.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.fishing.Skipjack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** (0.21.61) Draws the skipjack tuna: swims with a sway, and flops on its side out of water, as a cod does. */
public final class SkipjackRenderer extends MobRenderer<Skipjack, LivingEntityRenderState, SkipjackModel> {
    private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/fish/skipjack.png");

    public SkipjackRenderer(EntityRendererProvider.Context context) {
        super(context, SkipjackModel.create(), 0.4F);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.rotateDegrees(Axis.YP, 3.0F * Mth.sin(0.6F * state.ageInTicks));
        if (!state.isInWater) {
            poseStack.translate(0.15F, 0.1F, -0.1F);
            poseStack.rotateDegrees(Axis.ZP, 90.0F);
        }
    }
}
