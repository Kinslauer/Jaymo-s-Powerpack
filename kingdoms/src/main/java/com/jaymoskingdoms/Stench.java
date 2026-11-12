package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/**
 * Being smelt (Andy's TFC Hardrock picks: "if you stink you'll be caught, especially among non humans with better noses.
 * Villagers will comment on this"). How bad a player smells is Jaymo's Powers' ({@code core/wet/Smell}, 0 to 100,
 * through {@link PowersCompat#smell}). In {@link Senses#factor} a mob notices a smelly player from further off: a keen
 * nose ({@link #KEEN}: beasts, monsters, goblins and orcs, vampires and werewolves) up to twice as far, a person's
 * ({@link #DULL}) a little. And villagers who catch a whiff say so ({@link #tick}).
 * <p>
 * Andy, on blood that lingers: "EVERYONE will comment on it". Blood a player wears (Powers' {@code Bloodied.noticed}:
 * their own coat, or bloodied armour or weapons) is remarked on before any smell, by everyone who sees it, one after
 * another, each once a while ({@link #BLOOD_COOLDOWN}): fresh blood alarms people, dried blood is
 * frowned at, rotten blood disgusts them. Goblins and orcs have their own lines.
 */
public final class Stench {
	/** How much further a keen nose and a person's notice you at the worst smell. */
	static final double KEEN = 1.0, DULL = 0.25;
	/** Smell below this goes unnoticed; above {@link #REMARK} villagers say so. */
	static final float FAINT = 15.0F, REMARK = 45.0F;
	/** How near a villager must be to remark, how often it's checked (ticks), the chance, and the wait before another. */
	static final double REMARK_RANGE = 5.0;
	static final int REMARK_TICKS = 100, REMARK_COOLDOWN = 2400, REMARK_LINES = 6, GREEN_LINES = 3;
	static final float REMARK_CHANCE = 0.25F;
	/**
	 * Blood on a player: everyone in sight remarks, one a check (the nearest who hasn't yet), and each says it again only
	 * after {@link #BLOOD_COOLDOWN} ticks. Lines per stage below.
	 */
	static final int BLOOD_COOLDOWN = 6000, SAID_BLOOD_MAX = 2000;
	static final String[] BLOOD_STAGES = {"fresh", "dried", "rotten"};
	static final int BLOOD_LINES = 6, GREEN_BLOOD_LINES = 3;

	private static final Map<UUID, Long> LAST_REMARK = new HashMap<>();
	/** When each person last remarked on each player's blood (key: speaker|player). */
	private static final Map<String, Long> SAID_BLOOD = new HashMap<>();

	private Stench() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Stench::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_REMARK.clear();
			SAID_BLOOD.clear();
		});
	}

	/** People: villagers and illagers (not goblins or orcs). Everyone else has a keener nose. */
	static boolean dullNose(Mob mob) {
		Heritage.Kind kind = VillagerHeritage.of(mob);
		if (kind == Heritage.Kind.GOBLIN || kind == Heritage.Kind.ORC) {
			return false;
		}
		return mob instanceof Npc || mob instanceof AbstractIllager;
	}

	/** How much further {@code looker} notices {@code player} for their smell (1: not at all). */
	static double factor(ServerPlayer player, Mob looker) {
		float smell = PowersCompat.smell(player);
		if (smell < FAINT) {
			return 1.0;
		}
		return 1.0 + (smell - FAINT) / (100.0F - FAINT) * (dullNose(looker) ? DULL : KEEN);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % REMARK_TICKS != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long since = now - LAST_REMARK.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
			if (player.isSpectator() || player.isInvisible()) {
				continue;
			}
			int blood = PowersCompat.bloodSeen(player);
			if (blood >= 0) {
				// everyone who sees it says so, one at a time (a check apart), each once a while
				if (remarkBlood(player, Math.min(blood, BLOOD_STAGES.length - 1), now)) {
					LAST_REMARK.put(player.getUUID(), now);
				}
				continue;
			}
			if (since < REMARK_COOLDOWN || PowersCompat.smell(player) < REMARK || player.getRandom().nextFloat() >= REMARK_CHANCE) {
				continue;
			}
			ServerLevel level = player.level();
			List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(REMARK_RANGE),
				v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.hasLineOfSight(player));
			if (near.isEmpty()) {
				continue;
			}
			Villager speaker = near.get(player.getRandom().nextInt(near.size()));
			Heritage.Kind kind = VillagerHeritage.of(speaker);
			boolean green = kind == Heritage.Kind.GOBLIN || kind == Heritage.Kind.ORC;
			String key = green ? "speech.jaymoskingdoms.stench.green." + player.getRandom().nextInt(GREEN_LINES)
				: "speech.jaymoskingdoms.stench." + player.getRandom().nextInt(REMARK_LINES);
			Speech.sayTo(level, speaker, player, Component.translatable(key), Speech.WHITE);
			LAST_REMARK.put(player.getUUID(), now);
		}
	}

	/**
	 * Someone who can see {@code player} and hasn't yet said so remarks on the blood on them ({@code stage}: 0 fresh, 1
	 * dried, 2 rotten): the nearest of them, so over a few checks everyone in sight has had their say.
	 */
	private static boolean remarkBlood(ServerPlayer player, int stage, long now) {
		ServerLevel level = player.level();
		UUID who = player.getUUID();
		List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REMARK_RANGE),
			m -> (m instanceof Villager || m instanceof Npc) && m.isAlive() && !m.isBaby() && !m.isSleeping() && m.hasLineOfSight(player)
				&& now - SAID_BLOOD.getOrDefault(pair(m.getUUID(), who), Long.MIN_VALUE / 2) >= BLOOD_COOLDOWN);
		if (near.isEmpty()) {
			return false;
		}
		Mob speaker = near.stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElseThrow();
		SAID_BLOOD.put(pair(speaker.getUUID(), who), now);
		if (SAID_BLOOD.size() > SAID_BLOOD_MAX) {
			SAID_BLOOD.values().removeIf(at -> now - at >= BLOOD_COOLDOWN);
		}
		Heritage.Kind kind = VillagerHeritage.of(speaker);
		boolean green = kind == Heritage.Kind.GOBLIN || kind == Heritage.Kind.ORC;
		String key = "speech.jaymoskingdoms.blood." + BLOOD_STAGES[stage] + (green ? ".green." + player.getRandom().nextInt(GREEN_BLOOD_LINES)
			: "." + player.getRandom().nextInt(BLOOD_LINES));
		Speech.sayTo(level, speaker, player, Component.translatable(key), Speech.WHITE);
		return true;
	}

	private static String pair(UUID speaker, UUID player) {
		return speaker + "|" + player;
	}
}
