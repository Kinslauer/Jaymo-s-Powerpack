package com.jaymospowers.power.zfighter;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Z Fighter's state for their own HUD, synced to them only: their ki and its ceiling, when each cooldown ends (game
 * time) and how long it was, since when the Ki Wave has been gathering (or -1), what they're doing ({@link #CHARGING},
 * {@link #FLYING}, {@link #SUPER}), the fist combo, the form he's in (0 base, 1-3 Super to Super 3) and the Limit Break tier
 * (0 off, 1-3). Slots: {@link #BLAST} G, {@link #WAVE} V, {@link #STEP} B,
 * {@link #SUPER_SLOT} C.
 */
public record ZFighterState(int ki, int maxKi, long[] ready, int[] length, long waveFrom, int flags, int combo, int form, int limit) {
	public static final int BLAST = 0;
	public static final int WAVE = 1;
	public static final int STEP = 2;
	public static final int SUPER_SLOT = 3;
	public static final int SLOTS = 4;

	public static final int CHARGING = 1;
	public static final int FLYING = 2;
	public static final int SUPER = 4;

	public static final ZFighterState NONE = new ZFighterState(0, ZFighterPower.BASE_MAX_KI, new long[SLOTS], new int[] {1, 1, 1, 1}, -1L, 0, 0, 0, 0);

	public static final StreamCodec<ByteBuf, ZFighterState> STREAM_CODEC = StreamCodec.of((buf, state) -> {
		ByteBufCodecs.VAR_INT.encode(buf, state.ki);
		ByteBufCodecs.VAR_INT.encode(buf, state.maxKi);
		for (int i = 0; i < SLOTS; i++) {
			ByteBufCodecs.VAR_LONG.encode(buf, state.ready[i]);
			ByteBufCodecs.VAR_INT.encode(buf, state.length[i]);
		}
		ByteBufCodecs.LONG.encode(buf, state.waveFrom);
		ByteBufCodecs.VAR_INT.encode(buf, state.flags);
		ByteBufCodecs.VAR_INT.encode(buf, state.combo);
		ByteBufCodecs.VAR_INT.encode(buf, state.form);
		ByteBufCodecs.VAR_INT.encode(buf, state.limit);
	}, buf -> {
		int ki = ByteBufCodecs.VAR_INT.decode(buf);
		int maxKi = ByteBufCodecs.VAR_INT.decode(buf);
		long[] ready = new long[SLOTS];
		int[] length = new int[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			ready[i] = ByteBufCodecs.VAR_LONG.decode(buf);
			length[i] = ByteBufCodecs.VAR_INT.decode(buf);
		}
		long waveFrom = ByteBufCodecs.LONG.decode(buf);
		int flags = ByteBufCodecs.VAR_INT.decode(buf);
		int combo = ByteBufCodecs.VAR_INT.decode(buf);
		int form = ByteBufCodecs.VAR_INT.decode(buf);
		int limit = ByteBufCodecs.VAR_INT.decode(buf);
		return new ZFighterState(ki, maxKi, ready, length, waveFrom, flags, combo, form, limit);
	});

	/** Ticks left on {@code slot}'s cooldown at game time {@code now}. */
	public long left(int slot, long now) {
		return Math.max(0L, ready[slot] - now);
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public ZFighterState withCooldown(int slot, long now, int ticks) {
		long[] r = ready.clone();
		int[] l = length.clone();
		r[slot] = now + ticks;
		l[slot] = Math.max(1, ticks);
		return new ZFighterState(ki, maxKi, r, l, waveFrom, flags, combo, form, limit);
	}

	public ZFighterState cleared() {
		return new ZFighterState(ki, maxKi, new long[SLOTS], length.clone(), waveFrom, flags, combo, form, limit);
	}

	public ZFighterState with(int ki, int maxKi, long waveFrom, int flags, int combo, int form, int limit) {
		return new ZFighterState(ki, maxKi, ready, length, waveFrom, flags, combo, form, limit);
	}

	/** Same HUD (cooldowns aside, which are always written through {@link #withCooldown}). */
	public boolean sameAs(int ki, int maxKi, long waveFrom, int flags, int combo, int form, int limit) {
		return this.ki == ki && this.maxKi == maxKi && this.waveFrom == waveFrom && this.flags == flags && this.combo == combo
			&& this.form == form && this.limit == limit;
	}
}
