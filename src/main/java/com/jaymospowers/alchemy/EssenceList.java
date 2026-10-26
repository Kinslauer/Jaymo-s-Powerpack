package com.jaymospowers.alchemy;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/** An amount of each essence (immutable; no zero or negative entries). Used on items, in vessels, in recipes. */
public record EssenceList(Map<Essence, Integer> amounts) {
	public static final EssenceList EMPTY = new EssenceList(Map.of());
	public static final Codec<EssenceList> CODEC = Codec.unboundedMap(Essence.CODEC, Codec.INT).xmap(EssenceList::new, EssenceList::amounts);
	public static final StreamCodec<ByteBuf, EssenceList> STREAM_CODEC = ByteBufCodecs.<ByteBuf, Essence, Integer, Map<Essence, Integer>>map(
		i -> new EnumMap<>(Essence.class), Essence.STREAM_CODEC, ByteBufCodecs.VAR_INT).map(EssenceList::new, EssenceList::amounts);

	public EssenceList {
		EnumMap<Essence, Integer> copy = new EnumMap<>(Essence.class);
		amounts.forEach((e, n) -> {
			if (n > 0) {
				copy.put(e, n);
			}
		});
		amounts = Collections.unmodifiableMap(copy);
	}

	public static EssenceList of(Essence e, int n) {
		return new EssenceList(Map.of(e, n));
	}

	public static EssenceList of(Essence e1, int n1, Essence e2, int n2) {
		return new EssenceList(Map.of(e1, n1, e2, n2));
	}

	public int get(Essence e) {
		return amounts.getOrDefault(e, 0);
	}

	public int total() {
		int t = 0;
		for (int n : amounts.values()) {
			t += n;
		}
		return t;
	}

	public boolean isEmpty() {
		return amounts.isEmpty();
	}

	public EssenceList plus(EssenceList other) {
		EnumMap<Essence, Integer> sum = new EnumMap<>(Essence.class);
		sum.putAll(amounts);
		other.amounts.forEach((e, n) -> sum.merge(e, n, Integer::sum));
		return new EssenceList(sum);
	}

	public EssenceList plus(Essence e, int n) {
		return plus(of(e, n));
	}

	/** Each amount times {@code factor}, rounded down (an essence that drops to 0 is gone). */
	public EssenceList scaled(double factor) {
		EnumMap<Essence, Integer> out = new EnumMap<>(Essence.class);
		amounts.forEach((e, n) -> out.put(e, (int) Math.floor(n * factor)));
		return new EssenceList(out);
	}

	/** Whether this holds at least {@code other} of every essence. */
	public boolean covers(EssenceList other) {
		for (Map.Entry<Essence, Integer> en : other.amounts.entrySet()) {
			if (get(en.getKey()) < en.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** This less {@code other}, or null when it doesn't hold enough of some essence. */
	public @Nullable EssenceList minus(EssenceList other) {
		if (!covers(other)) {
			return null;
		}
		EnumMap<Essence, Integer> out = new EnumMap<>(Essence.class);
		out.putAll(amounts);
		other.amounts.forEach((e, n) -> out.merge(e, -n, Integer::sum));
		return new EssenceList(out);
	}

	/** The essence there's most of (ties: the earlier one), or null when empty. */
	public @Nullable Essence main() {
		Essence best = null;
		int most = 0;
		for (Map.Entry<Essence, Integer> en : amounts.entrySet()) {
			if (en.getValue() > most) {
				best = en.getKey();
				most = en.getValue();
			}
		}
		return best;
	}
}
