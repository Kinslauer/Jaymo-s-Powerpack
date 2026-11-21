package com.jaymospowers.moving;

import com.jaymospowers.core.Carry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.vehicle.VehicleEntity;

/**
 * Pull riders off: sneak and right-click a mount, cart or boat and its passengers are taken off, all but whoever
 * steers it (and you, if you're aboard too).
 */
public final class PullRiders {
	private PullRiders() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !isMount(entity)
				|| entity.getPassengers().isEmpty()) {
				return InteractionResult.PASS;
			}
			Entity driver = entity.getControllingPassenger();
			boolean pulled = false;
			for (Entity rider : entity.getPassengers().toArray(new Entity[0])) {
				if (rider != driver && rider != player && !Carry.isCarried(rider)) {
					if (!level.isClientSide()) {
						rider.stopRiding();
					}
					pulled = true;
				}
			}
			return pulled ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	/** A ridden animal, cart or boat (never a player). */
	private static boolean isMount(Entity entity) {
		return entity instanceof Animal || entity instanceof VehicleEntity;
	}
}
