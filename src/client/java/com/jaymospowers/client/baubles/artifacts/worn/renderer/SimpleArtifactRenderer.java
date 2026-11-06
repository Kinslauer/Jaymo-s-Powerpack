// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class SimpleArtifactRenderer extends ArtifactRenderer {

    private final Identifier texture;
    private final @Nullable Identifier glowTexture;
    private final HumanoidModel<HumanoidRenderState> model;

    public SimpleArtifactRenderer(Identifier texture, @Nullable Identifier glowTexture, HumanoidModel<HumanoidRenderState> model) {
        this.texture = texture;
        this.glowTexture = glowTexture;
        this.model = model;
    }

    public static ArtifactRenderer create(String name, HumanoidModel<HumanoidRenderState> model) {
        return new SimpleArtifactRenderer(getTextureId(name), null, model);
    }

    public static ArtifactRenderer createGlowing(String name, HumanoidModel<HumanoidRenderState> model) {
        return new SimpleArtifactRenderer(getTextureId(name, name), getTextureId(name, "%s_overlay".formatted(name)), model);
    }

    @Override
    protected Identifier getTexture(HumanoidRenderState renderState) {
        return texture;
    }

    @Override
    protected @Nullable Identifier getFullBrightOverlayTexture(HumanoidRenderState renderState) {
        return glowTexture;
    }

    @Override
    protected HumanoidModel<HumanoidRenderState> getModel(HumanoidRenderState renderState, int slotIndex) {
        return model;
    }
}
