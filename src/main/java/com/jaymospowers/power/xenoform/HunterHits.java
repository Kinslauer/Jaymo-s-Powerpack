package com.jaymospowers.power.xenoform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** The hunters' extra weight on a blow they have just landed (Howl's tracked prey, Lurk's unseen first hit). */
public final class HunterHits {
	/** True while an extra blow is being dealt, so it is not itself counted a blow that earns another. */
	private static boolean busy;

	private HunterHits() {
	}

	public static boolean busy() {
		return busy;
	}

	/** {@code extra} more damage to {@code target}, as a hit by {@code player} (past its hurt-time, so it lands on top of the blow just struck). */
	public static void extra(ServerPlayer player, LivingEntity target, float extra) {
		if (busy || extra <= 0.0F || !target.isAlive()) {
			return;
		}
		busy = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(player.level(), player.damageSources().playerAttack(player), extra);
		} finally {
			busy = false;
		}
	}
}
