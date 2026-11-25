package com.jaymoskitchen.forage;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.OptionalItems;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What a cultivation log can be inoculated with (NONE is a fresh log). The glowcap, bouncecap and inkcap are Jaymo's
 * Kingdoms' small mushrooms: found by id and by the item tags {@code jaymoskitchen:glowcaps} and so on, so the log works
 * without them (those three are then simply never found).
 */
public enum CultivationFungus implements StringRepresentable {
	NONE("none", () -> Items.AIR, null),
	GLOWCAP("glowcap", () -> OptionalItems.item(OptionalItems.KINGDOMS, "glowcap"), tag("glowcaps")),
	BOUNCECAP("bouncecap", () -> OptionalItems.item(OptionalItems.KINGDOMS, "bouncecap"), tag("bouncecaps")),
	INKCAP("inkcap", () -> OptionalItems.item(OptionalItems.KINGDOMS, "inkcap"), tag("inkcaps")),
	MOREL("morel", () -> Forage.MOREL, null),
	CHANTERELLE("chanterelle", () -> Forage.CHANTERELLE, null),
	FROSTCAP("frostcap", () -> Forage.FROSTCAP, null),
	EMBERSTOOL("emberstool", () -> Forage.EMBERSTOOL, null);

	private final String name;
	private final Supplier<Item> item;
	private final @Nullable TagKey<Item> tag;

	CultivationFungus(String name, Supplier<Item> item, @Nullable TagKey<Item> tag) {
		this.name = name;
		this.item = item;
		this.tag = tag;
	}

	private static TagKey<Item> tag(String id) {
		return TagKey.create(Registries.ITEM, JaymosKitchen.id(id));
	}

	/** The mushroom a ripe log gives (air if its mod isn't there). */
	public Item item() {
		return item.get();
	}

	/** The fungus this stack inoculates a log with, or NONE. */
	public static CultivationFungus of(ItemStack stack) {
		if (stack.isEmpty()) {
			return NONE;
		}
		for (CultivationFungus fungus : values()) {
			if (fungus != NONE && (stack.is(fungus.item()) || (fungus.tag != null && stack.is(fungus.tag)))) {
				return fungus;
			}
		}
		return NONE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
