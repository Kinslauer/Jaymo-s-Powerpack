package com.jaymospowers.alchemy.oil;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Every oil there is (Hunter's Road, patch 7, "D4" and "T-C1"): five <b>blade oils</b> by the kind of monster they bite
 * (the dead, beasts, brutes, people and the cursed, the garlic-and-silver oil of the hunter's kit: {@code jaymoskingdoms:kind/<kind>} tags), seven <b>effect oils</b> a blade carries into
 * the wound, a <b>mending oil</b> for tools, and two <b>splashes</b> for friends (fire resistance and healing). The same
 * list names what an oil pot holds ({@link OilPotBlock}), what a sash carries ({@link OilSashItem}) and what a thrown oil is.
 */
public enum OilKind implements StringRepresentable {
	DEAD("dead", 0xB8C4D8, Group.BLADE, "dead"), BEASTS("beasts", 0xC8883A, Group.BLADE, "beasts"),
	BRUTES("brutes", 0x7A8A5A, Group.BLADE, "brutes"), PEOPLE("people", 0xC85A5A, Group.BLADE, "people"),
	CURSED("cursed", 0xCFD8E0, Group.BLADE, "cursed"),
	POISON("poison", 0x6FA03A, Group.EFFECT, null), HAZE("haze", 0x9AA0A8, Group.EFFECT, null), MARK("mark", 0xF2D84A, Group.EFFECT, null),
	TOXIN("toxin", 0x7FB020, Group.EFFECT, null), SMOKE("smoke", 0x585860, Group.EFFECT, null), ACID("acid", 0xB8D030, Group.EFFECT, null),
	FIRE("fire", 0xF08030, Group.EFFECT, null),
	MENDING("mending", 0x58C8B0, Group.MENDING, null),
	FIRE_WARD("fire_ward", 0xE86A30, Group.SPLASH, null), HEALING("healing", 0xE85A78, Group.SPLASH, null);

	public enum Group {
		/** A blade oil: more harm to one kind of monster. */
		BLADE,
		/** An effect oil: a blade carries it into the wound. */
		EFFECT,
		/** Mending oil: a tool carries it and mends as it is used. */
		MENDING,
		/** A splash for friends: only ever thrown. */
		SPLASH
	}

	public final String id;
	public final int color;
	public final Group group;
	/** The monster kind (a {@code jaymoskingdoms:kind/<this>} tag) a blade oil bites, else null. */
	public final @Nullable String monsterKind;
	/** The flask item; set when it is registered. */
	public Item flask;

	OilKind(String id, int color, Group group, @Nullable String monsterKind) {
		this.id = id;
		this.color = color;
		this.group = group;
		this.monsterKind = monsterKind;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	/** The flask's item id. */
	public String itemId() {
		return "oil_" + this.id;
	}

	/** Whether a blade can carry it (a blade oil or an effect oil). */
	public boolean forBlades() {
		return this.group == Group.BLADE || this.group == Group.EFFECT;
	}

	public static @Nullable OilKind byId(@Nullable String id) {
		if (id != null) {
			for (OilKind k : values()) {
				if (k.id.equals(id.toLowerCase(Locale.ROOT))) {
					return k;
				}
			}
		}
		return null;
	}

	/** The oil a flask stack holds, or null if it is not a flask. */
	public static @Nullable OilKind of(ItemStack stack) {
		for (OilKind k : values()) {
			if (k.flask != null && stack.is(k.flask)) {
				return k;
			}
		}
		return null;
	}
}
