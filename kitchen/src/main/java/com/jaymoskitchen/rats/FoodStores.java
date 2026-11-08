package com.jaymoskitchen.rats;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.delight.CuttingBoardEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What rats come for: food left where they can get at it. Any storage block with food in it (a chest, a barrel, a
 * Kitchen cabinet or basket, a larder that holds its stock as a container; not a shulker box, which shuts tight),
 * something lying on a cutting board, and a carcass hung on a meat hook. A baited trap smells best of all.
 */
public final class FoodStores {
	/** How close a rat has to be to nibble at a store, or at a hung carcass (blocks). */
	static final double REACH = 1.6, CARCASS_REACH = 2.2;

	private FoodStores() {
	}

	/** Something rats eat: food, grain and seed, dough. */
	public static boolean ratFood(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.is(Items.WHEAT) || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
			|| stack.is(ModItems.DOUGH));
	}

	/** One place a rat can raid. */
	public interface Target {
		Vec3 at();

		/** How close it has to be (squared). */
		default double reachSqr() {
			return REACH * REACH;
		}

		/** A baited trap (it walks onto it; the trap does the rest). */
		default boolean trap() {
			return false;
		}

		default boolean carcass() {
			return false;
		}

		boolean stillThere(ServerLevel level);

		/** What it's eating (for crumbs), without taking it. */
		ItemStack peek(ServerLevel level);

		/** Takes one piece; empty if there was nothing to take (a carcass is only gnawed). */
		ItemStack take(ServerLevel level, Rat rat);
	}

	/** The nearest raid within {@code range} of the rat: a baited trap first, else the nearest food. */
	public static @Nullable Target nearest(ServerLevel level, Rat rat, double range) {
		Target best = null;
		double bestDist = Double.MAX_VALUE;
		boolean bestTrap = false;
		for (Target target : around(level, rat.blockPosition(), range, true)) {
			double dist = rat.distanceToSqr(target.at());
			if (dist > range * range) {
				continue;
			}
			if ((target.trap() && !bestTrap) || (target.trap() == bestTrap && dist < bestDist)) {
				best = target;
				bestDist = dist;
				bestTrap = target.trap();
			}
		}
		return best;
	}

	/** Every food store (and, if {@code traps}, every baited trap) within {@code range} of {@code center}. */
	public static List<Target> around(ServerLevel level, BlockPos center, double range, boolean traps) {
		List<Target> found = new ArrayList<>();
		int r = (int) Math.ceil(range);
		int minX = SectionPos.blockToSectionCoord(center.getX() - r), maxX = SectionPos.blockToSectionCoord(center.getX() + r);
		int minZ = SectionPos.blockToSectionCoord(center.getZ() - r), maxZ = SectionPos.blockToSectionCoord(center.getZ() + r);
		double rangeSqr = range * range;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos pos = be.getBlockPos();
					if (pos.distSqr(center) > rangeSqr) {
						continue;
					}
					Target target = of(be, traps);
					if (target != null) {
						found.add(target);
					}
				}
			}
		}
		for (CorpseEntity corpse : level.getEntitiesOfClass(CorpseEntity.class, new AABB(center).inflate(range),
			c -> c.hook() != null && !c.entityTags().contains(Rats.GNAWED_TAG))) {
			found.add(new Carcass(corpse));
		}
		return found;
	}

	private static @Nullable Target of(BlockEntity be, boolean traps) {
		if (be instanceof SnapTrapEntity trap) {
			return traps && trap.armed() ? new Trap(trap.getBlockPos()) : null;
		}
		if (be instanceof CageTrapEntity cage) {
			return traps && cage.armed() ? new Trap(cage.getBlockPos()) : null;
		}
		if (be instanceof CuttingBoardEntity board) {
			return ratFood(board.item()) ? new Board(board.getBlockPos()) : null;
		}
		if (be instanceof Container container && !(be instanceof ShulkerBoxBlockEntity) && foodSlot(container) >= 0) {
			return new Store(be.getBlockPos());
		}
		return null;
	}

	/** How many stacks of food a store holds (for how tempting it is). */
	public static int foodStacks(ServerLevel level, Target target) {
		if (target instanceof Store store && level.getBlockEntity(store.pos) instanceof Container container) {
			int count = 0;
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (ratFood(container.getItem(i))) {
					count++;
				}
			}
			return count;
		}
		return 1;
	}

	static int foodSlot(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (ratFood(container.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/** A storage block with food in it. */
	record Store(BlockPos pos) implements Target {
		@Override
		public Vec3 at() {
			return Vec3.atCenterOf(pos);
		}

		@Override
		public boolean stillThere(ServerLevel level) {
			return level.getBlockEntity(pos) instanceof Container container && foodSlot(container) >= 0;
		}

		@Override
		public ItemStack peek(ServerLevel level) {
			if (level.getBlockEntity(pos) instanceof Container container) {
				int slot = foodSlot(container);
				if (slot >= 0) {
					return container.getItem(slot);
				}
			}
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack take(ServerLevel level, Rat rat) {
			if (level.getBlockEntity(pos) instanceof Container container) {
				int slot = foodSlot(container);
				if (slot >= 0) {
					ItemStack taken = container.removeItem(slot, 1);
					container.setChanged();
					return taken;
				}
			}
			return ItemStack.EMPTY;
		}
	}

	/** Something left on a cutting board. */
	record Board(BlockPos pos) implements Target {
		@Override
		public Vec3 at() {
			return Vec3.atBottomCenterOf(pos);
		}

		@Override
		public boolean stillThere(ServerLevel level) {
			return level.getBlockEntity(pos) instanceof CuttingBoardEntity board && ratFood(board.item());
		}

		@Override
		public ItemStack peek(ServerLevel level) {
			return level.getBlockEntity(pos) instanceof CuttingBoardEntity board ? board.item() : ItemStack.EMPTY;
		}

		@Override
		public ItemStack take(ServerLevel level, Rat rat) {
			return level.getBlockEntity(pos) instanceof CuttingBoardEntity board ? board.take() : ItemStack.EMPTY;
		}
	}

	/** A baited trap. */
	record Trap(BlockPos pos) implements Target {
		@Override
		public Vec3 at() {
			return Vec3.atBottomCenterOf(pos);
		}

		@Override
		public boolean trap() {
			return true;
		}

		@Override
		public boolean stillThere(ServerLevel level) {
			BlockEntity be = level.getBlockEntity(pos);
			return (be instanceof SnapTrapEntity snap && snap.armed()) || (be instanceof CageTrapEntity cage && cage.armed());
		}

		@Override
		public ItemStack peek(ServerLevel level) {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack take(ServerLevel level, Rat rat) {
			return ItemStack.EMPTY;
		}
	}

	/**
	 * A carcass on a meat hook: rats gnaw it, and then its meat spoils like an unbled carcass's
	 * ({@link com.jaymoskitchen.butchery.Spoilage}), however well it's bled.
	 */
	record Carcass(CorpseEntity corpse) implements Target {
		@Override
		public Vec3 at() {
			return new Vec3(corpse.getX(), corpse.getBoundingBox().minY, corpse.getZ());
		}

		@Override
		public double reachSqr() {
			return CARCASS_REACH * CARCASS_REACH;
		}

		@Override
		public boolean carcass() {
			return true;
		}

		@Override
		public boolean stillThere(ServerLevel level) {
			return corpse.isAlive() && corpse.hook() != null && !corpse.entityTags().contains(Rats.GNAWED_TAG);
		}

		@Override
		public ItemStack peek(ServerLevel level) {
			return new ItemStack(Items.BEEF);
		}

		@Override
		public ItemStack take(ServerLevel level, Rat rat) {
			corpse.addTag(Rats.GNAWED_TAG);
			level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.5F, 1.4F);
			return ItemStack.EMPTY;
		}
	}
}
