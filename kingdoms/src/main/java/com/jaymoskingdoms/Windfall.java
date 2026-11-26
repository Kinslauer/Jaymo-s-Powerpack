package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.ItemEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Andy (2026-11-26, a mangrove swamp burning, at a few frames a second): "maybe if dropped items disappeared faster there
 * or something". When a wood burns, its leaves decay all at once and drop their sticks, saplings and apples by the
 * hundred, and each lies there five minutes. What a tree sheds on its own (sticks, saplings and propagules, apples,
 * leaves) and nobody threw goes after {@link #LIFE} instead, anywhere. Anything a player threw down keeps the usual five
 * minutes, and so does every other kind of item (tools, food, ores, a mob's loot): only these few things a tree sheds go
 * sooner.
 */
public final class Windfall {
	/** How long (ticks) a stick, sapling, apple or leaf nobody threw lies on the ground: a minute. */
	static final int LIFE = 1200;
	/** The game's own: an item goes at this age, and one kept for ever has this age. */
	static final int ITEM_LIFETIME = 6000, FOREVER = -32768;

	private Windfall() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && windfall(item.getItem())) {
				shorten(item);
			}
		});
	}

	/** Sticks, saplings (a mangrove's propagules too), apples and leaves: what a tree drops by itself. */
	static boolean windfall(ItemStack stack) {
		return stack.is(Items.STICK) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE) || stack.is(ItemTags.LEAVES);
	}

	/**
	 * Ages {@code item} so it has at most {@link #LIFE} left: never one a player threw, one kept for ever, or one given
	 * longer on purpose (a negative age); one already older is left as it is, so a chunk loading again changes nothing.
	 */
	private static void shorten(ItemEntity item) {
		int age = item.getAge();
		if (((ItemEntityAccessor) item).jaymoskingdoms$thrower() != null || age == FOREVER || age < 0 || age >= ITEM_LIFETIME - LIFE) {
			return;
		}
		((ItemEntityAccessor) item).jaymoskingdoms$age(ITEM_LIFETIME - LIFE);
	}
}
