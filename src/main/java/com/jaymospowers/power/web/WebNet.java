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
	/** Client → server: the suit's charged jump is being held (falls don't count while it is). */
	public record ChargeHold(boolean held) implements CustomPacketPayload {
		public static final Type<ChargeHold> TYPE = new Type<>(JaymosPowers.id("web_charge_hold"));
		public static final StreamCodec<ByteBuf, ChargeHold> CODEC = ByteBufCodecs.BOOL.map(ChargeHold::new, ChargeHold::held);

		@Override
		public Type<ChargeHold> type() {
			return TYPE;
		}
	}

	public record Wings(boolean open) implements CustomPacketPayload {
		public static final Type<Wings> TYPE = new Type<>(JaymosPowers.id("web_wings"));
		public static final StreamCodec<ByteBuf, Wings> CODEC = ByteBufCodecs.BOOL.map(Wings::new, Wings::open);

		@Override
		public Type<Wings> type() {
			return TYPE;
		}
	}

	/** Client → server: picked a symbiote ability from the radial menu (see {@link WebSymbiote}'s ability indices). */
	public record SymbioteUse(int ability) implements CustomPacketPayload {
		public static final Type<SymbioteUse> TYPE = new Type<>(JaymosPowers.id("web_symbiote_use"));
		public static final StreamCodec<ByteBuf, SymbioteUse> CODEC = ByteBufCodecs.VAR_INT.map(SymbioteUse::new, SymbioteUse::ability);

		@Override
		public Type<SymbioteUse> type() {
			return TYPE;
		}
	}

	/** Server → client: the symbiote takes over for {@code ticks}, lurching at entity {@code entityId} (-1: it lets go). */
	public record Takeover(int entityId, int ticks, boolean flee) implements CustomPacketPayload {
		public static final Type<Takeover> TYPE = new Type<>(JaymosPowers.id("web_takeover"));
		public static final StreamCodec<ByteBuf, Takeover> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Takeover::entityId,
			ByteBufCodecs.VAR_INT, Takeover::ticks,
			ByteBufCodecs.BOOL, Takeover::flee,
			Takeover::new);

		@Override
		public Type<Takeover> type() {
			return TYPE;
		}
	}

	/** Server → client: set your own velocity (a dodge's sidestep, a recoil). Player motion is client-authoritative. */
	public record Nudge(Vec3 velocity) implements CustomPacketPayload {
		public static final Type<Nudge> TYPE = new Type<>(JaymosPowers.id("web_nudge"));
		public static final StreamCodec<ByteBuf, Nudge> CODEC = Vec3.STREAM_CODEC.map(Nudge::new, Nudge::velocity);

		@Override
		public Type<Nudge> type() {
			return TYPE;
		}
	}

	/** Server → client: shake the screen this hard (Living Armor landing heavily). */
	public record Shake(float strength) implements CustomPacketPayload {
		public static final Type<Shake> TYPE = new Type<>(JaymosPowers.id("web_shake"));
		public static final StreamCodec<ByteBuf, Shake> CODEC = ByteBufCodecs.FLOAT.map(Shake::new, Shake::strength);

		@Override
		public Type<Shake> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: the symbiote presses in on you for {@code ticks}: the screen darkens under a pulsing
	 * blackish-purple vignette, and (if {@code growl}) a low growl plays for you alone. Sent with every whisper, and
	 * kept up through a whole feeding.
	 */
	public record Dread(int ticks, boolean growl) implements CustomPacketPayload {
		public static final Type<Dread> TYPE = new Type<>(JaymosPowers.id("web_symbiote_dread"));
		public static final StreamCodec<ByteBuf, Dread> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Dread::ticks,
			ByteBufCodecs.BOOL, Dread::growl,
			Dread::new);

		@Override
		public Type<Dread> type() {
			return TYPE;
		}
	}

	/** Server → client: the creatures that mean you harm right now (Spider-Sense draws them outlined). */
	public record Sense(java.util.List<Integer> ids) implements CustomPacketPayload {
		public static final Type<Sense> TYPE = new Type<>(JaymosPowers.id("web_sense"));
		public static final StreamCodec<ByteBuf, Sense> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(Sense::new, Sense::ids);

		@Override
		public Type<Sense> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SymbioteUse.TYPE, SymbioteUse.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Takeover.TYPE, Takeover.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Nudge.TYPE, Nudge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sense.TYPE, Sense.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Shake.TYPE, Shake.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Dread.TYPE, Dread.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wings.TYPE, Wings.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChargeHold.TYPE, ChargeHold.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Swing.TYPE, Swing.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwingReject.TYPE, SwingReject.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Pull.TYPE, Pull.CODEC);
	}
}
