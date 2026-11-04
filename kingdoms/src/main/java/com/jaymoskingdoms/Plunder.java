package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Plundering coves, camps and holds. What a raider (a pirate landing party, an illager war party, a greenskin
 * band, an ogre raid) takes from the settlement it hit, beyond its registered storage ({@link Storage}): the chests and
 * barrels standing loose on its land are broken into ({@link #breakIn}), and its treasury's coin is carried off as
 * emeralds ({@link #coin}). Greenskins bend what they take ({@link #bend}), since they trade in bent ingots.
 *
 * <p>Raiders carry one load each in their hands; a raider who falls drops it where he dies, so killing the carriers
 * gets the loot back. Everything here is cheap: one scan round the raider, once, when he has nothing to carry.
 */
final class Plunder {
	// ================================================================================================================
	// Tuning
	/** How far (blocks) a raider looks for loose chests and barrels round himself, across and up and down. */
	static final int LOOSE_ACROSS = 10, LOOSE_UP = 4;
	/** The most one raider takes from a loose container in one go. */
	static final int LOOSE_TAKE = 48;
	/** A chest that was stripped is smashed for sure; a chest that still holds things is smashed (spilling) this often. */
	static final float SMASH_CHANCE = 0.25F;
	/** The coin a raider carries off: this many emeralds at least, this share (percent) of the treasury, at most this many. */
	static final int COIN_LEAST = 8, COIN_PERCENT = 4, COIN_MOST = 64;
	/** The share of raiders who go for the coin first. */
	static final float COIN_FIRST = 0.25F;
	/** A greenskin camp bends what it brings home: its worth in bent copper for a plain emerald or a gold ingot. */
	static final int EMERALD_BENT = 3, GOLD_BENT = 2;
	// ================================================================================================================

	private Plunder() {
	}

	/** {@code stack} is something a raider takes before anything else. */
	static boolean valuable(ItemStack stack) {
		return stack.is(Items.EMERALD) || stack.is(Items.GOLD_INGOT) || stack.is(Items.IRON_INGOT) || stack.is(Items.DIAMOND)
			|| stack.is(Items.EMERALD_BLOCK) || stack.is(Items.GOLD_BLOCK) || BentIngots.bent(stack)
			// ((0.7.490) any realm's coin, and anything gold)
			|| Currencies.isMoney(stack) || Currencies.gold(stack);
	}

	/**
	 * A raider at {@code target}'s land breaks into a chest or barrel that stands loose near him (one that isn't the
	 * settlement's registered storage and that stands on its land) and takes up to {@link #LOOSE_TAKE} of its best stack.
	 * A container that is stripped bare is smashed (with the sound of it); one with things left is sometimes smashed
	 * anyway, spilling them for whoever comes by ((0.7.490) only where mobGriefing is on). Empty if there was nothing.
	 */
	static ItemStack breakIn(ServerLevel level, Mob raider, Faction target, float smash, int take) {
		MinecraftServer server = level.getServer();
		BlockPos centre = raider.blockPosition();
		RandomSource random = level.getRandom();
		BlockPos bestAt = null;
		int bestScore = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-LOOSE_ACROSS, -LOOSE_UP, -LOOSE_ACROSS), centre.offset(LOOSE_ACROSS, LOOSE_UP, LOOSE_ACROSS))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BarrelBlock)) {
				continue;
			}
			if (target.holdings().storage().contains(pos) || !(level.getBlockEntity(pos) instanceof Container container)) {
				continue;
			}
			Faction owner = Banners.owner(server, ChunkPos.containing(pos));
			if (owner == null || owner.id() != target.id()) {
				continue;
			}
			int score = 0;
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					score += valuable(stack) ? 4 : 1;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestAt = pos.immutable();
			}
		}
		if (bestAt == null || !(level.getBlockEntity(bestAt) instanceof Container container)) {
			return ItemStack.EMPTY;
		}
		int slotAt = -1;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && (slotAt < 0 || valuable(stack) && !valuable(container.getItem(slotAt)))) {
				slotAt = slot;
			}
		}
		if (slotAt < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = container.removeItem(slotAt, Math.min(take, container.getItem(slotAt).getCount()));
		container.setChanged();
		boolean bare = true;
		for (int slot = 0; slot < container.getContainerSize() && bare; slot++) {
			bare = container.getItem(slot).isEmpty();
		}
		Vec3 at = Vec3.atCenterOf(bestAt);
		// ((0.7.490) the lid is smashed only where mobs may break blocks: otherwise it is opened and robbed)
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		if (griefing && (bare || random.nextFloat() < smash)) {
			// (the lid is smashed in: whatever is left spills out)
			level.destroyBlock(bestAt, true, raider);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
			level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 1.0F, 0.7F);
		}
		return taken;
	}

	/**
	 * A raider carries off some of {@code target}'s coin: a share of its treasury, paid out of it and handed over as
	 * emeralds. Only a realm keeps a treasury. Empty if there was none.
	 */
	static ItemStack coin(MinecraftServer server, Faction target) {
		if (!target.type().isRealm() || target.treasury() <= 0L) {
			return ItemStack.EMPTY;
		}
		long taken = Math.min(target.treasury(), Math.min(COIN_MOST, Math.max(COIN_LEAST, target.treasury() * COIN_PERCENT / 100L)));
		if (taken <= 0L) {
			return ItemStack.EMPTY;
		}
		Factions.update(server, target.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - taken)));
		return new ItemStack(Items.EMERALD, (int)taken);
	}

	/**
	 * What a raider takes when he reaches the settlement: its coin (now and then first), then the valuables and goods in
	 * its storage, then whatever stands loose in chests and barrels near him, then coin. Empty if there is nothing left.
	 */
	static ItemStack take(ServerLevel level, Mob raider, Faction target, int perRaider, float smash, boolean pirate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		if (random.nextFloat() < COIN_FIRST) {
			ItemStack coin = coin(server, target);
			if (!coin.isEmpty()) {
				return coin;
			}
		}
		List<ItemStack> contents = Storage.contents(level, target);
		if (!contents.isEmpty()) {
			List<ItemStack> valuables = contents.stream().filter(Plunder::valuable).toList();
			ItemStack first = !valuables.isEmpty() && random.nextBoolean() ? valuables.get(random.nextInt(valuables.size()))
				: contents.get(random.nextInt(contents.size()));
			// (0.7.490) pirates take anything gold, and the richest gold first
			ItemStack pick = first;
			if (pirate) {
				ItemStack richest = contents.stream().filter(Currencies::gold).max(java.util.Comparator.comparingLong(Currencies::goldWorth)).orElse(null);
				if (richest != null) {
					pick = richest;
				}
			}
			ItemStack chosen = pick;
			ItemStack taken = Storage.take(level, target, s -> ItemStack.isSameItemSameComponents(s, chosen), perRaider);
			if (!taken.isEmpty()) {
				level.playSound(null, raider.getX(), raider.getY(), raider.getZ(), SoundEvents.BARREL_OPEN, SoundSource.HOSTILE, 1.0F, 0.8F);
				return taken;
			}
		}
		ItemStack loose = breakIn(level, raider, target, smash, Math.min(perRaider, LOOSE_TAKE));
		return loose.isEmpty() ? coin(server, target) : loose;
	}

	/** Every loose (not registered) chest or barrel on {@code target}'s land within {@code across} of its seat that isn't bare, nearest first. */
	static List<BlockPos> looseAll(ServerLevel level, Faction target, int across) {
		MinecraftServer server = level.getServer();
		BlockPos seat = target.seat();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(seat.offset(-across, -LOOSE_UP * 2, -across), seat.offset(across, LOOSE_UP * 2, across))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if ((state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) && !target.holdings().storage().contains(pos)
				&& level.getBlockEntity(pos) instanceof Container c && !c.isEmpty()) {
				Faction owner = Banners.owner(server, ChunkPos.containing(pos));
				if (owner != null && owner.id() == target.id()) {
					out.add(pos.immutable());
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(seat)));
		return out.size() > 24 ? new ArrayList<>(out.subList(0, 24)) : out;
	}

	/** Whether any loose chest or barrel on {@code target}'s land is within a raider's reach of {@code at} (a cheap look, used to decide where to walk). */
	static @Nullable BlockPos looseNear(ServerLevel level, Faction target, BlockPos at, int across) {
		MinecraftServer server = level.getServer();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-across, -LOOSE_UP, -across), at.offset(across, LOOSE_UP, across))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if ((state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) && !target.holdings().storage().contains(pos)) {
				Faction owner = Banners.owner(server, ChunkPos.containing(pos));
				if (owner != null && owner.id() == target.id() && level.getBlockEntity(pos) instanceof Container c && !c.isEmpty()) {
					return pos.immutable();
				}
			}
		}
		return null;
	}

	/**
	 * What a greenskin camp makes of a load it brings home: plain ingots, emeralds and gold bent into the coin the tribes
	 * trade in ({@link BentIngots}); anything else as it is.
	 */
	static ItemStack bend(ItemStack load) {
		if (load.isEmpty()) {
			return load;
		}
		if (load.is(Items.IRON_INGOT)) {
			return new ItemStack(BentIngots.BENT_IRON, load.getCount());
		}
		if (load.is(Items.COPPER_INGOT)) {
			return new ItemStack(BentIngots.BENT_COPPER, load.getCount());
		}
		if (load.is(Items.EMERALD)) {
			return new ItemStack(BentIngots.BENT_COPPER, load.getCount() * EMERALD_BENT);
		}
		if (load.is(Items.GOLD_INGOT)) {
			return new ItemStack(BentIngots.BENT_COPPER, load.getCount() * GOLD_BENT);
		}
		return load;
	}

	/** Deposits {@code stacks} (any size) into {@code camp}'s loot pile. */
	static void depositAll(ServerLevel level, Faction camp, List<ItemStack> stacks) {
		for (ItemStack stack : new ArrayList<>(stacks)) {
			if (!stack.isEmpty()) {
				Camps.deposit(level, camp, stack.copy());
			}
		}
	}

	/** Drops {@code stacks} at {@code at}, as a wreck would spill them. */
	static void spill(ServerLevel level, Vec3 at, List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(level, at.x, at.y, at.z, stack.copy());
			}
		}
	}

	/** {@code e} stands within {@code range} of {@code at}. */
	static boolean within(Entity e, BlockPos at, double range) {
		return e.distanceToSqr(Vec3.atCenterOf(at)) <= range * range;
	}
}
