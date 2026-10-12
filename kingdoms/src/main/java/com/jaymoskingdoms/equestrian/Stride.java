package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.5.33) A horse under way: its rider's gait, whether he's sprinting, its stamina left, whether it's blown (run
 * dry: no faster than a trot until it gets its wind back, {@link Equestrian#WIND_BACK}), and when it was last seen to
 * (game time, so stamina refills while it stands unridden). Saved, and synced so its rider's HUD shows it.
 */
public record Stride(int gait, boolean sprint, float stamina, boolean blown, long at) {
	public static final Codec<Stride> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("gait", Gait.TROT.ordinal()).forGetter(Stride::gait),
			Codec.FLOAT.optionalFieldOf("stamina", -1.0F).forGetter(Stride::stamina),
			Codec.BOOL.optionalFieldOf("blown", false).forGetter(Stride::blown),
			Codec.LONG.optionalFieldOf("at", 0L).forGetter(Stride::at))
		.apply(i, (gait, stamina, blown, at) -> new Stride(gait, false, stamina, blown, at)));

	public static final StreamCodec<ByteBuf, Stride> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Stride::gait,
		ByteBufCodecs.BOOL, Stride::sprint,
		ByteBufCodecs.FLOAT, Stride::stamina,
		ByteBufCodecs.BOOL, Stride::blown,
		ByteBufCodecs.VAR_LONG, Stride::at,
		Stride::new);

	public static final Stride FRESH = new Stride(Gait.TROT.ordinal(), false, -1.0F, false, 0L);

	public Gait gaitOf() {
		return Gait.byId(this.gait);
	}
}
