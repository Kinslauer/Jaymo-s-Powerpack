// Ported from Artifacts by Wouter Pauwels (MIT License, see textures/entity/wearable/artifacts/LICENSE.txt).
package com.jaymospowers.client.baubles.artifacts.worn.model;

public record CharmPose(float xOffset, float zOffset, float rotation) {

    public static final CharmPose OBSIDIAN_SKULL = new CharmPose(4.5F, -4F, -0.5F);
    public static final CharmPose ANTIDOTE_VESSEL = new CharmPose(4, -3, -0.5F);
    public static final CharmPose UNIVERSAL_ATTRACTOR = new CharmPose(2.5F, -3, 0);
    public static final CharmPose CLOUD_IN_A_BOTTLE = new CharmPose(3, -3, -0.5F);
    public static final CharmPose CRYSTAL_HEART = new CharmPose(2.5F, -3.01F, 0);
    public static final CharmPose CHORUS_TOTEM = new CharmPose(4, -3, -0.5F);
    public static final CharmPose WARP_DRIVE = new CharmPose(3.5F, -3, -0.3F);

}
