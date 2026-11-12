package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Meeting it face to face. The first time a host reaches the whole bond (the point the big suit opens) and is not in a
 * fight, a short film plays: the world goes dark around them, the camera comes round, and the symbiote stands a few blocks
 * away in their own shape, wearing the big suit. It bares its teeth, says a few things, then pours back into them and the
 * light returns. Nothing is travelled to: the host stays where they stand, held still and unhurt for the length of the film.
 * The film itself is {@link UltimateFilm#MEETING} (bars, cleared HUD, the camera); the dark, the figure and the pouring back
 * are the client's ({@code WebMeetingClient}). Its lines go out as whispers (the symbiote's voice); a skip on the client
 * ends the hold at once. The flag that it has been seen is saved with the player and kept through death.
 */
public final class WebMeeting {
	// ================================================================================================================
	// Tuning
	/** The film's length (ticks, about 8 seconds). */
	public static final int TICKS = 164;
	/** It begins this many ticks after the conditions first hold (the bond's own whisper gets said first), and never within {@code COMBAT_TICKS} of a fight. */
	static final int WAIT_TICKS = 100, COMBAT_TICKS = 200, CHECK = 10;
	/** How far in front it stands (blocks), and the least it will make do with before trying another side. */
	static final double STAND_AT = 4.5, STAND_MIN = 3.0, STAND_FALLBACK = 2.4;
	/** The ticks into the film its lines come at, and where it bares its teeth (also the client's cue). */
	static final int[] LINE_AT = {38, 78, 112};
	/** The film's own sounds. */
	static final int GROWL_AT = 100;
	/** Held still: slowness for a few ticks, refreshed every tick. */
	static final int HOLD_EFFECT_TICKS = 6, HOLD_EFFECT_LEVEL = 5;

	/** The meeting has been had (saved, kept through death). */
	public static final AttachmentType<Boolean> SEEN = AttachmentRegistry.create(JaymosPowers.id("web_meeting_seen"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Server → client: the meeting begins, with the symbiote standing at {@code foe} (feet). */
	public record Scene(Vec3 foe, int ticks) implements CustomPacketPayload {
		public static final Type<Scene> TYPE = new Type<>(JaymosPowers.id("web_meeting_scene"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Scene> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				Vec3.STREAM_CODEC.encode(buf, p.foe);
				buf.writeVarInt(p.ticks);
			},
			buf -> new Scene(Vec3.STREAM_CODEC.decode(buf), buf.readVarInt()));

		@Override
		public Type<Scene> type() {
			return TYPE;
		}
	}

	/** Client → server: the film was skipped; let the host go. */
	public record Skip() implements CustomPacketPayload {
		public static final Type<Skip> TYPE = new Type<>(JaymosPowers.id("web_meeting_skip"));
		public static final StreamCodec<ByteBuf, Skip> CODEC = StreamCodec.unit(new Skip());

		@Override
		public Type<Skip> type() {
			return TYPE;
		}
	}

	/** Who is in the film, until what server tick. */
	private static final Map<UUID, Integer> RUNNING = new HashMap<>();
	/** The server tick each host first met the conditions. */
	private static final Map<UUID, Integer> READY_SINCE = new HashMap<>();

	private WebMeeting() {
	}

	public static boolean seen(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(SEEN));
	}

	/** Whether {@code player} is in the film just now. */
	public static boolean running(ServerPlayer player) {
		return RUNNING.containsKey(player.getUUID());
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Scene.TYPE, Scene.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Skip.TYPE, Skip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Skip.TYPE, (payload, ctx) -> {
			if (running(ctx.player())) {
				finish(ctx.player(), false);
			}
		});
		// unhurt for as long as it lasts
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && running(player)));
		ServerTickEvents.END_SERVER_TICK.register(WebMeeting::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RUNNING.remove(handler.player.getUUID());
			READY_SINCE.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNNING.clear();
			READY_SINCE.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		Set<UUID> over = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Integer end = RUNNING.get(player.getUUID());
			if (end != null) {
				if (now >= end || !player.isAlive()) {
					over.add(player.getUUID());
				} else {
					hold(player);
				}
			} else if (player.tickCount % CHECK == 0) {
				consider(player, now);
			}
		}
		for (UUID id : over) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				finish(player, player.isAlive());
			} else {
				RUNNING.remove(id);
			}
		}
	}

	/** Held where they stand: no walking off, no falling up. */
	private static void hold(ServerPlayer player) {
		player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOLD_EFFECT_TICKS, HOLD_EFFECT_LEVEL, false, false, false));
	}

	/** Whether the film should start for {@code player} now (after the wait, out of a fight). */
	private static void consider(ServerPlayer player, int now) {
		UUID id = player.getUUID();
		if (seen(player) || !WebSymbiote.isBonded(player) || WebSymbiote.state(player).bond() < WebSymbiote.BOND_MAX || !player.isAlive()
			|| player.isSpectator() || Downed.isDowned(player) || player.isPassenger() || !player.onGround() || UltimateFilm.filming(player)
			|| UltimateFilm.inCombat(player, COMBAT_TICKS)) {
			READY_SINCE.remove(id);
			return;
		}
		Integer since = READY_SINCE.putIfAbsent(id, now);
		if (since == null || now - since < WAIT_TICKS) {
			return;
		}
		READY_SINCE.remove(id);
		begin(player);
	}

	private static void begin(ServerPlayer player) {
		player.setAttached(SEEN, Boolean.TRUE);
		Vec3 foe = standingPlace(player);
		RUNNING.put(player.getUUID(), player.level().getServer().getTickCount() + TICKS);
		UltimateFilm.play(player, UltimateFilm.MEETING, TICKS, foe, null, 0.0);
		ServerPlayNetworking.send(player, new Scene(foe, TICKS));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.6F);
		for (int i = 0; i < LINE_AT.length; i++) {
			String key = "meeting." + (i + 1) + ".1";
			UltimateFilm.later(player, LINE_AT[i], p -> {
				if (running(p)) {
					WebSymbiote.whisper(p, key);
				}
			});
		}
		UltimateFilm.later(player, GROWL_AT, p -> {
			if (running(p)) {
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 1.2F, 0.7F);
			}
		});
	}

	/** Back in: the light returns, and (unless it ended early because they died) it has a last word. */
	private static void finish(ServerPlayer player, boolean speak) {
		RUNNING.remove(player.getUUID());
		player.removeEffect(MobEffects.SLOWNESS);
		if (speak && player.isAlive()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.3F, 0.6F);
			WebSymbiote.whisper(player, "meeting.end.1");
		}
	}

	/**
	 * Where it stands: {@link #STAND_AT} blocks off along their look if that is clear at head height, else turned to
	 * either side or behind, else as far as there is room (never nearer than {@link #STAND_FALLBACK}). On their own level.
	 */
	private static Vec3 standingPlace(ServerPlayer player) {
		Vec3 eyes = player.getEyePosition();
		double bestRoom = 0.0;
		Vec3 best = null;
		for (float turn : new float[] {0.0F, 90.0F, -90.0F, 180.0F}) {
			Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot() + turn);
			Vec3 far = eyes.add(dir.scale(STAND_AT));
			HitResult hit = player.level().clip(new ClipContext(eyes, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			double room = hit.getType() == HitResult.Type.MISS ? STAND_AT : Math.max(0.0, hit.getLocation().distanceTo(eyes) - 0.8);
			if (room >= STAND_MIN) {
				return player.position().add(dir.scale(Math.min(STAND_AT, room)));
			}
			if (room > bestRoom) {
				bestRoom = room;
				best = dir;
			}
		}
		Vec3 dir = best != null ? best : Vec3.directionFromRotation(0.0F, player.getYRot());
		return player.position().add(dir.scale(Math.max(STAND_FALLBACK, bestRoom)));
	}
}
