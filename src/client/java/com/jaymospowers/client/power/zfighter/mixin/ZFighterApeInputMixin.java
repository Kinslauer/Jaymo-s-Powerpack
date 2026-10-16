package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.12.3) The Great Ape crippled: no movement key answers. The wild ape (a Warrior-born who's no Z Fighter) is
 * berserk: his keys are taken and he's turned and walked at whatever the server picked for him
 * ({@link ZFighterLineage#BERSERK}), hopping what he walks into.
 */
@Mixin(KeyboardInput.class)
abstract class ZFighterApeInputMixin extends ClientInput {
	/** How much of the way to his quarry he turns each tick. */
	private static final float TURN = 0.25F;

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$ape(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !ZFighterLineage.ape(player)) {
			return;
		}
		if (ZFighterLineage.crippled(player)) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
			return;
		}
		Integer id = player.getAttached(ZFighterLineage.BERSERK);
		if (id == null) {
			return;
		}
		Entity target = id < 0 ? null : player.level().getEntity(id);
		if (target == null) {
			// nothing near: he stands and seethes
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
			return;
		}
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TURN);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TURN, -90.0F, 90.0F));
		boolean close = to.horizontalDistance() < player.getBbWidth() * 0.6;
		this.keyPresses = new Input(!close, false, false, false, player.horizontalCollision, false, false);
		this.moveVector = close ? Vec2.ZERO : new Vec2(0.0F, 1.0F);
	}
}
