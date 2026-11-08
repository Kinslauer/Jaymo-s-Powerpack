package com.jaymospowers.client.fishing;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * (0.21.62) The skipjack tuna's model, our own: a deep, torpedo body with a pointed head, a narrow tail stalk and a tall
 * crescent tail, a first dorsal fin and two small side fins. The stalk and tail beat as it swims.
 */
public final class SkipjackModel extends EntityModel<LivingEntityRenderState> {
    /** How far the tail swings (radians) and how fast (per tick). */
    static final float TAIL_SWING = 0.35F, TAIL_SPEED = 0.7F;

    private final ModelPart stalk;
    private final ModelPart tail;

    SkipjackModel(ModelPart root) {
        super(root);
        this.stalk = root.getChild("stalk");
        this.tail = this.stalk.getChild("tail");
    }

    static SkipjackModel create() {
        return new SkipjackModel(layer().bakeRoot());
    }

    static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float y = 21.5F;
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, 0.0F, 3.0F, 5.0F, 8.0F), PartPose.offset(0.0F, y, -2.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 13).addBox(-1.5F, -2.0F, -4.0F, 3.0F, 4.0F, 4.0F), PartPose.offset(0.0F, y, -2.0F));
        PartDefinition stalk = root.addOrReplaceChild("stalk", CubeListBuilder.create().texOffs(14, 13).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F),
            PartPose.offset(0.0F, y, 6.0F));
        stalk.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(22, 0).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 2.5F));
        root.addOrReplaceChild("top_fin", CubeListBuilder.create().texOffs(22, 10).addBox(0.0F, -2.0F, 0.0F, 0.0F, 2.0F, 4.0F), PartPose.offset(0.0F, y - 2.5F, -1.0F));
        root.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(0, 21).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 0.0F, 3.0F),
            PartPose.offsetAndRotation(-1.5F, y + 0.5F, -1.0F, 0.0F, 0.0F, (float) (-Math.PI / 5)));
        root.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(10, 21).addBox(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 3.0F),
            PartPose.offsetAndRotation(1.5F, y + 0.5F, -1.0F, 0.0F, 0.0F, (float) (Math.PI / 5)));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float amplitude = state.isInWater ? 1.0F : 1.6F;
        this.stalk.yRot = -amplitude * TAIL_SWING * Mth.sin(TAIL_SPEED * state.ageInTicks);
        this.tail.yRot = -amplitude * TAIL_SWING * 0.6F * Mth.sin(TAIL_SPEED * state.ageInTicks - 0.6F);
    }
}
