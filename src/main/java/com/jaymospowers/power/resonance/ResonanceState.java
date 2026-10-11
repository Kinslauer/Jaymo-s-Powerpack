package com.jaymospowers.power.resonance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Resonance user's state for their own HUD, synced to them only: when each cooldown ends (game time) and how long it
 * was, the Throw Voice sound picked, the pitch tuned, since when Shatter has been humming (or -1), and until when
 * Sonic Boom is charging (or 0). Slots: {@link #PULSE} R, {@link #SHATTER} G, {@link #ECHO} V, {@link #HUSH} B,
 * {@link #VOICE} Z, {@link #ULTIMATE} C.
 */
public record ResonanceState(long[] ready, int[] length, int sound, int tune, long humFrom, long chargeUntil) {
	public static final int PULSE = 0;
	public static final int SHATTER = 1;
	public static final int ECHO = 2;
	public static final int HUSH = 3;
	public static final int VOICE = 4;
	public static final int ULTIMATE = 5;
	public static final int SLOTS = 6;

	public static final ResonanceState NONE = new ResonanceState(new long[SLOTS], new int[] {1, 1, 1, 1, 1, 1}, 0, 0, -1L, 0L);

	public static final StreamCodec<ByteBuf, ResonanceState> STREAM_CODEC = StreamCodec.of((buf, state) -> {
		for (int i = 0; i < SLOTS; i++) {
			ByteBufCodecs.VAR_LONG.encode(buf, state.ready[i]);
			ByteBufCodecs.VAR_INT.encode(buf, state.length[i]);
		}
		ByteBufCodecs.VAR_INT.encode(buf, state.sound);
		ByteBufCodecs.VAR_INT.encode(buf, state.tune + 8);
		ByteBufCodecs.LONG.encode(buf, state.humFrom);
		ByteBufCodecs.VAR_LONG.encode(buf, state.chargeUntil);
	}, buf -> {
		long[] ready = new long[SLOTS];
		int[] length = new int[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			ready[i] = ByteBufCodecs.VAR_LONG.decode(buf);
			length[i] = ByteBufCodecs.VAR_INT.decode(buf);
		}
		int sound = ByteBufCodecs.VAR_INT.decode(buf);
		int tune = ByteBufCodecs.VAR_INT.decode(buf) - 8;
		long humFrom = ByteBufCodecs.LONG.decode(buf);
		long chargeUntil = ByteBufCodecs.VAR_LONG.decode(buf);
		return new ResonanceState(ready, length, sound, tune, humFrom, chargeUntil);
	});

	/** Ticks left on {@code slot}'s cooldown at game time {@code now}. */
	public long left(int slot, long now) {
		return Math.max(0L, ready[slot] - now);
	}

	public ResonanceState withCooldown(int slot, long now, int ticks) {
		long[] r = ready.clone();
		int[] l = length.clone();
		r[slot] = now + ticks;
		l[slot] = Math.max(1, ticks);
		return new ResonanceState(r, l, sound, tune, humFrom, chargeUntil);
	}

	public ResonanceState cleared() {
		return new ResonanceState(new long[SLOTS], length.clone(), sound, tune, -1L, 0L);
	}

	public ResonanceState withSound(int value) {
		return new ResonanceState(ready, length, value, tune, humFrom, chargeUntil);
	}

	public ResonanceState withTune(int value) {
		return new ResonanceState(ready, length, sound, value, humFrom, chargeUntil);
	}

	public ResonanceState withHum(long from) {
		return new ResonanceState(ready, length, sound, tune, from, chargeUntil);
	}

	public ResonanceState withCharge(long until) {
		return new ResonanceState(ready, length, sound, tune, humFrom, until);
	}
}
