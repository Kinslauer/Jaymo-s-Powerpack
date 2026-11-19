package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mount climb, in first person: the view eases from standing eye height to the saddle and back.
 */
@Mixin(Camera.class)
abstract class MountClimbCameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    private net.minecraft.world.entity.Entity entity;

    @Shadow
    public abstract Vec3 position();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jaymospowers$mountClimbView(float partialTicks, CallbackInfo ci) {
        if (this.detached || !(this.entity instanceof Player player)) {
            return;
        }
        // Don't apply if player is actually a passenger (vanilla camera takes over)
        if (player.isPassenger()) {
            return;
        }
        float offset = MountClimbPose.getCameraEyeOffset(player, partialTicks);
        if (offset != 0.0F) {
            Vec3 at = this.position();
            // Apply vertical offset smoothly
            at = new Vec3(at.x, at.y + offset, at.z);
            this.setPosition(at);
        }
    }
}