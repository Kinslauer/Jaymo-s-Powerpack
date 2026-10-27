package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
 * ({@link ZFighterLineage#BERSERK}), hopping what he walks into; with nothing to go for he roams to
 * {@link ZFighterLineage#WILD_GOAL}, and hurt he runs there flat out (0.13.5).
 */
@Mixin(KeyboardInput.class)
abstract class ZFighterApeInputMixin extends ClientInput {
	/** How much of the way to his quarry he turns each tick. */
	private static final float TURN = 0.25F;
	/** (0.13.5) Where he looks while roaming or running off (degrees down). */
	private static final float ROAM_PITCH = 15.0F;

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
		if (com.jaymospowers.power.zfighter.ApeRage.inFit(player)) {
			// (0.15.148) a Z Fighter's ape in a fit: every new key a heave against it
			com.jaymospowers.client.power.zfighter.ApeRageClient.struggleKeys(this.keyPresses);
		}
		if (com.jaymospowers.power.zfighter.ApeGrab.grabbing(player)) {
			// (0.17.68) his grab has him: he stands where he is (the wild ape's eyes stay on its catch, below)
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
			if (ZFighterLineage.wild(player)) {
				Integer held = player.getAttached(ZFighterLineage.BERSERK);
				Entity catch_ = held == null || held < 0 ? null : player.level().getEntity(held);
				if (catch_ != null) {
					Vec3 to = catch_.getBoundingBox().getCenter().subtract(player.getEyePosition());
					float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
					player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TURN);
				}
			}
			return;
		}
		Integer id = player.getAttached(ZFighterLineage.BERSERK);
		BlockPos goal = player.getAttached(ZFighterLineage.WILD_GOAL);
		if (id == null && goal == null) {
			return;
		}
		Entity target = id == null || id < 0 ? null : player.level().getEntity(id);
		Vec3 to;
		float pitch;
		if (target != null) {
			to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
			pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else if (goal != null) {
			// roaming, or running from a hurt: eyes on the ground ahead
			to = Vec3.atBottomCenterOf(goal).subtract(player.position());
			pitch = ROAM_PITCH;
		} else {
			// nothing near: he stands and seethes
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
			return;
		}
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TURN);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TURN, -90.0F, 90.0F));
		boolean close = to.horizontalDistance() < (target != null ? player.getBbWidth() * 0.6 : 1.5);
		boolean fleeing = target == null && Boolean.TRUE.equals(player.getAttached(ZFighterLineage.WILD_FLEE));
		this.keyPresses = new Input(!close, false, false, false, player.horizontalCollision, false, fleeing && !close);
		this.moveVector = close ? Vec2.ZERO : new Vec2(0.0F, 1.0F);
		if (fleeing && !close) {
			player.setSprinting(true);
		}
	}
}
