package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.7.305) A horse among its herd ({@link Herding}): mare or stallion ({@link #MARE}, {@link #STALLION}; 0 until it's
 * first loaded), the order its owner last gave it from the command wheel ({@link Order}), its home (set from the wheel),
 * and the spot it stays at or wanders round. Saved, and synced so the wheel, the roster and the stats show it.
 */
public record Herd(int sex, int order, Optional<GlobalPos> home, Optional<BlockPos> anchor) {
	public static final int UNKNOWN = 0, MARE = 1, STALLION = 2;

	/** What it's been told to do. Saved by ordinal: only add at the end. */
	public enum Order {
		/** Nothing: it does as horses do. */
		NONE,
		/** Keeps close to its owner. */
		FOLLOW,
		/** Stands where it was told to, and won't stray. */
		STAY,
		/** Grazes about the spot it was told, never far from it. */
		WANDER,
		/** On its way home (then it wanders there). */
		HOME;

		public static Order byId(int id) {
			Order[] all = values();
			return all[Math.max(0, Math.min(all.length - 1, id))];
		}

		public String key() {
			return "order.jaymoskingdoms." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final Codec<Herd> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("sex", UNKNOWN).forGetter(Herd::sex),
			Codec.INT.optionalFieldOf("order", 0).forGetter(Herd::order),
			GlobalPos.CODEC.optionalFieldOf("home").forGetter(Herd::home),
			BlockPos.CODEC.optionalFieldOf("anchor").forGetter(Herd::anchor))
		.apply(i, Herd::new));

	public static final StreamCodec<ByteBuf, Herd> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Herd::sex,
		ByteBufCodecs.VAR_INT, Herd::order,
		ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Herd::home,
		ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Herd::anchor,
		Herd::new);

	public static final Herd NONE = new Herd(UNKNOWN, 0, Optional.empty(), Optional.empty());

	public Order orderOf() {
		return Order.byId(this.order);
	}

	public boolean mare() {
		return this.sex == MARE;
	}

	public Herd withSex(int sex) {
		return new Herd(sex, this.order, this.home, this.anchor);
	}

	public Herd withOrder(Order order, Optional<BlockPos> anchor) {
		return new Herd(this.sex, order.ordinal(), this.home, anchor);
	}

	public Herd withHome(Optional<GlobalPos> home) {
		return new Herd(this.sex, this.order, home, this.anchor);
	}

	/** The key for its sex's name ("mare", "stallion"; a foal is a filly or a colt). */
	public String sexKey(boolean foal) {
		return "sex.jaymoskingdoms." + (foal ? this.sex == MARE ? "filly" : "colt" : this.sex == MARE ? "mare" : "stallion");
	}
}
