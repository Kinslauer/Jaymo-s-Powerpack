package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.fishing.HookBait;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.21.64, Andy: "you should have to manually and visually bait the hook") The bait on a hook ({@link HookBait}) hangs
 * just under the bobber, small, turned to the camera like a dropped item.
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    /** How big the bait is drawn, and how far under the bobber. */
    private static final float BAIT_SCALE = 0.35F, BAIT_DROP = 0.3F;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
            at = @At("TAIL"))
    private void jaymospowers$extractBait(FishingHook hook, FishingHookRenderState state, float partialTicks, CallbackInfo ci) {
        ItemStackRenderState bait = ((HookBaitState) state).jaymospowers$bait();
        Player owner = hook.getPlayerOwner();
        Item item = owner == null ? null : HookBait.bait(owner);
        if (item == null) {
            bait.clear();
            return;
        }
        Minecraft.getInstance().getItemModelResolver().updateForNonLiving(bait, new ItemStack(item), ItemDisplayContext.GROUND, hook);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void jaymospowers$submitBait(FishingHookRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
            CallbackInfo ci) {
        ItemStackRenderState bait = ((HookBaitState) state).jaymospowers$bait();
        if (bait.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, -BAIT_DROP, 0.0F);
        poseStack.scale(BAIT_SCALE, BAIT_SCALE, BAIT_SCALE);
        poseStack.rotate(camera.orientation);
        bait.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
