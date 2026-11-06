// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.renderer;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.baubles.artifacts.worn.model.TransformCopyingHumanoidModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public abstract class ArtifactRenderer {

    protected abstract HumanoidModel<HumanoidRenderState> getModel(HumanoidRenderState renderState, int slotIndex);

    protected abstract Identifier getTexture(HumanoidRenderState renderState);

    protected @Nullable Identifier getFullBrightOverlayTexture(HumanoidRenderState renderState) {
        return null;
    }

    public final void render(
            ItemStack stack,
            LivingEntityRenderState renderState,
            EntityModel<?> entityModel,
            int slotIndex,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int light
    ) {
        if (entityModel instanceof HumanoidModel<?> humanoidModel) {
            render(stack, cast(renderState), humanoidModel, slotIndex, poseStack, submitNodeCollector, light);
        }
    }

    private <S extends HumanoidRenderState> void render(
            ItemStack stack,
            S renderState,
            HumanoidModel<S> entityModel,
            int slotIndex,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int light
    ) {
        // (jaymospowers) worn baubles aren't drawn on someone invisible
        if (renderState.isInvisible) {
            return;
        }

        HumanoidModel<HumanoidRenderState> artifactModel = getModel(renderState, slotIndex);
        Model<S> model = TransformCopyingHumanoidModel.create(entityModel, artifactModel);

        Identifier texture = getTexture(renderState);
        Identifier glowTexture = getFullBrightOverlayTexture(renderState);

        poseStack.pushPose();
        renderModelWithFoil(model, renderState, poseStack, submitNodeCollector, texture, light, stack.hasFoil());
        if (glowTexture != null) {
            renderModelWithFoil(model, renderState, poseStack, submitNodeCollector, glowTexture, LightCoordsUtil.FULL_BRIGHT, stack.hasFoil());
        }
        poseStack.popPose();
    }

    protected static Identifier getTextureId(String... names) {
        StringBuilder path = new StringBuilder("textures/entity/wearable/artifacts");
        for (String name : names) {
            path.append('/');
            path.append(name);
        }
        path.append(".png");
        return JaymosPowers.id(path.toString());
    }

    protected static <S extends HumanoidRenderState> void renderModelWithFoil(Model<S> model, S renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Identifier texture, int packedLight, boolean hasFoil) {
        RenderType renderType = model.renderType(texture);
        submitNodeCollector.order(0).submitModel(
                model,
                renderState,
                poseStack,
                renderType,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                null,
                renderState.outlineColor
        );
        if (hasFoil) {
            submitNodeCollector.order(1).submitModel(
                    model,
                    renderState,
                    poseStack,
                    RenderTypes.trimmedArmorGlint(),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF,
                    null,
                    renderState.outlineColor
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object object) {
        return (T) object;
    }
}
