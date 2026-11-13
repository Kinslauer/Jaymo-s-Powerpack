package com.jaymospowers.core.sun;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whether the Sun's rays reach a point: remade from RealisticSunBurn's idea in our own code (that mod is All Rights
 * Reserved and is not in the pack - none of its code or art is copied here). A ray is cast from the point toward the
 * Sun's real position; the point burns only while nothing opaque blocks it. A roof, a cave wall or a solid cliff
 * shades it; leaves, glass, torches and fences let the rays through, so tree shade no longer counts.
 * <p>
 * The Sun's direction comes from Kingdoms' {@code com.jaymoskingdoms.astronomy.SunMoon} when that mod is loaded
 * (its true orbit), and from the game's own sky angle otherwise. Nothing burns in the future's ashen sun - that is
 * {@link com.jaymospowers.power.chrono.mixin.FutureSunMixin}'s job and this class leaves it alone.
 */
public final class SunExposure {
    private SunExposure() {
    }

    /** Whether Jaymo's Kingdoms is loaded, so its SunMoon can be used for the Sun's direction. */
    public static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");

    @Nullable
    private static final MethodHandle SUN = jaymospowers$sun();

    @Nullable
    private static MethodHandle jaymospowers$sun() {
        if (!KINGDOMS) {
            return null;
        }
        try {
            Class<?> sunMoon = Class.forName("com.jaymoskingdoms.astronomy.SunMoon");
            return MethodHandles.publicLookup().findStatic(sunMoon, "sun", MethodType.methodType(Vec3.class, Level.class, Vec3.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** The unit direction (y up) to the Sun: Kingdoms' orbit when loaded, the game's own sky angle otherwise. */
    public static Vec3 sunDirection(Level level, Vec3 at) {
        if (SUN != null) {
            try {
                return (Vec3) SUN.invoke(level, at);
            } catch (Throwable t) {
                // fall through to the fallback
            }
        }
        return jaymospowers$sunDirection(level, at);
    }

    private static Vec3 jaymospowers$sunDirection(Level level, Vec3 at) {
        float rad = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, at) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), Mth.cos(rad), 0.0);
    }

    /** How far the ray walks before giving up and calling the point sunlit. */
    static final int REACH = 80;

    /**
     * Whether nothing opaque blocks the sky from {@code at} toward the Sun: a block with a full solid render shape
     * blocks, leaves, glass, torches and fences do not.
     */
    public static boolean exposedToSun(Level level, Vec3 at) {
        Vec3 dir = sunDirection(level, at);
        if (dir.y <= 0.0D) {
            return false;
        }
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        double x = at.x;
        double y = at.y;
        double z = at.z;
        for (int i = 0; i < REACH; i++) {
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) {
                return true;
            }
            if (level.getBlockState(pos).isSolidRender()) {
                return false;
            }
            x += dir.x;
            y += dir.y;
            z += dir.z;
        }
        return true;
    }

    /**
     * Whether the Sun's rays reach any part of {@code entity}'s body: the feet, chest and eyes down the middle, and the
     * four top corners of its hitbox. Any one of them in the sun is enough (a head under a roof edge with the legs
     * out in the light still burns).
     */
    public static boolean bodyExposed(Level level, net.minecraft.world.entity.Entity entity) {
        net.minecraft.world.phys.AABB box = entity.getBoundingBox().deflate(0.05); // just inside, so a wall it touches isn't "in the way"
        double x = entity.getX(), z = entity.getZ();
        double top = box.maxY;
        Vec3[] points = {
            new Vec3(x, box.minY + 0.05, z),
            new Vec3(x, (box.minY + box.maxY) * 0.5, z),
            entity.getEyePosition(),
            new Vec3(box.minX, top, box.minZ), new Vec3(box.maxX, top, box.minZ),
            new Vec3(box.minX, top, box.maxZ), new Vec3(box.maxX, top, box.maxZ),
        };
        for (Vec3 point : points) {
            if (exposedToSun(level, point)) {
                return true;
            }
        }
        return false;
    }
}
