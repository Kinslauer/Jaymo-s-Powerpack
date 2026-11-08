package com.jaymospowers.power.blindness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Which eye/side is blind. */
public enum BlindSide {
    LEFT,
    RIGHT;

    public static final Codec<BlindSide> CODEC = Codec.STRING.xmap(
            s -> valueOf(s.toUpperCase(java.util.Locale.ROOT)),
            Enum::name
    );

    public static final StreamCodec<ByteBuf, BlindSide> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByteBufCodecs.VAR_INT,
            BlindSide[]::new,
            Enum::ordinal
    );

    public String langKey() {
        return "blindness.jaymospowers.side." + name().toLowerCase(java.util.Locale.ROOT);
    }
}