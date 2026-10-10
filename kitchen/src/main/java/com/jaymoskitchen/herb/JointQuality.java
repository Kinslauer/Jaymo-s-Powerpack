package com.jaymoskitchen.herb;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * How a joint was rolled, which decides what a puff does (besides the hunger every puff brings):
 * a perfect roll only gives good effects, one with tears in the paper only bad ones, anything else either.
 */
public enum JointQuality {
	PERFECT(ChatFormatting.GOLD),
	CLEAN(ChatFormatting.WHITE),
	TORN(ChatFormatting.DARK_RED);

	public static final Codec<JointQuality> CODEC = Codec.STRING.xmap(JointQuality::byName, quality -> quality.name().toLowerCase());
	public static final StreamCodec<ByteBuf, JointQuality> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
		i -> values()[Math.floorMod(i, values().length)], JointQuality::ordinal);

	public static final List<Holder<MobEffect>> GOOD = List.of(MobEffects.SPEED, MobEffects.HASTE, MobEffects.REGENERATION,
		MobEffects.RESISTANCE, MobEffects.JUMP_BOOST, MobEffects.NIGHT_VISION, MobEffects.FIRE_RESISTANCE, MobEffects.LUCK,
		MobEffects.ABSORPTION, MobEffects.WATER_BREATHING);
	public static final List<Holder<MobEffect>> BAD = List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE,
		MobEffects.NAUSEA, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.POISON, MobEffects.UNLUCK, MobEffects.DARKNESS);

	private final ChatFormatting color;

	JointQuality(ChatFormatting color) {
		this.color = color;
	}

	public ChatFormatting color() {
		return color;
	}

	public String translationKey() {
		return "item.jaymoskitchen.joint." + name().toLowerCase();
	}

	/** The random effect one puff gives. */
	public Holder<MobEffect> rollEffect(RandomSource random) {
		List<Holder<MobEffect>> pool = switch (this) {
			case PERFECT -> GOOD;
			case TORN -> BAD;
			case CLEAN -> random.nextBoolean() ? GOOD : BAD;
		};
		return pool.get(random.nextInt(pool.size()));
	}

	public static JointQuality byName(String name) {
		for (JointQuality quality : values()) {
			if (quality.name().equalsIgnoreCase(name)) {
				return quality;
			}
		}
		return CLEAN;
	}
}
