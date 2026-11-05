package com.jaymospowers.baubles.artifacts;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * (Artifacts) The moves the client makes for the player (they're its own movement, as every move is) and tells the server
 * about, so it can check the bauble, clear the fall and show the others: the cloud in a bottle's second jump and the helium
 * wind bladder's float.
 */
public final class ArtifactNet {
	public static final byte CLOUD_JUMP = 0, FLOAT = 1;

	/** Client to server: "I made this move". */
	public record Move(byte kind) implements CustomPacketPayload {
		public static final Type<Move> TYPE = new Type<>(JaymosPowers.id("artifact_move"));
		public static final StreamCodec<ByteBuf, Move> CODEC = ByteBufCodecs.BYTE.map(Move::new, Move::kind);

		@Override
		public Type<Move> type() {
			return TYPE;
		}
	}

	/** Players who have used the cloud's jump since they last stood on something. Server memory only. */
	private static final Set<UUID> SPENT = new HashSet<>();

	private ArtifactNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Move.TYPE, Move.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (payload, context) -> move(context.player(), payload.kind()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!SPENT.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.onGround() || player.isInWater() || player.getAbilities().flying) {
						SPENT.remove(player.getUUID());
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPENT.remove(handler.getPlayer().getUUID()));
	}

	private static void move(ServerPlayer player, byte kind) {
		if (player.isSpectator() || player.onGround() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (kind == CLOUD_JUMP && ArtifactEffects.wears(player, ArtifactBaubles.CLOUD_IN_A_BOTTLE) && SPENT.add(player.getUUID())) {
			player.resetFallDistance();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
		} else if (kind == FLOAT && ArtifactEffects.wears(player, ArtifactBaubles.WIND_BLADDER)) {
			player.resetFallDistance();
			if (player.getRandom().nextInt(4) == 0) {
				level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
			}
		}
	}
}
