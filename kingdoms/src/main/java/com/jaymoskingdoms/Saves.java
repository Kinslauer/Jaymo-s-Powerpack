package com.jaymoskingdoms;

import com.jaymoskingdoms.Stats.Stat;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Saves from your attributes (fifth edition's saving throws, Andy's pick A2). Each of the seven attributes resists
 * something, so the points spent on the K screen keep you alive in ways you feel:
 * <ul>
 * <li><b>Agility</b>: blasts and breath land softer on one who is moving (up to half at the top of it).</li>
 * <li><b>Endurance</b>: poison, sickness, hunger and withering wear off sooner.</li>
 * <li><b>Grit</b>: fear and holds break sooner (frightened, held fast), and a caster holding a spell keeps it through a blow.</li>
 * <li><b>Wits</b>: charms and illusions are seen through more often.</li>
 * <li><b>Arcana</b>: a curse's bite (the wolf's, the vampire's) and possession are thrown off more often.</li>
 * <li><b>Might</b>: nets and grabs are broken more often.</li>
 * </ul>
 * Everyone else has a plain chance, and a boss a much better one. No numbers are shown; a save that holds is heard.
 */
public final class Saves {
	/** A player's chance with nothing in the attribute, and at its top (100). */
	static final float PLAYER_LEAST = 0.05F, PLAYER_MOST = 0.6F;
	/** Everyone else's chance, and a boss's. */
	static final float MOB = 0.1F, BOSS = 0.4F;
	/** A blessing or an aura never lifts a save past this. */
	static final float BLESSED_MOST = 0.85F;
	/** Poisoned, a save holds this share as often. */
	static final float POISONED = 0.7F;
	/** A save on a lasting thing shortens it by at most this share. */
	static final float MOST_SHORTER = 0.6F;
	/** At the top of Agility a moving body takes this share of a blast. */
	static final float BLAST_LEAST = 0.5F;
	/** Moving this fast (blocks a tick, along the ground) counts as moving for a blast. */
	static final double MOVING = 0.08;

	/** What a save is against, and the attribute behind it. */
	public enum Kind {
		BLAST(Stat.AGILITY), POISON(Stat.ENDURANCE), FEAR(Stat.GRIT), HOLD(Stat.GRIT), CHARM(Stat.WITS), ILLUSION(Stat.WITS),
		CURSE(Stat.ARCANA), POSSESSION(Stat.ARCANA), GRAB(Stat.MIGHT), FOCUS(Stat.GRIT);

		public final Stat stat;

		Kind(Stat stat) {
			this.stat = stat;
		}
	}

	/** The lasting effects Endurance wears off sooner. */
	private static final Set<Holder<MobEffect>> POISONS = Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER, MobEffects.NAUSEA);
	/** The lasting effects Grit breaks sooner. */
	private static final Set<Holder<MobEffect>> HOLDS = Set.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE);

	private Saves() {
	}

	/** How much of {@code stat} {@code player} has, 0 to 1. */
	static float share(Player player, Stat stat) {
		return Math.min(1.0F, Math.max(0.0F, Stats.value(player, stat) / (float) Stats.MAX));
	}

	/** {@code who}'s chance (0-1) of a save of {@code kind} holding. */
	public static float chance(LivingEntity who, Kind kind) {
		// fed at a heroes' feast: fear and poison cannot take
		if ((kind == Kind.FEAR || kind == Kind.POISON) && HeroesFeast.fed(who)) {
			return 1.0F;
		}
		float chance;
		if (who instanceof Player player) {
			chance = PLAYER_LEAST + (PLAYER_MOST - PLAYER_LEAST) * share(player, kind.stat);
		} else {
			chance = who instanceof Mob mob && Senses.boss(mob) ? BOSS : MOB;
		}
		// a cleric's blessing and a paladin's aura steady the nerve
		chance = Math.min(BLESSED_MOST, chance + Clerics.saveBonus(who) + Paladins.auraBonus(who));
		// shaking hands: poison makes every save worse
		return who.hasEffect(MobEffects.POISON) ? chance * POISONED : chance;
	}

	/** Rolls {@code who}'s save of {@code kind}: true if it holds (heard, for a player). */
	public static boolean resist(LivingEntity who, Kind kind) {
		if (who.getRandom().nextFloat() >= chance(who, kind)) {
			return false;
		}
		if (who instanceof ServerPlayer player) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.6F, 1.3F);
		}
		return true;
	}

	/** How much of a lasting {@code kind} thing {@code who} bears: 1 all of it, down to {@code 1 - MOST_SHORTER}. */
	public static float lasting(LivingEntity who, Kind kind) {
		return 1.0F - MOST_SHORTER * chance(who, kind) / PLAYER_MOST;
	}

	/** An effect about to go on {@code who}: poisons and holds shortened by Endurance and Grit. */
	public static MobEffectInstance onEffect(LivingEntity who, MobEffectInstance effect) {
		if (who.level().isClientSide() || effect.isInfiniteDuration() || effect.isAmbient() || effect.getDuration() < 40) {
			return effect;
		}
		Kind kind = POISONS.contains(effect.getEffect()) ? Kind.POISON : HOLDS.contains(effect.getEffect()) ? Kind.HOLD : null;
		if (kind == null) {
			return effect;
		}
		int duration = Math.max(20, Math.round(effect.getDuration() * lasting(who, kind)));
		if (duration >= effect.getDuration()) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** {@code target} taking {@code amount}: a blast or breath on a moving player lands softer by their Agility. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof Player player) || !(source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.DRAGON_BREATH)
			|| source.is(DamageTypes.FIREBALL) || source.is(DamageTypes.UNATTRIBUTED_FIREBALL))) {
			return amount;
		}
		var v = player.getDeltaMovement();
		if (Math.hypot(v.x, v.z) < MOVING && !player.isFallFlying()) {
			return amount;
		}
		return amount * (1.0F - (1.0F - BLAST_LEAST) * share(player, Stat.AGILITY));
	}
}
