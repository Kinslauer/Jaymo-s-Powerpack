package com.jaymospowers.power.wind;

import com.jaymospowers.api.PowerKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The three channelled abilities (hold to channel, release to cast). Shared by both sides. */
public enum WindAbility {
	/** G: Speed + Jump Boost, and a forward dash on release. */
	TAILWIND(PowerKey.ABILITY_2, "tailwind", 400, 0x9FE8FF, Items.FEATHER, 100),
	/** V: Resistance + knockback resistance, deflects projectiles, pushes hostile mobs back on release. */
	GALE_BARRIER(PowerKey.ABILITY_3, "gale_barrier", 600, 0xD8F6EE, Items.WIND_CHARGE, 40),
	/** B: Slow Falling + Regeneration for you and nearby players. */
	ZEPHYR(PowerKey.ABILITY_4, "zephyr", 900, 0xB8F5A8, Items.GHAST_TEAR, 40);

	/** Default ticks of channelling for a full-strength cast (2 s). Releasing earlier gives a weaker, shorter buff. */
	public static final int MAX_CHANNEL = 40;

	private static final WindAbility[] VALUES = values();

	public final PowerKey key;
	public final String id;
	/** Cooldown after a cast, in ticks. */
	public final int cooldown;
	/** 0xRRGGBB, for the HUD. */
	public final int color;
	public final Item icon;
	/** Ticks of channelling for this ability's full strength (Tailwind charges longer: 5 s). */
	public final int maxChannel;

	WindAbility(PowerKey key, String id, int cooldown, int color, Item icon, int maxChannel) {
		this.key = key;
		this.id = id;
		this.cooldown = cooldown;
		this.color = color;
		this.icon = icon;
		this.maxChannel = maxChannel;
	}

	public static WindAbility byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : null;
	}

	public static WindAbility byKey(PowerKey key) {
		for (WindAbility ability : VALUES) {
			if (ability.key == key) {
				return ability;
			}
		}
		return null;
	}

	public String langKey() {
		return "ability.jaymospowers.wind." + this.id;
	}
}
