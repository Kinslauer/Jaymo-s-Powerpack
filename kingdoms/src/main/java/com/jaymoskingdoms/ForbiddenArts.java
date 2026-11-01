package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.231) Death and blood magic, the forbidden arts ({@link Necromancy}, {@link Hemomancy}). Andy (2026-10-26): "death
 * magic, and blood magic, is there a way you can work that concept in?", and chose them as two new Sorcerer elements. What
 * they cost besides mana and blood:
 * <ul>
 * <li><b>Corruption:</b> every cast leaves lingering alchemical corruption on the caster (Jaymo's Powers' alchemy), death
 * magic a little ({@link #DEATH_CORRUPTION}), blood magic more ({@link #BLOOD_CORRUPTION}).</li>
 * <li><b>The law:</b> cast in a kingdom's land where its people see, it's a heavy crime ({@link Theft#forbidden}), even
 * where the realm holds with magic. Barbarian camps and the wilds don't care.</li>
 * <li><b>The healers:</b> a court wizard or a cleric won't mend a known necromancer or blood mage, nor sell them a
 * scroll ({@link Restoration#services}).</li>
 * </ul>
 */
public final class ForbiddenArts {
	/** Lingering corruption each cast leaves: death magic, blood magic, and raising the dead (on top). */
	static final int DEATH_CORRUPTION = 1, BLOOD_CORRUPTION = 2, RAISING_CORRUPTION = 2;

	/** The forbidden spells a scroll or a staff can hold. */
	static final List<String> DEATH_SPELLS = List.of("raising", "draining", "blight", "whispers", "deaths_door");
	static final List<String> BLOOD_SPELLS = List.of("hemorrhage", "blood_grip", "frenzy");

	private ForbiddenArts() {
	}

	static boolean death(String spellOrElement) {
		return "death".equals(spellOrElement) || DEATH_SPELLS.contains(spellOrElement);
	}

	static boolean blood(String spellOrElement) {
		return "blood".equals(spellOrElement) || BLOOD_SPELLS.contains(spellOrElement);
	}

	static boolean forbidden(String spellOrElement) {
		return death(spellOrElement) || blood(spellOrElement);
	}

	/** A known practitioner: a Death or Blood Sorcerer, or a caster who has learned a forbidden spell (raising and draining excepted: old, common scrolls). */
	static boolean known(Player player) {
		Classes.Chosen c = Classes.of(player);
		if (c.playerClass() == PlayerClass.SORCERER) {
			return forbidden(c.option());
		}
		for (String spell : c.spells()) {
			if (forbidden(spell) && !spell.equals("raising") && !spell.equals("draining")) {
				return true;
			}
		}
		return false;
	}

	/** A forbidden cast by {@code player}: its corruption ({@code extra} on top), and the crime if a realm's people see it. */
	static void cast(ServerPlayer player, ServerLevel level, boolean blood, int extra) {
		PowersCompat.corrupt(player, (blood ? BLOOD_CORRUPTION : DEATH_CORRUPTION) + extra);
		Theft.forbidden(player, level, player.blockPosition(), blood);
		// (0.7.465) the spirit warns of it; the Grue draws nearer
		com.jaymoskingdoms.spirits.Grue.darkAct(player);
	}

	/** (0.7.465) {@code player}'s spirit stops a forbidden cast before it's made (the Grue is near): true if it did, and the cast mustn't happen. */
	static boolean stayed(Player player) {
		return player instanceof ServerPlayer sp && com.jaymoskingdoms.spirits.Grue.stayHand(sp);
	}
}
