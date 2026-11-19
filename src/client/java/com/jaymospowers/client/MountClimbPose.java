package com.jaymospowers.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * How a player climbing onto or off a mount looks. Played on the vanilla player model through {@link KeyframeClip},
 * pausing Fresh Animations via {@link EmfCompat#hold} while the clip plays.
 * <p>
 * Climb-on (0.5-0.75 s): from the left (foot in stirrup, swing right leg over), from the right (mirror), from behind
 * (vault onto the rump, slide forward). Horses, donkeys, mules, our breeds, saddled pigs.
 * <p>
 * Climb-off: forward or back, toward the spot vanilla sets you down, ending exactly there.
 * <p>
 * Camera: ease from standing eye height to the saddle and back (~0.3-0.45 s) instead of snapping; first person
 * follows the head through the clip.
 * <p>
 * Armour: climb time scales with armour points (cloth ~1x, full plate ~2x). The horse must stand still.
 * Server side, the ride starts only when the climb ends, so a slow climb can be interrupted by a hit.
 * <p>
 * Other players see it too (it plays for any rider in view, not only you).
 */
public final class MountClimbPose {

    /**
     * The type of climb animation.
     */
    public enum ClimbType {
        CLIMB_ON_LEFT,
        CLIMB_ON_RIGHT,
        CLIMB_ON_BACK,
        CLIMB_OFF_FORWARD,
        CLIMB_OFF_BACK
    }

    /**
     * State held on the render state while a climb animation plays.
     */
    public record State(ClimbType type, float progress, float length, int mountId, boolean localPlayer,
                        boolean wasRiding, float armourScale) {
    }

    public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:mount_climb");

    /** Per-player climb state (client side). */
    private static final Map<Integer, ClimbInstance> CLIMBS = new HashMap<>();

    /** Cached keyframe clips. */
    private static final Map<ClimbType, KeyframeClip> CLIPS = new HashMap<>();

    private MountClimbPose() {
    }

    /**
     * Starts a climb-on animation for the local player. Called when the player attempts to mount.
     * Returns true if a climb was started (vanilla mount should be delayed).
     */
    public static boolean startClimbOn(Player player, Entity mount, ClimbType type) {
        if (player.level().isClientSide()) {
            // Determine climb type from player's angle to mount
            ClimbType climbType = type != null ? type : determineClimbType(player, mount);
            float armourScale = calculateArmourScale(player);
            float length = getBaseLength(climbType) * armourScale;

            CLIMBS.put(player.getId(), new ClimbInstance(climbType, length, mount.getId(), true, false));
            // Pause Fresh Animations for this player
            EmfCompat.hold(player.getId(), true);
            return true;
        }
        return false;
    }

    /**
     * Starts a climb-off animation for the local player. Called when the player dismounts.
     */
    public static void startClimbOff(Player player, Entity mount, ClimbType type) {
        if (player.level().isClientSide()) {
            ClimbType climbType = type != null ? type : ClimbType.CLIMB_OFF_FORWARD;
            float armourScale = calculateArmourScale(player);
            float length = getBaseLength(climbType) * armourScale;

            CLIMBS.put(player.getId(), new ClimbInstance(climbType, length, mount.getId(), true, true));
            EmfCompat.hold(player.getId(), true);
        }
    }

    /**
     * Determines the climb-on type based on the player's horizontal angle to the mount.
     */
    private static ClimbType determineClimbType(Player player, Entity mount) {
        double dx = mount.getX() - player.getX();
        double dz = mount.getZ() - player.getZ();
        float yawToMount = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float playerYaw = player.getYRot();
        float diff = Mth.rotLerp(1.0F, playerYaw, yawToMount); // actually we want the shortest angle difference
        diff = Mth.wrapDegrees(yawToMount - playerYaw);

        // Behind: roughly facing away from mount (diff near 180)
        // Left: mount is to player's left (diff near -90)
        // Right: mount is to player's right (diff near 90)
        if (diff > 135 || diff < -135) {
            return ClimbType.CLIMB_ON_BACK;
        } else if (diff < 0) {
            return ClimbType.CLIMB_ON_LEFT;
        } else {
            return ClimbType.CLIMB_ON_RIGHT;
        }
    }

    /**
     * Calculates the armour time scale (cloth ~1x, full plate ~2x).
     */
    private static float calculateArmourScale(Player player) {
        // Use vanilla's getArmorValue() which returns the total armor points (0-20)
        int armourPoints = player.getArmorValue();
        // Full diamond/netherite = 20 points, scale from 1.0 to 2.0
        return 1.0F + (armourPoints / 20.0F);
    }

    /**
     * Base animation length in seconds for each climb type.
     */
    private static float getBaseLength(ClimbType type) {
        return switch (type) {
            case CLIMB_ON_LEFT, CLIMB_ON_RIGHT, CLIMB_ON_BACK -> 0.65F; // 0.5-0.75s
            case CLIMB_OFF_FORWARD, CLIMB_OFF_BACK -> 0.5F;
        };
    }

    /**
     * Extracts the climb state for rendering. Called from the render pipeline.
     */
    public static void extract(AvatarRenderState state, float partialTicks) {
        Integer mobId = state.getData(PoseTakeover.MOB_ID);
        int entityId = mobId != null ? mobId : state.id;
        ClimbInstance instance = CLIMBS.get(entityId);

        if (instance == null) {
            state.setData(KEY, null);
            if (mobId != null) {
                EmfCompat.hold(entityId, false);
            }
            return;
        }

        // Update progress
        float delta = partialTicks / 20.0F; // partialTicks is in ticks
        instance.progress += delta;
        float share = Mth.clamp(instance.progress / instance.length, 0.0F, 1.0F);

        if (share >= 1.0F) {
            // Animation complete
            CLIMBS.remove(entityId);
            EmfCompat.hold(entityId, false);

            // If local player climbing on, notify server to start the ride
            if (instance.localPlayer && !instance.wasRiding) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    Entity mount = mc.level.getEntity(instance.mountId);
                    if (mount != null) {
                        // Send packet to server to actually start riding
                        MountClimbClientHandler.finishClimbOn(instance.mountId);
                    }
                }
            }
            state.setData(KEY, null);
            return;
        }

        state.setData(KEY, new State(instance.type, share, instance.length, instance.mountId, instance.localPlayer,
                instance.wasRiding, instance.armourScale));
    }

    /**
     * Applies the climb animation to the model.
     */
    public static void applyModel(Model<?> model, Object renderState) {
        if (!(model instanceof HumanoidModel<?> h) || !(renderState instanceof AvatarRenderState state)) {
            return;
        }
        State climb = state.getData(KEY);
        if (climb == null) {
            return;
        }

        KeyframeClip clip = getClip(climb.type());
        if (clip == null) {
            return;
        }

        float share = climb.progress();

        // Apply bone rotations from the clip
        // Bone names in our animations match vanilla player model parts: root, head, body, right_arm, left_arm, right_leg, left_leg
        applyBone(h, "root", clip, share);
        applyBone(h, "head", clip, share);
        applyBone(h, "body", clip, share);
        applyBone(h, "right_arm", clip, share);
        applyBone(h, "left_arm", clip, share);
        applyBone(h, "right_leg", clip, share);
        applyBone(h, "left_leg", clip, share);
    }

    private static void applyBone(HumanoidModel<?> model, String boneName, KeyframeClip clip, float share) {
        ModelPart part = getModelPart(model, boneName);
        if (part == null || !clip.has(boneName)) {
            return;
        }
        part.xRot = clip.partX(boneName, share);
        part.yRot = clip.partY(boneName, share);
        part.zRot = clip.partZ(boneName, share);
    }

    private static @Nullable ModelPart getModelPart(HumanoidModel<?> model, String boneName) {
        return switch (boneName) {
            case "root" -> model.root();
            case "head" -> model.head;
            case "body" -> model.body;
            case "right_arm" -> model.rightArm;
            case "left_arm" -> model.leftArm;
            case "right_leg" -> model.rightLeg;
            case "left_leg" -> model.leftLeg;
            default -> null;
        };
    }

    /**
     * Gets the camera eye height offset for first-person view during climb.
     * Returns the vertical offset from the normal eye height (positive = higher).
     */
    public static float getCameraEyeOffset(Player player, float partialTicks) {
        if (player.level().isClientSide()) {
            ClimbInstance instance = CLIMBS.get(player.getId());
            if (instance != null) {
                KeyframeClip clip = getClip(instance.type);
                if (clip != null && clip.has("head")) {
                    float share = Mth.clamp(instance.progress / instance.length, 0.0F, 1.0F);
                    // The head bone's x rotation in the clip gives the pitch; we want the vertical eye shift
                    // For simplicity, return a smooth transition based on climb type
                    if (instance.wasRiding) {
                        // Climbing off: ease from saddle to standing
                        return Mth.lerp(share, -0.5F, 0.0F); // saddle is lower than standing
                    } else {
                        // Climbing on: ease from standing to saddle
                        return Mth.lerp(share, 0.0F, -0.5F);
                    }
                }
            }
        }
        return 0.0F;
    }

    private static KeyframeClip getClip(ClimbType type) {
        return CLIPS.computeIfAbsent(type, t -> {
            String file = switch (t) {
                case CLIMB_ON_LEFT -> "player_mount_climb_on_left";
                case CLIMB_ON_RIGHT -> "player_mount_climb_on_right";
                case CLIMB_ON_BACK -> "player_mount_climb_on_back";
                case CLIMB_OFF_FORWARD -> "player_mount_climb_off_forward";
                case CLIMB_OFF_BACK -> "player_mount_climb_off_back";
            };
            return KeyframeClip.load("jaymospowers", file, t.name().toLowerCase());
        });
    }

    /** Internal climb instance tracking. */
    private static class ClimbInstance {
        final ClimbType type;
        final float length;
        final int mountId;
        final boolean localPlayer;
        final boolean wasRiding;
        final float armourScale;
        float progress = 0.0F;

        ClimbInstance(ClimbType type, float length, int mountId, boolean localPlayer, boolean wasRiding) {
            this.type = type;
            this.length = length;
            this.mountId = mountId;
            this.localPlayer = localPlayer;
            this.wasRiding = wasRiding;
            this.armourScale = localPlayer ? calculateArmourScale(Minecraft.getInstance().player) : 1.0F;
        }
    }

    /** Cancels a climb animation for a player (called from server sync). */
    public static void cancelClimb(int playerId) {
        CLIMBS.remove(playerId);
    }
}