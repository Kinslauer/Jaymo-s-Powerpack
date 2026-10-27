package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) The beam clash timing game, on top of {@link BeamClash}'s mashing. In a clash a marker
 * sweeps a bar near the bottom of the screen ({@link #marker}, a pure function of the clash's age, so the server and both
 * clients agree on it); the window it should be in sits somewhere on the bar and moves every pass ({@link #centre}).
 * Right click as the marker crosses it: <b>Perfect</b> (worth about {@link #PERFECT_TAPS} presses of the push key),
 * <b>Good</b> ({@link #GOOD_TAPS}) or <b>Miss</b> (costs {@link #MISS_TAPS} of the surge you had). One try a pass.
 * The client sends the clash age it saw when it clicked; the server only trusts it within {@link #TRUST_TICKS} of its own.
 * The old push key still works as before.
 */
public final class BeamClashTiming {
	/** Ticks the marker takes to cross the bar once (it comes back the other way the next pass). */
	public static final int SWEEP_TICKS = 24;
	/** The window's half-width on the 0-1 bar for Perfect and for Good. */
	public static final float PERFECT_HALF = 0.06F, GOOD_HALF = 0.16F;
	/** The window sits between these on the bar. */
	public static final float CENTRE_MIN = 0.25F, CENTRE_MAX = 0.75F;
	/** Presses of the push key each grade is worth (a miss takes some of the surge away). */
	public static final float PERFECT_TAPS = 3.0F, GOOD_TAPS = 1.5F, MISS_TAPS = -1.0F;
	/** A well-timed hit may push the surge this many times over its usual ceiling. */
	public static final float SURGE_OVERSHOOT = 1.6F;
	/** The client's clash age is believed within this many ticks of the server's. */
	public static final float TRUST_TICKS = 14.0F;

	public static final int MISS = 0, GOOD = 1, PERFECT = 2;

	/** Client → server: right click in a clash, at this clash age (ticks, with the partial tick). */
	public record Press(float age) implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("beam_clash_timing_press"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Press> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeFloat(p.age),
			buf -> new Press(buf.readFloat()));

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	/** Server → the presser: how it went ({@link #MISS}, {@link #GOOD}, {@link #PERFECT}) and on which pass. */
	public record Result(int grade, int pass) implements CustomPacketPayload {
		public static final Type<Result> TYPE = new Type<>(JaymosPowers.id("beam_clash_timing_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeByte(p.grade);
				buf.writeVarInt(p.pass);
			},
			buf -> new Result(buf.readByte(), buf.readVarInt()));

		@Override
		public Type<Result> type() {
			return TYPE;
		}
	}

	/** Player → the pass they last tried on, and when. */
	private static final Map<UUID, long[]> TRIED = new HashMap<>();

	private BeamClashTiming() {
	}

	/** Called from {@link BeamClash#init}. */
	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Result.TYPE, Result.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, ctx) -> press(ctx.player(), payload.age()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRIED.remove(handler.player.getUUID()));
	}

	// ---------------------------------------------------------------- the bar (both sides)

	/** Which pass across the bar this clash age is in. */
	public static int pass(float age) {
		return (int)Math.floor(Math.max(0.0F, age) / SWEEP_TICKS);
	}

	/** Where the marker is on the 0-1 bar at this clash age: left to right on even passes, back on odd ones. */
	public static float marker(float age) {
		float along = Math.max(0.0F, age) / SWEEP_TICKS;
		float within = along - (float)Math.floor(along);
		return (pass(age) & 1) == 0 ? within : 1.0F - within;
	}

	/** Where the window sits on the bar for this pass: the same on the server and both clients. */
	public static float centre(int pass) {
		int h = pass * 0x9E3779B1 + 0x7F4A7C15;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		float share = (h & 0xFFFF) / 65535.0F;
		return CENTRE_MIN + (CENTRE_MAX - CENTRE_MIN) * share;
	}

	/** How a click with the marker where it is at {@code age} grades. */
	public static int grade(float age) {
		float off = Math.abs(marker(age) - centre(pass(age)));
		return off <= PERFECT_HALF ? PERFECT : off <= GOOD_HALF ? GOOD : MISS;
	}

	// ---------------------------------------------------------------- the server

	private static void press(ServerPlayer player, float claimed) {
		int age = BeamClash.age(player);
		if (age < 0 || !Float.isFinite(claimed) || Math.abs(claimed - age) > TRUST_TICKS) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int pass = pass(claimed);
		long[] tried = TRIED.get(player.getUUID());
		if (tried != null && tried[0] == pass && now - tried[1] < SWEEP_TICKS * 2L) {
			// one try a pass
			return;
		}
		TRIED.put(player.getUUID(), new long[] {pass, now});
		int grade = grade(claimed);
		float taps = grade == PERFECT ? PERFECT_TAPS : grade == GOOD ? GOOD_TAPS : MISS_TAPS;
		if (!BeamClash.shove(player, taps, SURGE_OVERSHOOT)) {
			return;
		}
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.5));
		if (grade == PERFECT) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.8F, 0.9F);
			Shakes.send(player, 0.8F);
		} else if (grade == GOOD) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.6F, 1.2F);
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5F, 0.6F);
		}
		ServerPlayNetworking.send(player, new Result(grade, pass));
	}
}
