package com.jaymospowers.core.blood;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.15.91) The blood's particles, ported from Iron431's Iron's Simple Blood (MIT; github.com/iron431/simple-blood-mod,
 * notice in {@code licenses/LICENSE_simple_blood}; its sprites under {@code textures/particle/blood/}). The server sends
 * a {@link Spray} (its colour and size); each one the client gets becomes one of the {@link #DROPS} at random (a drop,
 * spatter, spurt or swipe) that flies, falls and leaves a {@link Puddle} where it lands, laid over whatever it lands on
 * (the client's {@code client/blood}). A {@link Puddle} can also be sent on its own (a heavy blow's pool, see
 * {@code BladeBlood}). Who bleeds, what colour and how much is {@code BladeBlood}'s.
 */
public final class BloodParticles {
	/** The blood a creature sprays: one of the drops at random, in its colour, this size (1 = a man's). */
	public record Spray(int color, float scale) implements ParticleOptions {
		public static final MapCodec<Spray> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Spray::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Spray::scale)).apply(i, Spray::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spray> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Spray::color, ByteBufCodecs.FLOAT, Spray::scale, Spray::new);

		@Override
		public ParticleType<?> getType() {
			return SPRAY;
		}
	}

	/** A pool of blood on the ground (or whatever's under it), this size. */
	public record Puddle(int color, float scale) implements ParticleOptions {
		public static final MapCodec<Puddle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Puddle::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Puddle::scale)).apply(i, Puddle::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Puddle> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Puddle::color, ByteBufCodecs.FLOAT, Puddle::scale, Puddle::new);

		@Override
		public ParticleType<?> getType() {
			return PUDDLE;
		}
	}

	public static final ParticleType<Spray> SPRAY = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("blood_spray"),
		FabricParticleTypes.complex(true, Spray.CODEC, Spray.STREAM_CODEC));
	public static final ParticleType<Puddle> PUDDLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("blood_puddle"),
		FabricParticleTypes.complex(true, Puddle.CODEC, Puddle.STREAM_CODEC));

	/** The drops a {@link Spray} picks from (each its own sprites, {@code particles/<name>.json}). */
	public static final SimpleParticleType DROP = simple("blood_drop"), SPAT_1 = simple("blood_spat_1"), SPAT_2 = simple("blood_spat_2"),
		SPURT = simple("blood_spurt"), SWIPE_1 = simple("blood_swipe_1"), SWIPE_2 = simple("blood_swipe_2");

	private BloodParticles() {
	}

	/** Registers the particles (by loading this class). */
	public static void init() {
	}

	private static SimpleParticleType simple(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id(name), FabricParticleTypes.simple(true));
	}
}
