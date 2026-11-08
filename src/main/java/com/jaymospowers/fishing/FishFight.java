package com.jaymospowers.fishing;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's fight with a fish on the line, synced so the client can draw the tension bar and bend the rod.
 * {@code tension} is 0..{@code breakAt} and the line breaks at {@code breakAt}; {@code progress} is 0..1 and the fish is
 * caught at 1. Each tick the server adds the fish's {@code strength} to tension while the player reels it down.
 * {@code run} is -1 while the fish runs to the player's left, 1 to the right, 0 while it holds (Track A).
 */
public record FishFight(
        int fishKind,   // 0 cod, 1 salmon, 2 pufferfish, 3 tropical fish, 4 shark, 5 tuna
        float strength, // how hard the fish fights (tension added per tick while reeling)
        float tension,  // 0..breakAt, line breaks at breakAt
        float progress, // 0..1, fish is caught at 1
        long startTime, // game time the fight began
        int run,        // -1 running left, 1 running right, 0 holding
        float breakAt   // tension the line snaps at (higher on the metal rod)
) {
    public static final FishFight NONE = new FishFight(-1, 0.0F, 0.0F, 0.0F, 0L, 0, 1.0F);

    public static final StreamCodec<ByteBuf, FishFight> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                ByteBufCodecs.VAR_INT.encode(buf, state.fishKind);
                ByteBufCodecs.FLOAT.encode(buf, state.strength);
                ByteBufCodecs.FLOAT.encode(buf, state.tension);
                ByteBufCodecs.FLOAT.encode(buf, state.progress);
                ByteBufCodecs.VAR_LONG.encode(buf, state.startTime);
                ByteBufCodecs.VAR_INT.encode(buf, state.run + 1);
                ByteBufCodecs.FLOAT.encode(buf, state.breakAt);
            },
            buf -> new FishFight(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf) - 1,
                    ByteBufCodecs.FLOAT.decode(buf)));

    /** A fresh fight: holding still, on a wooden rod's line until the first tick sets the real breaking point. */
    public FishFight(int fishKind, float strength, float tension, float progress, long startTime) {
        this(fishKind, strength, tension, progress, startTime, 0, 1.0F);
    }

    public FishFight withTension(float tension) {
        return new FishFight(fishKind, strength, tension, progress, startTime, run, breakAt);
    }

    public FishFight withProgress(float progress) {
        return new FishFight(fishKind, strength, tension, progress, startTime, run, breakAt);
    }

    public boolean fighting() {
        return fishKind >= 0;
    }

    /** Tension as a share of the breaking point, 0..1 (the HUD bar, the rod's bend and the creak). */
    public float strain() {
        return breakAt <= 0.0F ? 0.0F : Math.min(1.0F, tension / breakAt);
    }

    public FishFight with(float tension, float progress, int run, float breakAt) {
        return new FishFight(fishKind, strength, tension, progress, startTime, run, breakAt);
    }
}
