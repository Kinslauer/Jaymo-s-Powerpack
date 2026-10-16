package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * The Rig's packets: the client's key presses ({@link Use}, {@link Lock}) and the suit's state for the HUD
 * ({@link Status}), a scan's finds ({@link Scan}) and the Mind taking over ({@link Takeover}).
 */
public final class RigNet {
	/** {@link Use} actions. The first four are the power keys' order (R G V B) for a powerless wearer. */
	public static final int HOVER = 0, PALM = 1, LAUNCHER = 2, REPORT = 3;
	/** (0.11.87) Scan (level 3), call or park the suit (level 3), toggle full flight while hovering (level 5). */
	public static final int SCAN = 4, CALL = 5, FLIGHT = 6;
	/** (0.11.107) The Unibeam (level 4; no power: Sneak + G). */
	public static final int UNIBEAM = 7;
	/** The actives J cycles through, in order (the stage-2 ones only once the Mind's level has them). */
	public static final int[] CYCLE = {HOVER, PALM, LAUNCHER, UNIBEAM, SCAN, CALL};
	/** The first three of {@link #CYCLE} need no level. */
	public static final int ACTIVES = 3;

	/** {@link Status} warning bits (the Mind's, see {@link RigMind}). */
	public static final int WARN_LOW_CHARGE = 1, WARN_LOW_HEALTH = 2, WARN_PROJECTILE = 4, WARN_EXPLOSION = 8, WARN_LAVA = 16,
		WARN_VOID = 32, WARN_BEHIND = 64;

	/** {@link Scan} kinds. */
	public static final int FOUND_HOSTILE = 0, FOUND_PLAYER = 1, FOUND_CHEST = 2, FOUND_ORE = 3;

	/** Client → server: a Rig key was pressed ({@link #HOVER}, {@link #PALM}, ... {@link #FLIGHT}). */
	public record Use(int action) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosPowers.id("rig_use"));
		public static final StreamCodec<ByteBuf, Use> CODEC = ByteBufCodecs.VAR_INT.map(Use::new, Use::action);

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** (0.11.87) Client → server: lock on to this entity (by id), or let go (-1). */
	public record Lock(int entity) implements CustomPacketPayload {
		public static final Type<Lock> TYPE = new Type<>(JaymosPowers.id("rig_lock"));
		public static final StreamCodec<ByteBuf, Lock> CODEC = ByteBufCodecs.VAR_INT.map(Lock::new, Lock::entity);

		@Override
		public Type<Lock> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: hovering (and in full flight), the Mind's current warnings, the actives' cooldowns (ticks), the
	 * locked target's entity id (-1: none), and the takeover save's cooldown (seconds; 0 ready) and (0.11.107) the Unibeam's (ticks).
	 */
	public record Status(boolean hovering, boolean flying, int warnings, int palmCooldown, int launcherCooldown, int scanCooldown, int lock,
		int takeoverSeconds, int unibeamCooldown) implements CustomPacketPayload {
		public static final Status NONE = new Status(false, false, 0, 0, 0, 0, -1, 0, 0);
		public static final Type<Status> TYPE = new Type<>(JaymosPowers.id("rig_status"));
		public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.of((buf, s) -> {
			buf.writeByte((s.hovering ? 1 : 0) | (s.flying ? 2 : 0));
			ByteBufCodecs.VAR_INT.encode(buf, s.warnings);
			ByteBufCodecs.VAR_INT.encode(buf, s.palmCooldown);
			ByteBufCodecs.VAR_INT.encode(buf, s.launcherCooldown);
			ByteBufCodecs.VAR_INT.encode(buf, s.scanCooldown);
			ByteBufCodecs.VAR_INT.encode(buf, s.lock + 1);
			ByteBufCodecs.VAR_INT.encode(buf, s.takeoverSeconds);
			ByteBufCodecs.VAR_INT.encode(buf, s.unibeamCooldown);
		}, buf -> {
			int flags = buf.readByte();
			return new Status((flags & 1) != 0, (flags & 2) != 0, ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1,
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
		});

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	/**
	 * (0.11.87) Server → client: what a scan found, for the wearer's eyes only: entities (by id) glow, blocks (packed
	 * positions) are marked on the HUD; {@code kinds} holds a {@code FOUND_} kind for each, entities first.
	 */
	public record Scan(List<Integer> entities, List<Long> blocks, List<Integer> kinds) implements CustomPacketPayload {
		public static final Type<Scan> TYPE = new Type<>(JaymosPowers.id("rig_scan"));
		public static final StreamCodec<ByteBuf, Scan> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Scan::entities,
			ByteBufCodecs.LONG.apply(ByteBufCodecs.list()), Scan::blocks,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Scan::kinds,
			Scan::new);

		@Override
		public Type<Scan> type() {
			return TYPE;
		}
	}

	/** (0.11.87) Server → client: the Mind has taken over, thrusting away from {@code threat}. */
	public record Takeover(Vec3 threat) implements CustomPacketPayload {
		public static final Type<Takeover> TYPE = new Type<>(JaymosPowers.id("rig_takeover"));
		public static final StreamCodec<ByteBuf, Takeover> CODEC = Vec3.STREAM_CODEC.map(Takeover::new, Takeover::threat);

		@Override
		public Type<Takeover> type() {
			return TYPE;
		}
	}

	private RigNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Lock.TYPE, Lock.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Scan.TYPE, Scan.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Takeover.TYPE, Takeover.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> RigActives.use(context.player(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(Lock.TYPE, (payload, context) -> RigActives.lock(context.player(), payload.entity()));
	}
}
