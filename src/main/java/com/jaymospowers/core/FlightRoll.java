package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.smoke.SmokeVapour;
import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.zfighter.ZFighterPower;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The barrel roll: flying with Wind, a Xenoform alien, a Z Fighter's ki or Smoke's Vapour Form, double-tap Left or
 * Right (A / D) and you corkscrew a full turn that way, sliding a few blocks aside. The client spots the taps and moves
 * the player (movement is the client's); the server checks they're flying and syncs the roll so everyone sees it.
 */
public final class FlightRoll {
	/** How long a barrel roll takes (ticks), and the sideways speed at its middle (blocks a tick; about 5 blocks in all). */
	public static final int ROLL_TICKS = 12;
	public static final double SIDE_SPEED = 0.7;
	/** Ticks after a roll ends before the next can start. */
	public static final int COOLDOWN = 4;

	public record Rolling(long start, boolean right) {
		public static final StreamCodec<ByteBuf, Rolling> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Rolling::start,
			ByteBufCodecs.BOOL, Rolling::right,
			Rolling::new);
	}

	/** On a player who's barrel rolling (or last did): synced to everyone, who draw it. */
	public static final AttachmentType<Rolling> ROLLING = AttachmentRegistry.create(JaymosPowers.id("flight_roll"),
		builder -> builder.syncWith(Rolling.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: a barrel roll to the right ({@code right}) or left. */
	public record Roll(boolean right) implements CustomPacketPayload {
		public static final Type<Roll> TYPE = new Type<>(JaymosPowers.id("flight_roll"));
		public static final StreamCodec<ByteBuf, Roll> CODEC = ByteBufCodecs.BOOL.map(Roll::new, Roll::right);

		@Override
		public Type<Roll> type() {
			return TYPE;
		}
	}

	private FlightRoll() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Roll.TYPE, Roll.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Roll.TYPE, (payload, ctx) -> roll(ctx.player(), payload.right()));
	}

	private static void roll(ServerPlayer player, boolean right) {
		long now = player.level().getGameTime();
		Rolling last = player.getAttached(ROLLING);
		// a couple of ticks' grace for network jitter
		if (!flying(player) || last != null && now - last.start() < ROLL_TICKS + COOLDOWN - 2) {
			return;
		}
		player.setAttached(ROLLING, new Rolling(now, right));
		player.resetFallDistance();
		// everyone else hears the whoosh (the roller's own client plays theirs)
		player.level().playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.5F);
	}

	/**
	 * Whether this player is flying under one of the four flights that can barrel roll: Wind (hover or cruise, not a
	 * tumble), a flying Xenoform alien, a Z Fighter on ki, or Smoke's Vapour Form. Both sides (all synced).
	 */
	public static boolean flying(Player player) {
		// (0.11.83) the Rig's thrusters roll too, hovering or in full flight (server: the client has its own check)
		return powerFlying(player) || !player.onGround() && !player.isSpectator() && !player.isPassenger() && com.jaymospowers.rig.Rig.hovering(player);
	}

	/** {@link #flying} without the Rig: one of the powers' flights (the Rig's thrusters keep out of their way). */
	public static boolean powerFlying(Player player) {
		if (player.onGround() || player.isSpectator() || player.isPassenger()) {
			return false;
		}
		if (Powers.has(player, WindPower.INSTANCE)) {
			Integer state = player.getAttached(WindPower.FLIGHT);
			WindFlight flight = state == null ? null : WindFlight.byId(state);
			return flight != null && flight.flying();
		}
		return XenoformState.flight(player) != XenoformState.FLY_NONE
			|| ZFighterPower.flightPose(player) > 0
			|| SmokeVapour.active(player);
	}

	/**
	 * How far through a barrel roll this player is (0 to 1) at {@code gameTime + partialTick}, or null when they aren't
	 * rolling. Positive while rolling right, negative while rolling left.
	 */
	public static @Nullable Float progress(Player player, long gameTime, float partialTick) {
		Rolling rolling = player.getAttached(ROLLING);
		if (rolling == null) {
			return null;
		}
		float t = (gameTime - rolling.start() + partialTick) / ROLL_TICKS;
		if (t < 0.0F || t >= 1.0F) {
			return null;
		}
		return rolling.right() ? t : -t;
	}
}
