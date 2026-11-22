package com.jaymospowers.client.fishing;

import com.jaymospowers.fishing.HookBait;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (Andy: "the line doesnt extend from the tip of the rod") Where the fishing line leaves the rod. Vanilla guesses the
 * rod tip from a fixed spot beside the eyes, which misses our 3D held rods, the bend and every animated pose. Instead,
 * the item render state of a fishing player's rod hand is marked when it's filled (third person: RodTipArmedMixin;
 * first person: RodTipHandsMixin), and when that state is drawn (RodTipItemMixin) RodTipLayerMixin reads the real model
 * matrix and marks where the rod's tip lands. The hook renderer (FishingHookRendererMixin) starts the line there on the
 * next frame, else keeps vanilla's guess. Watching the render state rather than one renderer's method keeps working
 * when another mod draws the held item its own way.
 */
public final class RodTip {
    /** A tip older than this (ms) is stale (rod not drawn lately: hidden, off screen); vanilla's guess is used. */
    private static final long FRESH_MS = 250L;
    /** A third-person tip farther than this (blocks, times the player's scale) from the player is a GUI preview. */
    private static final double MAX_REACH = 4.0D;
    /** The tip when the model gives no extents: the vanilla rod texture's top-right pixel (model units, 0..1). */
    private static final Vector3fc FALLBACK_TIP = new Vector3f(14.5F / 16.0F, 13.5F / 16.0F, 0.5F);

    /** Third person: each fishing player's rod-hand item state, with the player's render state. */
    private static final Map<ItemStackRenderState, Armed> THIRD_PERSON_RODS = new WeakHashMap<>();
    /** First person: the rod hand's item state while you fish, else null. */
    private static @Nullable ItemStackRenderState firstPersonRod;

    /** What the rod being drawn now is: null, FIRST, or the third-person holder. */
    private static @Nullable Object drawing;
    private static final Object FIRST = new Object();
    private static final Vector3f CAPTURED = new Vector3f();
    private static Vector3fc @Nullable [] lastExtents;
    private static final Vector3f LAST_TIP = new Vector3f();

    /** The hands' base pose this frame (view rotation undone, then the hurt and walk bob). */
    private static final Matrix4f HAND_BASE = new Matrix4f();
    /** First person: the tip in the camera's own frame, bob and hand FOV taken out. */
    private static final Vector3f FIRST_PERSON = new Vector3f();
    private static long firstPersonAt;
    /** Third person: each player's tip from their feet. */
    private static final Int2ObjectMap<Seen> THIRD_PERSON = new Int2ObjectOpenHashMap<>();

    private record Armed(int id, ArmedEntityRenderState state) {
    }

    private record Seen(Vec3 offset, long at) {
    }

    private RodTip() {
    }

    /** The arm the line comes from: the main hand's if it holds a rod, else the off hand's. */
    public static HumanoidArm holdingArm(Player player) {
        return HookBait.isRod(player.getMainHandItem()) ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /** Third person: {@code entity}'s held items were just put in {@code state}; mark the rod hand if they fish. */
    public static void armed(LivingEntity entity, ArmedEntityRenderState state) {
        THIRD_PERSON_RODS.remove(state.rightHandItemState);
        THIRD_PERSON_RODS.remove(state.leftHandItemState);
        if (entity instanceof Player player && player.fishing != null) {
            boolean right = holdingArm(player) == HumanoidArm.RIGHT;
            if (HookBait.isRod(right ? state.rightHandItemStack : state.leftHandItemStack)) {
                THIRD_PERSON_RODS.put(right ? state.rightHandItemState : state.leftHandItemState, new Armed(player.getId(), state));
            }
        }
    }

    /** First person: the hands are about to be drawn on {@code base}; mark the rod hand if you fish. */
    public static void hands(FirstPersonHandsAndItemsRenderState state, Matrix4fc base) {
        HAND_BASE.set(base);
        Player player = Minecraft.getInstance().player;
        boolean main = HookBait.isRod(state.mainHandItem);
        firstPersonRod = player != null && player.fishing != null && (main || HookBait.isRod(state.offHandItem))
                ? (main ? state.mainHandRenderState : state.offHandRenderState) : null;
    }

    /** From RodTipItemMixin: {@code item} is about to be drawn. */
    public static void submitting(ItemStackRenderState item) {
        drawing = item == firstPersonRod ? FIRST : THIRD_PERSON_RODS.get(item);
    }

    /** From RodTipItemMixin: the item is drawn. */
    public static void submitted() {
        drawing = null;
    }

    /** A rod whose tip we want is being drawn (checked before reading a layer's extents, which may cost). */
    public static boolean watching() {
        return drawing != null;
    }

    /** From RodTipLayerMixin: the rod's first layer is drawn with {@code pose} (display transform applied). */
    public static void layer(Matrix4fc pose, Vector3fc[] extents) {
        Object watched = drawing;
        drawing = null;
        pose.transformPosition(modelTip(extents), CAPTURED);
        if (watched == FIRST) {
            firstPerson();
        } else if (watched instanceof Armed armed) {
            thirdPerson(armed);
        }
    }

    /** The model's tip: its top-right corner (most x + y), as every rod is drawn bottom-left to top-right. */
    private static Vector3fc modelTip(Vector3fc[] extents) {
        if (extents.length == 0) {
            return FALLBACK_TIP;
        }
        if (extents == lastExtents) {
            return LAST_TIP;
        }
        float best = Float.NEGATIVE_INFINITY;
        for (Vector3fc v : extents) {
            if (v.x() + v.y() > best) {
                best = v.x() + v.y();
                LAST_TIP.set(v.x(), v.y(), 0.5F);
            }
        }
        lastExtents = extents;
        return LAST_TIP;
    }

    /** First person: the rod's tip is drawn at CAPTURED; keep it in the camera's frame. */
    private static void firstPerson() {
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.viewRotationMatrix == null || camera.projectionMatrix == null) {
            return;
        }
        // into view space (hand FOV); the hands are drawn with their own FOV, the line with the world's: move the point so it
        // sits on the same spot of the screen at the same depth
        Vector3f view = camera.viewRotationMatrix.transformPosition(CAPTURED, new Vector3f());
        float handFocal = 1.0F / (float) Math.tan(Math.toRadians(camera.hudFov) * 0.5D);
        float worldFocal = camera.projectionMatrix.m11();
        if (!(worldFocal > 0.0F) || !Float.isFinite(handFocal)) {
            return;
        }
        float k = handFocal / worldFocal;
        view.set(view.x * k, view.y * k, view.z);
        // the world gets the same bob as the hands, so take it out here (bob = view rotation x hand base)
        Matrix4f bob = new Matrix4f(camera.viewRotationMatrix).mul(HAND_BASE);
        bob.invert().transformPosition(view, FIRST_PERSON);
        firstPersonAt = Util.getMillis();
    }

    /** Third person: the rod's tip is drawn at CAPTURED; keep it from the player's feet. */
    private static void thirdPerson(Armed armed) {
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null) {
            return;
        }
        ArmedEntityRenderState state = armed.state();
        Vec3 offset = new Vec3(CAPTURED.x + camera.pos.x - state.x, CAPTURED.y + camera.pos.y - state.y, CAPTURED.z + camera.pos.z - state.z);
        float scale = Math.max(1.0F, state.scale);
        if (offset.lengthSqr() > MAX_REACH * MAX_REACH * scale * scale) {
            return; // drawn somewhere else (inventory preview and such)
        }
        long now = Util.getMillis();
        THIRD_PERSON.put(armed.id(), new Seen(offset, now));
        if (THIRD_PERSON.size() > 32) {
            THIRD_PERSON.values().removeIf(seen -> now - seen.at() > FRESH_MS);
        }
    }

    /**
     * Where {@code player}'s line leaves the rod tip now, or null to keep vanilla's guess. Your own first-person hands
     * first, else the drawn body (third person, or a camera mod that shows your body in first person).
     */
    public static @Nullable Vec3 tip(Player player, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() && now - firstPersonAt <= FRESH_MS) {
            Camera camera = minecraft.gameRenderer.mainCamera();
            Vector3f world = camera.getViewRotationMatrix(new Matrix4f()).invert().transformPosition(FIRST_PERSON, new Vector3f());
            return camera.position().add(world.x, world.y, world.z);
        }
        Seen seen = THIRD_PERSON.get(player.getId());
        if (seen == null || now - seen.at() > FRESH_MS) {
            return null;
        }
        return player.getPosition(partialTicks).add(seen.offset());
    }
}
