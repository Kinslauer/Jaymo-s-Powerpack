package com.jaymospowers.power.handofgod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hand of God celestial abilities: freezing and turning the Sun and the Moon. Both act on the
 * Overworld clock through the ServerClockManager: the sky angles are computed from the
 * clock total ticks by the EnvironmentAttributeSystem. Pausing the clock freezes the Sun and
 * the Moon; setting its ticks moves them.
 *
 * <p>Celestial Hold (Q): the overworld clock is paused for HOLD_TICKS ticks. A global effect.
 * One hold at a time. Cooldown HOLD_COOLDOWN ticks.
 *
 * <p>Celestial Turn (E): the overworld clock jumps so the Sun stands where the player looks.
 * Cooldown TURN_COOLDOWN ticks.
 *
 * <p>Neither ability is saved: a server stop never leaves the sky stuck.
 */
public final class CelestialCommand {
	static final int HOLD_TICKS = 100;
	static final int HOLD_COOLDOWN = 1200;
	static final int TURN_COOLDOWN = 600;
	static final long DAY_TICKS = 24000L;

	private static @Nullable Hold active;
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	private static @Nullable ServerPlayer holdOwner;

	private record Hold(int ticksLeft) {}

	private CelestialCommand() {}

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> serverTick());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			active = null;
			holdOwner = null;
			COOLDOWNS.clear();
		});
	}

	static void init(ServerPlayer player) {
		COOLDOWNS.remove(player.getUUID());
	}

	static void clear(ServerPlayer player) {
		if (active != null && holdOwner != null && holdOwner.getUUID().equals(player.getUUID())) {
			unpause(player);
			active = null;
			holdOwner = null;
		}
		COOLDOWNS.remove(player.getUUID());
	}

	private static void serverTick() {
		if (active == null || holdOwner == null) {
			return;
		}
		Hold h = active;
		if (h.ticksLeft() <= 1) {
			active = null;
			unpause(holdOwner);
			holdOwner = null;
			return;
		}
		active = new Hold(h.ticksLeft() - 1);
	}

	static void tick(ServerPlayer player) {}

	static void hold(ServerPlayer player) {
		if (active != null) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.handofgod.hold_taken"));
			return;
		}
		long[] cd = cooldowns(player);
		long now = player.level().getGameTime();
		if (now < cd[0]) {
			long left = cd[0] - now;
			player.sendSystemMessage(Component.translatable("message.jaymospowers.handofgod.hold_cooldown", (left + 19) / 20));
			return;
		}
		ServerLevel overworld = overworld(player);
		if (overworld == null) return;
		Holder<WorldClock> clock = overworldClock(player);
		if (clock == null) return;
		overworld.clockManager().setPaused(clock, true);
		active = new Hold(HOLD_TICKS);
		holdOwner = player;
		cd[0] = now + HOLD_COOLDOWN;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	static void turn(ServerPlayer player) {
		long[] cd = cooldowns(player);
		long now = player.level().getGameTime();
		if (now < cd[1]) {
			long left = cd[1] - now;
			player.sendSystemMessage(Component.translatable("message.jaymospowers.handofgod.turn_cooldown", (left + 19) / 20));
			return;
		}
		ServerLevel overworld = overworld(player);
		if (overworld == null) return;
		Holder<WorldClock> clock = overworldClock(player);
		if (clock == null) return;
		ServerClockManager cm = overworld.clockManager();
		Vec3 look = player.getLookAngle();
		float bearing = (float) Math.toDegrees(Math.atan2(look.x, look.z));
		float sunAngle = Mth.positiveModulo(90.0F - bearing, 360.0F);
		long targetTicks = (long) (sunAngle / 360.0F * DAY_TICKS);
		long currentTotal = cm.getInstance(clock).totalTicks();
		long dayStart = currentTotal / DAY_TICKS * DAY_TICKS;
		cm.setTotalTicks(clock, dayStart + targetTicks);
		cd[1] = now + TURN_COOLDOWN;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 0.8F);
	}

	private static long[] cooldowns(ServerPlayer player) {
		return COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[2]);
	}

	private static @Nullable ServerLevel overworld(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return server != null ? server.overworld() : null;
	}

	private static @Nullable Holder<WorldClock> overworldClock(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return null;
		Registry<WorldClock> registry = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
		return registry.getAny().orElse(null);
	}

	private static void unpause(ServerPlayer player) {
		ServerLevel overworld = overworld(player);
		if (overworld == null) return;
		Holder<WorldClock> clock = overworldClock(player);
		if (clock != null) {
			overworld.clockManager().setPaused(clock, false);
		}
	}
}
