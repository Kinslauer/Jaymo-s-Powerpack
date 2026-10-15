package com.jaymospowers.client.power.smoke;

import com.jaymospowers.power.smoke.SmokeSight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Smoke Sight on the user's own screen: any creature within {@link SmokeSight#RANGE} blocks with smoke round its feet
 * or eyes (from the smoke this client was sent, anyone's) glows through walls, pale grey, or near black for the hostile.
 */
public final class SmokeSightClient {
	static final int PASSIVE = 0xC8CCD2, HOSTILE = 0x3A3D42;

	private SmokeSightClient() {
	}

	/** The outline colour {@code entity} gets on this screen, or 0 for none. */
	public static int color(Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || entity == player || !(entity instanceof LivingEntity) || !entity.isAlive() || !SmokeSight.on(player)
			|| entity.distanceToSqr(player) > SmokeSight.RANGE * SmokeSight.RANGE) {
			return 0;
		}
		float d = Math.max(SmokeFieldClient.density(entity.blockPosition().asLong(), 0.0F),
			SmokeFieldClient.density(BlockPos.containing(entity.getEyePosition()).asLong(), 0.0F));
		if (d < SmokeSight.THICK) {
			return 0;
		}
		return entity instanceof Enemy ? HOSTILE : PASSIVE;
	}
}
