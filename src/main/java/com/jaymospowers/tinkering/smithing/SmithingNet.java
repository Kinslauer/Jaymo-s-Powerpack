package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** (Smithing) The anvil's packets: choosing what to forge, the hammering game's state, and each blow. */
public final class SmithingNet {
	/** How far a smith may stand from the anvil (blocks, squared) for a packet to count. */
	static final double REACH_SQ = 8 * 8;

	/** One choice on the forge screen. */
	public record Choice(String id, ItemStack icon, int ingots) {
		static final StreamCodec<RegistryFriendlyByteBuf, Choice> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Choice::id, ItemStack.STREAM_CODEC, Choice::icon, ByteBufCodecs.VAR_INT, Choice::ingots, Choice::new);
	}

	/** Server → client: open the forge screen for the hot ingots on this anvil. */
	public record OpenForge(BlockPos pos, List<Choice> choices) implements CustomPacketPayload {
		public static final Type<OpenForge> TYPE = new Type<>(JaymosPowers.id("smithing_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenForge> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, OpenForge::pos, Choice.CODEC.apply(ByteBufCodecs.list()), OpenForge::choices, OpenForge::new);

		@Override
		public Type<OpenForge> type() {
			return TYPE;
		}
	}

	/** Client → server: forge this ({@link Forging.Option#id}) from the ingots on the anvil. */
	public record Choose(BlockPos pos, String id) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosPowers.id("smithing_choose"));
		public static final StreamCodec<ByteBuf, Choose> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Choose::pos, ByteBufCodecs.STRING_UTF8, Choose::id, Choose::new);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	/** Server → client: the hammering game at this anvil (off when {@code active} is false). */
	public record Session(BlockPos pos, boolean active, int total, int done, float difficulty, float bonus, long seed)
		implements CustomPacketPayload {
		public static final Type<Session> TYPE = new Type<>(JaymosPowers.id("smithing_session"));
		public static final StreamCodec<ByteBuf, Session> CODEC = StreamCodec.of((buf, s) -> {
			BlockPos.STREAM_CODEC.encode(buf, s.pos);
			buf.writeBoolean(s.active);
			ByteBufCodecs.VAR_INT.encode(buf, s.total);
			ByteBufCodecs.VAR_INT.encode(buf, s.done);
			buf.writeFloat(s.difficulty);
			buf.writeFloat(s.bonus);
			buf.writeLong(s.seed);
		}, buf -> new Session(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readLong()));

		public static Session off(BlockPos pos) {
			return new Session(pos, false, 0, 0, 1.0F, 0.0F, 0L);
		}

		@Override
		public Type<Session> type() {
			return TYPE;
		}
	}

	/** Client → server: a blow landed {@link Forging#MISS}, {@link Forging#GOOD} or {@link Forging#PERFECT}. */
	public record Hit(BlockPos pos, int result) implements CustomPacketPayload {
		public static final Type<Hit> TYPE = new Type<>(JaymosPowers.id("smithing_hit"));
		public static final StreamCodec<ByteBuf, Hit> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Hit::pos, ByteBufCodecs.VAR_INT, Hit::result, Hit::new);

		@Override
		public Type<Hit> type() {
			return TYPE;
		}
	}

	private SmithingNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenForge.TYPE, OpenForge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Session.TYPE, Session.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Hit.TYPE, Hit.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> {
			SmithingAnvilEntity anvil = anvil(context.player(), payload.pos());
			if (anvil != null) {
				anvil.choose(context.player(), payload.id());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Hit.TYPE, (payload, context) -> {
			SmithingAnvilEntity anvil = anvil(context.player(), payload.pos());
			if (anvil != null) {
				anvil.hit(context.player(), payload.result());
			}
		});
	}

	private static SmithingAnvilEntity anvil(ServerPlayer player, BlockPos pos) {
		if (player.blockPosition().distSqr(pos) > REACH_SQ || !player.level().isLoaded(pos)) {
			return null;
		}
		return player.level().getBlockEntity(pos) instanceof SmithingAnvilEntity anvil ? anvil : null;
	}
}
