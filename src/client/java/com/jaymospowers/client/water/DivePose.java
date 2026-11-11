package com.jaymospowers.client.water;

import com.jaymospowers.water.Dives;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The pose a player's body takes when they hit the water - cannonball tucked into a ball, belly flop flat on
 * impact with a wince after, clean dive arms forward over the head - and eases out under water. Worked out from the
 * synced entry ({@link Dives#ENTRY}) so everyone in view sees the same pose, not only the player themselves.
 */
public final class DivePose {
    /** How long the entry pose shows (ticks), easing in on impact and easing out under water. */
    static final int DURATION = 40;

    /** {@code entry}: 1 flop / 2 dive / 3 cannonball (ordinal of {@link Dives.Entry} plus one); {@code e}: how far
     *  into the pose (0-1); {@code time}: client tick it began. */
    public record Dive(int entry, float strength, float time) {
    }

    public static final RenderStateDataKey<Dive> KEY = RenderStateDataKey.create(() -> "jaymospowers:dive");

    private DivePose() {
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        if (!(entity instanceof Player player) || !player.isInWater()) {
            state.setData(KEY, null);
            return;
        }
        Long packed = player.getAttached(Dives.ENTRY);
        if (packed == null || packed == 0L) {
            state.setData(KEY, null);
            return;
        }
        int entry = (int) (packed >>> 56) - 1;
        long start = packed & 0x00FF_FFFF_FFFF_FFFFL;
        float time = player.tickCount + partialTicks;
        float age = time - start;
        if (age < 0.0F || age >= DURATION) {
            state.setData(KEY, null);
            return;
        }
        float s = age / (float) DURATION;
        float ease = s * s * (3.0F - 2.0F * s);
        float strength = ease * (1.0F - s) * 2.0F;
        state.setData(KEY, new Dive(entry, strength, time));
    }

    static @Nullable Dive dive(LivingEntityRenderState state) {
        return state.deathTime > 0.0F ? null : state.getData(KEY);
    }

    /** After the body faces its way: tucked, streamlined or laid flat, about the middle of the body. */
    public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
        Dive dive = dive(state);
        if (dive == null) {
            return;
        }
        float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
        poseStack.translate(0.0F, middle, 0.0F);
        switch (dive.entry()) {
            case 1 -> poseStack.rotateDegrees(Axis.XP, -70.0F * dive.strength()); // belly flop: almost flat
            case 2 -> poseStack.rotateDegrees(Axis.XP, -20.0F * dive.strength()); // clean dive: head first
            case 3 -> poseStack.rotateDegrees(Axis.XP, -12.0F * dive.strength()); // cannonball: tucked forward
            default -> {
            }
        }
        poseStack.translate(0.0F, -middle, 0.0F);
    }

    /** After the model animates: the arms and legs of each entry. */
    public static void applyModel(Model<?> model, Object state) {
        if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
            return;
        }
        Dive dive = dive(living);
        if (dive == null) {
            return;
        }
        float e = dive.strength();
        float t = dive.time();
        switch (dive.entry()) {
            case 1 -> {
                // belly flop: flat and spread at impact, a short wince after
                set(h.rightArm, -0.2F, 0.0F, 1.1F, e);
                set(h.leftArm, -0.2F, 0.0F, -1.1F, e);
                set(h.rightLeg, 0.15F, 0.0F, -1.0F, e);
                set(h.leftLeg, 0.15F, 0.0F, 1.0F, e);
                h.head.xRot = Mth.lerp(e * 1.5F, h.head.xRot, 0.3F);
                h.hat.xRot = h.head.xRot;
            }
            case 2 -> {
                // clean dive: arms forward over the head, body straight, head first
                set(h.rightArm, -1.1F, 0.0F, 0.15F, e);
                set(h.leftArm, -1.1F, 0.0F, -0.15F, e);
                set(h.rightLeg, 0.02F, 0.0F, 0.02F, e);
                set(h.leftLeg, 0.02F, 0.0F, -0.02F, e);
                h.head.xRot = Mth.lerp(e, h.head.xRot, -0.5F);
                h.hat.xRot = h.head.xRot;
            }
            case 3 -> {
                // cannonball: knees hugged to the chest, tucked into a ball
                set(h.rightArm, -0.9F, 0.0F, 0.15F, e);
                set(h.leftArm, -0.9F, 0.0F, -0.15F, e);
                set(h.rightLeg, -1.3F, 0.0F, 0.05F, e);
                set(h.leftLeg, -1.3F, 0.0F, -0.05F, e);
            }
            default -> {
            }
        }
        h.hat.xRot = h.head.xRot;
    }

    private static void set(ModelPart part, float x, float y, float z, float amount) {
        part.xRot = Mth.lerp(amount, part.xRot, x);
        part.yRot = Mth.lerp(amount, part.yRot, y);
        part.zRot = Mth.lerp(amount, part.zRot, z);
    }
}
