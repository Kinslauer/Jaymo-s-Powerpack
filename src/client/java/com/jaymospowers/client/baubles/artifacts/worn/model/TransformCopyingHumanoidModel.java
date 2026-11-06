// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public final class TransformCopyingHumanoidModel<S extends HumanoidRenderState> extends Model<S> {

    private final HumanoidModel<? super S> source;
    private final HumanoidModel<HumanoidRenderState> delegate;

    public static <S extends HumanoidRenderState> TransformCopyingHumanoidModel<S> create(HumanoidModel<? super S> source, HumanoidModel<HumanoidRenderState> delegate) {
        return new TransformCopyingHumanoidModel<>(source, delegate);
    }

    private TransformCopyingHumanoidModel(HumanoidModel<? super S> source, HumanoidModel<HumanoidRenderState> delegate) {
        super(delegate.root(), delegate::renderType);
        this.source = source;
        this.delegate = delegate;
    }

    @Override
    public void setupAnim(S renderState) {
        // reset pose & setup animations
        delegate.setupAnim(renderState);
        // setup source model pose
        source.setupAnim(renderState);
        // copy transforms to delegate
        copyTransforms(delegate, source);
    }

    private static void copyTransforms(HumanoidModel<?> delegate, HumanoidModel<?> source) {
        copyTransforms(delegate.root(), source.root());
        copyTransforms(delegate.head, source.head);
        copyTransforms(delegate.hat, source.hat);
        copyTransforms(delegate.body, source.body);
        copyTransforms(delegate.leftArm, source.leftArm);
        copyTransforms(delegate.rightArm, source.rightArm);
        copyTransforms(delegate.leftLeg, source.leftLeg);
        copyTransforms(delegate.rightLeg, source.rightLeg);
    }

    private static void copyTransforms(ModelPart delegate, ModelPart source) {
        delegate.x = source.x;
        delegate.y = source.y;
        delegate.z = source.z;
        delegate.xRot = source.xRot;
        delegate.yRot = source.yRot;
        delegate.zRot = source.zRot;
        // multiply to retain the necklace default part pose
        // should be fine as long as setupAnim on the delegate doesn't change part scale anywhere else
        delegate.xScale *= source.xScale;
        delegate.yScale *= source.yScale;
        delegate.zScale *= source.zScale;
    }
}
