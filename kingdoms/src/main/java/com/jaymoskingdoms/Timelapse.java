package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * (0.7.4) The night as a timelapse, our own take on Sleeping Overhaul 2 (Forge only): once enough players are fast asleep,
 * instead of the clock jumping to morning, the whole world runs faster: the server ticks at {@link #TICK_RATE} (vanilla's
 * {@code /tick rate}, so villagers walk, build and farm at speed and the sleepers can watch them), and on top of that the
 * Overworld's clock runs {@link #SPEED} times as fast, both eased in over {@link #RAMP_TICKS}. The world only goes as fast
 * as the server keeps up. Both rates are sent to every player, so what they see moves smoothly. Anyone can leave bed at
 * any time (the Leave Bed button or Escape), and the moment too few are asleep everything goes back to its own pace
 * wherever the night got to. On reaching the morning it stops exactly at vanilla's wake-up time and vanilla wakes
 * everyone and clears the rain as ever.
 * <p>
 * The hours the clock runs on ahead of the ticks aren't ticked through, so the settlements make them up at once
 * afterwards ({@link Nights#pass}), as they do for a skipped night ({@link Nights} only sees jumps, and a timelapse moves
 * the clock a little each tick).
 */
public final class Timelapse {
	/**
	 * The server's ticks a second while everyone's asleep (vanilla's is 20, so 5 times as fast), and how many times as fast
	 * as that the clock runs: 30 times in all, a night in about 20 s if the server keeps up.
	 */
	static final float TICK_RATE = 100.0F, SPEED = 6.0F;
	/** Ticks the world takes to speed up from its own pace. */
	static final int RAMP_TICKS = 40;

	private static boolean active;
	private static long startClock, startTick, target;
	private static int ran;
	/** The server's tick rate before the night ran by, put back after. */
	private static float tickRate = 20.0F;

	private Timelapse() {
	}

	static void register() {
		// never leave the clock racing in a save
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (active) {
				stop(Factions.level(server));
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			active = false;
			ServerLevel level = Factions.level(server);
			level.dimensionType().defaultClock().ifPresent(clock -> {
				float rate = server.clockManager().getInstance(clock).rate();
				if (rate > 1.0F && rate <= SPEED) {
					server.clockManager().setRate(clock, 1.0F);
				}
			});
		});
	}

	/**
	 * In place of {@code ServerLevel.tick}'s "enough are sleeping" check: true only when vanilla should wake everyone now,
	 * which, with a timelapse, is once the clock has run to the morning.
	 */
	public static boolean enoughSleeping(ServerLevel level, SleepStatus status, int percentage, List<ServerPlayer> players) {
		boolean enough = status.areEnoughSleeping(percentage) && status.areEnoughDeepSleeping(percentage, players);
		Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
		if (level.dimension() != Level.OVERWORLD || clock.isEmpty()) {
			return enough;
		}
		if (!enough || !level.getGameRules().get(GameRules.ADVANCE_TIME)) {
			if (active) {
				stop(level);
			}
			return enough;
		}
		MinecraftServer server = level.getServer();
		ServerClockManager clocks = server.clockManager();
		long now = level.getOverworldClockTime();
		if (!active) {
			long wake = wakeTime(server, clock.get(), now);
			if (wake < 0L) {
				return true;
			}
			active = true;
			startClock = now;
			startTick = level.getGameTime();
			target = wake;
			ran = 0;
			tickRate = server.tickRateManager().tickrate();
		}
		if (now >= target) {
			// back to exactly the wake-up time (the last step may overshoot it), so vanilla's move to it stays put
			clocks.setTotalTicks(clock.get(), target);
			stop(level);
			return true;
		}
		if (ran < RAMP_TICKS) {
			ran++;
			float eased = (float) ran / RAMP_TICKS;
			clocks.setRate(clock.get(), 1.0F + (SPEED - 1.0F) * eased);
			if (tickRate < TICK_RATE) {
				server.tickRateManager().setTickRate(tickRate + (TICK_RATE - tickRate) * eased);
			}
		}
		return false;
	}

	/** Whether the Overworld's night is running by just now. */
	public static boolean running() {
		return active;
	}

	/** The clock's next wake-up time from {@code now}, or -1 if it has none. */
	private static long wakeTime(MinecraftServer server, Holder<WorldClock> clock, long now) {
		Map<ResourceKey<ClockTimeMarker>, ClockTimeMarker> markers = new HashMap<>();
		server.registryAccess().lookupOrThrow(Registries.TIMELINE).listElements().forEach(t -> t.value().registerTimeMarkers((key, marker) -> {
			if (marker.clock().equals(clock)) {
				markers.put(key, marker);
			}
		}));
		ClockTimeMarker wake = markers.get(ClockTimeMarkers.WAKE_UP_FROM_SLEEP);
		if (wake == null || wake.occursAt(now)) {
			return -1L;
		}
		return wake.resolveTimeToMoveTo(now);
	}

	private static void stop(ServerLevel level) {
		active = false;
		level.dimensionType().defaultClock().ifPresent(clock -> level.getServer().clockManager().setRate(clock, 1.0F));
		if (tickRate < TICK_RATE) {
			level.getServer().tickRateManager().setTickRate(tickRate);
		}
		long skipped = (level.getOverworldClockTime() - startClock) - (level.getGameTime() - startTick);
		if (skipped >= Nights.MIN_JUMP) {
			Nights.pass(level, Math.min(Nights.MOST, skipped));
		}
	}
}
