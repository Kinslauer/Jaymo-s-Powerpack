// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CloudInABottleModel extends BeltModel {

    private final ModelPart cloud;

    public CloudInABottleModel(ModelPart part, Function<Identifier, RenderType> renderType, CharmPose charmPose, int slot) {
        super(part, renderType, charmPose, slot);
        this.cloud = charm.getChild("cloud");
    }

    public static List<BeltModel> create(ModelPart part, Function<Identifier, RenderType> renderType, CharmPose charmPose) {
        List<BeltModel> models = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            models.add(new CloudInABottleModel(part, renderType, charmPose, i));
        }
        return models;
    }

    @Override
    public void setupAnim(HumanoidRenderState renderState) {
        super.setupAnim(renderState);
        cloud.yRot = (renderState.ageInTicks) / 50;
        cloud.y = Mth.cos((renderState.ageInTicks) / 30) / 2;
    }
}
