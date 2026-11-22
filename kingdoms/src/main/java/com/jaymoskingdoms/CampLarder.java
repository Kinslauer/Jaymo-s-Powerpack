package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * A camp's larder: the chests and barrels round its fire, and whatever is lying on the ground by the fire (a hunter's
 * kill set down there). It holds only what was put in it. Andy (2026-11-19): "when anyone goes hungry they cant just
 * make the food come out of nowhere": a camp eats what its hunters bring in and what it took, and nothing else.
 */
final class CampLarder {
	/** The camp's chests and barrels: any this near the fire (blocks across, and up or down). */
	static final int STORES = 12, STORES_RISE = 3;
	/** What lies on the ground this near the fire counts as in the larder (blocks). */
	static final double BY_FIRE = 3.0;
	/** One who went without supper is weak this long (ticks, a day), or till they next eat. */
	static final int HUNGRY_TICKS = 24000;

	/** Who's weak with hunger just now (so eating takes off only that weakness, not a poisoned supper's). */
	private static final Map<Mob, Boolean> HUNGRY = new WeakHashMap<>();

	private CampLarder() {
	}

	/** Anything good to eat. */
	static boolean food(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD);
	}

	/** {@code m} went without: weak with hunger for a day, or till they eat. */
	static void hungry(Mob m) {
		m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HUNGRY_TICKS, 0));
		HUNGRY.put(m, Boolean.TRUE);
	}

	/** {@code m} has eaten: the weakness of hunger leaves them. */
	static void fed(Mob m) {
		if (HUNGRY.remove(m) != null) {
			m.removeEffect(MobEffects.WEAKNESS);
		}
	}

	/** One of what {@code what} takes, out of the larder round {@code fire}: off the ground by it first, then its boxes. Empty if none. */
	static ItemStack take(ServerLevel level, BlockPos fire, Predicate<ItemStack> what) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(fire).inflate(BY_FIRE),
			i -> i.isAlive() && what.test(i.getItem()))) {
			ItemStack one = item.getItem().split(1);
			if (item.getItem().isEmpty()) {
				item.discard();
			}
			return one;
		}
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-STORES, -STORES_RISE, -STORES), fire.offset(STORES, STORES_RISE, STORES))) {
			BlockEntity be = level.getBlockEntity(p);
			if (!(be instanceof Container box)) {
				continue;
			}
			for (int i = 0; i < box.getContainerSize(); i++) {
				ItemStack stack = box.getItem(i);
				if (!stack.isEmpty() && what.test(stack)) {
					ItemStack one = box.removeItem(i, 1);
					box.setChanged();
					return one;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	/** How many of what {@code what} takes the larder round {@code fire} holds. */
	static int count(ServerLevel level, BlockPos fire, Predicate<ItemStack> what) {
		int n = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(fire).inflate(BY_FIRE),
			i -> i.isAlive() && what.test(i.getItem()))) {
			n += item.getItem().getCount();
		}
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-STORES, -STORES_RISE, -STORES), fire.offset(STORES, STORES_RISE, STORES))) {
			if (level.getBlockEntity(p) instanceof Container box) {
				for (int i = 0; i < box.getContainerSize(); i++) {
					ItemStack stack = box.getItem(i);
					if (!stack.isEmpty() && what.test(stack)) {
						n += stack.getCount();
					}
				}
			}
		}
		return n;
	}

	/** {@code stack} into the nearest box round {@code fire} with room; what won't go in is set down by the fire. */
	static void put(ServerLevel level, BlockPos fire, ItemStack stack) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-STORES, -STORES_RISE, -STORES), fire.offset(STORES, STORES_RISE, STORES))) {
			if (level.getBlockEntity(p) instanceof Container && p.distSqr(fire) < bestD) {
				bestD = p.distSqr(fire);
				best = p.immutable();
			}
		}
		if (best != null && level.getBlockEntity(best) instanceof Container box) {
			for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack in = box.getItem(i);
				if (in.isEmpty()) {
					box.setItem(i, stack.split(stack.getCount()));
				} else if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
					int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
					in.grow(move);
					stack.shrink(move);
				}
			}
			box.setChanged();
		}
		if (!stack.isEmpty()) {
			ItemEntity drop = new ItemEntity(level, fire.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2.0, fire.getY() + 0.2,
				fire.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2.0, stack);
			drop.setDeltaMovement(0.0, 0.1, 0.0);
			level.addFreshEntity(drop);
		}
	}
}
