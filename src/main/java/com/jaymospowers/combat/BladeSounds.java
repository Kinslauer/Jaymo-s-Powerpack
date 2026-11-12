package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Our own blade sounds (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: it has sixteen swings and sixteen
 * clashes a blade, each at a slightly random pitch; ours are synthesised, {@code tools/gen_blade_sounds.py}): five
 * swings each for light, medium and heavy weapons, six clashes of steel, three draws. Every play picks one at random
 * and bends its pitch a little, so no two blows sound alike. Better Combat's own swing sounds are left alone: these
 * are for the cuts, throws and parries that are ours.
 */
public final class BladeSounds {
	public static final int LIGHT = 0, MEDIUM = 1, HEAVY = 2;
	/** How far a play's pitch wanders either side of the pitch asked for. */
	static final float PITCH_SPREAD = 0.08F;

	private static final SoundEvent[] SWINGS = {
		SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.swing.light")),
		SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.swing.medium")),
		SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.swing.heavy"))};
	private static final SoundEvent CLASH = SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.clash"));

	private BladeSounds() {
	}

	/** How heavy {@code weapon} sounds: a great weapon or a polearm heavy, a sword medium, anything else light. */
	public static int weight(ItemStack weapon) {
		Armed.Kind kind = Armed.kind(weapon);
		if (kind == Armed.Kind.TWO_HAND || kind == Armed.Kind.POLE) {
			return HEAVY;
		}
		return kind == Armed.Kind.ONE_HAND && weapon.is(net.minecraft.tags.ItemTags.SWORDS) ? MEDIUM : LIGHT;
	}

	/** A swing of {@code weight} at {@code at}, for everyone near. Kingdoms' blade forms call this by name. */
	public static void swing(ServerLevel level, Vec3 at, int weight, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, SWINGS[Math.max(LIGHT, Math.min(HEAVY, weight))], SoundSource.PLAYERS, volume, bend(level, pitch));
	}

	/** Steel meeting steel at {@code at}. */
	public static void clash(ServerLevel level, Vec3 at, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, CLASH, SoundSource.PLAYERS, volume, bend(level, pitch));
	}

	private static float bend(ServerLevel level, float pitch) {
		return pitch + (level.getRandom().nextFloat() - 0.5F) * 2.0F * PITCH_SPREAD;
	}
}
