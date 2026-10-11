package com.jaymospowers.power.gravity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * When each Gravity cooldown ends (game time), plus how long it was (for the HUD's shading). Synced to the owner only.
 * Index: {@link GravityPower#LIFT}, {@link GravityPower#WELL}, {@link GravityPower#WEIGHTLESS}, {@link GravityPower#CRUSH},
 * {@link GravityPower#SINGULARITY}.
 */
public record GravityCooldowns(long lift, long well, long weightless, long crush, long singularity, int crushLength, int singularityLength) {
	public static final GravityCooldowns NONE = new GravityCooldowns(0L, 0L, 0L, 0L, 0L, 1, 1);

	public static final StreamCodec<ByteBuf, GravityCooldowns> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, GravityCooldowns::lift,
		ByteBufCodecs.VAR_LONG, GravityCooldowns::well,
		ByteBufCodecs.VAR_LONG, GravityCooldowns::weightless,
		ByteBufCodecs.VAR_LONG, GravityCooldowns::crush,
		ByteBufCodecs.VAR_LONG, GravityCooldowns::singularity,
		ByteBufCodecs.VAR_INT, GravityCooldowns::crushLength,
		ByteBufCodecs.VAR_INT, GravityCooldowns::singularityLength,
		GravityCooldowns::new);

	public long get(int ability) {
		return switch (ability) {
			case GravityPower.LIFT -> lift;
			case GravityPower.WELL -> well;
			case GravityPower.WEIGHTLESS -> weightless;
			case GravityPower.CRUSH -> crush;
			default -> singularity;
		};
	}

	public GravityCooldowns with(int ability, long until) {
		return new GravityCooldowns(
			ability == GravityPower.LIFT ? until : lift,
			ability == GravityPower.WELL ? until : well,
			ability == GravityPower.WEIGHTLESS ? until : weightless,
			ability == GravityPower.CRUSH ? until : crush,
			ability == GravityPower.SINGULARITY ? until : singularity,
			crushLength, singularityLength);
	}

	public GravityCooldowns withCrush(long until, int length) {
		return new GravityCooldowns(lift, well, weightless, until, singularity, Math.max(1, length), singularityLength);
	}

	/** The C's cooldown, when it's as long as the Meteor's charge made it. */
	public GravityCooldowns withSingularity(long until, int length) {
		return new GravityCooldowns(lift, well, weightless, crush, until, crushLength, Math.max(1, length));
	}
}
