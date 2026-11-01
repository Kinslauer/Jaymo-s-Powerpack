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
 * (0.15.93) The blood's particles, ported from Iron431's Iron's Simple Blood (MIT; github.com/iron431/simple-blood-mod,
 * notice in {@code licenses/LICENSE_simple_blood}; its sprites under {@code textures/particle/blood/}). The server sends
 * a {@link Spray} (its colour and size); each one the client gets becomes one of the drop kinds ({@link #DROP} and the rest) at random (a drop,
 * spatter, spurt or swipe) that flies, falls and leaves a {@link Puddle} where it lands, laid over whatever it lands on
 * (the client's {@code client/blood}). A {@link Puddle} can also be sent on its own (a heavy blow's pool, see
 * {@code BladeBlood}). Who bleeds, what colour and how much is {@code BladeBlood}'s.
 */
public final class BloodParticles {
	/**
	 * The blood a creature sprays: one of the drops at random, in its colour, this size (1 = a man's). (0.17.182) A drop
	 * leaves its own short-lived splash where it lands unless {@code stains} is false (a cut's drip, whose lasting stain
	 * the server lays, {@link BloodTrail}).
	 */
	public record Spray(int color, float scale, boolean stains) implements ParticleOptions {
		public static final MapCodec<Spray> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Spray::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Spray::scale),
			Codec.BOOL.optionalFieldOf("stains", true).forGetter(Spray::stains)).apply(i, Spray::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spray> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Spray::color, ByteBufCodecs.FLOAT, Spray::scale, ByteBufCodecs.BOOL, Spray::stains, Spray::new);

		public Spray(int color, float scale) {
			this(color, scale, true);
		}

		@Override
		public ParticleType<?> getType() {
			return SPRAY;
		}
	}

	/**
	 * A pool of blood on the ground (or whatever's under it), this size. (0.17.182) One of {@link BloodTrail}'s lasting
	 * stains carries its {@code mark} id (0 for a passing splash), how many ticks old it is and how many it has left.
	 * (0.19.52) {@code shape}: {@link #ROUND}, or a smear dragged out along the ground east-west ({@link #SMEAR_X}) or
	 * north-south ({@link #SMEAR_Z}), long and narrow (a body dragged through it, {@link BloodTrail#smear}).
	 */
	public record Puddle(int color, float scale, long mark, int age, int life, int shape) implements ParticleOptions {
		public static final int ROUND = 0, SMEAR_X = 1, SMEAR_Z = 2;
		public static final MapCodec<Puddle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Puddle::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Puddle::scale),
			Codec.LONG.optionalFieldOf("mark", 0L).forGetter(Puddle::mark),
			Codec.INT.optionalFieldOf("age", 0).forGetter(Puddle::age),
			Codec.INT.optionalFieldOf("life", 0).forGetter(Puddle::life),
			Codec.INT.optionalFieldOf("shape", ROUND).forGetter(Puddle::shape)).apply(i, Puddle::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Puddle> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Puddle::color, ByteBufCodecs.FLOAT, Puddle::scale, ByteBufCodecs.VAR_LONG, Puddle::mark, ByteBufCodecs.VAR_INT,
			Puddle::age, ByteBufCodecs.VAR_INT, Puddle::life, ByteBufCodecs.VAR_INT, Puddle::shape, Puddle::new);

		public Puddle(int color, float scale) {
			this(color, scale, 0L, 0, 0, ROUND);
		}

		@Override
		public ParticleType<?> getType() {
			return PUDDLE;
		}
	}

	/**
	 * (0.15.109) A fleck of blood: a still drop that drifts down and fades, never shimmering like redstone dust (Andy).
	 * Every bit of blood that isn't a {@link Spray} (gore, severing, a symbiote's meal) is these, see {@link #fleck}.
	 */
	public record Fleck(int color, float scale) implements ParticleOptions {
		public static final MapCodec<Fleck> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Fleck::color),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Fleck::scale)).apply(i, Fleck::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fleck> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Fleck::color, ByteBufCodecs.FLOAT, Fleck::scale, Fleck::new);

		@Override
		public ParticleType<?> getType() {
			return FLECK;
		}
	}

	public static final ParticleType<Spray> SPRAY = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("blood_spray"),
		FabricParticleTypes.complex(true, Spray.CODEC, Spray.STREAM_CODEC));
	public static final ParticleType<Puddle> PUDDLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("blood_puddle"),
		FabricParticleTypes.complex(true, Puddle.CODEC, Puddle.STREAM_CODEC));

	public static final ParticleType<Fleck> FLECK = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("blood_fleck"),
		FabricParticleTypes.complex(false, Fleck.CODEC, Fleck.STREAM_CODEC));
	/** (0.15.109) A plain red {@link Fleck}, by id for mods that can't build one (Jaymo's Kingdoms looks it up). */
	public static final SimpleParticleType RED_FLECK = simple("blood_fleck_red");

	/** The drops a {@link Spray} picks from (each its own sprites, {@code particles/<name>.json}). */
	public static final SimpleParticleType DROP = simple("blood_drop"), SPAT_1 = simple("blood_spat_1"), SPAT_2 = simple("blood_spat_2"),
		SPURT = simple("blood_spurt"), SWIPE_1 = simple("blood_swipe_1"), SWIPE_2 = simple("blood_swipe_2");

	private BloodParticles() {
	}

	/** Registers the particles (by loading this class). */
	public static void init() {
		BloodTrail.init();
		// (0.19.52) bodies dragged over the ground smear it
		BloodSmears.init();
	}

	/** (0.15.109) A {@link Fleck} of this colour (RGB) and size, where a {@code DustParticleOptions} would go. */
	public static Fleck fleck(int color, float size) {
		return new Fleck(color, size);
	}

	private static SimpleParticleType simple(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id(name), FabricParticleTypes.simple(true));
	}
}
