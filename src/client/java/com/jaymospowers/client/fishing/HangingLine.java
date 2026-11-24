package com.jaymospowers.client.fishing;

import com.jaymospowers.fishing.FishFight;
import com.jaymospowers.fishing.FishingReal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (Reference Mods patch 12, F6: Enchanted Fishing Line's idea, our own code) A fishing line that hangs like rope. Slack, it
 * sags between the rod and the hook (into the water, if that's under it) and lies over whatever it crosses, branches,
 * rails and banks; drawn tight by a fish, it straightens. Points are worked out with the hook's render state
 * ({@link com.jaymospowers.client.fishing.mixin.FishingHookRendererMixin}), in the hook's own render space.
 */
public final class HangingLine {
    /** Pieces the line is drawn in (vanilla's 16). */
    public static final int SEGMENTS = 16;
    /** How far a slack line sags at its middle, per block of line, and at most (blocks). */
    static final float SAG_PER_BLOCK = 0.12F, SAG_MOST = 3.0F;
    /** How slack a line is resting with the hook in the water or on the ground, and flying out on a cast (1: all slack). */
    static final float REST_SLACK = 0.75F, FLYING_SLACK = 0.12F;
    /** With a fish on, its slack at no strain; it straightens as the strain rises. */
    static final float FIGHT_SLACK = 0.6F;
    /** How far over a block a draped line lies, and how many blocks up it may be lifted at one point. */
    static final double DRAPE_LIFT = 0.03D;
    static final int DRAPE_TRIES = 3;

    private HangingLine() {
    }

    /**
     * The line's points, rod to hook in render space (hook end first, as vanilla draws it), {@code (SEGMENTS + 1) * 3} floats;
     * {@code offset} is the rod tip from the hook's line start (the render state's {@code lineOriginOffset}).
     */
    public static float[] points(FishingHook hook, Vec3 offset, float partialTicks) {
        Vec3 at = hook.getPosition(partialTicks);
        Level level = hook.level();
        double len = offset.length();
        double sag = slack(hook) * Math.min(SAG_MOST, len * SAG_PER_BLOCK);
        float[] out = new float[(SEGMENTS + 1) * 3];
        for (int i = 0; i <= SEGMENTS; i++) {
            double f = i / (double) SEGMENTS;
            double x = offset.x * f;
            double y = 0.25D + offset.y * f - sag * 4.0D * f * (1.0D - f);
            double z = offset.z * f;
            if (i > 0 && i < SEGMENTS) {
                y = drape(level, at, x, y, z);
            }
            out[i * 3] = (float) x;
            out[i * 3 + 1] = (float) y;
            out[i * 3 + 2] = (float) z;
        }
        return out;
    }

    /** How slack the line is: tight with a fish pulling on it, slack lying out, near straight flying out on a cast. */
    private static float slack(FishingHook hook) {
        Player owner = hook.getPlayerOwner();
        if (owner != null && owner == Minecraft.getInstance().player) {
            FishFight fight = owner.getAttached(FishingReal.FIGHTING);
            if (fight != null && fight.fighting()) {
                float loose = 1.0F - fight.strain();
                return FIGHT_SLACK * loose * loose;
            }
        }
        boolean resting = hook.isInWater() || hook.onGround() || hook.getDeltaMovement().lengthSqr() < 0.01D;
        return resting ? REST_SLACK : FLYING_SLACK;
    }

    /** The height (render space) the line lies at over {@code (x, z)}: on top of anything solid it would pass through. */
    private static double drape(Level level, Vec3 at, double x, double y, double z) {
        for (int t = 0; t < DRAPE_TRIES; t++) {
            BlockPos pos = BlockPos.containing(at.x + x, at.y + y, at.z + z);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return y;
            }
            double top = pos.getY() + shape.max(Direction.Axis.Y) - at.y + DRAPE_LIFT;
            if (y >= top) {
                return y;
            }
            y = top;
        }
        return y;
    }

    /** Whether {@code points} came from {@link #points} (not empty). */
    public static boolean drawn(float @Nullable [] points) {
        return points != null && points.length == (SEGMENTS + 1) * 3;
    }
}
