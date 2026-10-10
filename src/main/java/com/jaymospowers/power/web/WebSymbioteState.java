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
 * {@code flags} holds the toggles ({@link #SUIT}, {@link #ARMOR}, {@link #CRAWL}); {@code hints} records which
 * one-time whispers have already been heard.
 */
public record WebSymbioteState(boolean bonded, int satiety, int stress, int rejections, int flags, int hints) {
	/** The suit is showing (off: the powers still work, but you look like yourself). */
	public static final int SUIT = 1;
	/** Living Armor is on. */
	public static final int ARMOR = 2;
	/** Wall Crawl is on. */
	public static final int CRAWL = 4;
	/** The big body: the symbiote swollen over you (near death, or when it takes over). Not a toggle. */
	public static final int BULK = 8;

	public static final WebSymbioteState NONE = new WebSymbioteState(false, 0, 0, 0, 0, 0);

	public static final Codec<WebSymbioteState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("bonded", false).forGetter(WebSymbioteState::bonded),
			Codec.INT.optionalFieldOf("satiety", 0).forGetter(WebSymbioteState::satiety),
			Codec.INT.optionalFieldOf("stress", 0).forGetter(WebSymbioteState::stress),
			Codec.INT.optionalFieldOf("rejections", 0).forGetter(WebSymbioteState::rejections),
			Codec.INT.optionalFieldOf("flags", 0).forGetter(WebSymbioteState::flags),
			Codec.INT.optionalFieldOf("hints", 0).forGetter(WebSymbioteState::hints))
		.apply(i, WebSymbioteState::new));

	public static final StreamCodec<ByteBuf, WebSymbioteState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, WebSymbioteState::bonded,
		ByteBufCodecs.VAR_INT, WebSymbioteState::satiety,
		ByteBufCodecs.VAR_INT, WebSymbioteState::stress,
		ByteBufCodecs.VAR_INT, WebSymbioteState::rejections,
		ByteBufCodecs.VAR_INT, WebSymbioteState::flags,
		ByteBufCodecs.VAR_INT, WebSymbioteState::hints,
		WebSymbioteState::new);

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public WebSymbioteState withFlag(int flag, boolean on) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, on ? flags | flag : flags & ~flag, hints);
	}

	public WebSymbioteState withSatiety(int value) {
		return new WebSymbioteState(bonded, value, stress, rejections, flags, hints);
	}

	public WebSymbioteState withStress(int value) {
		return new WebSymbioteState(bonded, satiety, value, rejections, flags, hints);
	}

	public WebSymbioteState withRejections(int value) {
		return new WebSymbioteState(bonded, satiety, stress, value, flags, hints);
	}

	public WebSymbioteState withHint(int hint) {
		return new WebSymbioteState(bonded, satiety, stress, rejections, flags, hints | hint);
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
