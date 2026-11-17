package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.Lean;
import com.jaymoskingdoms.client.LeanClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaning ({@link Lean}), in first person: the eye moves out to the side (stopping short of a wall) and the view rolls with the
 * tilt of the body. In third person the model does the leaning and the camera stays.
 */
@Mixin(Camera.class)
abstract class CameraLeanMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private int matrixPropertiesDirty;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymoskingdoms$lean(float partialTicks, CallbackInfo ci) {
		if (this.detached || !(this.entity instanceof Player player) || player.isPassenger()) {
			return;
		}
		float amount = LeanClient.amount(player, partialTicks);
		if (amount == 0.0F) {
			return;
		}
		Vec3 at = this.position();
		Vec3 out = Lean.offset(player.getViewYRot(partialTicks), amount);
		var hit = player.level().clip(new ClipContext(at, at.add(out), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			out = hit.getLocation().subtract(at).subtract(out.normalize().scale(LeanClient.CAMERA_GAP));
		}
		this.setPosition(at.add(out));
		// roll about the view's own axis (vanilla's rotation is yaw and pitch only), then refresh the up and left vectors
		this.rotation.rotateZ((float)Math.toRadians(-amount * Lean.ROLL));
		new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.rotation, this.up);
		new Vector3f(-1.0F, 0.0F, 0.0F).rotate(this.rotation, this.left);
		this.matrixPropertiesDirty |= 3;
	}
}
