// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.mesh;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;

import java.util.Set;

public final class BeltMeshDefinitions {

    private BeltMeshDefinitions() { }

    private static MeshDefinition createBelt(CubeListBuilder charm) {
        CubeDeformation deformation = new CubeDeformation(0.5F);
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);

        mesh.getRoot().addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4, 0, -2, 8, 12, 4, deformation),
                PartPose.ZERO
        );

        mesh.getRoot().getChild("body").addOrReplaceChild(
                "charm",
                charm,
                PartPose.ZERO
        );

        mesh.getRoot().retainPartsAndChildren(Set.of("body"));

        return mesh;
    }

    public static MeshDefinition createAntidoteVessel() {
        CubeListBuilder charm = CubeListBuilder.create();

        // jar
        charm.texOffs(0, 16);
        charm.addBox(-2, 0, -2, 4, 6, 4);

        // lid
        charm.texOffs(0, 26);
        charm.addBox(-1, -1, -1, 2, 1, 2);

        return createBelt(charm);
    }

    public static MeshDefinition createCloudInABottle() {
        CubeListBuilder charm = CubeListBuilder.create();

        // jar
        charm.texOffs(0, 16);
        charm.addBox(-2, 0, -2, 4, 5, 4);

        // lid
        charm.texOffs(0, 25);
        charm.addBox(-1, -1, -1, 2, 1, 2);

        MeshDefinition mesh = createBelt(charm);

        mesh.getRoot().getChild("body").getChild("charm").addOrReplaceChild(
                "cloud",
                CubeListBuilder.create()
                        .texOffs(8, 25) // cloud
                        .addBox(-1, 1.5F, -1, 2, 2, 2),
                PartPose.ZERO
        );

        return mesh;
    }

    public static MeshDefinition createCrystalHeart() {
        CubeListBuilder charm = CubeListBuilder.create();

        charm.texOffs(0, 16);
        charm.addBox(-2.5F, 0, 0, 2, 3, 1);
        charm.texOffs(6, 16);
        charm.addBox(0.5F, 0, 0, 2, 3, 1);
        charm.texOffs(0, 20);
        charm.addBox(-0.5F, 1, 0, 1, 4, 1);
        charm.texOffs(4, 20);
        charm.addBox(-1.5F, 3, 0, 1, 1, 1);
        charm.texOffs(8, 20);
        charm.addBox(0.5F, 3, 0, 1, 1, 1);

        return createBelt(charm);
    }

    public static MeshDefinition createHeliumFlamingo() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);

        mesh.getRoot().addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(16, 36).addBox(-1, 1, -14, 2, 3, 5)
                        .texOffs(0, 18).addBox(4, 9, -7, 4, 4, 14)
                        .texOffs(0, 0).addBox(-8, 9, -7, 4, 4, 14)
                        .texOffs(36, 0).addBox(-4, 9, 3, 8, 4, 4)
                        .texOffs(36, 8).addBox(-4, 9, -7, 8, 4, 4)
                        .texOffs(0, 36).addBox(-2, 1, -9, 4, 11, 4),
                PartPose.ZERO
        );
        mesh.getRoot().retainExactParts(Set.of("body"));

        return mesh;
    }

    public static MeshDefinition createObsidianSkull() {
        CubeListBuilder charm = CubeListBuilder.create();

        // cranium
        charm.texOffs(0, 16);
        charm.addBox(-2.5F, 0, 0, 5, 3, 4);

        // teeth
        charm.texOffs(18, 16);
        charm.addBox(-1.5F, 3, 0, 1, 1, 2);
        charm.texOffs(18, 19);
        charm.addBox(0.5F, 3, 0, 1, 1, 2);

        return createBelt(charm);
    }

    public static MeshDefinition createUniversalAttractor() {
        CubeListBuilder charm = CubeListBuilder.create();

        charm.texOffs(0, 16);
        charm.addBox(-2.5F, 0, 0, 5, 2, 1);
        charm.texOffs(0, 19);
        charm.addBox(-2.5F, 2, 0, 2, 4, 1);
        charm.texOffs(6, 19);
        charm.addBox(0.5F, 2, 0, 2, 4, 1);

        return createBelt(charm);
    }

    public static MeshDefinition createChorusTotem() {
        CubeListBuilder charm = CubeListBuilder.create();

        charm.texOffs(0, 16);
        charm.addBox(-1.5F, -1, -1, 3, 7, 2);
        charm.texOffs(10, 16);
        charm.addBox(-3.5F, 0, 1, 7, 4, 0);
        charm.texOffs(0, 25);
        charm.addBox(-2.5F, 3, -2, 5, 2, 2);
        charm.texOffs(10, 20);
        charm.addBox(-1, 1, -1.5F, 2, 2, 0);

        return createBelt(charm);
    }

    public static MeshDefinition createWarpDrive() {
        CubeListBuilder charm = CubeListBuilder.create();

        charm.texOffs(0, 16);
        charm.addBox(-2.5F, 0, -2, 5, 4, 4);
        charm.texOffs(0, 24);
        charm.addBox(-3, 1, -1, 1, 2, 2);
        charm.texOffs(6, 24);
        charm.addBox(2, 1, -1, 1, 2, 2);

        // flash
        charm.texOffs(0, 28);
        charm.addBox(-2.5F, 1, 0, 5, 2, 0);
        charm.texOffs(0, 30);
        charm.addBox(-2.5F, 2, -1, 5, 0, 2);

        return createBelt(charm);
    }
}
