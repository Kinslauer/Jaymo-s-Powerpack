package com.jaymoskitchen.mill.power;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * What a windmill's sail is sewn from (GT New Horizons picks, patch 8). Each catches its own share of the wind (pull),
 * lasts its own while turning (life, in seconds of turning at speed 1: an in-game day is 1200), and has its own chance to
 * tear in a thunderstorm, a check (once a second) under the worst of it. Hemp canvas is the miller's sail: it pulls
 * hardest and lasts well. Wool is easy to come by but stretches and wears; leather is heavy and pulls less but outlasts
 * them all and seldom tears; paper is the cheap stopgap that the first storm shreds.
 */
public enum SailKind {
	CLOTH(1.0F, 6000, 1.0F / 1800.0F),
	WOOL(0.85F, 3600, 1.0F / 1200.0F),
	LEATHER(0.7F, 9600, 1.0F / 3600.0F),
	PAPER(0.6F, 1200, 1.0F / 300.0F);

	/** Share of the wind it catches (1 = a hemp sail). */
	public final float pull;
	/** Seconds of turning at speed 1 before it's worn through; also the sail item's durability. */
	public final int life;
	/** Chance a check, under a full thunderstorm, that it tears. */
	public final float tear;
	/** What patches it, on the windmill or at an anvil: {@code #jaymoskitchen:sail_patch/<kind>}. */
	public final TagKey<Item> patch;
	/** The sail's item, set at registration. */
	@Nullable Item item;

	SailKind(float pull, int life, float tear) {
		this.pull = pull;
		this.life = life;
		this.tear = tear;
		this.patch = TagKey.create(Registries.ITEM, JaymosKitchen.id("sail_patch/" + name().toLowerCase(Locale.ROOT)));
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Item item() {
		return item;
	}

	public static @Nullable SailKind byId(String id) {
		for (SailKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
