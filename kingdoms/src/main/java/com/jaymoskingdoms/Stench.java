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

	private static final Map<UUID, Long> LAST_REMARK = new HashMap<>();

	private Stench() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Stench::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REMARK.clear());
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
			if (player.isSpectator() || now - LAST_REMARK.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < REMARK_COOLDOWN
				|| PowersCompat.smell(player) < REMARK || player.getRandom().nextFloat() >= REMARK_CHANCE) {
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
}
