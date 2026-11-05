package com.jaymoskingdoms;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) The eight patrons (docs/Patrons-Plan.md). Andy picked the Runic Gods idea; these are our own. The seven are the
 * old faiths; The Truth is the human one. Stored by key, never ordinal, so a ninth can be appended later and move nothing.
 */
public enum Patron {
	HEARTHKEEPER("hearthkeeper", 0xFFE0A050, true),
	ANVIL_FATHER("anvil_father", 0xFFB0B4BC, true),
	DEEP_MOTHER("deep_mother", 0xFF3A8FB7, true),
	GREEN_HUNTSMAN("green_huntsman", 0xFF5E9E3A, true),
	LANTERN_WARDEN("lantern_warden", 0xFF9AA8FF, true),
	CROWNED_SUN("crowned_sun", 0xFFFFC21A, true),
	ASH_WHISPERER("ash_whisperer", 0xFF8A5A4A, true),
	// (0.7.509) Andy: "an 8th patron known as the truth ... usually the faith among humans"
	TRUTH("truth", 0xFFF2F2F2, false);

	public final String key;
	public final int colour;
	public final boolean seven;
	/** jaymoskingdoms:offerings/&lt;key&gt; */
	public final TagKey<Item> offerings;
	/** A named rival (the Sun and the Ash Whisperer, the Mother and the Anvil-Father). The Truth's are all the seven ({@link #rivals}). */
	public @Nullable Patron rival;

	static {
		CROWNED_SUN.rival = ASH_WHISPERER;
		ASH_WHISPERER.rival = CROWNED_SUN;
		DEEP_MOTHER.rival = ANVIL_FATHER;
		ANVIL_FATHER.rival = DEEP_MOTHER;
	}

	Patron(String key, int colour, boolean seven) {
		this.key = key;
		this.colour = colour;
		this.seven = seven;
		this.offerings = TagKey.create(Registries.ITEM, JaymosKingdoms.id("offerings/" + key));
	}

	public static @Nullable Patron byKey(@Nullable String key) {
		if (key != null) {
			for (Patron p : values()) {
				if (p.key.equals(key)) {
					return p;
				}
			}
		}
		return null;
	}

	public Component named() {
		return Component.translatable("patron.jaymoskingdoms." + this.key);
	}

	/** A named rival either way, or The Truth against any of the seven. */
	public boolean rivals(Patron other) {
		return other != this && (this.rival == other || other.rival == this || this == TRUTH && other.seven || other == TRUTH && this.seven);
	}
}
