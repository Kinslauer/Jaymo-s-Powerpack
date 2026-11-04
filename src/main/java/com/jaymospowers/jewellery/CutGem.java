package com.jaymospowers.jewellery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.jaymospowers.tinkering.smithing.Grade;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) A cut gem: which gem, which cut, how well it was cut ({@link Grade} id) and how often it chipped. One
 * component (jaymospowers:cut_gem) on the cut gem item, kept on jewellery set with it, and ready to be socketed into
 * weapons and armour. The ids are saved on items: never rename them.
 */
public record CutGem(String gem, String cut, String quality, int chips) {
	public static final Codec<CutGem> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("gem").forGetter(CutGem::gem),
		Codec.STRING.fieldOf("cut").forGetter(CutGem::cut),
		Codec.STRING.fieldOf("quality").forGetter(CutGem::quality),
		Codec.INT.optionalFieldOf("chips", 0).forGetter(CutGem::chips)).apply(i, CutGem::new));
	public static final StreamCodec<ByteBuf, CutGem> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, CutGem::gem, ByteBufCodecs.STRING_UTF8, CutGem::cut, ByteBufCodecs.STRING_UTF8, CutGem::quality,
		ByteBufCodecs.VAR_INT, CutGem::chips, CutGem::new);

	public Gems.@Nullable Gem gemType() {
		return Gems.byId(gem);
	}

	public Cut cutType() {
		return Cut.byId(cut);
	}

	public Grade grade() {
		Grade grade = Grade.byId(quality);
		return grade == null ? Grade.WELL : grade;
	}

	/** Grade times cut: the part of the bonus that comes from the stone itself (frames and sockets add their own). */
	public double scale() {
		return Jewellery.GRADE_FACTOR.get(grade()) * cutType().bonus;
	}

	public int colour() {
		Gems.Gem type = gemType();
		return type == null ? 0xFFFFFF : type.colour();
	}
}
