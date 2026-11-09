package com.jaymospowers.power.blindness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One-eye blind variant: Borrado (blur) or Escuro (darken). */
public enum BlindnessVariant {
    BORRADO,
    ESCURO;

    public static final Codec<BlindnessVariant> CODEC = Codec.STRING.xmap(
            s -> valueOf(s.toUpperCase(java.util.Locale.ROOT)),
            Enum::name
    );

    public static final StreamCodec<ByteBuf, BlindnessVariant> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public String langKey() {
        return "blindness.jaymospowers.variant." + name().toLowerCase(java.util.Locale.ROOT);
    }
}