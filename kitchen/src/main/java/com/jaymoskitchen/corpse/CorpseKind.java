package com.jaymoskitchen.corpse;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;

/** What a corpse is, which decides how it has to be dealt with. */
public enum CorpseKind {
	/** Carry it to a butcher block and chop it with an axe for its meat and hide. */
	ANIMAL,
	/** Search it, then burn it: left alone, it gets back up. */
	ZOMBIE,
	/** Pull its bones out one at a time, or burn it (it catches in the sun) and pick the bones from the ashes. */
	SKELETON,
	/** Search it, then burn it or leave it to rot. */
	OTHER,
	/** A dead player: right-click for their hotbar back; burn it, or it gets up as a zombie wearing their face. */
	PLAYER;

	public static CorpseKind of(LivingEntity mob) {
		if (mob.is(Corpses.ZOMBIE_CORPSES)) {
			return ZOMBIE;
		}
		if (mob.is(Corpses.SKELETON_CORPSES)) {
			return SKELETON;
		}
		return mob instanceof Animal ? ANIMAL : OTHER;
	}

	public boolean burns() {
		return true;
	}

	public static CorpseKind byName(String name) {
		for (CorpseKind kind : values()) {
			if (kind.name().equals(name)) {
				return kind;
			}
		}
		return OTHER;
	}
}
