package com.jaymospowers.power.cryo;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.14.0) A Cryokinesis user's state for their own HUD, synced to them only: when each cooldown ends (game time) and
 * how long it was, and since when Frost Breath has been blowing and the Blizzard raging (or -1).
 * Slots: {@link #SLIDE} R, {@link #BREATH} G, {@link #SHARDS} V, {@link #FREEZE} B, {@link #WALL} X, {@link #BLIZZARD} C.
 */
public record CryoState(long[] ready, int[] length, long breathFrom, long blizzardFrom) {
	public static final int SLIDE = 0;
	public static final int BREATH = 1;
	public static final int SHARDS = 2;
	public static final int FREEZE = 3;
	public static final int WALL = 4;
	public static final int BLIZZARD = 5;
	public static final int SLOTS = 6;

	public static final CryoState NONE = new CryoState(new long[SLOTS], new int[] {1, 1, 1, 1, 1, 1}, -1L, -1L);

	public static final StreamCodec<ByteBuf, CryoState> STREAM_CODEC = StreamCodec.of((buf, state) -> {
		for (int i = 0; i < SLOTS; i++) {
			ByteBufCodecs.VAR_LONG.encode(buf, state.ready[i]);
			ByteBufCodecs.VAR_INT.encode(buf, state.length[i]);
		}
		ByteBufCodecs.LONG.encode(buf, state.breathFrom);
		ByteBufCodecs.LONG.encode(buf, state.blizzardFrom);
	}, buf -> {
		long[] ready = new long[SLOTS];
		int[] length = new int[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			ready[i] = ByteBufCodecs.VAR_LONG.decode(buf);
			length[i] = ByteBufCodecs.VAR_INT.decode(buf);
		}
		return new CryoState(ready, length, ByteBufCodecs.LONG.decode(buf), ByteBufCodecs.LONG.decode(buf));
	});

	/** Ticks left on {@code slot}'s cooldown at game time {@code now}. */
	public long left(int slot, long now) {
		return Math.max(0L, ready[slot] - now);
	}

	public CryoState withCooldown(int slot, long now, int ticks) {
		long[] r = ready.clone();
		int[] l = length.clone();
		r[slot] = now + ticks;
		l[slot] = Math.max(1, ticks);
		return new CryoState(r, l, breathFrom, blizzardFrom);
	}

	public CryoState withBreath(long from) {
		return new CryoState(ready, length, from, blizzardFrom);
	}

	public CryoState withBlizzard(long from) {
		return new CryoState(ready, length, breathFrom, from);
	}
}
