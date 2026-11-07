package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.XenoformState;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.18.117, Scan F) Billow's riders sit side by side across its back, as on a happy ghast, not all in one spot over its
 * head. Runs on both sides, so every client places them the same.
 */
@Mixin(Entity.class)
abstract class BillowSeatsMixin {
	/** Seat offsets across its back (sideways, forward), in blocks at Billow's size. */
	private static final double[][] SEATS = {{-0.55, 0.15}, {0.55, 0.15}, {0.0, -0.45}};
	/** (0.19.34) The top of the cloud, in blocks at size 1 (its box is the low swimming one; the cloud is drawn taller). */
	private static final double SEAT_HEIGHT = 0.7;

	@Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("TAIL"))
	private void jaymospowers$billowSeats(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
		if (!((Object)this instanceof Player cloud) || !"billow".equals(cloud.getAttached(XenoformState.FORM))) {
			return;
		}
		List<Entity> riders = cloud.getPassengers();
		int seat = riders.indexOf(passenger);
		if (seat < 0 || seat >= SEATS.length) {
			return;
		}
		double scale = cloud.getScale();
		Vec3 offset = new Vec3(SEATS[seat][0] * scale, 0.0, SEATS[seat][1] * scale).yRot(-cloud.yBodyRot * ((float)Math.PI / 180.0F));
		move.accept(passenger, cloud.getX() + offset.x, cloud.getY() + scale * SEAT_HEIGHT, cloud.getZ() + offset.z);
	}
}
