package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.24) Andy: "a cinematic like the handshake for every applicable power's ultimate". An ultimate going off plays
 * a short film for the one who cast it: black bars, the HUD cleared, and one camera shot made for the move (the client
 * side is {@code UltimateCinematic}). Everyone else within a move's {@code witnesses} range only sees the bars, so no
 * one else's camera is ever taken. Nothing here touches the game: the move has already happened on the server, the
 * caster keeps their movement and aim, and the film ends early on a skip key, an attack or a hit.
 *
 * <p>The shots, each a kind of camera move the client knows ({@code at} and {@code target} are what it frames):
 * <ul>
 * <li>{@link #ZOOM}: straight back out of your head and in again, slow to start and faster all the way (Time Flow).</li>
 * <li>{@link #SKYWARD}: low behind {@code at}, tilting up into the sky above it (the Thunderhead, the Spirit Bomb).</li>
 * <li>{@link #CRANE}: rising and circling wide round you (the Blizzard, the Ash Storm, Skyfall).</li>
 * <li>{@link #ORBIT}: low and close, sweeping round your front (a transformation, a flurry).</li>
 * <li>{@link #PROFILE}: side on, along the line from you to {@code at} (a lance, a beam, a flow).</li>
 * <li>{@link #TRACK}: behind your shoulder, following {@code target} as it comes down (the Meteor).</li>
 * <li>{@link #PUSH_IN}: over your shoulder, pushing in on {@code target} or {@code at} (a shadow rising, a well).</li>
 * <li>{@link #CHASE}: alongside you, keeping pace as you go for {@code target} (the web strike).</li>
 * </ul>
 */
public final class UltimateFilm {
	public static final int ZOOM = 0, SKYWARD = 1, CRANE = 2, ORBIT = 3, PROFILE = 4, TRACK = 5, PUSH_IN = 6, CHASE = 7;
	/** The shortest and longest a film may run (ticks), whatever a move asks for. */
	public static final int MIN_TICKS = 10, MAX_TICKS = 100;

	private static final List<Later> LATER = new ArrayList<>();

	private UltimateFilm() {
	}

	/**
	 * Server → client: play a film. {@code caster} is whose ultimate it is; {@code witness} says this player is only near
	 * it (bars, no camera).
	 */
	public record Play(int caster, int shot, int ticks, Vec3 at, int target, boolean witness) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosPowers.id("ultimate_film"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.caster);
				buf.writeVarInt(p.shot);
				buf.writeVarInt(p.ticks);
				Vec3.STREAM_CODEC.encode(buf, p.at);
				buf.writeVarInt(p.target);
				buf.writeBoolean(p.witness);
			},
			buf -> new Play(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	private record Later(UUID player, int due, Consumer<ServerPlayer> what) {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(UltimateFilm::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LATER.clear());
	}

	/** A film framed on the caster themselves, seen as bars by everyone within {@code witnesses} blocks (0: no one). */
	public static void play(ServerPlayer caster, int shot, int ticks, double witnesses) {
		play(caster, shot, ticks, caster.position(), null, witnesses);
	}

	/** A film framed on {@code at} (and {@code target}, if any), seen as bars by everyone within {@code witnesses} blocks. */
	public static void play(ServerPlayer caster, int shot, int ticks, Vec3 at, @Nullable Entity target, double witnesses) {
		int length = Math.clamp(ticks, MIN_TICKS, MAX_TICKS);
		int targetId = target == null ? -1 : target.getId();
		ServerPlayNetworking.send(caster, new Play(caster.getId(), shot, length, at, targetId, false));
		if (witnesses <= 0.0) {
			return;
		}
		for (ServerPlayer other : caster.level().players()) {
			if (other != caster && other.distanceToSqr(caster) <= witnesses * witnesses) {
				ServerPlayNetworking.send(other, new Play(caster.getId(), shot, length, at, targetId, true));
			}
		}
	}

	/**
	 * (0.17.2) A film for an ultimate that isn't a player's (a gifted ruler's, {@code core/gifted}): everyone within
	 * {@code witnesses} blocks of {@code caster} sees the bars; no camera is taken.
	 */
	public static void witnessed(Entity caster, int shot, int ticks, Vec3 at, @Nullable Entity target, double witnesses) {
		if (!(caster.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		int length = Math.clamp(ticks, MIN_TICKS, MAX_TICKS);
		int targetId = target == null ? -1 : target.getId();
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(caster) <= witnesses * witnesses) {
				ServerPlayNetworking.send(other, new Play(caster.getId(), shot, length, at, targetId, true));
			}
		}
	}

	/** Runs {@code what} on {@code player} once {@code ticks} have passed (if they're still on the server), e.g. a sound after the film. */
	public static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> what) {
		LATER.add(new Later(player.getUUID(), player.level().getServer().getTickCount() + Math.max(1, ticks), what));
	}

	private static void tick(MinecraftServer server) {
		if (LATER.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		List<Later> due = new ArrayList<>();
		for (Iterator<Later> it = LATER.iterator(); it.hasNext();) {
			Later later = it.next();
			if (now >= later.due) {
				it.remove();
				due.add(later);
			}
		}
		for (Later later : due) {
			ServerPlayer player = server.getPlayerList().getPlayer(later.player);
			if (player != null) {
				later.what.accept(player);
			}
		}
	}
}
