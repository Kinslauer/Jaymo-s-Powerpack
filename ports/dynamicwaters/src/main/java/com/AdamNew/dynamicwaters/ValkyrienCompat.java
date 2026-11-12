package com.AdamNew.dynamicwaters;

import com.AdamNew.dynamicwaters.platform.Platform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ValkyrienCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Dynamic Waters");

    private static final double RESPONSE = 0.6;

    private static final double LEAST_FLOW_SQ = 0.01;

    private static final double SHIP_SPEED = 2.0;

    private static final Map<String, Map<Long, double[]>> CURRENTS = new ConcurrentHashMap<>();

    private static volatile boolean checked;
    private static volatile boolean ready;
    private static boolean listening;

    private static Object eventSource;
    private static Method onEvent;

    private static MethodHandle inShipyard;
    private static MethodHandle shipManagingBlock;
    private static MethodHandle shipManagingEntity;
    private static MethodHandle positionToWorld;
    private static MethodHandle shipWorld;
    private static MethodHandle dimensionId;
    private static Method loadedShips;
    private static Method shipId;
    private static Method shipDimension;
    private static Method shipTransform;
    private static Method positionInWorld;
    private static Method worldBox;
    private static Method boxMinY;
    private static Method shipVelocity;
    private static Method eventWorld;
    private static Method physDimension;
    private static Method physShipById;
    private static Method physMass;
    private static Method physForce;

    private ValkyrienCompat() {
    }

    private static boolean available() {
        if (!checked) init();
        return ready;
    }

    private static synchronized void init() {
        if (checked) return;
        try {
            if (Platform.isModLoaded("valkyrienskies")) {
                final Class<?> api = Class.forName("org.valkyrienskies.mod.api.ValkyrienSkies");
                final Class<?> ship = Class.forName("org.valkyrienskies.core.api.ships.Ship");
                final Class<?> physShip = Class.forName("org.valkyrienskies.core.api.ships.PhysShip");
                final Class<?> physLevel = Class.forName("org.valkyrienskies.core.api.world.PhysLevel");
                final Class<?> physTick = Class.forName("org.valkyrienskies.core.api.events.PhysTickEvent");
                final Class<?> serverWorld = Class.forName("org.valkyrienskies.core.api.world.ServerShipWorld");
                final Class<?> transform = Class.forName("org.valkyrienskies.core.api.ships.properties.ShipTransform");
                final Class<?> coreApi = Class.forName("org.valkyrienskies.core.api.VsCoreApi");
                final Class<?> listenable = Class.forName("org.valkyrienskies.core.api.event.ListenableEvent");

                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                inShipyard = lookup.findStatic(api, "isBlockInShipyard",
                        MethodType.methodType(boolean.class, Level.class, int.class, int.class, int.class));
                shipManagingBlock = lookup.findStatic(api, "getShipManagingBlock", MethodType.methodType(ship, Level.class, BlockPos.class))
                        .asType(MethodType.methodType(Object.class, Level.class, BlockPos.class));
                shipManagingEntity = lookup.findStatic(api, "getShipManagingEntity", MethodType.methodType(ship, Entity.class))
                        .asType(MethodType.methodType(Object.class, Entity.class));
                positionToWorld = lookup.findStatic(api, "positionToWorld", MethodType.methodType(Vec3.class, ship, Vec3.class))
                        .asType(MethodType.methodType(Vec3.class, Object.class, Vec3.class));
                shipWorld = lookup.findStatic(api, "getShipWorld", MethodType.methodType(serverWorld, MinecraftServer.class))
                        .asType(MethodType.methodType(Object.class, MinecraftServer.class));
                dimensionId = lookup.findStatic(api, "getDimensionId", MethodType.methodType(String.class, Level.class));
                loadedShips = serverWorld.getMethod("getLoadedShips");
                shipId = ship.getMethod("getId");
                shipDimension = ship.getMethod("getChunkClaimDimension");
                shipTransform = ship.getMethod("getTransform");
                positionInWorld = transform.getMethod("getPositionInWorld");
                worldBox = ship.getMethod("getWorldAABB");
                boxMinY = Class.forName("org.joml.primitives.AABBdc").getMethod("minY");
                shipVelocity = ship.getMethod("getVelocity");
                eventWorld = physTick.getMethod("getWorld");
                physDimension = physLevel.getMethod("getDimension");
                physShipById = physLevel.getMethod("getShipById", long.class);
                physMass = physShip.getMethod("getMass");
                physForce = physShip.getMethod("applyInvariantForce", Vector3dc.class);
                final Object vsApi = lookup.findStatic(api, "api",
                        MethodType.methodType(Class.forName("org.valkyrienskies.mod.api.VsApi"))).asType(MethodType.methodType(Object.class)).invokeExact();
                eventSource = coreApi.getMethod("getPhysTickEvent").invoke(vsApi);
                onEvent = listenable.getMethod("on", Consumer.class);
                ready = true;
                LOGGER.info("Valkyrien Skies found: ships are carried by the rivers");
            }
        } catch (Throwable t) {
            ready = false;
            LOGGER.warn("Valkyrien Skies is here but not as expected, its ships will not feel the rivers", t);
        }
        checked = true;
    }

    public static BlockPos worldPos(net.minecraft.world.level.BlockGetter getter, BlockPos pos) {
        if (!available() || !(getter instanceof Level level)) return pos;
        try {
            if (!(boolean) inShipyard.invokeExact(level, pos.getX(), pos.getY(), pos.getZ())) return pos;
            final Object ship = (Object) shipManagingBlock.invokeExact(level, pos);
            if (ship == null) return pos;
            return BlockPos.containing((Vec3) positionToWorld.invokeExact(ship, Vec3.atCenterOf(pos)));
        } catch (Throwable t) {
            return pos;
        }
    }

    public static Vec3 worldPos(Entity entity) {
        final Vec3 here = entity.position();
        if (!available()) return here;
        try {
            if (!(boolean) inShipyard.invokeExact(entity.level(), entity.getBlockX(), entity.getBlockY(), entity.getBlockZ())) return here;
            final Object ship = (Object) shipManagingEntity.invokeExact(entity);
            return ship == null ? here : (Vec3) positionToWorld.invokeExact(ship, here);
        } catch (Throwable t) {
            return here;
        }
    }

    public static void tickShips(ServerLevel level) {
        if (!available()) return;
        try {
            if (!listening) {
                listening = true;
                onEvent.invoke(eventSource, (Consumer<Object>) ValkyrienCompat::physicsTick);
            }
            final String dimension = (String) dimensionId.invokeExact((Level) level);
            final Map<Long, double[]> currents = new HashMap<>();
            final double speed = SHIP_SPEED * Config.SABLE_SHIP_SPEED.get();
            final Object world = (Object) shipWorld.invokeExact(level.getServer());
            for (Object ship : (Iterable<?>) loadedShips.invoke(world)) {
                if (speed <= 0.0) break;
                if (!dimension.equals(shipDimension.invoke(ship))) continue;
                final Vector3dc centre = (Vector3dc) positionInWorld.invoke(shipTransform.invoke(ship));

                final int hull = (int) Math.floor((double) boxMinY.invoke(worldBox.invoke(ship)));
                if (!afloat(level, centre.x(), hull, centre.z())) continue;
                final Vec3 flow = HydrologyManager.getRiverFlow(centre.x(), hull, centre.z(), level);
                if (flow.lengthSqr() > LEAST_FLOW_SQ) {
                    currents.put((Long) shipId.invoke(ship), new double[] {flow.x * speed, flow.z * speed});
                }
            }
            CURRENTS.put(dimension, currents);
        } catch (Throwable t) {
            giveUp(t);
        }
    }

    private static boolean afloat(ServerLevel level, double x, int hull, double z) {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), hull, Math.floor(z));
        return !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.move(0, -1, 0)).isEmpty();
    }

    private static void physicsTick(Object event) {
        if (!ready) return;
        try {
            final Object world = eventWorld.invoke(event);
            final Map<Long, double[]> currents = CURRENTS.get((String) physDimension.invoke(world));
            if (currents == null || currents.isEmpty()) return;
            for (Map.Entry<Long, double[]> entry : currents.entrySet()) {
                final Object ship = physShipById.invoke(world, entry.getKey());
                if (ship == null) continue;
                final Vector3dc velocity = (Vector3dc) shipVelocity.invoke(ship);
                final double[] target = entry.getValue();
                final double pull = (double) physMass.invoke(ship) * RESPONSE;
                physForce.invoke(ship, new Vector3d((target[0] - velocity.x()) * pull, 0.0, (target[1] - velocity.z()) * pull));
            }
        } catch (Throwable t) {
            giveUp(t);
        }
    }

    private static void giveUp(Throwable t) {
        ready = false;
        CURRENTS.clear();
        LOGGER.warn("Valkyrien Skies ships stop feeling the rivers", t);
    }

    public static void onServerStopped() {
        CURRENTS.clear();
    }
}
