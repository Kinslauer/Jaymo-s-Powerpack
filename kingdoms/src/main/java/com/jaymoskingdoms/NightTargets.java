package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * Who a vampire's new gifts (the batstep, the flechettes, the drinking colony) may harm: any living thing that is not theirs (a
 * party member, a pet, a companion, their own puppets). Players only where players may fight, and never in creative or spectator.
 */
public final class NightTargets {
	private NightTargets() {
	}

	/** Whether {@code e} may be harmed by {@code player}'s gifts. */
	public static boolean target(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || ArtKeys.mine(player, e) || NightForms.isPuppet(e)) {
			return false;
		}
		if (e instanceof Player other) {
			return player.level() instanceof ServerLevel level && level.isPvpAllowed() && !other.isCreative();
		}
		return true;
	}

	/** {@code harm}, a little more on a new moon and less in the sun or near a holy thing (as the claws are). */
	public static float harm(ServerPlayer player, float harm) {
		float base = NewMoon.on(player) ? harm * NewMoon.CLAW_SHARE : harm;
		return BloodLeap.weak(player) ? base * (float) BloodLeap.WEAK_SHARE : base;
	}
}
