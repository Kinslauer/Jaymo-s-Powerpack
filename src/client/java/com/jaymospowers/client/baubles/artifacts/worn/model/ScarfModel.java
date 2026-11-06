// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public class ScarfModel extends HumanoidModel<HumanoidRenderState> {

    private final ModelPart cloak = body.getChild("cloak");

    public ScarfModel(ModelPart part) {
        this(part, RenderTypes::armorCutoutNoCull);
    }

    public ScarfModel(ModelPart part, Function<Identifier, RenderType> renderType) {
        super(part, renderType);
    }

    @Override
    public void setupAnim(HumanoidRenderState renderState) {
        super.setupAnim(renderState);

        // TODO: fix rendering on non-player entities
        if (renderState instanceof AvatarRenderState avatarRenderState) {
            cloak.xRot = body.xRot + (6 + avatarRenderState.capeLean / 2 + avatarRenderState.capeFlap) / 180 * (float) Math.PI;
        }
    }
}
