package com.jaymospowers.alchemy.brewing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.cauldron.CauldronStirring;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** (0.20.13) The grinding and stirring bars' packets: the bar's state down, each stroke up. */
public final class BrewingNet {
	/** How far a brewer may stand from the block (blocks, squared) for a packet to count. */
	static final double REACH_SQ = 8 * 8;

	/** Server to client: the grinding or stirring bar at this block (off when {@code active} is false). {@code last}: the stroke just judged, -1 for none. */
	public record BrewBar(BlockPos pos, boolean active, int game, int total, int done, float bonus, long seed, int last)
		implements CustomPacketPayload {
		public static final Type<BrewBar> TYPE = new Type<>(JaymosPowers.id("brew_bar"));
		public static final StreamCodec<ByteBuf, BrewBar> CODEC = StreamCodec.of((buf, s) -> {
			BlockPos.STREAM_CODEC.encode(buf, s.pos);
			buf.writeBoolean(s.active);
			ByteBufCodecs.VAR_INT.encode(buf, s.game);
			ByteBufCodecs.VAR_INT.encode(buf, s.total);
			ByteBufCodecs.VAR_INT.encode(buf, s.done);
			buf.writeFloat(s.bonus);
			buf.writeLong(s.seed);
			buf.writeInt(s.last);
		}, buf -> new BrewBar(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readLong(), buf.readInt()));

		public static BrewBar off(BlockPos pos) {
			return new BrewBar(pos, false, 0, 0, 0, 0.0F, 0L, -1);
		}

		@Override
		public Type<BrewBar> type() {
			return TYPE;
		}
	}

	/** Client to server: a stroke at this arrow position (0 to 100) for this game. */
	public record BrewStroke(BlockPos pos, int game, float arrow) implements CustomPacketPayload {
		public static final Type<BrewStroke> TYPE = new Type<>(JaymosPowers.id("brew_stroke"));
		public static final StreamCodec<ByteBuf, BrewStroke> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, BrewStroke::pos, ByteBufCodecs.VAR_INT, BrewStroke::game, ByteBufCodecs.FLOAT, BrewStroke::arrow,
			BrewStroke::new);

		@Override
		public Type<BrewStroke> type() {
			return TYPE;
		}
	}

	private BrewingNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(BrewBar.TYPE, BrewBar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BrewStroke.TYPE, BrewStroke.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BrewStroke.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			BlockPos pos = payload.pos();
			if (player.blockPosition().distSqr(pos) > REACH_SQ || !player.level().isLoaded(pos)) {
				return;
			}
			if (payload.game() == BrewQuality.GRIND_GAME) {
				if (player.level().getBlockEntity(pos) instanceof MortarEntity mortar) {
					mortar.stroke(player, payload.arrow());
				}
			} else if (payload.game() == BrewQuality.STIR_GAME) {
				CauldronStirring.stroke(player, pos, payload.arrow());
			}
		});
	}
}
