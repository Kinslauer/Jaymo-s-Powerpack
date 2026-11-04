package com.jaymospowers.jewellery;

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
import org.jspecify.annotations.Nullable;

/** (0.19.87) The lapidary wheel's packets: the cuts on offer, the choice, the cutting game's state and each stroke. */
public final class JewelleryNet {
	/** How far a cutter may stand from the wheel (blocks, squared) for a packet to count. */
	static final double REACH_SQ = 8 * 8;

	/** One cut on the cut screen. */
	public record CutChoice(String id, int strokes) {
		static final StreamCodec<ByteBuf, CutChoice> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CutChoice::id, ByteBufCodecs.VAR_INT, CutChoice::strokes, CutChoice::new);
	}

	/** Server to client: open the cut screen for the gem on this wheel. */
	public record OpenCuts(BlockPos pos, ItemStack gem, List<CutChoice> cuts) implements CustomPacketPayload {
		public static final Type<OpenCuts> TYPE = new Type<>(JaymosPowers.id("jewellery_cuts"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCuts> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, OpenCuts::pos, ItemStack.STREAM_CODEC, OpenCuts::gem,
			CutChoice.CODEC.apply(ByteBufCodecs.list()), OpenCuts::cuts, OpenCuts::new);

		@Override
		public Type<OpenCuts> type() {
			return TYPE;
		}
	}

	/** Client to server: cut the gem on this wheel this way. */
	public record ChooseCut(BlockPos pos, String cut) implements CustomPacketPayload {
		public static final Type<ChooseCut> TYPE = new Type<>(JaymosPowers.id("jewellery_choose_cut"));
		public static final StreamCodec<ByteBuf, ChooseCut> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ChooseCut::pos, ByteBufCodecs.STRING_UTF8, ChooseCut::cut, ChooseCut::new);

		@Override
		public Type<ChooseCut> type() {
			return TYPE;
		}
	}

	/** Server to client: the cutting game at this wheel (off when {@code active} is false). {@code last} is the last stroke's result or -1. */
	public record CutSession(BlockPos pos, boolean active, int total, int done, float difficulty, float bonus, long seed, int chips, int last)
		implements CustomPacketPayload {
		public static final Type<CutSession> TYPE = new Type<>(JaymosPowers.id("jewellery_cut_session"));
		public static final StreamCodec<ByteBuf, CutSession> CODEC = StreamCodec.of((buf, s) -> {
			BlockPos.STREAM_CODEC.encode(buf, s.pos);
			buf.writeBoolean(s.active);
			ByteBufCodecs.VAR_INT.encode(buf, s.total);
			ByteBufCodecs.VAR_INT.encode(buf, s.done);
			buf.writeFloat(s.difficulty);
			buf.writeFloat(s.bonus);
			buf.writeLong(s.seed);
			ByteBufCodecs.VAR_INT.encode(buf, s.chips);
			buf.writeByte(s.last);
		}, buf -> new CutSession(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readLong(), ByteBufCodecs.VAR_INT.decode(buf),
			buf.readByte()));

		public static CutSession off(BlockPos pos) {
			return new CutSession(pos, false, 0, 0, 1.0F, 0.0F, 0L, 0, -1);
		}

		@Override
		public Type<CutSession> type() {
			return TYPE;
		}
	}

	/** Client to server: a stroke with the arrow at this place on the bar (0 to 100). */
	public record Stroke(BlockPos pos, float arrow) implements CustomPacketPayload {
		public static final Type<Stroke> TYPE = new Type<>(JaymosPowers.id("jewellery_stroke"));
		public static final StreamCodec<ByteBuf, Stroke> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Stroke::pos, ByteBufCodecs.FLOAT, Stroke::arrow, Stroke::new);

		@Override
		public Type<Stroke> type() {
			return TYPE;
		}
	}

	private JewelleryNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenCuts.TYPE, OpenCuts.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CutSession.TYPE, CutSession.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChooseCut.TYPE, ChooseCut.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Stroke.TYPE, Stroke.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChooseCut.TYPE, (payload, context) -> {
			LapidaryWheelEntity wheel = wheel(context.player(), payload.pos());
			if (wheel != null) {
				wheel.choose(context.player(), payload.cut());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Stroke.TYPE, (payload, context) -> {
			LapidaryWheelEntity wheel = wheel(context.player(), payload.pos());
			if (wheel != null) {
				wheel.stroke(context.player(), payload.arrow());
			}
		});
	}

	private static @Nullable LapidaryWheelEntity wheel(ServerPlayer player, BlockPos pos) {
		if (player.blockPosition().distSqr(pos) > REACH_SQ || !player.level().isLoaded(pos)) {
			return null;
		}
		return player.level().getBlockEntity(pos) instanceof LapidaryWheelEntity wheel ? wheel : null;
	}
}
