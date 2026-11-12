package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.power.zfighter.ZFighterAndroid;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The stomach growl (Andy's ImmersiveFX pick, built as our own): a hungry player's stomach growls now and then, and
 * everyone near hears it (a sound from the server, at their middle), more often and louder the emptier they are. A
 * thirsty one (by Jaymo's Kitchen's thirst) swallows dry and rasps instead, now and then. Androids and the Divine-born
 * never make either sound, and nobody does while asleep, in creative or spectator, or dead.
 */
public final class StomachSounds {
	/** Growls start at this much food (of 20), and come every {@link #GROWL_MIN}..{@link #GROWL_MAX} ticks at that, half that at none. */
	static final int GROWL_FOOD = 6;
	static final int GROWL_MIN = 600, GROWL_MAX = 1800;
	/** A growl's volume at the start of hunger and at an empty stomach (heard about 16 blocks off at full). */
	static final float GROWL_QUIET = 0.35F, GROWL_LOUD = 0.75F;
	/** Thirst: dry swallows come every {@link #DRY_MIN}..{@link #DRY_MAX} ticks once thirsty, this loud. */
	static final int DRY_MIN = 700, DRY_MAX = 2000;
	static final float DRY_VOLUME = 0.3F;
	/** Checked this often (ticks). */
	static final int CHECK = 20;

	static final Holder<SoundEvent> GROWL = Holder.direct(SoundEvent.createVariableRangeEvent(JaymosPowers.id("immersive.stomach.growl")));
	static final Holder<SoundEvent> DRY = Holder.direct(SoundEvent.createVariableRangeEvent(JaymosPowers.id("immersive.stomach.thirst")));

	/** Player → the game time of their next growl, and of their next dry swallow. */
	private static final Map<UUID, long[]> NEXT = new HashMap<>();

	private StomachSounds() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StomachSounds::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT.clear());
	}

	/** Whether {@code player}'s body makes these sounds at all. */
	static boolean hungers(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping()
			&& !ZFighterAndroid.android(player) && !ZFighterLineage.divine(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!hungers(player)) {
				continue;
			}
			long now = player.level().getGameTime();
			long[] next = NEXT.computeIfAbsent(player.getUUID(), k -> new long[] {0L, 0L});
			RandomSource random = player.getRandom();
			int food = player.getFoodData().getFoodLevel();
			if (food <= GROWL_FOOD) {
				float empty = 1.0F - food / (float)GROWL_FOOD;
				if (next[0] == 0L) {
					next[0] = now + wait(random, GROWL_MIN, GROWL_MAX, empty);
				} else if (now >= next[0]) {
					next[0] = now + wait(random, GROWL_MIN, GROWL_MAX, empty);
					play(player, GROWL, GROWL_QUIET + (GROWL_LOUD - GROWL_QUIET) * empty, 0.85F + random.nextFloat() * 0.3F);
				}
			} else {
				next[0] = 0L;
			}
			int water = KitchenCompat.water(player);
			if (water <= KitchenCompat.THIRSTY_WATER) {
				float dry = 1.0F - water / (float)KitchenCompat.THIRSTY_WATER;
				if (next[1] == 0L) {
					next[1] = now + wait(random, DRY_MIN, DRY_MAX, dry);
				} else if (now >= next[1]) {
					next[1] = now + wait(random, DRY_MIN, DRY_MAX, dry);
					play(player, DRY, DRY_VOLUME * (0.7F + 0.3F * dry), 0.9F + random.nextFloat() * 0.2F);
				}
			} else {
				next[1] = 0L;
			}
		}
	}

	/** A wait between {@code min} and {@code max}, down to half that as {@code need} reaches 1. */
	private static long wait(RandomSource random, int min, int max, float need) {
		return (long)((min + random.nextInt(max - min + 1)) * (1.0F - 0.5F * need));
	}

	private static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY() + player.getBbHeight() * 0.45, player.getZ(), sound, SoundSource.PLAYERS,
			volume, pitch);
	}
}
