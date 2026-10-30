package com.jaymospowers.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * The essences everything is made of (our own scheme: the four elements and the apothecary's three principles, salt,
 * sulphur and mercury, then compounds of two). A compound is only ever learned after both its parents.
 * Colours are the phial and particle tint (0xRRGGBB).
 */
public enum Essence {
	// the seven primals
	FIRE(0xE2572B), WATER(0x3B78D8), EARTH(0x6B4F2A), AIR(0xDCE8F0),
	/** Order, preserving, crystal: what keeps things as they are. */
	SALT(0xF2EEE2),
	/** Passion, burning, the soul's heat. */
	SULPHUR(0xD9C22E),
	/** Change, flowing, the restless spirit. */
	MERCURY(0xA9B4BF),
	// first compounds
	LIFE(0xD8364A, WATER, EARTH),
	LIGHT(0xFFF3A0, FIRE, AIR),
	METAL(0x8E8E96, EARTH, SALT),
	FROST(0xA8E4FF, WATER, SALT),
	MOTION(0xB7F0D0, AIR, MERCURY),
	SPARK(0xFF9B21, FIRE, SULPHUR),
	ROT(0x5E6B2A, EARTH, MERCURY),
	VENOM(0x7AC74F, WATER, SULPHUR),
	CRYSTAL(0x7FE0D8, SALT, FIRE),
	SPIRIT(0xC9B8FF, SULPHUR, MERCURY),
	// second compounds
	PLANT(0x4E9A2E, LIFE, EARTH),
	BEAST(0x9C6B3E, LIFE, MOTION),
	DEATH(0x3A3340, LIFE, ROT),
	MIND(0xF2A8E0, SPIRIT, LIGHT),
	SHADOW(0x2B2240, LIGHT, ROT),
	/** (0.18.56) What falls from the sky and what drifts at the black hole's rim: light carried on the air. */
	STARLIGHT(0xC8D4FF, LIGHT, AIR),
	ARCANA(0x9B4DFF, SPIRIT, CRYSTAL),
	WEALTH(0xF5C542, METAL, CRYSTAL),
	// third compounds
	CRAFT(0xB08A5A, METAL, MIND),
	WAR(0xA02020, METAL, BEAST),
	UNDEATH(0x5B6E4A, DEATH, SPIRIT),
	/** Magic gone wrong: what alchemy leaves behind. Never wanted, sometimes needed. */
	FLUX(0x8A2BB8, ARCANA, ROT);

	public static final Codec<Essence> CODEC = Codec.STRING.comapFlatMap(
		s -> {
			Essence e = byId(s);
			return e == null ? DataResult.error(() -> "Unknown essence: " + s) : DataResult.success(e);
		}, Essence::id);
	public static final StreamCodec<ByteBuf, Essence> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	public final int colour;
	public final @Nullable Essence a, b;

	Essence(int colour) {
		this(colour, null, null);
	}

	Essence(int colour, @Nullable Essence a, @Nullable Essence b) {
		this.colour = colour;
		this.a = a;
		this.b = b;
	}

	public boolean primal() {
		return a == null;
	}

	/** 0 for primals, then one more than the deeper parent. */
	public int tier() {
		return primal() ? 0 : 1 + Math.max(a.tier(), b.tier());
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The lang key of its name: {@code essence.jaymospowers.<id>}. */
	public String key() {
		return "essence.jaymospowers." + id();
	}

	public static @Nullable Essence byId(String id) {
		for (Essence e : values()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
