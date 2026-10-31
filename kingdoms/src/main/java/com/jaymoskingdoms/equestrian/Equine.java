package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * (0.5.34) What a horse is: its breed, its coat (an index into {@link Breed#coats}), and its five hidden stats, each
 * 0-1: speed, stamina, jump, courage and temper (high temper: harder to break in). Saved on the horse and seen by
 * every client (the coat and size are drawn from it; the stats show to whoever has tamed it).
 */
public record Equine(Breed breed, int coat, float speed, float stamina, float jump, float courage, float temper) {
	public static final Codec<Equine> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(Breed::byId, Breed::id).fieldOf("breed").forGetter(Equine::breed),
			Codec.INT.optionalFieldOf("coat", 0).forGetter(Equine::coat),
			Codec.FLOAT.optionalFieldOf("speed", 0.5F).forGetter(Equine::speed),
			Codec.FLOAT.optionalFieldOf("stamina", 0.5F).forGetter(Equine::stamina),
			Codec.FLOAT.optionalFieldOf("jump", 0.5F).forGetter(Equine::jump),
			Codec.FLOAT.optionalFieldOf("courage", 0.5F).forGetter(Equine::courage),
			Codec.FLOAT.optionalFieldOf("temper", 0.5F).forGetter(Equine::temper))
		.apply(i, Equine::new));

	public static final StreamCodec<ByteBuf, Equine> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(i -> Breed.values()[Math.floorMod(i, Breed.values().length)], Breed::ordinal), Equine::breed,
		ByteBufCodecs.VAR_INT, Equine::coat,
		ByteBufCodecs.FLOAT, Equine::speed,
		ByteBufCodecs.FLOAT, Equine::stamina,
		ByteBufCodecs.FLOAT, Equine::jump,
		ByteBufCodecs.FLOAT, Equine::courage,
		ByteBufCodecs.FLOAT, Equine::temper,
		Equine::new);

	/**
	 * (0.7.322) The moonlit horse: rarer than any breed's rare coat, a horse of any breed (but the elephants and the
	 * mythic mounts) is now and then born pale and silver-maned, drawn in a shape of its own (jsellier23's Mystical Deer
	 * made into a horse, CC BY 4.0). Its coat index, past every breed's coats; foals take it from a parent as any coat.
	 */
	public static final int MOONLIT = 100;
	public static final float MOONLIT_CHANCE = 0.01F;

	/** A new one of {@code breed}: stats rolled as three dice (most horses middling, a few fine), a coat (now and then the rare one). */
	public static Equine roll(Breed breed, RandomSource random) {
		return new Equine(breed, coat(breed, random), stat(random), stat(random), stat(random), stat(random), stat(random));
	}

	static float stat(RandomSource random) {
		return (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3.0F;
	}

	static int coat(Breed breed, RandomSource random) {
		if (random.nextFloat() < MOONLIT_CHANCE && java.util.Arrays.asList(Breed.HORSES).contains(breed)) {
			return MOONLIT;
		}
		int rare = breed.coats.length - 1;
		return random.nextFloat() < Equestrian.RARE_COAT_CHANCE ? rare : random.nextInt(rare);
	}

	/** A foal of {@code a} and {@code b}: one parent's breed and (mostly) one parent's coat, each stat between theirs plus a little luck. */
	public static Equine foal(Equine a, Equine b, RandomSource random) {
		Equine from = a.breed == b.breed || random.nextBoolean() ? a : b;
		Equine other = from == a ? b : a;
		Breed breed = from.breed;
		float pick = random.nextFloat();
		int coat = pick < 0.45F ? from.coat : pick < 0.9F && other.breed == breed ? other.coat : coat(breed, random);
		return new Equine(breed, coat, mix(a.speed, b.speed, random), mix(a.stamina, b.stamina, random), mix(a.jump, b.jump, random),
			mix(a.courage, b.courage, random), mix(a.temper, b.temper, random));
	}

	/** Between the parents' values, widened by {@link Equestrian#FOAL_LUCK}; folded back in at 0 and 1. */
	static float mix(float a, float b, RandomSource random) {
		double range = Math.abs(a - b) + Equestrian.FOAL_LUCK * 2.0;
		double quality = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0 - 0.5;
		double v = (a + b) / 2.0 + range * quality;
		if (v > 1.0) {
			v = 2.0 - v;
		} else if (v < 0.0) {
			v = -v;
		}
		return (float)Math.max(0.0, Math.min(1.0, v));
	}

	/** Its courage in the saddle (0-1): the breed's nature blended with its own. */
	public double bravery() {
		return this.breed.courage * 0.6 + this.courage * 0.4;
	}

	public double topSpeed() {
		return this.breed.topSpeed(this.speed);
	}

	public double jumpStrength() {
		return this.breed.jump(this.jump);
	}

	/** How much stamina it holds when full. */
	public double capacity() {
		return Equestrian.STAMINA * this.breed.capacity * (0.75 + 0.5 * this.stamina);
	}

	public double maxHealth() {
		double hardiness = (this.stamina + this.courage) / 2.0;
		return Math.round(this.breed.healthMin + (this.breed.healthMax - this.breed.healthMin) * hardiness);
	}

	/** Vanilla's taming threshold: a hot-tempered horse takes longer to break in. */
	public boolean moonlit() {
		return this.coat == MOONLIT;
	}

	public int maxTemper() {
		return Math.round(40 + 100 * this.temper);
	}

	/** (0.7.445) Its coat is its breed's rare one, or moonlit. */
	public boolean rareCoat() {
		return this.coat == MOONLIT || this.breed.coats.length > 1 && Math.floorMod(this.coat, this.breed.coats.length) == this.breed.coats.length - 1;
	}

	public String coatName() {
		return this.coat == MOONLIT ? "moonlit" : this.breed.coat(this.coat);
	}
}
