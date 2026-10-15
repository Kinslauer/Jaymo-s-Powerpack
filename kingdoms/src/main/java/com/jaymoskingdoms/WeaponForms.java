package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.30) Weapon Forms: a weapon's full Better Combat combo (every attack of its string, our twirls, spins and flips
 * among them) is something you learn. A player knows it with the Strength perk {@link Skills.Perk#WEAPON_FORMS}; until
 * then every weapon has only its {@link #basic} swings. Mobs fighting Better Combat's way ({@link MobCombat}) are the
 * same: most only know the basic swings, the {@link #experienced} ones the whole combo.
 * <p>
 * Players' attacks are cut down in Better Combat's own attack pick ({@code mixin.bettercombat.AttackSelectionMixin}),
 * which runs on both sides, so the client's swing and the server's hit agree.
 */
public final class WeaponForms {
	/** The swings a weapon has without the forms. */
	static final int BASIC_ATTACKS = 2;
	/** A realm's fighter grown this many days or more is a veteran (folk first seen grown have been for 0-16 days). */
	static final int VETERAN_DAYS = 12;
	/** Our own flourish animations (twirls, spins, flips): never among the basic swings while there are plain ones. */
	static final String FLOURISH_NAMESPACE = "jaymospowers:";

	private WeaponForms() {
	}

	/** Whether {@code player} has learnt every weapon's full combo. */
	public static boolean knows(Player player) {
		return Skills.rank(player, Skills.Perk.WEAPON_FORMS) > 0;
	}

	/**
	 * A mob that knows its weapon's full combo: rulers and chiefs (every leader: kings, lords, barbarian, goblin and orc
	 * chiefs), a ruler's escort, assassins, the guards of the wealthiest realms ({@link Javelins#elite}), veterans grown
	 * {@link #VETERAN_DAYS} days or more, and illager captains. Everything else (monsters, young guards) swings plainly.
	 */
	public static boolean experienced(Mob mob) {
		if (mob instanceof Folk folk) {
			if (folk.leader() || folk.escort() != null || Assassins.is(folk)) {
				return true;
			}
			if (folk.level() instanceof ServerLevel level) {
				if (CourtWizards.grownDays(level, folk) >= VETERAN_DAYS) {
					return true;
				}
				MinecraftServer server = level.getServer();
				return folk.factionId() >= 0 && Javelins.elite(server, folk.factionId());
			}
			return false;
		}
		return mob instanceof Raider raider && (raider.isCaptain() || raider.isPatrolLeader());
	}

	/**
	 * The swings a weapon keeps without the forms: its first {@link #BASIC_ATTACKS} plain attacks (Better Combat's own
	 * animations, not our flourishes), topped up from the rest in order if it has fewer.
	 */
	public static <T> List<T> basic(List<T> attacks, Function<T, @Nullable String> animation) {
		if (attacks.size() <= BASIC_ATTACKS) {
			return attacks;
		}
		boolean[] keep = new boolean[attacks.size()];
		int kept = 0;
		for (int i = 0; i < attacks.size() && kept < BASIC_ATTACKS; i++) {
			String name = animation.apply(attacks.get(i));
			if (name == null || !name.startsWith(FLOURISH_NAMESPACE)) {
				keep[i] = true;
				kept++;
			}
		}
		for (int i = 0; i < attacks.size() && kept < BASIC_ATTACKS; i++) {
			if (!keep[i]) {
				keep[i] = true;
				kept++;
			}
		}
		// in the weapon's own order
		List<T> basic = new ArrayList<>(BASIC_ATTACKS);
		for (int i = 0; i < attacks.size(); i++) {
			if (keep[i]) {
				basic.add(attacks.get(i));
			}
		}
		return basic;
	}
}
