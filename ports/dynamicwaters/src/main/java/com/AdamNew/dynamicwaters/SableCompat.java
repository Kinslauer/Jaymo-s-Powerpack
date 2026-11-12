package com.AdamNew.dynamicwaters;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SableCompat {
    private static boolean checked = false;
    private static Object helperInstance = null;
    private static Method getContainingEntityMethod = null;
    private static Method getContainingPosMethod = null;
    private static Method projectMethod = null;

    private static void init() {
        if (checked) return;
        checked = true;
        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Field helperField = sableClass.getField("HELPER");
            helperInstance = helperField.get(null);
            if (helperInstance != null) {
                Class<?> helperClass = helperInstance.getClass();

                for (Method m : helperClass.getMethods()) {
                    if (m.getName().equals("getContaining") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(Entity.class)) {
                        getContainingEntityMethod = m;
                    }
                    if (m.getName().equals("getContaining") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(Level.class)
                            && m.getParameterTypes()[1].isAssignableFrom(net.minecraft.core.Position.class)) {
                        getContainingPosMethod = m;
                    }
                    if (m.getName().equals("projectOutOfSubLevel") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(Level.class)
                            && m.getParameterTypes()[1].isAssignableFrom(net.minecraft.core.Position.class)) {
                        projectMethod = m;
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    public static Vec3 getGlobalPos(Entity entity) {
        init();
        if (helperInstance != null && getContainingEntityMethod != null && projectMethod != null) {
            try {
                Object subLevel = getContainingEntityMethod.invoke(helperInstance, entity);
                if (subLevel != null) {
                    return (Vec3) projectMethod.invoke(helperInstance, entity.level(), entity.position());
                }
            } catch (Throwable t) {
            }
        }
        return ValkyrienCompat.worldPos(entity);
    }

    public static BlockPos getGlobalBlockPos(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        init();
        if (helperInstance != null && getContainingPosMethod != null && projectMethod != null && level instanceof Level l) {
            try {
                Vec3 localPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Object subLevel = getContainingPosMethod.invoke(helperInstance, l, localPos);
                if (subLevel != null) {
                    Vec3 globalPos = (Vec3) projectMethod.invoke(helperInstance, l, localPos);
                    return BlockPos.containing(globalPos.x, globalPos.y, globalPos.z);
                }
            } catch (Throwable t) {
            }
        }
        return ValkyrienCompat.worldPos(level, pos);
    }

    private static Class<?> containerClass = null;
    private static Method getContainerMethod = null;
    private static Method getAllSubLevelsMethod = null;
    private static Class<?> serverSubLevelClass = null;
    private static Class<?> poseClass = null;
    private static Method logicalPoseMethod = null;
    private static Method positionMethod = null;
    private static Method orientationMethod = null;
    private static Class<?> massTrackerClass = null;
    private static Method getMassTrackerMethod = null;
    private static Method getMassMethod = null;
    private static Class<?> rigidBodyHandleClass = null;
    private static Method ofMethod = null;
    private static Method applyLinearImpulseMethod = null;
    private static Field latestLinearVelocityField = null;
    private static boolean physicsChecked = false;

    private static void initPhysics() {
        if (physicsChecked) return;
        physicsChecked = true;
        try {
            containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            getContainerMethod = containerClass.getMethod("getContainer", net.minecraft.server.level.ServerLevel.class);
            getAllSubLevelsMethod = containerClass.getMethod("getAllSubLevels");

            serverSubLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
            logicalPoseMethod = serverSubLevelClass.getMethod("logicalPose");
            positionMethod = poseClass.getMethod("position");
            orientationMethod = poseClass.getMethod("orientation");

            massTrackerClass = Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData");
            getMassTrackerMethod = serverSubLevelClass.getMethod("getMassTracker");
            getMassMethod = massTrackerClass.getMethod("getMass");

            rigidBodyHandleClass = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
            ofMethod = rigidBodyHandleClass.getMethod("of", serverSubLevelClass);
            applyLinearImpulseMethod = rigidBodyHandleClass.getMethod("applyLinearImpulse", org.joml.Vector3dc.class);

            latestLinearVelocityField = serverSubLevelClass.getField("latestLinearVelocity");
        } catch (Throwable t) {
        }
    }

    public static void tickPhysicsShips(net.minecraft.server.level.ServerLevel serverLevel) {
        init();
        initPhysics();
        if (helperInstance == null || getContainerMethod == null) return;
        try {
            Object container = getContainerMethod.invoke(null, serverLevel);
            if (container != null) {
                java.util.List<?> subLevels = (java.util.List<?>) getAllSubLevelsMethod.invoke(container);
                if (subLevels != null && !subLevels.isEmpty()) {
                    for (Object sl : subLevels) {
                        if (serverSubLevelClass.isInstance(sl)) {
                            Object pose = logicalPoseMethod.invoke(sl);
                            org.joml.Vector3dc center = (org.joml.Vector3dc) positionMethod.invoke(pose);

                            net.minecraft.world.phys.Vec3 flow = HydrologyManager.getRiverFlow(center.x(), (int)center.y(), center.z(), serverLevel);
                            if (flow.lengthSqr() > 0.01) {
                                double mult = Config.SABLE_SHIP_SPEED.get();
                                org.joml.Vector3d targetVel = new org.joml.Vector3d(flow.x * mult * 2.0, 0.0, flow.z * mult * 2.0);

                                org.joml.Vector3d currentVel = (org.joml.Vector3d) latestLinearVelocityField.get(sl);
                                org.joml.Vector3d globalDelta = new org.joml.Vector3d(targetVel.x - currentVel.x, 0.0, targetVel.z - currentVel.z);

                                org.joml.Quaterniondc orientation = (org.joml.Quaterniondc) orientationMethod.invoke(pose);
                                org.joml.Vector3d localDelta = new org.joml.Vector3d();
                                orientation.transformInverse(globalDelta, localDelta);

                                Object massTracker = getMassTrackerMethod.invoke(sl);
                                double mass = (Double) getMassMethod.invoke(massTracker);

                                localDelta.mul(mass * 0.03);

                                Object handle = ofMethod.invoke(null, sl);
                                if (handle != null) {
                                    applyLinearImpulseMethod.invoke(handle, localDelta);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
    }
}
