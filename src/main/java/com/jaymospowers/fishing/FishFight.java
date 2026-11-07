package com.jaymospowers.fishing;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's fight with a fish on the line, synced to them only so the client can draw the tension bar.
 * {@code tension} is 0..1 and the line breaks at 1; {@code progress} is 0..1 and the fish is caught at 1.
 * Each tick the server adds the fish's {@code strength} to tension while the player reels it down.
 */
public record FishFight(
        int fishKind,   // 0 cod, 1 salmon, 2 pufferfish, 3 tropical fish
        float strength, // how hard the fish fights (tension added per tick while not reeling)
        float tension,  // 0..1, line breaks at 1
        float progress, // 0..1, fish is caught at 1
        long startTime  // game time the fight began
) {
    public static final FishFight NONE = new FishFight(-1, 0.0F, 0.0F, 0.0F, 0L);

    public static final StreamCodec<ByteBuf, FishFight> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                ByteBufCodecs.VAR_INT.encode(buf, state.fishKind);
                ByteBufCodecs.FLOAT.encode(buf, state.strength);
                ByteBufCodecs.FLOAT.encode(buf, state.tension);
                ByteBufCodecs.FLOAT.encode(buf, state.progress);
                ByteBufCodecs.VAR_LONG.encode(buf, state.startTime);
            },
            buf -> new FishFight(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)));

    public boolean fighting() {
        return fishKind >= 0;
    }

    public FishFight withTension(float tension) {
        return new FishFight(fishKind, strength, tension, progress, startTime);
    }

    public FishFight withProgress(float progress) {
        return new FishFight(fishKind, strength, tension, progress, startTime);
    }
}
