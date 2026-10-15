package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Rig's packets: the client's key presses ({@link Use}) and the suit's state for the HUD ({@link Status}). */
public final class RigNet {
	/** {@link Use} actions. */
	public static final int HOVER = 0, PALM = 1, LAUNCHER = 2, REPORT = 3;
	/** The actives J cycles through, in order. */
	public static final int ACTIVES = 3;

	/** {@link Status} warning bits (the Mind's, see {@link RigMind}). */
	public static final int WARN_LOW_CHARGE = 1, WARN_LOW_HEALTH = 2, WARN_PROJECTILE = 4, WARN_EXPLOSION = 8, WARN_LAVA = 16,
		WARN_VOID = 32, WARN_BEHIND = 64;

	/** Client → server: a Rig key was pressed ({@link #HOVER}, {@link #PALM}, {@link #LAUNCHER}, {@link #REPORT}). */
	public record Use(int action) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosPowers.id("rig_use"));
		public static final StreamCodec<ByteBuf, Use> CODEC = ByteBufCodecs.VAR_INT.map(Use::new, Use::action);

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** Server → client: hovering, the Mind's current warnings, and the actives' cooldowns (ticks). */
	public record Status(boolean hovering, int warnings, int palmCooldown, int launcherCooldown) implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(JaymosPowers.id("rig_status"));
		public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Status::hovering,
			ByteBufCodecs.VAR_INT, Status::warnings, ByteBufCodecs.VAR_INT, Status::palmCooldown, ByteBufCodecs.VAR_INT, Status::launcherCooldown,
			Status::new);

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	private RigNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> RigActives.use(context.player(), payload.action()));
	}
}
