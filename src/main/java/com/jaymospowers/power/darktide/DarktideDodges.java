package com.jaymospowers.power.darktide;

import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

/**
 * (0.15.4) Darktide and the dodge ({@link Dodge}, the pack's own, in place of Combat Roll): Darktide gets
 * {@link #BONUS} golden {@link Feathers} on top of the row, and a dodge that slips an attack is an evasion
 * (+{@link #EVASION_FLOW} Flow and an ink trail).
 * <ul>
 * <li>Dashes: every Riptide Dash (and flurry) costs a dodge's feathers ({@link Dodge#COST}), and a flurry that lands
 * gives them back.</li>
 * <li>Evasion: within {@link #WINDOW} ticks of the dodge's start, an enemy projectile passing within
 * {@link #NEAR_PROJECTILE} or a mob that has you as its target within {@link #NEAR_MOB}, while nothing hits you, counts
 * once per dodge.</li>
 * </ul>
 */
final class DarktideDodges {
	/** Golden feathers while Darktide is your power (half-feathers: two more dodges). */
	static final int BONUS = 12;
	static final float EVASION_FLOW = 10.0F;
	static final int WINDOW = 8;
	static final double NEAR_PROJECTILE = 2.5, NEAR_MOB = 3.0;

	private DarktideDodges() {
	}

	static void init() {
		Feathers.addBonus(player -> DarktidePower.has(player) ? BONUS : 0);
		Dodge.onDodge((player, direction) -> started(player));
	}

	/** Whether {@code player} has the feathers for a dash. */
	static boolean hasRoll(ServerPlayer player) {
		return Feathers.canSpend(player, Dodge.COST);
	}

	/** A dash spends a dodge's feathers. */
	static void spendRoll(ServerPlayer player) {
		Feathers.spend(player, Dodge.COST);
	}

	/** A landed flurry gives them back. */
	static void refundRoll(ServerPlayer player) {
		Feathers.refund(player, Dodge.COST);
	}

	private static void started(ServerPlayer player) {
		if (!DarktidePower.has(player)) {
			return;
		}
		DarktideLive live = DarktidePower.live(player);
		live.rollStart = player.level().getGameTime();
		live.rollEvaded = false;
		live.rollHit = false;
	}

	static void tickEvasion(ServerPlayer player, DarktideLive live) {
		if (live.rollStart < 0) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.rollStart;
		if (t > WINDOW) {
			live.rollStart = -1L;
			return;
		}
		// a faint ink trail behind the dodge once it's slipped something
		if (live.rollEvaded) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			level.sendParticles(new DustParticleOptions(0x0A1430, 1.0F), player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			return;
		}
		if (live.rollHit) {
			return;
		}
		AABB near = player.getBoundingBox().inflate(NEAR_PROJECTILE);
		boolean threat = !level.getEntitiesOfClass(Projectile.class, near,
			p -> p.getOwner() != player && p.getDeltaMovement().lengthSqr() > 0.04).isEmpty();
		if (!threat) {
			threat = !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR_MOB), m -> m.getTarget() == player && m.isAggressive())
				.isEmpty();
		}
		if (threat) {
			live.rollEvaded = true;
			DarktidePower.addFlow(player, EVASION_FLOW);
		}
	}
}
