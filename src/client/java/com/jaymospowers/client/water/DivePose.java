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
 * (Andy, 2026-11-22) Diving by the Dodge key ({@link Dives#DIVING}): in the air the body turns head first along the way
 * it's going over {@link #TURN_TICKS}, arms forward over the head, and goes into the water so; the entry's turn starts
 * from there, upright again as they go under ({@link #UNTURN_TICKS}).
 * <p>
 * The pose a player's body takes when they hit the water - cannonball tucked into a ball, belly flop flat on
 * impact with a wince after, clean dive arms forward over the head - and eases out under water. Worked out from the
 * synced entry ({@link Dives#ENTRY}) so everyone in view sees the same pose, not only the player themselves.
 */
public final class DivePose {
    /** How long the entry pose shows (ticks), easing in on impact and easing out under water. */
    static final int DURATION = 40;
    /** Diving: ticks to turn head first in the air; ticks under water to come back from head down. */
    static final float TURN_TICKS = 7.0F, UNTURN_TICKS = 12.0F;
    /** Diving, head down this far into the water at the most (degrees). */
    static final float HEAD_DOWN = 160.0F;
    /** Our own dive from the Dodge key, drawn at once (game time; Long.MIN_VALUE: none): the server's copy comes a moment later. */
    public static long localDive = Long.MIN_VALUE;

    /** In the air diving: how far the body is turned forward, head first (degrees). */
    public record AirDive(float tilt, float arms) {
    }

    public static final RenderStateDataKey<AirDive> AIR = RenderStateDataKey.create(() -> "jaymospowers:air_dive");

    /** {@code entry}: 1 flop / 2 dive / 3 cannonball (ordinal of {@link Dives.Entry} plus one); {@code e}: how far
     *  into the pose (0-1); {@code time}: client tick it began. */
    public record Dive(int entry, float strength, float time, float age) {
    }

    public static final RenderStateDataKey<Dive> KEY = RenderStateDataKey.create(() -> "jaymospowers:dive");

    private DivePose() {
    }

    /** Whether {@code entity} is a player diving head first through the air by the Dodge key. */
    public static boolean airDiving(LivingEntity entity) {
        return entity instanceof Player player && !player.isInWater() && !player.onGround() && diveStart(player) != Long.MIN_VALUE;
    }

    private static long diveStart(Player player) {
        Long at = player.getAttached(Dives.DIVING);
        if (at != null) {
            return at;
        }
        return player == net.minecraft.client.Minecraft.getInstance().player ? localDive : Long.MIN_VALUE;
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        state.setData(AIR, null);
        if (entity instanceof Player player && airDiving(player)) {
            // head first along the way it's going: level out ahead is 90 degrees over, straight down 180
            double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
            float steep = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
            float aim = Math.min(HEAD_DOWN, 90.0F + Mth.clamp(steep, -20.0F, 90.0F));
            float t = Mth.clamp((player.level().getGameTime() - diveStart(player) + partialTicks) / TURN_TICKS, 0.0F, 1.0F);
            float e = t * t * (3.0F - 2.0F * t);
            state.setData(AIR, new AirDive(aim * e, e));
        }
        if (entity instanceof Player player && (player.onGround() || player.isInWater())) {
            if (player == net.minecraft.client.Minecraft.getInstance().player) {
                localDive = Long.MIN_VALUE;
            }
        }
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
        // The server stamps the entry with the level's game time, so compare against that (not the player's age).
        long now = player.level().getGameTime();
        float time = now + partialTicks;
        float age = (now - start) + partialTicks;
        if (age < 0.0F || age >= DURATION) {
            state.setData(KEY, null);
            return;
        }
        float s = age / (float) DURATION;
        float ease = s * s * (3.0F - 2.0F * s);
        float strength = ease * (1.0F - s) * 2.0F;
        state.setData(KEY, new Dive(entry, strength, time, age));
    }

    static @Nullable Dive dive(LivingEntityRenderState state) {
        return state.deathTime > 0.0F ? null : state.getData(KEY);
    }

    /** After the body faces its way: tucked, streamlined or laid flat, about the middle of the body. */
    public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
        float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
        AirDive air = state.deathTime > 0.0F ? null : state.getData(AIR);
        if (air != null) {
            poseStack.translate(0.0F, middle, 0.0F);
            poseStack.rotateDegrees(Axis.XP, -air.tilt());
            poseStack.translate(0.0F, -middle, 0.0F);
            return;
        }
        Dive dive = dive(state);
        if (dive == null) {
            return;
        }
        poseStack.translate(0.0F, middle, 0.0F);
        switch (dive.entry()) {
            case 1 -> poseStack.rotateDegrees(Axis.XP, -70.0F * dive.strength()); // belly flop: almost flat
            // clean dive: head first, in head down from the air and coming back up as they go under
            case 2 -> poseStack.rotateDegrees(Axis.XP, -Math.max(20.0F * dive.strength(), HEAD_DOWN * (1.0F - Mth.clamp(dive.age() / UNTURN_TICKS, 0.0F, 1.0F)))); 
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
        AirDive air = living.deathTime > 0.0F ? null : living.getData(AIR);
        if (air != null) {
            cleanDive(h, air.arms());
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
            case 2 -> cleanDive(h, e);
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

    /**
     * The clean dive's limbs, {@code e} of the way in (0-1): arms forward over the head, body straight, head first. The
     * breach leap ({@code RunMovesPose}) takes the same pose as it leaves the water.
     */
    public static void cleanDive(HumanoidModel<?> h, float e) {
        set(h.rightArm, -1.1F, 0.0F, 0.15F, e);
        set(h.leftArm, -1.1F, 0.0F, -0.15F, e);
        set(h.rightLeg, 0.02F, 0.0F, 0.02F, e);
        set(h.leftLeg, 0.02F, 0.0F, -0.02F, e);
        h.head.xRot = Mth.lerp(e, h.head.xRot, -0.5F);
        h.hat.xRot = h.head.xRot;
    }

    private static void set(ModelPart part, float x, float y, float z, float amount) {
        part.xRot = Mth.lerp(amount, part.xRot, x);
        part.yRot = Mth.lerp(amount, part.yRot, y);
        part.zRot = Mth.lerp(amount, part.zRot, z);
    }
}
