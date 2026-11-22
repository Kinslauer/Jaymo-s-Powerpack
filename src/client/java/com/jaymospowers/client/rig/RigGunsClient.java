package com.jaymospowers.client.rig;

import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigFrame;
import com.jaymospowers.rig.RigGuns;
import com.jaymospowers.rig.RigMoves;
import com.jaymospowers.rig.RigUnibeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side state and poses for the Rig's gun actives ({@link RigGuns}).
 */
public final class RigGunsClient {

    /** Palm blast charge amount (0..1) for the limb glow. */
    public static float palmCharge = 0.0F;
    /** Gatling heat (0..1) for the HUD heat bar. */
    public static float gatlingHeat = 0.0F;
    /** Railgun charge amount (0..1). */
    public static float railgunCharge = 0.0F;
    /** Sonic cannon firing amount (0..1). */
    public static float sonicAmount = 0.0F;
    /** Twin beam charge/beam amount (0..1). */
    public static float twinAmount = 0.0F;
    /** Siege mode active (for pose: rooted, cannon unfolded). */
    public static boolean siegeActive = false;

    private static final Map<Integer, Float> CHARGE_TRACK = new HashMap<>();
    private static final Map<Integer, Float> HEAT_TRACK = new HashMap<>();
    private static final Map<Integer, Float> RAIL_TRACK = new HashMap<>();
    private static final Map<Integer, Float> SONIC_TRACK = new HashMap<>();
    private static final Map<Integer, Float> TWIN_TRACK = new HashMap<>();

    private RigGunsClient() {}

    /** Call from {@link RigClient#tick} to update client-side charge/heat amounts. */
    public static void tick(Minecraft minecraft, net.minecraft.client.player.LocalPlayer player) {
        if (player == null) return;
        int id = player.getId();
        float dt = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // Palm charge
        float targetCharge = RigMoves.get(player) != null && RigMoves.get(player).kind() == RigMoves.PALM_CHARGE ? 1.0F : 0.0F;
        float charge = CHARGE_TRACK.getOrDefault(id, 0.0F);
        charge += (targetCharge - charge) * 0.2F * (dt > 0 ? 1.0F : 0.0F);
        CHARGE_TRACK.put(id, charge);
        palmCharge = charge;
        // Gatling heat
        float heat = HEAT_TRACK.getOrDefault(id, 0.0F);
        heat += (gatlingHeat - heat) * 0.15F;
        HEAT_TRACK.put(id, heat);
        gatlingHeat = heat;
        // Railgun charge
        float rail = RAIL_TRACK.getOrDefault(id, 0.0F);
        rail += (railgunCharge - rail) * 0.2F;
        RAIL_TRACK.put(id, rail);
        railgunCharge = rail;
        // Sonic amount
        float sonic = SONIC_TRACK.getOrDefault(id, 0.0F);
        sonic += (sonicAmount - sonic) * 0.25F;
        SONIC_TRACK.put(id, sonic);
        sonicAmount = sonic;
        // Twin amount
        float twin = TWIN_TRACK.getOrDefault(id, 0.0F);
        twin += (twinAmount - twin) * 0.2F;
        TWIN_TRACK.put(id, twin);
        twinAmount = twin;
    }

    /** Apply gun-specific model transforms (called from {@link RigPose#applyModel}). */
    public static void applyModel(Model<?> model, AvatarRenderState state) {
        if (!(model instanceof HumanoidModel<?> h)) return;
        float time = state.getData(RigPose.KEY) != null ? state.getData(RigPose.KEY).time() : 0.0F;
        boolean leftMain = state.mainArm == HumanoidArm.LEFT;
        ModelPart main = leftMain ? h.leftArm : h.rightArm;
        ModelPart off = leftMain ? h.rightArm : h.leftArm;
        float side = leftMain ? -1.0F : 1.0F;
        float recoil = state.getData(RigPose.KEY) != null ? state.getData(RigPose.KEY).recoil() : 1.0F;
        float still = state.getData(RigPose.KEY) != null ? state.getData(RigPose.KEY).still() : 1.0F;

        if (siegeActive) {
            h.body.xRot += 0.15F;
            h.head.xRot -= 0.1F;
            set(main, 0.3F, 0.0F, 0.2F * side, 0.8F);
            set(off, 0.3F, 0.0F, -0.2F * side, 0.8F);
            h.rightLeg.xRot -= 0.2F;
            h.leftLeg.xRot -= 0.2F;
        }
    }

    private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
        part.xRot = Mth.lerp(amount, part.xRot, xRot);
        part.yRot = Mth.lerp(amount, part.yRot, yRot);
        part.zRot = Mth.lerp(amount, part.zRot, zRot);
    }
}