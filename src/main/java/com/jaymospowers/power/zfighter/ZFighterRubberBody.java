package com.jaymospowers.power.zfighter;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.145, Andy: every heritage gets something as a Z Fighter) A Rubber-folk Z Fighter (Kingdoms heritage
 * {@code rubberfolk}) fights like rubber: blows and close hits bounce off ({@link #BLOWS} less from anything that hits him
 * by hand, claw or weapon), falls barely hurt ({@link #FALLS} less), and the stretchy fists snap back harder ({@link #FISTS}
 * more fist damage). Still an Earthling's lineage otherwise ({@link ZFighterLineage#fromHeritage}). Hooked from
 * {@link ZFighterBattle#modifyDamage} and {@link ZFighterPower#fistBonus}.
 */
final class ZFighterRubberBody {
	static final String TAG = "jaymoskingdoms_heritage_rubberfolk";
	/** The share taken off a melee blow and off a fall. */
	static final float BLOWS = 0.25F, FALLS = 0.5F;
	static final double FISTS = 1.5;

	private ZFighterRubberBody() {
	}

	static boolean is(Player player) {
		return player.entityTags().contains(TAG);
	}

	/** What a hit of {@code damage} does to a Rubber-folk {@code player}: melee blows and falls bounce off. */
	static float bounce(Player player, DamageSource source, float damage) {
		if (!is(player)) {
			return damage;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return damage * (1.0F - FALLS);
		}
		boolean melee = source.getEntity() != null && source.getDirectEntity() == source.getEntity() && !source.is(DamageTypeTags.IS_PROJECTILE)
			&& !source.is(DamageTypeTags.IS_EXPLOSION);
		return melee ? damage * (1.0F - BLOWS) : damage;
	}

	static double fists(Player player) {
		return is(player) ? FISTS : 0.0;
	}
}
