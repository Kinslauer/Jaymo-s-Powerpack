package com.jaymospowers.power.zfighter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * A horse's lines in a scan, read only from the vanilla horse's own attributes (speed, jump strength, health) and its
 * tameness and temper, so it reads whatever breed system the pack uses. Kept in one small class so it can be pointed at a
 * horse mod's own stats later.
 */
final class ZFighterScanHorse {
	/** A walking speed attribute of 1.0 is this many blocks a second. */
	static final double BLOCKS_PER_SECOND = 43.17;

	private ZFighterScanHorse() {
	}

	/** Speed, jump height, health, and how tame it is; empty if {@code entity} is no horse. */
	static List<Component> lines(LivingEntity entity) {
		List<Component> lines = new ArrayList<>();
		if (!(entity instanceof AbstractHorse horse)) {
			return lines;
		}
		lines.add(Component.translatable("hud.jaymospowers.scan.horse.speed", String.format("%.1f", horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * BLOCKS_PER_SECOND)));
		lines.add(Component.translatable("hud.jaymospowers.scan.horse.jump", String.format("%.1f", jumpHeight(horse.getAttributeValue(Attributes.JUMP_STRENGTH)))));
		lines.add(Component.translatable(horse.isTamed() ? "hud.jaymospowers.scan.horse.tame"
			: "hud.jaymospowers.scan.horse.temper", Math.min(100, horse.getTemper() * 100 / Math.max(1, horse.getMaxTemper()))));
		return lines;
	}

	/** The height in blocks a horse of this jump strength clears (the curve the game's own jump follows). */
	static double jumpHeight(double strength) {
		return -0.1817584952 * strength * strength * strength + 3.689713992 * strength * strength + 2.128599134 * strength - 0.343930367;
	}
}
