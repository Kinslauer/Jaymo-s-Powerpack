package com.jaymospowers.core;

import com.jaymospowers.compat.KingdomsCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Awakening pick) Your side gets caught too. When a power's move freezes or slows an enemy player, the caster's own
 * teammates near that player are caught by it as well, so stopping time in a fight between players has a cost. Teammates
 * are a Kingdoms party ({@code Parties.allied}, by reflection) or the same scoreboard team; the caster is never caught by their
 * own move. What counts:
 * <ul>
 * <li>a slow: Slowness, or Powers' Freezing, laid by a power while it acts ({@link #effect}, from the effect mixin);</li>
 * <li>a freeze: frost set on the enemy while a power acts ({@link #frozen}, from the frozen-ticks mixin);</li>
 * <li>Chrono's Stasis Field: an enemy player caught in it pulls the caster's teammates within {@link #RANGE} of the field into it
 * ({@code StasisField}). Time Stop and Slow Time already catch every player but the Chrono.</li>
 * </ul>
 */
public final class TeamTime {
	/** How near the enemy (or the field) a teammate must be to be caught (blocks). */
	public static final double RANGE = 8.0;

	private TeamTime() {
	}

	/** Whether {@code a} and {@code b} are on the same side: one Kingdoms party, or one scoreboard team. */
	public static boolean allied(ServerPlayer a, ServerPlayer b) {
		if (a == b || a.isAlliedTo(b)) {
			return true;
		}
		return Boolean.TRUE.equals(KingdomsCompat.call("Parties", "allied", new Class<?>[] {ServerPlayer.class, ServerPlayer.class}, false, a, b));
	}

	/** {@code caster}'s teammates within {@link #RANGE} of {@code near}, the caster and {@code near} left out. */
	public static List<ServerPlayer> side(ServerPlayer caster, LivingEntity near) {
		List<ServerPlayer> side = new ArrayList<>();
		for (ServerPlayer other : caster.level().players()) {
			if (other != caster && other != near && other.isAlive() && !other.isSpectator() && !other.isCreative()
				&& other.distanceToSqr(near) <= RANGE * RANGE && allied(caster, other)) {
				side.add(other);
			}
		}
		return side;
	}

	/** The power acting now, if {@code target} is a player it counts as an enemy of; else null. */
	private static ServerPlayer enemyOf(LivingEntity target) {
		ServerPlayer caster = Powers.actor();
		if (caster == null || !(target instanceof ServerPlayer enemy) || enemy == caster || target.level().isClientSide() || allied(caster, enemy)) {
			return null;
		}
		return caster;
	}

	/** A slow or a freeze laid on an enemy player while a power acts: the caster's teammates near them get the same. */
	public static void effect(LivingEntity target, MobEffectInstance effect) {
		if (!effect.is(MobEffects.SLOWNESS) && !(com.jaymospowers.frost.Freezing.FREEZING != null && effect.is(com.jaymospowers.frost.Freezing.FREEZING))) {
			return;
		}
		ServerPlayer caster = enemyOf(target);
		if (caster == null) {
			return;
		}
		for (ServerPlayer mate : side(caster, target)) {
			mate.addEffect(new MobEffectInstance(effect));
		}
	}

	/** Frost set on an enemy player while a power acts (more than they had): the caster's teammates near them are as frozen. */
	public static void frozen(LivingEntity target, int ticks) {
		if (ticks <= target.getTicksFrozen()) {
			return;
		}
		ServerPlayer caster = enemyOf(target);
		if (caster == null) {
			return;
		}
		for (ServerPlayer mate : side(caster, target)) {
			if (mate.getTicksFrozen() < ticks) {
				mate.setTicksFrozen(ticks);
			}
		}
	}
}
