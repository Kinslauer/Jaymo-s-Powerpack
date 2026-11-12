package com.jaymospowers.core.blood;

import com.jaymospowers.JaymosPowers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Blood's own sounds (a pick from Cruor's ideas), made for us from noise ({@code tools/gen_blood_sounds.py}), each a pool
 * of variants: a foot squelching through a fresh pool ({@link BloodTrail}), a wet hit when a blade or a shot bites into
 * a bleeding body, and a body bursting when it's crushed or blown open ({@code BladeBlood}).
 */
public final class BloodSounds {
	static final SoundEvent SQUELCH = SoundEvent.createVariableRangeEvent(JaymosPowers.id("blood.squelch"));
	static final SoundEvent HIT = SoundEvent.createVariableRangeEvent(JaymosPowers.id("blood.hit"));
	static final SoundEvent BURST = SoundEvent.createVariableRangeEvent(JaymosPowers.id("blood.burst"));
	/** How far pitch strays either way, so no two sound alike. */
	static final float PITCH_SPREAD = 0.1F;

	private BloodSounds() {
	}

	/** A foot through fresh blood at {@code at}: quieter sneaking, louder running. */
	public static void squelch(ServerLevel level, Vec3 at, SoundSource source, float volume) {
		play(level, at, SQUELCH, source, volume, 1.0F);
	}

	/** A wet bite into a body at {@code at}; heavier blows sound lower. */
	public static void hit(ServerLevel level, Vec3 at, SoundSource source, float volume, float pitch) {
		play(level, at, HIT, source, volume, pitch);
	}

	/** A body bursting at {@code at}. */
	public static void burst(ServerLevel level, Vec3 at, SoundSource source, float volume) {
		play(level, at, BURST, source, volume, 0.9F);
	}

	private static void play(ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch + (level.getRandom().nextFloat() - 0.5F) * 2.0F * PITCH_SPREAD);
	}
}
