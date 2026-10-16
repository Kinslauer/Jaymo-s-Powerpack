package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterSpiritCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raising a Spirit Bomb: the view pulls way out behind and above him ({@link ZFighterSpiritCamera}), in any view. */
@Mixin(Camera.class)
abstract class ZFighterSpiritCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$spiritBombView(float partialTicks, CallbackInfo ci) {
		if (this.minecraft.player == null || this.minecraft.getCameraEntity() != this.minecraft.player) {
			return;
		}
		Vec3 eyes = this.minecraft.player.getEyePosition(partialTicks);
		Vec3 view = ZFighterSpiritCamera.position(this.minecraft, eyes, partialTicks);
		if (view != null) {
			this.setPosition(view);
			// drawn from outside: his own body shows
			this.detached = true;
		}
	}
}
