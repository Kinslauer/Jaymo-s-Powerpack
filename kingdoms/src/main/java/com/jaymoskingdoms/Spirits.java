package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.46) Hunger and weariness, after Village Life's (remade, not copied: that mod is All Rights Reserved). When a
 * realm's storehouse has no food left, its townsfolk go hungry: their bellies rumble, they grumble about it and their
 * spirits sink (MCA mood) until it's stocked again. And a day's end tells: of an evening the townsfolk yawn and say
 * they're for bed; at dawn they stretch.
 */
final class Spirits {
	/** How often spirits are thought about (ticks); how far round a seat; how often a storehouse's larder is looked in again (ticks). */
	static final int EVERY = 100;
	static final double WATCHED = 96.0;
	static final long LARDER_TICKS = 1200L;
	/** The chance each think that a hungry villager grumbles (and their mood's fall, MCA), or a weary one yawns. */
	static final float HUNGRY_CHANCE = 0.01F, WEARY_CHANCE = 0.02F, STRETCH_CHANCE = 0.02F;
	static final int HUNGER = -2;
	/** When they're weary (time of day), and stretching. */
	static final long WEARY_FROM = 10500L, WEARY_TO = 12000L, STRETCH_TO = 1000L;
	/** Lines (lang {@code spirits.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 4;
	static final double SPEECH_RANGE = 12.0;

	/** Per realm: when its larder was last looked in, and whether it had food. */
	private static final Map<Integer, long[]> LARDER = new HashMap<>();

	private Spirits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Spirits::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LARDER.clear());
	}

	/** {@code f}'s storehouse is out of food (looked in now and then). */
	static boolean hungry(ServerLevel level, Faction f) {
		long now = level.getGameTime();
		long[] larder = LARDER.get(f.id());
		if (larder == null || now - larder[0] >= LARDER_TICKS) {
			// (a storehouse that isn't loaded, or a realm with none yet, isn't taken for an empty one)
			boolean empty = Storage.loaded(level, f) && Storage.food(level, f) == 0;
			larder = new long[] {now, empty ? 1 : 0};
			LARDER.put(f.id(), larder);
		}
		return larder[1] == 1;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % EVERY != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		long t = Royals.timeOfDay(level);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			boolean hungry = hungry(level, f);
			boolean weary = t >= WEARY_FROM && t < WEARY_TO;
			boolean waking = t < STRETCH_TO;
			if (!hungry && !weary && !waking) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED),
				v -> v.isAlive() && !v.isSleeping() && !Festivals.going(v) && Routines.free(v, t))) {
				if (hungry && random.nextFloat() < HUNGRY_CHANCE) {
					McaCompat.cheer(v, HUNGER);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.2F, 0.5F);
					say(level, v, v.isBaby() ? "hungry_child" : "hungry", random);
				} else if (weary && random.nextFloat() < WEARY_CHANCE) {
					say(level, v, "weary", random);
				} else if (waking && random.nextFloat() < STRETCH_CHANCE) {
					say(level, v, "waking", random);
				}
			}
		}
	}

	private static void say(ServerLevel level, Villager v, String kind, RandomSource random) {
		Speech.say(level, v, Component.translatable("spirits.jaymoskingdoms." + kind + "." + random.nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}
}
