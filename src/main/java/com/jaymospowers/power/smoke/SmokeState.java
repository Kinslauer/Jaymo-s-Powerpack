package com.jaymospowers.power.smoke;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Smoke user's state for their own HUD, synced to them only: when each cooldown ends (game time) and how long it
 * was, and since when Smokescreen has been pouring, Smother choking and Vapour Form flying (or -1).
 * Slots: {@link #VAPOUR} R, {@link #SCREEN} G, {@link #BOMB} V, {@link #SMOTHER} B, {@link #STEP} Sneak+X (Smoke Step), {@link #PYRO} C (Pyroclasm).
 */
public record SmokeState(long[] ready, int[] length, long pourFrom, long smotherFrom, long vapourFrom) {
	public static final int SCREEN = 0;
	public static final int VAPOUR = 1;
	public static final int BOMB = 2;
	public static final int SMOTHER = 3;
	public static final int STEP = 4;
	public static final int PYRO = 5;
	public static final int SLOTS = 6;

	public static final SmokeState NONE = new SmokeState(new long[SLOTS], new int[] {1, 1, 1, 1, 1, 1}, -1L, -1L, -1L);

	public static final StreamCodec<ByteBuf, SmokeState> STREAM_CODEC = StreamCodec.of((buf, state) -> {
		for (int i = 0; i < SLOTS; i++) {
			ByteBufCodecs.VAR_LONG.encode(buf, state.ready[i]);
			ByteBufCodecs.VAR_INT.encode(buf, state.length[i]);
		}
		ByteBufCodecs.LONG.encode(buf, state.pourFrom);
		ByteBufCodecs.LONG.encode(buf, state.smotherFrom);
		ByteBufCodecs.LONG.encode(buf, state.vapourFrom);
	}, buf -> {
		long[] ready = new long[SLOTS];
		int[] length = new int[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			ready[i] = ByteBufCodecs.VAR_LONG.decode(buf);
			length[i] = ByteBufCodecs.VAR_INT.decode(buf);
		}
		return new SmokeState(ready, length, ByteBufCodecs.LONG.decode(buf), ByteBufCodecs.LONG.decode(buf), ByteBufCodecs.LONG.decode(buf));
	});

	/** Ticks left on {@code slot}'s cooldown at game time {@code now}. */
	public long left(int slot, long now) {
		return Math.max(0L, ready[slot] - now);
	}

	public SmokeState withCooldown(int slot, long now, int ticks) {
		long[] r = ready.clone();
		int[] l = length.clone();
		r[slot] = now + ticks;
		l[slot] = Math.max(1, ticks);
		return new SmokeState(r, l, pourFrom, smotherFrom, vapourFrom);
	}

	public SmokeState withPour(long from) {
		return new SmokeState(ready, length, from, smotherFrom, vapourFrom);
	}

	public SmokeState withSmother(long from) {
		return new SmokeState(ready, length, pourFrom, from, vapourFrom);
	}

	public SmokeState withVapour(long from) {
		return new SmokeState(ready, length, pourFrom, smotherFrom, from);
	}
}
