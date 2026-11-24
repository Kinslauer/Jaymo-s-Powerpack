package com.jaymospowers.fishing;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's fight with a fish on the line, synced so the client can draw the fight's readout and bend the rod.
 * {@code tension} is 0..{@code breakAt} and the line breaks at {@code breakAt}; {@code progress} is 0..1 and the fish is
 * caught at 1. Each tick the server adds the fish's {@code strength} to tension while the player reels it down.
 * {@code run} is -1 while the fish runs to the player's left, 1 to the right, 0 while it holds (Track A).
 * <p>
 * (Andy, 2026-11-23, a fishing gif: "thats what i want our fishing to be like") The fish's {@code mood} tells you what to do
 * ({@link #TAKING}, {@link #CALM}: reel; {@link #RUNNING}, {@link #JUMPING}, {@link #THRASHING}: ease off; {@link #LANDING}),
 * its {@code energy} (1 fresh, falling as it fights) tires it, and the line out is in metres of the spool ({@link #lineOut},
 * {@code lineMax}): all of it run out, and the line parts. {@code kg} is its weight, for the readout.
 */
public record FishFight(
        int fishKind,   // the species ({@link Species#of})
        float strength, // how hard the fish fights (tension added per tick while reeling)
        float tension,  // 0..breakAt, line breaks at breakAt
        float progress, // 0..1, fish is caught at 1
        long startTime, // game time the fight began
        int run,        // -1 running left, 1 running right, 0 holding
        float breakAt,  // tension the line snaps at (higher on the metal rod)
        int mood,       // what the fish is doing now (TAKING ... LANDING)
        float energy,   // 1 fresh, down toward 0 as it tires
        float lineMax,  // metres of line on the spool
        float kg        // its weight
) {
    /** The fish's moods. */
    public static final int TAKING = 0, CALM = 1, RUNNING = 2, JUMPING = 3, THRASHING = 4, LANDING = 5;

    public static final FishFight NONE = new FishFight(-1, 0.0F, 0.0F, 0.0F, 0L, 0, 1.0F, TAKING, 1.0F, 1.0F, 0.0F);

    public static final StreamCodec<ByteBuf, FishFight> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                ByteBufCodecs.VAR_INT.encode(buf, state.fishKind + 1);
                ByteBufCodecs.FLOAT.encode(buf, state.strength);
                ByteBufCodecs.FLOAT.encode(buf, state.tension);
                ByteBufCodecs.FLOAT.encode(buf, state.progress);
                ByteBufCodecs.VAR_LONG.encode(buf, state.startTime);
                ByteBufCodecs.VAR_INT.encode(buf, state.run + 1);
                ByteBufCodecs.FLOAT.encode(buf, state.breakAt);
                ByteBufCodecs.VAR_INT.encode(buf, state.mood);
                ByteBufCodecs.FLOAT.encode(buf, state.energy);
                ByteBufCodecs.FLOAT.encode(buf, state.lineMax);
                ByteBufCodecs.FLOAT.encode(buf, state.kg);
            },
            buf -> new FishFight(
                    ByteBufCodecs.VAR_INT.decode(buf) - 1,
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf) - 1,
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf)));

    /** A fresh fight: taking the lure, fresh, on {@code lineMax} metres of line. */
    public FishFight(int fishKind, float strength, float progress, long startTime, float lineMax, float kg) {
        this(fishKind, strength, 0.0F, progress, startTime, 0, 1.0F, TAKING, 1.0F, lineMax, kg);
    }

    public boolean fighting() {
        return fishKind >= 0;
    }

    /** Tension as a share of the breaking point, 0..1 (the readout's bar, the rod's bend and the creak). */
    public float strain() {
        return breakAt <= 0.0F ? 0.0F : Math.min(1.0F, tension / breakAt);
    }

    /** Metres of line out to the fish: all of it at no progress, none when it's landed. */
    public float lineOut() {
        return lineMax * (1.0F - Math.max(0.0F, Math.min(1.0F, progress)));
    }

    /** Whether the fish's mood asks you to ease off (it runs, jumps or thrashes). */
    public boolean easeOff() {
        return mood == RUNNING || mood == JUMPING || mood == THRASHING;
    }

    public FishFight with(float tension, float progress, int run, float breakAt, int mood, float energy) {
        return new FishFight(fishKind, strength, tension, progress, startTime, run, breakAt, mood, energy, lineMax, kg);
    }
}
