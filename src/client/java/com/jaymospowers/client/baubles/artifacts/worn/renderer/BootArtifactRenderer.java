// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public class BootArtifactRenderer extends ArtifactRenderer {

    private final Identifier texture;
    private final HumanoidModel<HumanoidRenderState> model;
    private final HumanoidModel<HumanoidRenderState> armorModel;

    public BootArtifactRenderer(String name, Function<Boolean, HumanoidModel<HumanoidRenderState>> model) {
        this.texture = ArtifactRenderer.getTextureId(name);
        this.model = model.apply(false);
        this.armorModel = model.apply(true);
    }

    @Override
    protected HumanoidModel<HumanoidRenderState> getModel(HumanoidRenderState renderState, int slotIndex) {
        if (renderState == null || renderState.feetEquipment.isEmpty()) {
            return model;
        }
        return armorModel;
    }

    @Override
    protected Identifier getTexture(HumanoidRenderState renderState) {
        return texture;
    }
}
