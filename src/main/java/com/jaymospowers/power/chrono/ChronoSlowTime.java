package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Slow time at speed (hold the slow-time key while a speedster): the world drops to a crawl while you move normally, for
 * as long as a meter lasts ({@link #METER_TICKS}; it fills back over {@link #REFILL_TICKS} when you let go).
 *
 * <p>Done the way {@link ChronoTimeStop} does its stop, on vanilla's tick manager: the server is frozen (vanilla never
 * freezes players, so the runner and every other player keep moving at full rate) and one world tick is let through every
 * {@link #STEP_EVERY} ticks ({@code stepGameIfPaused}), so mobs, shots, fire and weather run at a fifth of their pace. A real
 * slowed tick rate would slow the runner's own movement too, so it is not used. Other players are not frozen either, so
 * those near you are dragged with Slowness instead. It never starts while a Time Stop, {@code /tick freeze} or a tick sprint
 * has the clock, only unfreezes what it froze itself, and ends if someone unfreezes, if you let go, run dry, stop being a
 * speedster, die or leave.
 */
public final class ChronoSlowTime {
	// ---- tuning ----
	/** The meter: ticks of slow time a full one gives, ticks to refill it when not slowing, and the least it must hold to start (share). */
	static final int METER_TICKS = 140, REFILL_TICKS = 500;
	static final float MIN_TO_START = 0.15F;
	/** One world tick in this many gets through while time is slowed. */
	static final int STEP_EVERY = 5;
	/** Other players this close are dragged with Slowness (amplifier), refreshed each time. */
	static final double DRAG_RADIUS = 24.0;
	static final int DRAG_AMPLIFIER = 3;

	/** Client to server: the slow-time key went down or up. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("chrono_slow_time"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	/** The owner of the slow now, or null. Server thread only. */
	private static @Nullable UUID active;
	private static final Map<UUID, Float> METER = new HashMap<>();
	private static int stepIn;

	private ChronoSlowTime() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.down()) {
				start(player);
			} else if (player.getUUID().equals(active)) {
				end(player.level().getServer(), true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ChronoSlowTime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> end(server, true));
	}

	private static void start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerTickRateManager ticks = server.tickRateManager();
		if (active != null || !ChronoSpeed.overclocked(player) || !player.isAlive() || ChronoTimeStop.running() || ticks.isFrozen() || ticks.isSprinting()
			|| METER.getOrDefault(player.getUUID(), 1.0F) < MIN_TO_START) {
			return;
		}
		active = player.getUUID();
		stepIn = STEP_EVERY;
		ticks.setFrozen(true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), 3.0F, 0xBFE8FF);
	}

	private static void tick(MinecraftServer server) {
		ServerTickRateManager ticks = server.tickRateManager();
		UUID id = active;
		if (id != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			float meter = METER.getOrDefault(id, 1.0F) - 1.0F / METER_TICKS;
			if (!ticks.isFrozen()) {
				end(server, false); // someone else took the clock
			} else if (owner == null || !owner.isAlive() || owner.isSpectator() || !ChronoSpeed.overclocked(owner) || meter <= 0.0F) {
				METER.put(id, Math.max(0.0F, meter));
				end(server, true);
			} else {
				METER.put(id, meter);
				ChronoMeters.set(owner, ChronoMeters.SLOW, meter);
				if (--stepIn <= 0) {
					stepIn = STEP_EVERY;
					ticks.stepGameIfPaused(1);
				}
				if (owner.tickCount % 5 == 0) {
					for (ServerPlayer other : owner.level().players()) {
						if (other != owner && other.distanceToSqr(owner) <= DRAG_RADIUS * DRAG_RADIUS) {
							other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, DRAG_AMPLIFIER, false, false, false));
						}
					}
				}
			}
			return;
		}
		// idle: every meter refills, and the owner's shows while it isn't full
		if (!METER.isEmpty()) {
			METER.replaceAll((uuid, share) -> Math.min(1.0F, share + 1.0F / REFILL_TICKS));
			for (Map.Entry<UUID, Float> e : METER.entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player != null) {
					ChronoMeters.set(player, ChronoMeters.SLOW, e.getValue() < 1.0F && ChronoSpeed.overclocked(player) ? e.getValue() : -1.0F);
				}
			}
			METER.values().removeIf(share -> share >= 1.0F);
		}
	}

	private static void end(MinecraftServer server, boolean unfreeze) {
		UUID id = active;
		active = null;
		if (id == null) {
			return;
		}
		if (unfreeze && server.tickRateManager().isFrozen()) {
			server.tickRateManager().setFrozen(false);
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(id);
		if (owner != null) {
			ServerLevel level = owner.level();
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
	}
}
