package com.jaymospowers.costume;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Base64;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.66) One costume, as designed at the Costume Table: its name, body cut, two dye colours, what covers the head, the
 * cape, and a 16x16 emblem for the chest. It rides on the costume item ({@link Costume#DESIGN}) and, once put on, on the
 * player ({@link Costume#WORN}); everything that draws or reads a costume works from this alone.
 * <p>
 * The emblem is packed: each pixel is one of {@link #EMBLEM_COLOURS} palette entries (3 bits), 256 pixels in 96 bytes,
 * kept as Base64 (128 characters), or empty for a blank emblem.
 */
public record CostumeDesign(String name, Cut cut, DyeColor primary, DyeColor secondary, Mask mask, Cape cape, String emblem) {
	/** The longest name a costume can have. */
	public static final int MAX_NAME = 24;
	/** The emblem's size, in pixels each way. */
	public static final int EMBLEM_SIZE = 16;
	public static final int EMBLEM_PIXELS = EMBLEM_SIZE * EMBLEM_SIZE;
	/** Palette entries a pixel can take: empty, primary, secondary, black, white, gold. */
	public static final int EMPTY = 0, PRIMARY = 1, SECONDARY = 2, BLACK = 3, WHITE = 4, GOLD = 5, EMBLEM_COLOURS = 6;
	/** The fixed palette entries' colours (ARGB); primary and secondary are the dyes'. */
	public static final int BLACK_RGB = 0xFF1A1A1E, WHITE_RGB = 0xFFF2F2F2, GOLD_RGB = 0xFFE8B830;
	private static final int PACKED_BYTES = EMBLEM_PIXELS * 3 / 8;

	/** A fresh design: a plain blue and white bodysuit. */
	public static final CostumeDesign DEFAULT = new CostumeDesign("", Cut.BODYSUIT, DyeColor.BLUE, DyeColor.WHITE, Mask.NONE, Cape.NONE, "");

	/**
	 * How the body is cut: each is a pair of mask textures (where the primary goes, where the secondary goes).
	 * (0.11.78) The cuts after {@link #RAGGED} are power sets ({@link CostumeSets}): {@link #power} names the power
	 * (its id's path) that adds it to the table. New ones only ever go at the end (saved designs keep their names, and
	 * the network sends the ordinal).
	 */
	public enum Cut implements StringRepresentable {
		BODYSUIT, ARMOURED, ROBES, COAT, RAGGED,
		WEBBED("web"), WINDSWEPT("wind"), DENSE("gravity"), CLOCKWORK("chrono"), BANDOLIER("arsenal"), RESONANT("resonance"),
		TIDAL("darktide"), ASH_WRAPS("smoke"), GI("zfighter"), CARAPACE("xenoform"),
		// (0.13.36) anyone's: the assassin's robe
		ASSASSIN;

		public static final Codec<Cut> CODEC = StringRepresentable.fromEnum(Cut::values);
		public static final StreamCodec<ByteBuf, Cut> STREAM_CODEC = CostumeDesign.byOrdinal(values());

		/** The power whose set this is (its id's path), or null for anyone's. */
		public final @Nullable String power;

		Cut() {
			this(null);
		}

		Cut(@Nullable String power) {
			this.power = power;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** What covers the head. {@link #hidesFace} is what a later "masked" (secret identity) check reads. */
	public enum Mask implements StringRepresentable {
		NONE(false), HALF_MASK(false), FULL_MASK(true), HOOD(false), HELMET(true), GOGGLES(false),
		// (0.11.78) power sets
		WEB_MASK(true, "web"), ASH_VEIL(true, "smoke"), HEADBAND(false, "zfighter"),
		// (0.13.36) anyone's: a deep hood with a peak over the brow; like the plain hood it leaves the face showing
		PEAKED_HOOD(false);

		public static final Codec<Mask> CODEC = StringRepresentable.fromEnum(Mask::values);
		public static final StreamCodec<ByteBuf, Mask> STREAM_CODEC = CostumeDesign.byOrdinal(values());

		/**
		 * (0.13.36) Whether it is a hood ({@link #HOOD}, {@link #PEAKED_HOOD}): the face shows, but a hooded player is
		 * harder to pick out at a distance ({@code SecretIdentity#HOODED}, the name tag's range) and Kingdoms' stealth
		 * reads it.
		 */
		public boolean isHood() {
			return this == HOOD || this == PEAKED_HOOD;
		}

		/** Whether it hides who you are (the whole face is covered). */
		public final boolean hidesFace;
		/** The power whose set this is (its id's path), or null for anyone's. */
		public final @Nullable String power;

		Mask(boolean hidesFace) {
			this(hidesFace, null);
		}

		Mask(boolean hidesFace, @Nullable String power) {
			this.hidesFace = hidesFace;
			this.power = power;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The cape, if any. */
	public enum Cape implements StringRepresentable {
		NONE, SHORT, LONG, TATTERED,
		// (0.11.78) power sets
		GALE("wind"), TIDE("darktide");

		public static final Codec<Cape> CODEC = StringRepresentable.fromEnum(Cape::values);
		public static final StreamCodec<ByteBuf, Cape> STREAM_CODEC = CostumeDesign.byOrdinal(values());

		/** The power whose set this is (its id's path), or null for anyone's. */
		public final @Nullable String power;

		Cape() {
			this(null);
		}

		Cape(@Nullable String power) {
			this.power = power;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> byOrdinal(E[] values) {
		return ByteBufCodecs.VAR_INT.map(i -> values[Math.floorMod(i, values.length)], Enum::ordinal);
	}

	public static final Codec<CostumeDesign> CODEC = RecordCodecBuilder.<CostumeDesign>create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(CostumeDesign::name),
		Cut.CODEC.optionalFieldOf("cut", Cut.BODYSUIT).forGetter(CostumeDesign::cut),
		DyeColor.CODEC.optionalFieldOf("primary", DyeColor.BLUE).forGetter(CostumeDesign::primary),
		DyeColor.CODEC.optionalFieldOf("secondary", DyeColor.WHITE).forGetter(CostumeDesign::secondary),
		Mask.CODEC.optionalFieldOf("mask", Mask.NONE).forGetter(CostumeDesign::mask),
		Cape.CODEC.optionalFieldOf("cape", Cape.NONE).forGetter(CostumeDesign::cape),
		Codec.STRING.optionalFieldOf("emblem", "").forGetter(CostumeDesign::emblem)
	).apply(i, CostumeDesign::new)).xmap(CostumeDesign::sanitized, d -> d);

	public static final StreamCodec<ByteBuf, CostumeDesign> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(MAX_NAME * 4), CostumeDesign::name,
		Cut.STREAM_CODEC, CostumeDesign::cut,
		DyeColor.STREAM_CODEC, CostumeDesign::primary,
		DyeColor.STREAM_CODEC, CostumeDesign::secondary,
		Mask.STREAM_CODEC, CostumeDesign::mask,
		Cape.STREAM_CODEC, CostumeDesign::cape,
		ByteBufCodecs.stringUtf8(256), CostumeDesign::emblem,
		CostumeDesign::new).map(CostumeDesign::sanitized, d -> d);

	/** The same design with its name cleaned (no control characters, at most {@link #MAX_NAME}) and a valid emblem. */
	public CostumeDesign sanitized() {
		String clean = cleanName(this.name);
		String packed = pack(unpack(this.emblem));
		return clean.equals(this.name) && packed.equals(this.emblem) ? this
			: new CostumeDesign(clean, this.cut, this.primary, this.secondary, this.mask, this.cape, packed);
	}

	public static String cleanName(String name) {
		StringBuilder out = new StringBuilder();
		name.codePoints().filter(c -> c >= 0x20 && c != 0x7F && c != '§').forEach(out::appendCodePoint);
		String s = out.toString().strip();
		return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).strip() : s;
	}

	public CostumeDesign withName(String name) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withCut(Cut cut) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withPrimary(DyeColor primary) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withSecondary(DyeColor secondary) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withMask(Mask mask) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withCape(Cape cape) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, emblem);
	}

	public CostumeDesign withEmblem(byte[] pixels) {
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, pack(pixels));
	}

	/** The emblem's pixels (palette entries, row by row from the top left). */
	public byte[] pixels() {
		return unpack(this.emblem);
	}

	public boolean hasEmblem() {
		return !this.emblem.isEmpty();
	}

	/** The colour (ARGB) of a palette entry in this design, or 0 (clear) for an empty pixel. */
	public int colour(int entry) {
		return switch (entry) {
			case PRIMARY -> 0xFF000000 | this.primary.getTextureDiffuseColor();
			case SECONDARY -> 0xFF000000 | this.secondary.getTextureDiffuseColor();
			case BLACK -> BLACK_RGB;
			case WHITE -> WHITE_RGB;
			case GOLD -> GOLD_RGB;
			default -> 0;
		};
	}

	/** 256 palette entries packed 3 bits each into Base64; a blank emblem packs to "". */
	public static String pack(byte[] pixels) {
		byte[] out = new byte[PACKED_BYTES];
		boolean any = false;
		for (int i = 0; i < EMBLEM_PIXELS && i < pixels.length; i++) {
			int v = pixels[i] >= 0 && pixels[i] < EMBLEM_COLOURS ? pixels[i] : EMPTY;
			if (v == EMPTY) {
				continue;
			}
			any = true;
			for (int b = 0; b < 3; b++) {
				if ((v >> b & 1) != 0) {
					int bit = i * 3 + b;
					out[bit >> 3] |= (byte)(1 << (bit & 7));
				}
			}
		}
		return any ? Base64.getEncoder().withoutPadding().encodeToString(out) : "";
	}

	/** The reverse of {@link #pack}: anything malformed reads as blank, entries out of range as empty. */
	public static byte[] unpack(String packed) {
		byte[] pixels = new byte[EMBLEM_PIXELS];
		if (packed.isEmpty()) {
			return pixels;
		}
		byte[] in;
		try {
			in = Base64.getDecoder().decode(packed);
		} catch (IllegalArgumentException e) {
			return pixels;
		}
		if (in.length != PACKED_BYTES) {
			return pixels;
		}
		for (int i = 0; i < EMBLEM_PIXELS; i++) {
			int v = 0;
			for (int b = 0; b < 3; b++) {
				int bit = i * 3 + b;
				if ((in[bit >> 3] >> (bit & 7) & 1) != 0) {
					v |= 1 << b;
				}
			}
			pixels[i] = (byte)(v < EMBLEM_COLOURS ? v : EMPTY);
		}
		return pixels;
	}
}
