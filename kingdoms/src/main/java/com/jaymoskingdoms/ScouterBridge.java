package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * What Jaymo's Powers' scouter asks of Kingdoms (it reaches here by reflection, Kingdoms never compiles against Powers):
 * the realm and the mood of a person it reads, and what a full scan of a beast teaches the one who made it.
 */
public final class ScouterBridge {
	/** What a person's mood reads when there is none (not an MCA villager). */
	public static final int NO_MOOD = Integer.MIN_VALUE;

	private ScouterBridge() {
	}

	/** The name of the realm {@code person} works for or stands in the land of, or "" for none. */
	public static String realm(ServerLevel level, LivingEntity person) {
		Faction faction = person instanceof Worker worker ? Factions.get(level.getServer(), worker.factionId())
			: person instanceof Villager ? Factions.around(level.getServer(), level, person.blockPosition(), 0.0) : null;
		return faction == null ? "" : faction.name();
	}

	/** An MCA villager's mood, -15 to +15; {@link #NO_MOOD} for anyone else. */
	public static int mood(LivingEntity person) {
		return person instanceof Villager villager ? McaCompat.mood(villager) : NO_MOOD;
	}

	/** How far a dead walker is on its road to the horde: 0 walking alone, 1 walking with a horde, 2 leading one (its captain). */
	public static int horde(LivingEntity dead) {
		return ZombieHordes.captain(dead) ? 2 : ZombieHordes.inHorde(dead) ? 1 : 0;
	}

	/**
	 * A full scan of {@code beast}: it counts as studying that kind. The kind is marked met, one with no star yet gets
	 * one (a lesson's worth, {@link FoeStudy#TAUGHT}), and a Druid learns the shapes it is kin to ({@link Druids#scanned}).
	 */
	public static void studied(ServerPlayer player, LivingEntity beast) {
		if (beast instanceof Player) {
			return;
		}
		String kind = FoeStudy.kind(beast);
		if (!FoeStudy.met(player, kind)) {
			Map<String, Integer> fought = new HashMap<>(player.getAttachedOrElse(FoeStudy.FOUGHT, Map.<String, Integer>of()));
			fought.put(kind, 0);
			player.setAttached(FoeStudy.FOUGHT, fought);
		}
		if (beast instanceof Mob && FoeStudy.stars(player, kind) == 0) {
			Map<String, Integer> taught = new HashMap<>(player.getAttachedOrElse(FoeStudy.TAUGHT, Map.<String, Integer>of()));
			taught.merge(kind, 1, Integer::sum);
			player.setAttached(FoeStudy.TAUGHT, taught);
		}
		Druids.scanned(player, player.level(), kind);
	}
}
