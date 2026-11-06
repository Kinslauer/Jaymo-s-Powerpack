// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;

public class BeltArtifactRenderer extends ArtifactRenderer {

    private final Identifier texture;
    private final List<? extends HumanoidModel<HumanoidRenderState>> models;

    public BeltArtifactRenderer(Identifier texture, List<? extends HumanoidModel<HumanoidRenderState>> models) {
        this.texture = texture;
        this.models = models;
    }

    public static BeltArtifactRenderer create(String name, List<? extends  HumanoidModel<HumanoidRenderState>> models) {
        return new BeltArtifactRenderer(getTextureId(name), models);
    }

    @Override
    protected HumanoidModel<HumanoidRenderState> getModel(HumanoidRenderState renderState, int slotIndex) {
        return models.get(slotIndex % models.size());
    }

    @Override
    protected Identifier getTexture(HumanoidRenderState renderState) {
        return texture;
    }
}
