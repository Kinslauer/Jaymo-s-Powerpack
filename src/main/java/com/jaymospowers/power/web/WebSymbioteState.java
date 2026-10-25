package com.jaymospowers.power.web;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A web slinger's bond with a symbiote. Saved, kept through death, and synced to everyone (others see the suit).
 * {@code flags} holds the toggles ({@link #SUIT}, {@link #ARMOR}, {@link #CRAWL}, {@link #TENDRILS}); {@code hints} records which
 * one-time whispers have already been heard. {@code bond} (0-{@link WebSymbiote#BOND_MAX}) is how far it trusts you: it
 * starts somewhere from nothing to halfway, grows with time and meals, and opens up its abilities as it climbs.
 * {@code strain} is a {@link WebSymbioteStrain} id (0 black, 1 white, 2 red); old saves load as black.
 */
public record WebSymbioteState(boolean bonded, int satiety, int stress, int rejections, int flags, int hints, int bond, int strain) {
	/** The suit is showing (off: the powers still work, but you look like yourself). */
	public static final int SUIT = 1;
	/** Living Armor is on. */
	public static final int ARMOR = 2;
	/** Wall Crawl is on. */
	public static final int CRAWL = 4;
	/** The big body: the symbiote swollen over you (near death, or when it takes over). Not a toggle. */
	public static final int BULK = 8;
	// 16 is dead: it was TENDRILS_IN (0.15.152-0.16.5, "the tendrils drawn in"), replaced by the positive TENDRILS below.
	// Old saves may still carry the bit; don't reuse it.
	/**
	 * (0.16.6) The tendrils are out: over the plain suit, bare skin, the thin armour or the big suit. Their own toggle
	 * (the menu's Tendrils slot, {@code WebSymbiote.TENDRILS_TOGGLE}) from Living Armor's unlock (Andy: "the tendrils
	 * button should work as a toggle for the tendrils once you unlock them at 60 bond"). Set whenever Living Armor or
	 * the big suit forms ("the big suit still forms with the tendrils for ... initial activation") and whenever it takes
	 * the wheel (a takeover, the near-death roar); the armour coming off leaves it alone.
	 */
	public static final int TENDRILS = 32;

	public static final WebSymbioteState NONE = new WebSymbioteState(false, 0, 0, 0, 0, 0, 0, 0);

	public static final Codec<WebSymbioteState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("bonded", false).forGetter(WebSymbioteState::bonded),
			Codec.INT.optionalFieldOf("satiety", 0).forGetter(WebSymbioteState::satiety),
			Codec.INT.optionalFieldOf("stress", 0).forGetter(WebSymbioteState::stress),
			Codec.INT.optionalFieldOf("rejections", 0).forGetter(WebSymbioteState::rejections),
			Codec.INT.optionalFieldOf("flags", 0).forGetter(WebSymbioteState::flags),
			Codec.INT.optionalFieldOf("hints", 0).forGetter(WebSymbioteState::hints),
			// bonds made before the bond meter existed start halfway
			Codec.INT.optionalFieldOf("bond", WebSymbiote.BOND_MAX / 2).forGetter(WebSymbioteState::bond),
			// bonds made before strains existed are black
			Codec.INT.optionalFieldOf("strain", 0).forGetter(WebSymbioteState::strain))
		.apply(i, WebSymbioteState::new));

	public static final StreamCodec<ByteBuf, WebSymbioteState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, WebSymbioteState::bonded,
		ByteBufCodecs.VAR_INT, WebSymbioteState::satiety,
		ByteBufCodecs.VAR_INT, WebSymbioteState::stress,
		ByteBufCodecs.VAR_INT, WebSymbioteState::rejections,
		ByteBufCodecs.VAR_INT, WebSymbioteState::flags,
		ByteBufCodecs.VAR_INT, WebSymbioteState::hints,
		ByteBufCodecs.VAR_INT, WebSymbioteState::bond,
		ByteBufCodecs.VAR_INT, WebSymbioteState::strain,
		WebSymbioteState::new);

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public WebSymbioteState withFlag(int flag, boolean on) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, on ? flags | flag : flags & ~flag, hints, bond, strain);
	}

	public WebSymbioteState withSatiety(int value) {
		return new WebSymbioteState(bonded, value, stress, rejections, flags, hints, bond, strain);
	}

	public WebSymbioteState withStress(int value) {
		return new WebSymbioteState(bonded, satiety, value, rejections, flags, hints, bond, strain);
	}

	public WebSymbioteState withRejections(int value) {
		return new WebSymbioteState(bonded, satiety, stress, value, flags, hints, bond, strain);
	}

	public WebSymbioteState withBond(int value) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, flags, hints, value, strain);
	}

	public WebSymbioteState withHint(int hint) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, flags, hints | hint, bond, strain);
	}

	public WebSymbioteState withStrain(int value) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, flags, hints, bond, value);
	}

	/** This bond's strain. */
	public WebSymbioteStrain kind() {
		return WebSymbioteStrain.of(strain);
	}

	/** The game time each symbiote ability is ready again (index = ability). Synced to the owner only, for the menu. */
	public record Cooldowns(List<Long> ends) {
		public static final Cooldowns NONE = new Cooldowns(List.of());
		public static final StreamCodec<ByteBuf, Cooldowns> STREAM_CODEC = ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list())
			.map(Cooldowns::new, Cooldowns::ends);

		public long end(int ability) {
			return ability < ends.size() ? ends.get(ability) : 0L;
		}

		public Cooldowns with(int ability, long end, int count) {
			List<Long> next = new ArrayList<>(ends);
			while (next.size() < count) {
				next.add(0L);
			}
			next.set(ability, end);
			return new Cooldowns(List.copyOf(next));
		}
	}
}
