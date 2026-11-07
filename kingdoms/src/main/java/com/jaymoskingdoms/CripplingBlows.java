package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Ogre;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.426) Havens patch 2: <b>crippling blows</b>. A heavy hit from an ogre (its fists, club or ground smash) or an orc
 * (the warchief's axe and slam, a grunt in his blood rage) may break a leg of whoever takes it (Powers' wounds; nothing
 * without Powers). Only a hit of at least {@link #HEAVY} counts.
 */
final class CripplingBlows {
	// ================================================================================================================
	// Tuning
	/** The least damage taken that can cripple. */
	static final float HEAVY = 6.0F;
	/** The chance it does: an ogre's (the Ogre King's), a warchief's, a raging grunt's. */
	static final float OGRE = 0.18F, KING = 0.3F, WARCHIEF = 0.22F, RAGING = 0.1F;
	// ================================================================================================================

	private CripplingBlows() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (blocked || taken < HEAVY || !victim.isAlive() || !(victim.level() instanceof ServerLevel level)
				|| source.getDirectEntity() != source.getEntity() || !(source.getEntity() instanceof LivingEntity attacker)) {
				return;
			}
			float chance = chance(attacker);
			if (chance <= 0.0F || victim.getRandom().nextFloat() >= chance || !PowersCompat.breakLeg(victim, attacker)) {
				return;
			}
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
			if (victim instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crippled_leg"));
			}
		});
	}

	/** How likely a heavy blow of {@code attacker}'s is to cripple (0 for anyone else's). */
	private static float chance(LivingEntity attacker) {
		// (0.7.431) a heavy-handed captain's
		float captain = Captains.cripples(attacker);
		if (captain >= 0.0F) {
			return captain;
		}
		if (attacker instanceof Ogre) {
			return OgreHolds.king(attacker) ? KING : OGRE;
		}
		if (attacker instanceof Worker w && Orcs.isOrc(w.role())) {
			if (w.role() == Orcs.WARCHIEF) {
				return WARCHIEF;
			}
			return w.role() == Orcs.GRUNT && attacker.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH) ? RAGING : 0.0F;
		}
		return 0.0F;
	}
}
