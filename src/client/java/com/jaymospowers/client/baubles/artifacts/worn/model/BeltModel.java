// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BeltModel extends HumanoidModel<HumanoidRenderState> {

    protected final ModelPart charm;
    private final CharmPose charmPose;
    private final int slot;

    protected BeltModel(ModelPart part, Function<Identifier, RenderType> renderType, CharmPose charmPose, int slot) {
        super(part, renderType);
        this.charm = body.getChild("charm");
        this.charmPose = charmPose;
        this.slot = slot;
    }

    public static List<BeltModel> create(ModelPart part, CharmPose charmPose) {
        return create(part, RenderTypes::armorCutoutNoCull, charmPose);
    }

    public static List<BeltModel> create(ModelPart part, Function<Identifier, RenderType> renderType, CharmPose charmPose) {
        List<BeltModel> models = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            models.add(new BeltModel(part, renderType, charmPose, i));
        }
        return models;
    }

    @Override
    public void setupAnim(HumanoidRenderState humanoidRenderState) {
        super.setupAnim(humanoidRenderState);
        setCharmPosition(slot);
    }

    protected void setCharmPosition(int slot) {
        float xOffset = slot % 2 == 0 ? charmPose.xOffset() : -charmPose.xOffset();
        float zOffset = slot % 4 < 2 ? charmPose.zOffset() : -charmPose.zOffset();
        charm.setPos(xOffset, 9, zOffset);

        float rotation = slot % 4 < 2 ? 0 : (float) -Math.PI;
        rotation += slot % 2 == 0 ^ slot % 4 >= 2 ? charmPose.rotation() : -charmPose.rotation();
        charm.yRot = rotation;
    }
}
