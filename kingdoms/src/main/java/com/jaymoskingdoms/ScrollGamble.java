package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A scroll above your skill. Learning a spell for good is a higher art, and a reader without the knowledge for it used to be
 * turned away. Now anyone may try: the scroll may fizzle (and crumble), backfire (a burn and a strain on the reader), or work,
 * and a scroll that works is cast once and not learned. Knowledge and Arcana tilt the odds, a staff caster has the best of them.
 */
public final class ScrollGamble {
	/** The odds of it working before skill: the base, what each level of Knowledge adds, what each point of Arcana adds. */
	static final float BASE = 0.30F, PER_KNOWLEDGE = 0.10F, PER_ARCANA = 0.003F;
	/** What a staff caster adds (they know the shape of the words), and the least and most the odds can be. */
	static final float CASTER_BONUS = 0.15F, LEAST = 0.15F, MOST = 0.85F;
	/** Of the scrolls that do not work, the share that backfire (the rest fizzle). */
	static final float BACKFIRE_SHARE = 0.4F;
	/** A backfire's burn and the mana strain it leaves. */
	static final float BACKFIRE_HURT = 4.0F, BACKFIRE_STRAIN = 12.0F;

	/** How a try goes. */
	public enum Outcome { WORKS, FIZZLES, BACKFIRES }

	private ScrollGamble() {
	}

	/** Whether this reader would have been turned away: a staff caster or Sorcerer without the Knowledge to learn. */
	static boolean above(ServerPlayer player) {
		return !Knowledge.has(player, Knowledge.HIGHER_ARTS);
	}

	/** The chance the scroll works for {@code player}. */
	public static float chance(ServerPlayer player) {
		float chance = BASE + PER_KNOWLEDGE * Knowledge.level(player) + PER_ARCANA * Stats.value(player, Stats.Stat.ARCANA);
		if (Spellcraft.staffCaster(player) || Classes.is(player, Classes.PlayerClass.SORCERER)) {
			chance += CASTER_BONUS;
		}
		return Mth.clamp(chance, LEAST, MOST);
	}

	/** Rolls the try. A fizzle and a backfire are played out here; the caller spends the scroll either way. */
	public static Outcome roll(ServerPlayer player, ServerLevel level) {
		float r = player.getRandom().nextFloat();
		float works = chance(player);
		if (r < works) {
			return Outcome.WORKS;
		}
		boolean backfire = (r - works) / (1.0F - works) < BACKFIRE_SHARE;
		if (backfire) {
			level.sendParticles(Fx.CRACKLE, player.getX(), player.getY(1.0), player.getZ(), 24, 0.3, 0.5, 0.3, 0.3);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
			player.hurtServer(level, level.damageSources().magic(), BACKFIRE_HURT);
			ManaSickness.strain(player, BACKFIRE_STRAIN);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll.backfire"));
			return Outcome.BACKFIRES;
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, player.getX(), player.getY(1.2), player.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll.fizzle"));
		return Outcome.FIZZLES;
	}
}
