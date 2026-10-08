package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.ByteBuf;

/** Web Slinger's packets. */
public final class WebNet {
	private WebNet() {
	}

	/** The two swing webs: from the main hand (the swing key) and the off hand (G, while the off hand is empty). */
	public static final int MAIN = 0;
	public static final int OFF = 1;

	/** Client → server: the {@code hand}'s swing web stuck to {@code anchor} ({@code attach}), or was let go. */
	public record Swing(int hand, boolean attach, Vec3 anchor) implements CustomPacketPayload {
		public static final Type<Swing> TYPE = new Type<>(JaymosPowers.id("web_swing"));
		public static final StreamCodec<ByteBuf, Swing> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Swing::hand,
			ByteBufCodecs.BOOL, Swing::attach,
			Vec3.STREAM_CODEC, Swing::anchor,
			Swing::new);

		@Override
		public Type<Swing> type() {
			return TYPE;
		}
	}

	/** Server → client: the server refused the {@code hand}'s swing web (out of range, no line of sight, anchor gone): let go of it. */
	public record SwingReject(int hand) implements CustomPacketPayload {
		public static final Type<SwingReject> TYPE = new Type<>(JaymosPowers.id("web_swing_reject"));
		public static final StreamCodec<ByteBuf, SwingReject> CODEC = ByteBufCodecs.VAR_INT.map(SwingReject::new, SwingReject::hand);

		@Override
		public Type<SwingReject> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: reel yourself in along a web (Zip, or Yank on something too big to move). The line is stuck at
	 * {@code anchor}; the feet head for {@code target} (or for entity {@code entityId}, if it's not -1). {@code face}
	 * is the ordinal of the block face that was hit (-1 for none): a side face means "perch on top of the ledge".
	 */
	public record Pull(Vec3 anchor, Vec3 target, int entityId, int maxTicks, byte face) implements CustomPacketPayload {
		public static final Type<Pull> TYPE = new Type<>(JaymosPowers.id("web_pull"));
		public static final StreamCodec<ByteBuf, Pull> CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, Pull::anchor,
			Vec3.STREAM_CODEC, Pull::target,
			ByteBufCodecs.VAR_INT, Pull::entityId,
			ByteBufCodecs.VAR_INT, Pull::maxTicks,
			ByteBufCodecs.BYTE, Pull::face,
			Pull::new);

		@Override
		public Type<Pull> type() {
			return TYPE;
		}
	}

	/** Client → server: web wings spread ({@code open}) or folded. */
	public record Wings(boolean open) implements CustomPacketPayload {
		public static final Type<Wings> TYPE = new Type<>(JaymosPowers.id("web_wings"));
		public static final StreamCodec<ByteBuf, Wings> CODEC = ByteBufCodecs.BOOL.map(Wings::new, Wings::open);

		@Override
		public Type<Wings> type() {
			return TYPE;
		}
	}

	/** Client → server: the Web Zip key (Z by default) was pressed. */
	public record Zip() implements CustomPacketPayload {
		public static final Type<Zip> TYPE = new Type<>(JaymosPowers.id("web_zip"));
		public static final StreamCodec<ByteBuf, Zip> CODEC = StreamCodec.unit(new Zip());

		@Override
		public Type<Zip> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Zip.TYPE, Zip.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wings.TYPE, Wings.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Swing.TYPE, Swing.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwingReject.TYPE, SwingReject.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Pull.TYPE, Pull.CODEC);
	}
}
