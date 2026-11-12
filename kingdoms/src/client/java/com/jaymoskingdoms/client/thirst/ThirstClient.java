package com.jaymoskingdoms.client.thirst;

import com.jaymoskingdoms.thirst.Thirst;
import com.jaymoskingdoms.thirst.ThirstForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * The thirsty on the screen. As a bat ({@link ThirstForms#BAT}) the living nearby show to their owner as red outlines through walls
 * (client mixins {@code ThirstGlowMixin} and {@code ThirstGlowColorMixin}); nobody else sees a thing. A player in any shape is not
 * drawn, nor is their hand (the puppet the server made is all that is seen of them: {@code ThirstHidesPlayerMixin},
 * {@code ThirstHidesHandMixin}).
 */
public final class ThirstClient {
	/** How far a bat sees the living (blocks), and how near is too near to bother (blocks). */
	static final double SIGHT = 22.0, TOO_NEAR = 1.5;
	/** The outline's colour. */
	public static final int RED = 0xC01010;

	private ThirstClient() {
	}

	/** Whether {@code entity} shows outlined to this screen's player: they are a bat, and it is alive and warm and near. */
	public static boolean sensed(Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || entity == player || !(entity instanceof LivingEntity) || entity instanceof ArmorStand || !entity.isAlive()
			|| !ThirstForms.BAT.equals(Thirst.form(player)) || entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
			return false;
		}
		double d = entity.distanceToSqr(player);
		return d < SIGHT * SIGHT && d > TOO_NEAR * TOO_NEAR;
	}

	/** Whether {@code entity} is a player in a shape (the puppet is drawn in their place). */
	public static boolean shaped(Entity entity) {
		return entity instanceof Player player && !Thirst.form(player).isEmpty();
	}
}
