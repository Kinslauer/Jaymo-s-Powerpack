package com.jaymoskingdoms.spirits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.7.237) A mana spirit's bond with its keeper. Andy: "the ones with personalities can kick in on their own to assist you
 * ... all can be upgraded and leveled". Saved, kept through death and synced to everyone (the viewers draw the arms).
 * {@code kind} is {@link Spirits#GRIMSBY}...{@link Spirits#QUIET}; {@code level} 1-{@link Spirits#MAX_LEVEL} with its
 * {@code xp}; {@code bond} 0-{@link Spirits#MAX_BOND}; {@code out} whether it is conjured; {@code move} the selected move
 * (an index into the player's move list); {@code show} is game time times 64 plus a {@link Poses} id, the pose the arms hold
 * now; {@code arms} is 1, or 2 from bond {@link Spirits#TWO_ARMS_BOND}; {@code scale} is the size of the arms times 100
 * (100 is the spirit's own size; the Quiet One is conjured at a new size each time: 50 to 300, see {@link Spirit#size(SpiritState)});
 * {@code toggles} is a bitmask of the toggle moves that are on ({@link #STEADY}, {@link #ECHO}, {@link #ARMOUR}), so the HUD can say so.
 */
public record SpiritState(int kind, int level, int xp, int bond, boolean out, int move, long show, int arms, int scale, int toggles) {
	public static final Codec<SpiritState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("kind", 0).forGetter(SpiritState::kind),
			Codec.INT.optionalFieldOf("level", 1).forGetter(SpiritState::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(SpiritState::xp),
			Codec.INT.optionalFieldOf("bond", 0).forGetter(SpiritState::bond),
			Codec.BOOL.optionalFieldOf("out", false).forGetter(SpiritState::out),
			Codec.INT.optionalFieldOf("move", 0).forGetter(SpiritState::move),
			Codec.LONG.optionalFieldOf("show", 0L).forGetter(SpiritState::show),
			Codec.INT.optionalFieldOf("arms", 1).forGetter(SpiritState::arms),
			Codec.INT.optionalFieldOf("scale", 100).forGetter(SpiritState::scale),
			Codec.INT.optionalFieldOf("toggles", 0).forGetter(SpiritState::toggles))
		.apply(i, SpiritState::new));

	public static final StreamCodec<ByteBuf, SpiritState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SpiritState::kind,
		ByteBufCodecs.VAR_INT, SpiritState::level,
		ByteBufCodecs.VAR_INT, SpiritState::xp,
		ByteBufCodecs.VAR_INT, SpiritState::bond,
		ByteBufCodecs.BOOL, SpiritState::out,
		ByteBufCodecs.VAR_INT, SpiritState::move,
		ByteBufCodecs.VAR_LONG, SpiritState::show,
		ByteBufCodecs.VAR_INT, SpiritState::arms,
		ByteBufCodecs.VAR_INT, SpiritState::scale,
		ByteBufCodecs.VAR_INT, SpiritState::toggles,
		SpiritState::new);

	/** {@link #toggles} bits: Ambrose's steady hands, the Quiet One's echo strike, the shared spirit armour. */
	public static final int STEADY = 1, ECHO = 2, ARMOUR = 4;

	/** A fresh spirit of {@code kind}: level 1, {@code bond} to start. */
	public static SpiritState fresh(int kind, int bond) {
		return new SpiritState(kind, 1, 0, bond, false, 0, 0L, bond >= Spirits.TWO_ARMS_BOND ? 2 : 1, 100, 0);
	}

	public SpiritState withLevel(int value, int xpLeft) {
		return new SpiritState(kind, value, xpLeft, bond, out, move, show, arms, scale, toggles);
	}

	public SpiritState withXp(int value) {
		return new SpiritState(kind, level, value, bond, out, move, show, arms, scale, toggles);
	}

	/** Bond set (clamped), the arms following it. */
	public SpiritState withBond(int value) {
		int b = Math.clamp(value, 0, Spirits.MAX_BOND);
		return new SpiritState(kind, level, xp, b, out, move, show, b >= Spirits.TWO_ARMS_BOND ? 2 : 1, scale, toggles);
	}

	public SpiritState withOut(boolean value) {
		return new SpiritState(kind, level, xp, bond, value, move, show, arms, scale, toggles);
	}

	public SpiritState withMove(int value) {
		return new SpiritState(kind, level, xp, bond, out, value, show, arms, scale, toggles);
	}

	/** The arms hold {@code pose} from game time {@code now}. */
	public SpiritState withShow(long now, int pose) {
		return new SpiritState(kind, level, xp, bond, out, move, now * 64L + pose, arms, scale, toggles);
	}

	/** The size of the arms set to {@code value} (times 100). */
	public SpiritState withScale(int value) {
		return new SpiritState(kind, level, xp, bond, out, move, show, arms, value, toggles);
	}

	/** Toggle {@code bit} ({@link #STEADY}, {@link #ECHO}, {@link #ARMOUR}) set {@code on}, or every toggle off with {@code bit} 0. */
	public SpiritState withToggle(int bit, boolean on) {
		int t = bit == 0 ? 0 : on ? toggles | bit : toggles & ~bit;
		return new SpiritState(kind, level, xp, bond, out, move, show, arms, scale, t);
	}

	/** Whether toggle {@code bit} is on. */
	public boolean toggled(int bit) {
		return (toggles & bit) != 0;
	}

	/** The pose {@link #show} holds. */
	public int pose() {
		return (int)(show & 63L);
	}

	/** The game time {@link #show} was stamped. */
	public long shownAt() {
		return show >> 6;
	}
}
