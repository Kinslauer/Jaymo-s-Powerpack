package com.jaymoskingdoms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Zone of truth (fifth edition): a circle on the ground inside which nobody can lie ({@link Liars}). A caster's circle is a
 * held spell that a dispel can end. Judges and jailers raise one too ({@link #court}): a ruler hearing a case, and a guard
 * sentencing a prisoner, so a forged pardon is seen through and a verdict follows the record, not the dice.
 */
public final class Truth {
	static final double RADIUS = 6.0;
	static final int TICKS = 20 * 180;
	/** A court's circle: how far it reaches, how long it stands (ticks). */
	static final double COURT_RADIUS = 7.0;
	static final int COURT_TICKS = 20 * 60;

	private Truth() {
	}

	static void register() {
		Liars.addHonesty(Truth::inCircle);
	}

	/** Whether {@code who} stands in a caster's circle. */
	static boolean inCircle(LivingEntity who) {
		return who.level() instanceof ServerLevel level && !Fields.at(level, who.position(), Fields.Kind.TRUTH).isEmpty();
	}

	static boolean cast(ServerLevel level, ServerPlayer caster, float power) {
		Vec3 at = NewSpells.aim(level, caster, NewSpells.AIM_REACH);
		int ticks = Math.round(TICKS * power);
		Fields.Field field = Fields.add(Fields.Kind.TRUTH, level, at, RADIUS, 0.0, ticks, caster.getUUID(), null);
		Concentration.hold(caster, "zone_of_truth", ticks, field::end);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.6F);
		Fx.ring(level, at.add(0.0, 0.1, 0.0), (float) RADIUS, Fields.Kind.TRUTH.colour);
		return true;
	}

	/** A judge's or jailer's circle at {@code centre}: no lie told there, for a minute. Not the kind a dispel can end. */
	public static void court(ServerLevel level, Vec3 centre) {
		Liars.zone(level, centre, COURT_RADIUS, COURT_TICKS);
		Fx.ring(level, centre.add(0.0, 0.1, 0.0), (float) COURT_RADIUS * 0.6F, Fields.Kind.TRUTH.colour);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.5F, 1.8F);
	}
}
