package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27): "npcs across the board should think for an extra second to give stealth users the required time to close in
 * for a stealth takedown." Every mob, the first time its eyes pick out a player it means to fight (a combat sighting, through
 * {@code TargetingConditions.test}, from {@code mixin.TargetingThinkMixin}), takes {@link #THINK_TICKS} sizing them up before it
 * can set on them. All that time it hasn't taken them as its foe, so it is still unaware for a stealth kill, a sneak attack or
 * an assassination, and it doesn't turn round: only the eye ({@link Senses}' gauge) fills as it makes up its mind, and the dev
 * view says it is thinking ({@link NpcFocus}). If it still sees them after the second, it sets on them as before.
 * <p>No second is taken when it is already after them, was told of them by a friend ({@link Senses#knows}), or was just hurt by
 * them: a blow is answered at once. A player it hasn't picked out for {@link #FORGET_TICKS} is sized up afresh the next time.
 * Creative and spectator players are left to the game.
 */
public final class ThinkFirst {
	/** The extra second (ticks) a mob thinks before setting on a player it has just picked out. */
	public static final int THINK_TICKS = 20;
	/** Not picked out for this long (ticks), the next sighting is thought over again. */
	static final int FORGET_TICKS = 40;

	/** Each mob's players it is thinking over or has made up its mind about: when it first and last picked them out (game time). */
	private static final Map<Mob, Map<UUID, long[]>> SIZING = new WeakHashMap<>();

	private ThinkFirst() {
	}

	/**
	 * At the end of {@code TargetingConditions.test}, a combat check that passed: whether {@code source} may take {@code target}
	 * now (false: it is still thinking them over).
	 */
	public static boolean ready(@Nullable LivingEntity source, LivingEntity target) {
		if (!(source instanceof Mob mob) || !(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return true;
		}
		if (Comrades.target(mob) == player || Senses.knows(mob, player) || mob.getLastHurtByMob() == player) {
			return true;
		}
		long now = mob.level().getGameTime();
		Map<UUID, long[]> sizing = SIZING.computeIfAbsent(mob, m -> new HashMap<>());
		// (those it hasn't picked out for a while are forgotten)
		sizing.values().removeIf(t -> now - t[1] > FORGET_TICKS);
		long[] seen = sizing.get(player.getUUID());
		if (seen == null) {
			seen = new long[] {now, now};
			sizing.put(player.getUUID(), seen);
		}
		seen[1] = now;
		return now - seen[0] >= THINK_TICKS;
	}

	/** How far {@code mob} is through thinking {@code player} over, 0 (not thinking them over) to just under 1. */
	public static double share(Mob mob, LivingEntity player) {
		Map<UUID, long[]> sizing = SIZING.get(mob);
		long[] seen = sizing == null ? null : sizing.get(player.getUUID());
		if (seen == null || Comrades.target(mob) == player) {
			return 0.0;
		}
		long now = mob.level().getGameTime();
		long into = now - seen[0];
		if (now - seen[1] > FORGET_TICKS || into >= THINK_TICKS) {
			return 0.0;
		}
		return Math.max(1.0, into + 1.0) / (THINK_TICKS + 1.0);
	}

	/** The player {@code mob} is thinking over now and the ticks it has left, or null. */
	public static @Nullable Sizing sizing(Mob mob) {
		Map<UUID, long[]> sizing = SIZING.get(mob);
		if (sizing == null) {
			return null;
		}
		long now = mob.level().getGameTime();
		for (Map.Entry<UUID, long[]> e : sizing.entrySet()) {
			long into = now - e.getValue()[0];
			if (now - e.getValue()[1] <= FORGET_TICKS && into < THINK_TICKS) {
				return new Sizing(e.getKey(), (int) (THINK_TICKS - into));
			}
		}
		return null;
	}

	/** Whom it is thinking over, and the ticks it has left. */
	public record Sizing(UUID who, int left) {
	}
}
