package com.jaymospowers.core.wounds;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A blast close by knocks you out cold. Andy, after a blast went off beside him while he lay downed: "the explosion that
 * happened while i was downed should have knocked me unconscious, like any blast at that proximity should". A blast's
 * harm falls off with distance, so how hard it hit says how near it was: {@link #OUT_FROM} or more lays anyone who can
 * go down out cold for {@link #OUT_TICKS} ({@link Downed#knockOut}: standing, they drop; downed, they pass out), and
 * sneak mashes them round sooner as from any knock-out. One already down that a blast hits for a {@link Downed#KILL_BLOW}
 * is killed by it, as before.
 */
public final class BlastOut {
	/** A blast that hurts this much (health points) was near enough to knock you out; for this long (ticks). */
	static final float OUT_FROM = 4.0F;
	static final int OUT_TICKS = 20 * 10;

	private BlastOut() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BlastOut::hit);
	}

	private static void hit(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (taken < OUT_FROM || !source.is(DamageTypeTags.IS_EXPLOSION) || !victim.isAlive() || !(victim.level() instanceof ServerLevel level)
			|| victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		Downed.knockOut(level, victim, OUT_TICKS, false, "message.jaymospowers.downed.blast_out");
	}
}
