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
 * <p>
 * (0.21.24) Andy: "chrono in time flow should get the display, but for fist users across the board the menu can show
 * while you have an empty hand and are locked on." The same moves sit on the RPG skill bar ({@link #moves}, read by
 * Jaymo's Kingdoms' skill bar source): in Time Flow always, the takedown and the blink; with an empty hand and locked on,
 * whatever takedown your power has. Cast from the bar ({@link #cast}) they go at your lock-on foe, else the one in your sights.
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

	/** (0.21.24) A bar move: the takedown. */
	public static final String TAKEDOWN = "takedown";
	/** (0.21.24) A bar move: Chrono's shadow-strike blink. */
	public static final String BLINK = "blink";

	/**
	 * (0.21.24) The moves {@code player} has on the skill bar just now, either side (for Kingdoms' bar, by reflection): in
	 * Time Flow, the takedown and the blink; otherwise, with an empty hand and locked on, Web's takedown. None with
	 * anything in hand.
	 */
	public static java.util.List<String> moves(Player player) {
		if (!player.getMainHandItem().isEmpty() || player.isSpectator()) {
			return java.util.List.of();
		}
		if (ChronoFlow.on(player)) {
			return java.util.List.of(TAKEDOWN, BLINK);
		}
		if (com.jaymospowers.core.LockOn.target(player) != null && Powers.has(player, WebPower.INSTANCE)) {
			return java.util.List.of(TAKEDOWN);
		}
		return java.util.List.of();
	}

	/** (0.21.24) {@code move} cast from the skill bar: at the lock-on foe, else the one in sights. False if there was none, or it can't go. */
	public static boolean cast(ServerPlayer player, String move) {
		if (!moves(player).contains(move)) {
			return false;
		}
		LivingEntity target = com.jaymospowers.core.LockOn.target(player);
		if (target == null) {
			target = lookedAt(player, ASK_RANGE);
		}
		if (target == null || target == player || !target.isAlive() || target.distanceTo(player) > ASK_RANGE + 2.0) {
			return false;
		}
		if (ChronoFlow.on(player)) {
			return BLINK.equals(move) ? ChronoTakedowns.blinkAt(player, target) : ChronoTakedowns.takedown(player, target);
		}
		return WebTakedowns.takedown(player, target);
	}

	private static @org.jspecify.annotations.Nullable LivingEntity lookedAt(ServerPlayer player, double range) {
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
		net.minecraft.world.phys.Vec3 end = eye.add(look.scale(range));
		net.minecraft.world.phys.BlockHitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = block.getLocation();
		}
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(),
			eye.distanceToSqr(end));
		return hit == null ? null : (LivingEntity)hit.getEntity();
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
