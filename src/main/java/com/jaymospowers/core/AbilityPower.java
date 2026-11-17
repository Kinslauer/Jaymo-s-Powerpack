package com.jaymospowers.core;

import com.jaymospowers.compat.KingdomsCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (Awakening pick) Ability Power: Kingdoms' attribute of that name (its K screen's Attributes tab, for those with a power)
 * read here by reflection, and the relic belt's focus relics on top. It works on what a power does while it acts (its keys
 * and its own tick, {@link Powers#actor}), each lever in its own way:
 * <ul>
 * <li>harm: what the move deals to anyone but its maker is raised by the damage share;</li>
 * <li>reach: a move that picks its target by aim ({@link PickKit#aimed}) looks that much further;</li>
 * <li>duration: what a move lays on someone else (an effect) lasts that much longer.</li>
 * </ul>
 * Without Kingdoms only the focus relics count.
 */
public final class AbilityPower {
	private AbilityPower() {
	}

	/** {@code player}'s Ability Power share for {@code what} ("damage", "reach", "length"): Kingdoms' attribute, 0 without it. */
	public static double share(Player player, String what) {
		Object got = KingdomsCompat.call("Stats", "abilityShare", new Class<?>[] {Player.class, String.class}, 0.0, player, what);
		return got instanceof Double d ? d : 0.0;
	}

	/** Harm dealt while a power acts: its maker's share more (not on the maker, and not on behalf of anyone else). */
	public static float damage(LivingEntity victim, DamageSource source, float damage) {
		ServerPlayer actor = Powers.actor();
		if (actor == null || victim == actor || damage <= 0.0F) {
			return damage;
		}
		Entity by = source.getEntity();
		if (by != null && by != actor) {
			return damage;
		}
		double s = share(actor, "damage") + com.jaymospowers.relics.Relics.focus(actor);
		return s <= 0.0 ? damage : (float)(damage * (1.0 + s));
	}

	/** An aimed move's reach, while a power acts. */
	public static double reach(ServerPlayer player, double reach) {
		if (Powers.actor() != player) {
			return reach;
		}
		return reach * (1.0 + share(player, "reach"));
	}

	/** An effect laid on {@code target} while someone else's power acts: longer by its maker's share. */
	public static MobEffectInstance lengthen(LivingEntity target, MobEffectInstance effect) {
		ServerPlayer actor = Powers.actor();
		if (actor == null || target == actor || effect.isInfiniteDuration() || target.level().isClientSide()) {
			return effect;
		}
		double s = share(actor, "length");
		return s <= 0.0 ? effect : effect.withScaledDuration((float)(1.0 + s));
	}
}
