package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * (Motion patch 0) Flips in the air, after Actions &amp; Stuff's idea (Andy: "make this how web flips in air in addition to
 * his current flip off of a jump. It should also be accessible in freerun"). A single tap of Sneak while airborne,
 * well above the ground, flips you the way you're steering: over forwards with Forward or nothing held, over
 * backwards with Back, a cartwheel with a strafe key. Two taps are still the dodge and the landing roll
 * ({@code DodgeClient}), so the client waits out the double-tap window before it asks ({@code AirFlipClient}).
 * <p>Who may: a web slinger (beside the flips his swings and slides already throw), and a freerunner with Freerunner
 * rank 1 or more. It's for show: no speed, no damage, a few Feathers, and a little Stealth experience. Drawn by the web
 * slinger's whole-body pose ({@link WebPower#POSE}, {@code WebBodyPose}), which draws for anyone it's set on.
 */
public final class AirFlip {
	// ===== the knobs ================================================================================================
	/** Feathers a flip costs. */
	public static final int COST = 2;
	/** Client: the ground must be at least this far under the feet (blocks), so a tap near touchdown is left for the landing roll. */
	public static final double MIN_HEIGHT = 2.0;
	/** Server: at most one flip this often (ticks). */
	static final int GAP_TICKS = 10;
	// ================================================================================================================

	/** Client → server: flip {@code kind} ({@link WebPose#FRONT_FLIP}, {@link WebPose#BACK_FLIP} or {@link WebPose#CARTWHEEL}), turning to {@code side} (±1). */
	public record Flip(int kind, int side) implements CustomPacketPayload {
		public static final Type<Flip> TYPE = new Type<>(JaymosPowers.id("air_flip"));
		public static final StreamCodec<ByteBuf, Flip> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Flip::kind, ByteBufCodecs.VAR_INT, Flip::side, Flip::new);

		@Override
		public Type<Flip> type() {
			return TYPE;
		}
	}

	private AirFlip() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Flip.TYPE, Flip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Flip.TYPE, (payload, ctx) -> flip(ctx.player(), payload));
	}

	/** Whether this player may flip in the air at all (both sides: the client reads the synced ranks). */
	public static boolean allowed(Player player) {
		return Powers.has(player, WebPower.INSTANCE) || Freerun.perk(player, Freerun.Perk.FREERUNNER) > 0;
	}

	private static void flip(ServerPlayer player, Flip flip) {
		int kind = flip.kind();
		if (kind != WebPose.FRONT_FLIP && kind != WebPose.BACK_FLIP && kind != WebPose.CARTWHEEL) {
			return;
		}
		if (!allowed(player) || player.onGround() || !Ledge.canHang(player) || WebPower.ragdolled(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebPose current = player.getAttached(WebPower.POSE);
		if (current != null && now - current.start() < Math.max(current.ticks(), GAP_TICKS)) {
			// nothing cut short: a dodge, a ragdoll, a trick still going
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		WebPower.pose(player, kind, flip.side() < 0 ? -1 : 1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, 1.8F);
		player.addTag(Freerun.XP_TAG);
	}
}
