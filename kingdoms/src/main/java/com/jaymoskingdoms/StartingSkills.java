package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Skills.Perk;
import com.jaymoskingdoms.Skills.Pillar;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Andy: "i'd like the classes to spawn with skills that pertain to their class ... give them one skill and enough points in
 * that tree to get started on another". Picking a first class teaches one skill of the class's kind free (a trainer's gift:
 * {@link Learnables#giveFree}, so a respec keeps it), and brings the class's pillar up to {@link #START_LEVEL} if it was below,
 * so there are points to learn a second skill there (a Thief or Ranger: the freerunning moves). A top-up, not a gift on top,
 * so picking again (a Writ of Rebirth) gives nothing twice.
 */
final class StartingSkills {
	/** The class's pillar is raised to this level: its points, and the freerunning page opens (Climber, Freerunner, Slide: level 3). */
	static final int START_LEVEL = 3;

	private StartingSkills() {
	}

	/** {@code cls}'s free skill. */
	static Perk perk(PlayerClass cls) {
		return switch (cls) {
			// the Rogue's Arts' bare-handed stealth kill, and the quicker one for a hunter
			case THIEF -> Perk.SILENT_SNAP;
			case RANGER -> Perk.QUICK_WORK;
			case WARLORD -> Perk.COMMAND;
			case BRUTE -> Perk.BATTLE_RHYTHM;
			case PALADIN -> Perk.SHIELDBEARER;
			case RIDER -> Perk.WEAPON_FORMS;
			case MARKSMAN -> Perk.AIM_PARTS;
			case MONK -> Perk.SWEEP_KICK;
			case MAGE -> Perk.MEDITATION;
			case SORCERER -> Perk.SORCERY;
			case CLERIC -> Perk.FIELD_DRESSER;
			case DRUID -> Perk.FORAGER;
			case WARLOCK -> Perk.DEVOTION;
			case TINKERER -> Perk.SMITH;
		};
	}

	/** The pillar whose points {@code cls} starts with: the Thief's and Ranger's Stealth (freerunning), else its skill's own. */
	static Pillar pillar(PlayerClass cls) {
		return cls == PlayerClass.THIEF || cls == PlayerClass.RANGER ? Pillar.STEALTH : perk(cls).pillar;
	}

	/** {@code player} has just become a {@code cls}: its free skill, and its pillar up to {@link #START_LEVEL}. */
	static void give(ServerPlayer player, PlayerClass cls) {
		Perk perk = perk(cls);
		boolean taught = Learnables.giveFree(player, perk);
		Pillar pillar = pillar(cls);
		Skills.Progress p = Skills.of(player);
		int least = Skills.total(START_LEVEL);
		boolean raised = p.xp(pillar) < least;
		if (raised) {
			Map<String, Integer> xp = new HashMap<>(p.xp());
			xp.put(pillar.key(), least);
			player.setAttached(Skills.PROGRESS, new Skills.Progress(Map.copyOf(xp), p.ranks()));
		}
		if (taught || raised) {
			Skills.refresh(player);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.class.starting_skills",
				Component.translatable("skill.jaymoskingdoms.perk." + perk.key()), Component.translatable("skill.jaymoskingdoms." + pillar.key()), START_LEVEL)
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
