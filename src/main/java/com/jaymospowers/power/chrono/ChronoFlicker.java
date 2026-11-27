package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Blur steps (Andy's "short bursts of speed in any direction, side, back and up"), for a Chrono overclocked in the low
 * gears (the gear a run starts in, {@link #MAX_GEAR}, or under; above that you are simply running). The dodge key with
 * left, right, back or jump held sends one {@link Step}; the client carries the runner along the path at speed for a few
 * ticks ({@code ChronoFlickerClient}, as the wall run does: no teleport, they cross every block between), and this side
 * checks the step is allowed, spends a little hunger and draws the blur. Chrono only: the Z Fighters' dodge is built on
 * different rules, so it is not shared.
 */
public final class ChronoFlicker {
	// ---- tuning ----
	/** The highest gear a step may be made in; the wait between steps (ticks); the food exhaustion one costs. */
	public static final int MAX_GEAR = 3;
	public static final int GAP = 14;
	static final float EXHAUSTION = 0.3F;
	/** The directions a step can go (the byte sent). */
	public static final int LEFT = 0, RIGHT = 1, BACK = 2, UP = 3;
	static final int COLOR = 0xBFE8FF;

	/** Client to server: a blur step in {@code direction}. */
	public record Step(int direction) implements CustomPacketPayload {
		public static final Type<Step> TYPE = new Type<>(JaymosPowers.id("chrono_flicker"));
		public static final StreamCodec<ByteBuf, Step> CODEC = ByteBufCodecs.VAR_INT.map(Step::new, Step::direction);

		@Override
		public Type<Step> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private ChronoFlicker() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Step.TYPE, Step.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Step.TYPE, (payload, ctx) -> step(ctx.player(), payload.direction()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/** Whether {@code player} may blur-step now (also asked by the client, which knows the gear and Overclock). */
	public static boolean able(net.minecraft.world.entity.player.Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isPassenger() && ChronoSpeed.overclocked(player) && !ChronoSpeed.velocity(player)
			&& ChronoGears.gear(player) <= MAX_GEAR && !Downed.isDowned(player) && !player.getAbilities().flying;
	}

	private static void step(ServerPlayer player, int direction) {
		if (!Powers.has(player, ChronoPower.INSTANCE) || !able(player) || direction < LEFT || direction > UP) {
			return;
		}
		Integer last = LAST.get(player.getUUID());
		if (last != null && player.tickCount - last < GAP - 2) {
			return;
		}
		LAST.put(player.getUUID(), player.tickCount);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.9, 0.0);
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		// your left (facing south, east)
		Vec3 across = new Vec3(look.z, 0.0, -look.x);
		Vec3 way = switch (direction) {
			case LEFT -> across;
			case RIGHT -> across.reverse();
			case BACK -> look.reverse();
			default -> new Vec3(0.0, 1.0, 0.0);
		};
		// the blur it leaves: a long streak where it began, and a few motes
		MagicFx.stroke(level, at, at.add(way.reverse().scale(2.2)), 0.35F, COLOR);
		MagicFx.burst(level, at.x, at.y, at.z, COLOR, 0.5F, 4, 0.25, 0.4, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.4F, 1.9F);
	}

	/** Someone left. */
	static void forget(ServerPlayer player) {
		LAST.remove(player.getUUID());
	}
}
