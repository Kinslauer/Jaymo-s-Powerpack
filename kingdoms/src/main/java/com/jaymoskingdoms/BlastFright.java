package com.jaymoskingdoms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.576) Villagers run from explosions they hear (Andy, 2026-11-05, seven-mods picklist): within {@link #HEAR}
 * blocks every villager who isn't a fighter runs from the blast and hides, as from a monster ({@link Frights#blast}).
 * Guards, archers, soldiers out with a war party, MCA's own guards and anyone already fighting stand their ground.
 * Called from {@code mixin/equestrian/ServerExplosionMixin}, where horses shy at the same blast.
 */
public final class BlastFright {
	/** How far a blast is heard (blocks), before the villager's temperament scales it. */
	static final double HEAR = 40.0;
	/** A chain of blasts (TNT, a meteor's ring) is one fright: blasts this near the last one, this soon, are skipped. */
	static final double SAME_NEAR = 16.0;
	static final long SAME_TICKS = 10L;

	private static Vec3 lastAt = Vec3.ZERO;
	private static long lastTick = Long.MIN_VALUE;
	private static ServerLevel lastLevel;

	private BlastFright() {
	}

	/** A blast at {@code at}: the villagers who hear it run. */
	public static void heard(ServerLevel level, Vec3 at) {
		long now = level.getGameTime();
		if (level == lastLevel && now - lastTick < SAME_TICKS && at.distanceToSqr(lastAt) < SAME_NEAR * SAME_NEAR) {
			return;
		}
		lastLevel = level;
		lastAt = at;
		lastTick = now;
		Frights.blast(level, at);
	}

	/** {@code v} keeps its post: a guard, an archer, a soldier, or already in a fight. */
	static boolean standsGround(Villager v) {
		if (v instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER || WarParties.isSoldier(w))) {
			return true;
		}
		if (McaCompat.isGuard(v)) {
			return true;
		}
		return v.getTarget() != null || v.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
	}
}
