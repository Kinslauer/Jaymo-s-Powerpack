package com.jaymoskingdoms;

import net.minecraft.world.entity.Entity;

/**
 * (0.7.391) Andy: "looters / bandits should not introduce themselves, nor should villagers / bandits instantly get
 * tombstones". The folk who live outside the law: looters robbing the dead ({@link Looters}), assassins
 * ({@link Assassins}), a camp's people (barbarian raiders and chiefs, a cove's pirates, goblins and orcs, camp casters:
 * {@link Barbarians#isCampVillager}) and anyone in a bandit mask or bandana ({@link Bandits#wears}). They never come up to
 * introduce themselves, never put a hand out or take yours, and won't give their name ({@link Introductions#askName});
 * when one dies in a settlement's land, its bearers burn the body with the enemy's dead instead of burying it in their
 * graveyard ({@link Bearers}).
 */
public final class Outlaws {
	private Outlaws() {
	}

	/** {@code e} is an outlaw. */
	public static boolean is(Entity e) {
		return e instanceof Worker w && role(w.role()) || Barbarians.isCampVillager(e) || Bandits.wears(e);
	}

	/** {@code role} is an outlaw's job. */
	static boolean role(int role) {
		return role == Looters.LOOTER || role == Assassins.ASSASSIN || Barbarians.villagerRole(role);
	}
}
