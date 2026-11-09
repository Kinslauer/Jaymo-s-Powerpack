package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Client-synced state for the Blindness power.
 */
public record BlindnessState(
        long echoFrom,
        long oneEyeFrom,
        long blindsideFrom,
        long deafenFrom,
        BlindnessVariant variant,
        BlindSide blindSide,
        boolean deafenActive,
        boolean voidSightActive
) {
    public static final BlindnessState NONE = new BlindnessState(0, 0, 0, 0, BlindnessVariant.BORRADO, BlindSide.LEFT, false, false);

    // slot indices
    public static final int ECHO = 0;
    public static final int ONE_EYE = 1;
    public static final int BLINDSIDE = 2;
    public static final int DEAFEN = 3;
    public static final int SLOT_COUNT = 4;

    public static final Codec<BlindnessState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("echoFrom").forGetter(BlindnessState::echoFrom),
            Codec.LONG.fieldOf("oneEyeFrom").forGetter(BlindnessState::oneEyeFrom),
            Codec.LONG.fieldOf("blindsideFrom").forGetter(BlindnessState::blindsideFrom),
            Codec.LONG.fieldOf("deafenFrom").forGetter(BlindnessState::deafenFrom),
            BlindnessVariant.CODEC.fieldOf("variant").forGetter(BlindnessState::variant),
            BlindSide.CODEC.fieldOf("blindSide").forGetter(BlindnessState::blindSide),
            Codec.BOOL.fieldOf("deafenActive").forGetter(BlindnessState::deafenActive),
            Codec.BOOL.fieldOf("voidSightActive").forGetter(BlindnessState::voidSightActive)
    ).apply(instance, BlindnessState::new));

    public static final StreamCodec<ByteBuf, BlindnessState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BlindnessState::echoFrom,
            ByteBufCodecs.VAR_LONG, BlindnessState::oneEyeFrom,
            ByteBufCodecs.VAR_LONG, BlindnessState::blindsideFrom,
            ByteBufCodecs.VAR_LONG, BlindnessState::deafenFrom,
            BlindnessVariant.STREAM_CODEC, BlindnessState::variant,
            BlindSide.STREAM_CODEC, BlindnessState::blindSide,
            ByteBufCodecs.BOOL, BlindnessState::deafenActive,
            ByteBufCodecs.BOOL, BlindnessState::voidSightActive,
            BlindnessState::new
    );

    public long[] length() {
        return new long[]{echoFrom, oneEyeFrom, blindsideFrom, deafenFrom};
    }

    public long left(int slot, long now) {
        long from = switch (slot) {
            case ECHO -> echoFrom;
            case ONE_EYE -> oneEyeFrom;
            case BLINDSIDE -> blindsideFrom;
            case DEAFEN -> deafenFrom;
            default -> 0;
        };
        if (from <= 0) {
            return 0;
        }
        // duration is stored as negative from for active effects, positive for cooldowns
        if (from < 0) {
            return -from; // active: time left
        }
        return from - now; // cooldown: time left
    }

    public boolean isActive(int slot) {
        long from = switch (slot) {
            case ECHO -> echoFrom;
            case ONE_EYE -> oneEyeFrom;
            case DEAFEN -> deafenFrom;
            default -> 0;
        };
        return from < 0;
    }

    public BlindnessState withCooldown(int slot, long now, int ticks) {
        long from = now + ticks;
        return switch (slot) {
            case ECHO -> new BlindnessState(from, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
            case ONE_EYE -> new BlindnessState(echoFrom, from, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
            case BLINDSIDE -> new BlindnessState(echoFrom, oneEyeFrom, from, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
            case DEAFEN -> new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, from, variant, blindSide, deafenActive, voidSightActive);
            default -> this;
        };
    }

    public BlindnessState withActive(int slot, int durationTicks, long now) {
        long from = -(now + durationTicks); // negative means active
        return switch (slot) {
            case ECHO -> new BlindnessState(from, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
            case ONE_EYE -> new BlindnessState(echoFrom, from, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
            case DEAFEN -> new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, from, variant, blindSide, true, voidSightActive);
            default -> this;
        };
    }

    public BlindnessState withVariant(BlindnessVariant variant) {
        return new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
    }

    public BlindnessState withBlindSide(BlindSide blindSide) {
        return new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, voidSightActive);
    }

    public BlindnessState withDeafenActive(boolean active) {
        return new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, active, voidSightActive);
    }

    public BlindnessState withVoidSightActive(boolean active) {
        return new BlindnessState(echoFrom, oneEyeFrom, blindsideFrom, deafenFrom, variant, blindSide, deafenActive, active);
    }
}