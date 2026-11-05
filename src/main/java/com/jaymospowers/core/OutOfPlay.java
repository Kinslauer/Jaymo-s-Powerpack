package com.jaymospowers.core;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (2026-11-05, Andy: "make sure all the new features work with all the old, im thinking specifically of the chat
 * interactions") Someone nobody should walk up to and talk to just now: down or out cold, carried, a player lying left
 * for dead (waking somewhere), black while a new life is made, or held back from a death in Chrono's window (which must
 * look exactly like any death, so nothing may open a screen over it). Kingdoms asks this (by reflection) before a villager
 * comes over to ask something or hold out a hand. Server side.
 */
public final class OutOfPlay {
	private OutOfPlay() {
	}

	public static boolean is(Entity entity) {
		if (Downed.isDowned(entity) || Carry.isCarried(entity)) {
			return true;
		}
		if (entity instanceof Player player && (Mortality.waking(player) || player.hasAttached(Mortality.REBORNING))) {
			return true;
		}
		return entity instanceof LivingEntity living && ChronoCheatDeath.held(living);
	}
}
