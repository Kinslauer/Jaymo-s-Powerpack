package com.jaymoskitchen.corpse;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * (0.5.21) A chunk of a body blown apart ({@link Gibs}): a piece of the creature's own model in its own skin, which the
 * client picks from the look by the chunk's id. It flies, tumbles, lands and sinks away like a crushed spider's loose
 * leg, which it is built on; nothing to pick up or grab.
 */
public class Gib extends SpiderLeg {
	public Gib(EntityType<? extends Gib> type, Level level) {
		super(type, level);
	}
}
