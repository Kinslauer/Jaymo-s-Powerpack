// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.function.Function;

public record ArmsModelSet<M>(M leftArmWide, M rightArmWide, M leftArmSlim, M rightArmSlim) {

    public M get(HumanoidArm arm, PlayerModelType modelType) {
        if (modelType == PlayerModelType.WIDE) {
            return arm == HumanoidArm.LEFT ? leftArmWide : rightArmWide;
        } else {
            return arm == HumanoidArm.LEFT ? leftArmSlim : rightArmSlim;
        }
    }

    public <T> ArmsModelSet<T> map(Function<? super M, ? extends T> f) {
        return new ArmsModelSet<>(
                f.apply(this.leftArmWide), f.apply(this.rightArmWide),
                f.apply(this.leftArmSlim), f.apply(this.rightArmSlim)
        );
    }

    public static ArmsModelSet<HumanoidModel<HumanoidRenderState>> bake(ArmsModelSet<ModelLayerLocation> layerLocations) {
        return bake(layerLocations, root -> new HumanoidModel<>(root, RenderTypes::armorCutoutNoCull));
    }

    public static <M> ArmsModelSet<M> bake(ArmsModelSet<ModelLayerLocation> layerLocations, Function<ModelPart, M> modelFactory) {
        return layerLocations
                .map(Minecraft.getInstance().getEntityModels()::bakeLayer)
                .map(modelFactory);
    }
}
