package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * (0.7.97) A Deepborn's sense ({@link Heritage}): sneaking, they feel the creatures round them through stone and walls,
 * outlined on their own screen alone (client mixin {@code MinecraftGlowMixin}); nobody else sees a thing.
 */
public final class DeepSense {
	/** How far a sneaking Deepborn senses (blocks). */
	static final double RANGE = 16.0;

	private DeepSense() {
	}

	/** Whether {@code entity} shows outlined to the player at this screen: a sneaking Deepborn's sense. */
	public static boolean senses(Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && entity != player && entity instanceof LivingEntity && !(entity instanceof ArmorStand) && entity.isAlive()
			&& player.isShiftKeyDown() && !player.isSpectator() && Heritage.is(player, Heritage.Kind.DEEPBORN)
			&& entity.distanceToSqr(player) < RANGE * RANGE;
	}
}
