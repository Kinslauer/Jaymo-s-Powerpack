package com.jaymospowers.costume;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.58, Andy's NOEA picks) The suit editor's extras on a {@link CostumeDesign}: a third colour (the trim) and the
 * small pieces worn over the suit, each its own choice: gloves, boots, a belt, a helmet and head fins. They live in one
 * record at the end of the design, so a design saved before them reads with {@link #NONE} (no pieces, the trim following
 * the secondary colour) and looks exactly as it did.
 * <p>
 * Every enum here only ever grows at the end (saved designs keep their names, and the network sends the ordinal).
 */
public record CostumeKit(int trim, Gloves gloves, Boots boots, Belt belt, Helm helm, Fins fins) {
	/** {@link #trim} when the trim follows the secondary colour. */
	public static final int TRIM_FOLLOWS = -1;
	/** A kit with nothing on it. */
	public static final CostumeKit NONE = new CostumeKit(TRIM_FOLLOWS, Gloves.NONE, Boots.NONE, Belt.NONE, Helm.NONE, Fins.NONE);

	/** The hands: wraps round the wrists, fingerless gloves, or armoured gauntlets. */
	public enum Gloves implements StringRepresentable {
		NONE, WRAPS, GLOVES, GAUNTLETS;

		public static final Codec<Gloves> CODEC = StringRepresentable.fromEnum(Gloves::values);
		public static final StreamCodec<ByteBuf, Gloves> STREAM_CODEC = CostumeKit.byOrdinal(values());

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The feet: wrapped ankles, boots, or greaves up the shin. */
	public enum Boots implements StringRepresentable {
		NONE, WRAPPED, BOOTS, GREAVES;

		public static final Codec<Boots> CODEC = StringRepresentable.fromEnum(Boots::values);
		public static final StreamCodec<ByteBuf, Boots> STREAM_CODEC = CostumeKit.byOrdinal(values());

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The waist: a sash with hanging ends, a plain belt, or a wide champion's belt with a plate. */
	public enum Belt implements StringRepresentable {
		NONE, SASH, BELT, CHAMPION;

		public static final Codec<Belt> CODEC = StringRepresentable.fromEnum(Belt::values);
		public static final StreamCodec<ByteBuf, Belt> STREAM_CODEC = CostumeKit.byOrdinal(values());

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The head, over a mask: a circlet, a battle helm with cheek guards, or a visor. */
	public enum Helm implements StringRepresentable {
		NONE, CIRCLET, BATTLE_HELM, VISOR;

		public static final Codec<Helm> CODEC = StringRepresentable.fromEnum(Helm::values);
		public static final StreamCodec<ByteBuf, Helm> STREAM_CODEC = CostumeKit.byOrdinal(values());

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Head fins: small ones at the temples, long swept-back ones, or a tall crest along the top. */
	public enum Fins implements StringRepresentable {
		NONE, SMALL, SWEPT, CREST;

		public static final Codec<Fins> CODEC = StringRepresentable.fromEnum(Fins::values);
		public static final StreamCodec<ByteBuf, Fins> STREAM_CODEC = CostumeKit.byOrdinal(values());

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> byOrdinal(E[] values) {
		return ByteBufCodecs.VAR_INT.map(i -> values[Math.floorMod(i, values.length)], Enum::ordinal);
	}

	public static final Codec<CostumeKit> CODEC = RecordCodecBuilder.<CostumeKit>create(i -> i.group(
		Codec.INT.optionalFieldOf("trim", TRIM_FOLLOWS).forGetter(CostumeKit::trim),
		Gloves.CODEC.optionalFieldOf("gloves", Gloves.NONE).forGetter(CostumeKit::gloves),
		Boots.CODEC.optionalFieldOf("boots", Boots.NONE).forGetter(CostumeKit::boots),
		Belt.CODEC.optionalFieldOf("belt", Belt.NONE).forGetter(CostumeKit::belt),
		Helm.CODEC.optionalFieldOf("helm", Helm.NONE).forGetter(CostumeKit::helm),
		Fins.CODEC.optionalFieldOf("fins", Fins.NONE).forGetter(CostumeKit::fins)
	).apply(i, CostumeKit::new)).xmap(CostumeKit::sanitized, k -> k);

	public static final StreamCodec<ByteBuf, CostumeKit> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CostumeKit::trim,
		Gloves.STREAM_CODEC, CostumeKit::gloves,
		Boots.STREAM_CODEC, CostumeKit::boots,
		Belt.STREAM_CODEC, CostumeKit::belt,
		Helm.STREAM_CODEC, CostumeKit::helm,
		Fins.STREAM_CODEC, CostumeKit::fins,
		CostumeKit::new).map(CostumeKit::sanitized, k -> k);

	/** The same kit with a trim that is a real dye (or follows the secondary). */
	public CostumeKit sanitized() {
		int clean = this.trim < 0 || this.trim >= DyeColor.values().length ? TRIM_FOLLOWS : this.trim;
		return clean == this.trim ? this : new CostumeKit(clean, gloves, boots, belt, helm, fins);
	}

	/** The trim's dye: its own, or {@code secondary} when it follows it. */
	public DyeColor trimColour(DyeColor secondary) {
		return this.trim == TRIM_FOLLOWS ? secondary : DyeColor.byId(this.trim);
	}

	public boolean followsSecondary() {
		return this.trim == TRIM_FOLLOWS;
	}

	/** How many pieces are worn (each costs {@link CostumeNet#NUGGETS_PER_PIECE} iron nuggets to sew). */
	public int pieces() {
		return (this.gloves != Gloves.NONE ? 1 : 0) + (this.boots != Boots.NONE ? 1 : 0) + (this.belt != Belt.NONE ? 1 : 0)
			+ (this.helm != Helm.NONE ? 1 : 0) + (this.fins != Fins.NONE ? 1 : 0);
	}

	public CostumeKit withTrim(@Nullable DyeColor colour) {
		return new CostumeKit(colour == null ? TRIM_FOLLOWS : colour.getId(), gloves, boots, belt, helm, fins);
	}

	public CostumeKit withGloves(Gloves gloves) {
		return new CostumeKit(trim, gloves, boots, belt, helm, fins);
	}

	public CostumeKit withBoots(Boots boots) {
		return new CostumeKit(trim, gloves, boots, belt, helm, fins);
	}

	public CostumeKit withBelt(Belt belt) {
		return new CostumeKit(trim, gloves, boots, belt, helm, fins);
	}

	public CostumeKit withHelm(Helm helm) {
		return new CostumeKit(trim, gloves, boots, belt, helm, fins);
	}

	public CostumeKit withFins(Fins fins) {
		return new CostumeKit(trim, gloves, boots, belt, helm, fins);
	}
}
