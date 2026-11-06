package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.chrono.ChronoTakedowns;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebTakedowns;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.21.23, Kingdoms Rivals patch 9) Chrono's and Web's takedowns, on one input: sneak + attack, bare-handed, at a foe
 * (a weapon in hand keeps it the finisher's hold and the hit streak's quick kill). What it does is the power's:
 * {@link ChronoTakedowns} (a frozen-time beatdown at 8 combo, two at 16; the shadow-strike blink to a marked foe) and
 * {@link WebTakedowns} (a wrap-and-slam at 8 of the hit streak, two at 16; stringing up a foe below your perch).
 */
public final class Takedowns {
	/** How far a takedown may be asked for (the blink reaches furthest), with slack. */
	public static final double ASK_RANGE = 24.0;
	/** Chrono's least combo for anything here (the blink); a takedown's own at {@link HitStreak#READY}. */
	public static final int CHRONO_MIN = ChronoTakedowns.BLINK_COST;

	/** Client → server: sneak + attack, bare-handed, at the entity {@code target}. */
	public record Ask(int target) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosPowers.id("takedown"));
		public static final StreamCodec<ByteBuf, Ask> CODEC = ByteBufCodecs.VAR_INT.map(Ask::new, Ask::target);

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	private Takedowns() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, ctx) -> asked(ctx.player(), payload.target()));
	}

	/** Whether {@code player} has a takedown to ask for just now (either side; the server decides what it is). */
	public static boolean mayAsk(Player player) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
			return false;
		}
		if (ChronoFlow.on(player)) {
			return player.getAttachedOrElse(ChronoFlow.COMBO, 0) >= CHRONO_MIN;
		}
		return Powers.has(player, WebPower.INSTANCE);
	}

	private static void asked(ServerPlayer player, int id) {
		if (!mayAsk(player) || !(player.level().getEntity(id) instanceof LivingEntity target) || target == player || !target.isAlive()
			|| target.distanceTo(player) > ASK_RANGE + 2.0) {
			return;
		}
		if (ChronoFlow.on(player)) {
			ChronoTakedowns.takedown(player, target);
		} else {
			WebTakedowns.takedown(player, target);
		}
	}
}
