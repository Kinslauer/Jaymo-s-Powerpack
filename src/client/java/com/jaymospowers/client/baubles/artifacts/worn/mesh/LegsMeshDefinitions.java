// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.mesh;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;

import java.util.Set;

public final class LegsMeshDefinitions {

    private LegsMeshDefinitions() { }

    public static MeshDefinition createLegs(float delta, CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        CubeDeformation deformation = new CubeDeformation(delta);
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);

        mesh.getRoot().addOrReplaceChild(
                "left_leg",
                leftLeg.texOffs(0, 0)
                        .addBox(-2, 0, -2, 4, 12, 4, deformation),
                PartPose.offset(1.9F, 12, 0)
        );
        mesh.getRoot().addOrReplaceChild(
                "right_leg",
                rightLeg.texOffs(16, 0)
                        .addBox(-2, 0, -2, 4, 12, 4, deformation),
                PartPose.offset(-1.9F, 12, 0)
        );

        mesh.getRoot().retainExactParts(Set.of("left_leg", "right_leg"));

        return mesh;
    }

    public static MeshDefinition createSleevedLegs(float delta, CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        CubeDeformation deformation = new CubeDeformation(delta + 0.25F);

        // sleeves
        leftLeg.texOffs(0, 16);
        leftLeg.addBox(-2, 0, -2, 4, 12, 4, deformation);
        rightLeg.texOffs(16, 16);
        rightLeg.addBox(-2, 0, -2, 4, 12, 4, deformation);

        return createLegs(delta, leftLeg, rightLeg);
    }

    public static MeshDefinition createBoots(float delta) {
        return createBoots(delta, CubeListBuilder.create(), CubeListBuilder.create());
    }

    public static MeshDefinition createBoots(float delta, CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        CubeDeformation deformation = new CubeDeformation(delta, delta / 4, delta / 4);

        // shoe tips
        leftLeg.texOffs(0, 16);
        leftLeg.addBox(-2, 12 - 3 + delta * 3 / 4, -3F - delta * 5 / 4, 4, 3, 1, deformation);
        rightLeg.texOffs(16, 16);
        rightLeg.addBox(-2, 12 - 3 + delta * 3 / 4, -3F - delta * 5 / 4, 4, 3, 1, deformation);

        return createLegs(delta, leftLeg, rightLeg);
    }

    public static MeshDefinition createSlippers(CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        leftLeg.texOffs(32, 0);
        leftLeg.addBox(-2.5F, 8.51F, -7.01F, 5, 4, 5);
        rightLeg.texOffs(32, 16);
        rightLeg.addBox(-2.5F, 8.51F, -7, 5, 4, 5);

        return createSleevedLegs(0.51F, leftLeg, rightLeg);
    }

    public static MeshDefinition createAquaDashers(float delta) {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        CubeDeformation deformation = new CubeDeformation(0, delta, delta);

        // wings
        leftLeg.texOffs(0, 16);
        leftLeg.addBox(2 + delta, 0, -2 + 3 + delta * 3 / 2, 0, 12, 4, deformation);
        rightLeg.texOffs(16, 16);
        rightLeg.addBox(-2 - delta, 0, -2 + 3 + delta * 3 / 2, 0, 12, 4, deformation);

        return createBoots(delta, leftLeg, rightLeg);
    }

    public static MeshDefinition createBunnyHoppers() {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        // noses
        leftLeg.texOffs(32, 9);
        leftLeg.addBox(-0.5F, 10, -7.5F, 1, 1, 1);
        rightLeg.texOffs(32, 25);
        rightLeg.addBox(-0.5F, 10, -7.5F, 1, 1, 1);

        // tails
        leftLeg.texOffs(52, 6);
        leftLeg.addBox(-1, 9, 2, 2, 2, 2);
        rightLeg.texOffs(52, 22);
        rightLeg.addBox(-1, 9, 2, 2, 2, 2);

        MeshDefinition mesh = createSlippers(leftLeg, rightLeg);

        mesh.getRoot().getChild("left_leg").addOrReplaceChild(
                "left_ear",
                CubeListBuilder.create()
                        .texOffs(52, 0)
                        .addBox(-3.15F, 3.51F, -3.01F, 2, 5, 1),
                PartPose.rotation(0, -0.2617994F, 0)
        );
        mesh.getRoot().getChild("right_leg").addOrReplaceChild(
                "left_ear",
                CubeListBuilder.create()
                        .texOffs(52, 16)
                        .addBox(-3.15F, 3.51F, -3, 2, 5, 1),
                PartPose.rotation(0, -0.2617994F, 0)
        );
        mesh.getRoot().getChild("left_leg").addOrReplaceChild(
                "right_ear",
                CubeListBuilder.create()
                        .texOffs(58, 0)
                        .addBox(1.15F, 3.51F, -3.01F, 2, 5, 1),
                PartPose.rotation(0, 0.2617994F, 0)
        );
        mesh.getRoot().getChild("right_leg").addOrReplaceChild(
                "right_ear",
                CubeListBuilder.create()
                        .texOffs(58, 16)
                        .addBox(1.15F, 3.51F, -3, 2, 5, 1),
                PartPose.rotation(0, 0.2617994F, 0)
        );

        return mesh;
    }

    public static MeshDefinition createFlippers() {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        leftLeg.texOffs(0, 16);
        leftLeg.addBox(-2, 11.5F, -16, 9, 0, 20);
        rightLeg.texOffs(0, 36);
        rightLeg.addBox(-7, 11.5F, -16, 9, 0, 20);

        return createLegs(0.5F, leftLeg, rightLeg);
    }

    public static MeshDefinition createKittySlippers() {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        // ears
        leftLeg.texOffs(32, 9);
        leftLeg.addBox(-2, 7.51F, -4, 1, 1, 2);
        rightLeg.texOffs(32, 25);
        rightLeg.addBox(-2, 7.51F, -4, 1, 1, 2);
        leftLeg.texOffs(38, 9);
        leftLeg.addBox(1, 7.51F, -4, 1, 1, 2);
        rightLeg.texOffs(38, 25);
        rightLeg.addBox(1, 7.51F, -4, 1, 1, 2);

        // noses
        leftLeg.texOffs(44, 9);
        leftLeg.addBox(-1.5F, 10.51F, -8, 3, 2, 1);
        rightLeg.texOffs(44, 25);
        rightLeg.addBox(-1.5F, 10.51F, -8, 3, 2, 1);

        return createSlippers(leftLeg, rightLeg);
    }

    public static MeshDefinition createSnowshoes() {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        leftLeg.texOffs(0, 16);
        leftLeg.addBox(-2.5F, 11.5F, -10, 10, 0, 22);
        rightLeg.texOffs(0, 16 + 22);
        rightLeg.addBox(-10 + 2.5F, 11.5F, -10, 10, 0, 22);

        return createLegs(0.5F, leftLeg, rightLeg);
    }

    public static MeshDefinition createSteadfastSpikes() {
        CubeListBuilder leftLeg = CubeListBuilder.create();
        CubeListBuilder rightLeg = CubeListBuilder.create();

        // claws
        leftLeg.texOffs(32, 0);
        leftLeg.addBox(-1.5F, 9, -7, 1, 3, 5);
        rightLeg.texOffs(43, 0);
        rightLeg.addBox(-1.5F, 9, -7, 1, 3, 5);
        leftLeg.texOffs(32, 8);
        leftLeg.addBox(0.5F, 9, -7, 1, 3, 5);
        rightLeg.texOffs(43, 8);
        rightLeg.addBox(0.5F, 9, -7, 1, 3, 5);

        return createSleevedLegs(0.5F, leftLeg, rightLeg);
    }
}
