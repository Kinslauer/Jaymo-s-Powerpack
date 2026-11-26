package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Flying the pod yourself (Patch 9, track B). A trip is a lift-off, a climb you pilot up through the sky bands of the world
 * you leave ({@link PodClimb}, in the real world, the server moving the pod), and then the crossing ({@link PodSpace}): a flight
 * view, not a dimension. The server holds the whole crossing (where the pod is, what is out there, what hits what); the client
 * ({@code PodFlightScreen}) only draws the {@link State} it is sent and sends back its {@link Input}.
 * <p>
 * This class is the numbers every part shares (distances, speeds, how long a way takes), the two packets and what the server
 * last heard from each pilot.
 */
public final class PodFlight {
	// ---- tuning: the way between worlds ----
	/** How far one league of a trip's cost is (blocks of flight space), and how big a world hangs (its radius). */
	public static final double LEAGUE = 400.0, WORLD_R = 140.0;
	/** The pod starts this far from the world it left, and the crossing ends this far from the world it is going to (centre to pod). */
	public static final double START = WORLD_R * 1.8, ARRIVE_R = WORLD_R * 1.4;
	/** Speeds (blocks a tick of flight space) at cruise and boosted; the climb's own (blocks a tick in the real world). */
	public static final double CRUISE = 1.7, BOOST = 3.6, CLIMB_CRUISE = 1.0, CLIMB_BOOST = 2.5;
	/** The last share of the way the autopilot slows down over, and how much slower it ends up. */
	public static final double AUTO_SLOW_SHARE = 0.10, AUTO_SLOW_FLOOR = 0.55;
	/** The real world's top the climb goes up past before the crossing begins (blocks over the highest block). */
	public static final int CLIMB_OVER_TOP = 40;

	// ---- the input flags ----
	public static final int BOOST_KEY = 1, BRAKE_KEY = 2, FIRE_KEY = 4, TOOL_KEY = 8, DOCK_KEY = 16, AUTO_KEY = 32;

	// ---- the state flags ----
	public static final int F_AUTO = 1, F_BOOST = 2, F_SHIELD = 4, F_CANNON = 8, F_BEAM = 16, F_TOW = 32, F_CAN_DOCK = 64, F_SKIM = 128, F_HIT = 256,
		F_ALERT = 512, F_THROWN = 1024;
	public static final int PHASE_CLIMB = 0, PHASE_SPACE = 1, PHASE_END = 2;

	/** What a body in the crossing is. */
	public static final int ROCK = 0, WRECK = 1, RAIDER = 2, BOLT = 3, MAW = 4, POST = 5;

	/** One thing out there: its id, what it is, where, how big, and a number between 0 and 1 (how much of it is left). */
	public record Obj(int id, int kind, float x, float y, float z, float r, float f) {
	}

	/**
	 * Server to client, every tick of the crossing (every other tick of the climb): everything the view draws. {@code notice} is
	 * a line to show ({@link PodSpace.Notice}), {@code shot} counts the cannon's shots and {@code shotAt} is the id of what the
	 * last one hit (-1 for a miss).
	 */
	public record State(int phase, int flags, float speed, float progress, int eta, float hull, float shield, float boost, float x, float y, float z,
		float yaw, float pitch, int from, int to, int charts, int notice, int noticeArg, int shot, int shotAt, int toolTarget, float toolProgress, float rel,
		List<Obj> objs) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(JaymosPowers.id("pod_flight_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.phase);
			buf.writeVarInt(p.flags);
			buf.writeFloat(p.speed);
			buf.writeFloat(p.progress);
			buf.writeVarInt(p.eta);
			buf.writeFloat(p.hull);
			buf.writeFloat(p.shield);
			buf.writeFloat(p.boost);
			buf.writeFloat(p.x);
			buf.writeFloat(p.y);
			buf.writeFloat(p.z);
			buf.writeFloat(p.yaw);
			buf.writeFloat(p.pitch);
			buf.writeVarInt(p.from + 1);
			buf.writeVarInt(p.to + 1);
			buf.writeVarInt(p.charts);
			buf.writeVarInt(p.notice);
			buf.writeVarInt(p.noticeArg);
			buf.writeVarInt(p.shot);
			buf.writeVarInt(p.shotAt + 1);
			buf.writeVarInt(p.toolTarget + 1);
			buf.writeFloat(p.toolProgress);
			buf.writeFloat(p.rel);
			buf.writeVarInt(p.objs.size());
			for (Obj o : p.objs) {
				buf.writeVarInt(o.id);
				buf.writeVarInt(o.kind);
				buf.writeFloat(o.x);
				buf.writeFloat(o.y);
				buf.writeFloat(o.z);
				buf.writeFloat(o.r);
				buf.writeFloat(o.f);
			}
		}, buf -> {
			int phase = buf.readVarInt(), flags = buf.readVarInt();
			float speed = buf.readFloat(), progress = buf.readFloat();
			int eta = buf.readVarInt();
			float hull = buf.readFloat(), shield = buf.readFloat(), boost = buf.readFloat();
			float x = buf.readFloat(), y = buf.readFloat(), z = buf.readFloat(), yaw = buf.readFloat(), pitch = buf.readFloat();
			int from = buf.readVarInt() - 1, to = buf.readVarInt() - 1, charts = buf.readVarInt(), notice = buf.readVarInt(), arg = buf.readVarInt(), shot = buf.readVarInt();
			int shotAt = buf.readVarInt() - 1, tool = buf.readVarInt() - 1;
			float toolProgress = buf.readFloat(), rel = buf.readFloat();
			int n = Math.min(buf.readVarInt(), 256);
			List<Obj> objs = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				objs.add(new Obj(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
			}
			return new State(phase, flags, speed, progress, eta, hull, shield, boost, x, y, z, yaw, pitch, from, to, charts, notice, arg, shot, shotAt, tool,
				toolProgress, rel, objs);
		});

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	/** Client to server: the pilot's stick ({@code yaw} and {@code pitch} each -1, 0 or 1) and keys ({@code flags}). */
	public record Input(int yaw, int pitch, int flags) implements CustomPacketPayload {
		public static final Type<Input> TYPE = new Type<>(JaymosPowers.id("pod_flight_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeByte(p.yaw);
			buf.writeByte(p.pitch);
			buf.writeVarInt(p.flags);
		}, buf -> new Input(buf.readByte(), buf.readByte(), buf.readVarInt()));
		public static final Input NONE = new Input(0, 0, 0);

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<Input> type() {
			return TYPE;
		}
	}

	/** What each pilot last sent, and an autopilot press waiting to be read (it is read once). */
	private static final Map<UUID, Input> INPUTS = new HashMap<>();
	private static final Map<UUID, Boolean> TOGGLES = new HashMap<>();

	private PodFlight() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Input.TYPE, (payload, ctx) -> {
			Input clean = new Input(Math.max(-1, Math.min(1, payload.yaw())), Math.max(-1, Math.min(1, payload.pitch())), payload.flags() & 63);
			INPUTS.put(ctx.player().getUUID(), clean);
			if (clean.has(AUTO_KEY)) {
				TOGGLES.put(ctx.player().getUUID(), Boolean.TRUE);
			}
		});
	}

	/** What {@code player} last sent. */
	static Input input(ServerPlayer player) {
		return INPUTS.getOrDefault(player.getUUID(), Input.NONE);
	}

	/** Whether {@code player} pressed the autopilot key since this was last asked (and forgets it). */
	static boolean toggled(ServerPlayer player) {
		return TOGGLES.remove(player.getUUID()) != null;
	}

	/** Forgets {@code player}'s input when a trip ends. */
	public static void forget(ServerPlayer player) {
		INPUTS.remove(player.getUUID());
		TOGGLES.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- the way (shared with the star map)

	/** How many leagues the way to {@code index} (or Home) from {@code from} is: what the trip costs before any discount, at least one. */
	public static int leagues(int index, ResourceKey<Level> from) {
		return Math.max(1, ZFighterSpace.cost(index, from));
	}

	/** A way that skirts the Hollow ({@code ZFighterSpace#skirtsHollow}) is this much longer: its pull drags the pod off course. */
	public static final double HOLLOW_STRETCH = 1.35;

	/** The distance (blocks of flight space) the pod flies between leaving the world's glare and the arrival ring. */
	public static double distance(int leagues, boolean hollow) {
		return leagues * LEAGUE * (hollow ? HOLLOW_STRETCH : 1.0);
	}

	/** The speed multiplier of a class of pod: a class that flies a way in less time is this much faster. */
	public static double classSpeed(float flightShare) {
		return 1.0 / Math.max(0.5, flightShare);
	}

	/** How long the way takes at cruise (ticks) or boosted, for a pod whose class flies it in {@code flightShare} of the usual time. */
	public static int cruiseTicks(int leagues, boolean hollow, float flightShare) {
		return (int)Math.round(distance(leagues, hollow) / (CRUISE * classSpeed(flightShare)));
	}

	public static int boostTicks(int leagues, boolean hollow, float flightShare) {
		return (int)Math.round(distance(leagues, hollow) / (BOOST * classSpeed(flightShare)));
	}
}
