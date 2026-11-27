package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.ItemEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Andy (2026-11-26, a mangrove swamp burning, at a few frames a second): "maybe if dropped items disappeared faster there
 * or something". When a wood burns, its leaves decay all at once and drop their sticks, saplings and apples by the
 * hundred, and each lies there five minutes. What a tree sheds on its own (sticks, saplings and propagules, apples,
 * leaves) and nobody threw goes after {@link #LIFE} instead, anywhere. Anything a player threw down keeps the usual five
 * minutes, and so does every other kind of item (tools, food, ores, a mob's loot): only these few things a tree sheds go
 * sooner.
 * <p>
 * Andy (2026-11-26, a night wood on the map ringed with dropped items where its canopies had decayed): "making none of
 * them drop on their own unless a player is near". Anything the world drops by itself, with no one behind it, drops
 * nothing unless a player is within {@link #WATCH} ({@link #unwatched}):
 * <ul>
 * <li>a block that goes with nobody breaking it ({@code UnwatchedDropsMixin}): leaves decaying (Quick Leaf Decay's too),
 * crops and torches washed off by water, cactus, cane, bamboo, vines and the like left with nothing under them, what a
 * storm, a collapse or fire's knock-on brings down. A block a player, a mob, a villager at work or a falling tree breaks
 * gives what it always did, near or far.</li>
 * <li>what an animal leaves of its own accord ({@code UnwatchedGiftMixin}): a chicken's egg, an armadillo's or a grown
 * turtle's scute, a sniffer's dug-up seed, a panda's sneezed slimeball. With nobody near, that one simply isn't laid;
 * the next comes at its usual time.</li>
 * </ul>
 * A mob's death needs nothing here: its loot already goes into its body (Kitchen's corpses), not onto the ground.
 */
public final class Windfall {
	/** How long (ticks) a stick, sapling, apple or leaf nobody threw lies on the ground: a minute. */
	static final int LIFE = 1200;
	/** A player this close (blocks) to where the world drops something by itself sees it drop; further off, it doesn't. */
	public static final double WATCH = FallingTree.DEBRIS_WATCH;
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

	/** Whether something the world drops by itself at ({@code x}, {@code y}, {@code z}) should drop nothing: no living player (not a spectator) within {@link #WATCH}. */
	public static boolean unwatched(Level level, double x, double y, double z) {
		return level instanceof ServerLevel && !level.hasNearbyAlivePlayer(x, y, z, WATCH);
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
