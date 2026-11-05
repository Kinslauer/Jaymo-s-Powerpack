package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.524) Taken by your killers. Andy (2026-11-04), on Powers' death by cause: "Certain wakes, you keep your gear but if you
 * are kidnapped by your sentient murderers (orcs goblins ogres pirates etc) theyll have it hidden or even wear it."
 * <p>
 * One left for dead by someone who thinks (an orc, a goblin, an ogre, a pirate, a barbarian, a bandit or outlaw, an illager:
 * {@link #sentient}) doesn't wake in the wild: they wake in chains in that one's camp ({@link #campOf}: their own, else the
 * nearest standing camp within {@link #CAPTURE_RANGE}), shackled as the life story's "still in chains" start is
 * ({@link StoryHard#shackle}: pick the lock, or sneak perfectly still at night to slip it; the camp hunts them for a day after).
 * Everything they carried is taken ({@link #strip}): some of their armour and weapons go on the backs and into the hands of the
 * one who downed them and the camp's own ({@link #WORN_SHARE}; whoever wears it drops it when killed), and the rest is hidden
 * in chests buried about the camp ({@link #bury}). Called by Jaymo's Powers ({@code Mortality.CAPTORS}, through
 * {@link PowersCompat#tellCaptors}); with no camp to take them to, they wake in the wild with their gear as before.
 */
public final class Captors {
	/** How far (blocks) the nearest camp may be when the killer has none of its own. */
	static final int CAPTURE_RANGE = 640;
	/** The share of armour and weapons worn by the captors (the rest is hidden). */
	static final float WORN_SHARE = 0.5F;
	/** How near the camp's fire (blocks) its people must be to put on what was taken. */
	static final double WEARERS_NEAR = 48.0;
	/** Where the captive is chained (blocks from the camp's fire), and where the chests are buried. */
	static final int CHAIN_MIN = 3, CHAIN_MAX = 9;
	static final int STASH_MIN = 5, STASH_MAX = 16, STASH_TRIES = 24;

	private Captors() {
	}

	static void register() {
		PowersCompat.tellCaptors(Captors::take);
	}

	/**
	 * {@code player} was left for dead by {@code by}: if {@code by} is one who takes captives and a camp stands to take them to,
	 * they're stripped and chained there, and this is where; null leaves them to wake in the wild.
	 */
	static @Nullable GlobalPos take(ServerPlayer player, @Nullable Entity by) {
		if (by == null || !sentient(by)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		Faction camp = campOf(server, by, player.blockPosition());
		if (camp == null || camp.fallen() || camp.seat() == null) {
			return null;
		}
		ServerLevel level = Factions.level(server);
		RandomSource random = player.getRandom();
		BlockPos at = LifeStory.ground(level, camp.seat(), CHAIN_MIN, CHAIN_MAX, random);
		if (at == null) {
			return null;
		}
		strip(player, by, camp, level, random);
		StoryHard.shackle(player, at, camp.id());
		JaymosKingdoms.LOGGER.info("{} was taken captive to {} ({})", player.getScoreboardName(), camp.name(), camp.id());
		return GlobalPos.of(level.dimension(), at);
	}

	/** One who takes captives: greenskins, ogres, pirates, the camps' raiders and illagers, bandits and outlaws. */
	static boolean sentient(Entity e) {
		if (e instanceof Worker w && w.factionId() >= 0) {
			Faction f = Factions.get(e.level().getServer(), w.factionId());
			if (f != null && f.is(FactionType.BARBARIAN)) {
				return true;
			}
		}
		return e instanceof LivingEntity living && (Orcs.isOrc(living) || Goblins.isGoblin(living) || Pirates.isPirate(living))
			|| e instanceof com.jaymoskingdoms.monsters.Ogre || e instanceof AbstractIllager || Illagers.isCampIllager(e) || Outlaws.is(e);
	}

	/** The camp {@code by} belongs to, else the nearest standing one to {@code from} within {@link #CAPTURE_RANGE}. */
	static @Nullable Faction campOf(MinecraftServer server, Entity by, BlockPos from) {
		int id = -1;
		if (by instanceof Worker w) {
			id = w.factionId();
		}
		Illagers.Tag tag = Illagers.tag(by);
		if (id < 0 && tag != null) {
			id = tag.camp();
		}
		Faction own = id >= 0 ? Factions.get(server, id) : null;
		if (own != null && own.is(FactionType.BARBARIAN) && !own.fallen() && own.seat() != null) {
			return own;
		}
		Faction best = null;
		double bestD = (double)CAPTURE_RANGE * CAPTURE_RANGE;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (!f.fallen() && f.seat() != null) {
				double d = f.seat().distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}

	/** Everything {@code player} carries is taken: some worn by {@code by} and the camp's people, the rest buried about the camp. */
	private static void strip(ServerPlayer player, Entity by, Faction camp, ServerLevel level, RandomSource random) {
		Inventory inventory = player.getInventory();
		List<ItemStack> taken = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				taken.add(stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (taken.isEmpty()) {
			return;
		}
		List<Mob> wearers = new ArrayList<>();
		if (by instanceof Mob mob && mob.isAlive()) {
			wearers.add(mob);
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(camp.seat()).inflate(WEARERS_NEAR), m -> m.isAlive() && m != by && ofCamp(m, camp))) {
			wearers.add(mob);
		}
		Collections.shuffle(wearers, new java.util.Random(random.nextLong()));
		List<ItemStack> hidden = new ArrayList<>();
		for (ItemStack stack : taken) {
			EquipmentSlot slot = slotFor(stack);
			if (slot == null || random.nextFloat() >= WORN_SHARE || !wear(wearers, slot, stack)) {
				hidden.add(stack);
			}
		}
		bury(level, camp.seat(), hidden, random);
	}

	static boolean ofCamp(Mob mob, Faction camp) {
		if (mob instanceof Worker w && w.factionId() == camp.id()) {
			return true;
		}
		Illagers.Tag tag = Illagers.tag(mob);
		return tag != null && tag.camp() == camp.id();
	}

	/** Where {@code stack} is worn or held, or null (not gear). */
	private static @Nullable EquipmentSlot slotFor(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().isArmor()) {
			return equippable.slot();
		}
		return stack.has(DataComponents.WEAPON) ? EquipmentSlot.MAINHAND : null;
	}

	/** Put on by the first of {@code wearers} with that slot free; it falls from them when they die. */
	private static boolean wear(List<Mob> wearers, EquipmentSlot slot, ItemStack stack) {
		for (Mob mob : wearers) {
			if (mob.getItemBySlot(slot).isEmpty()) {
				mob.setItemSlot(slot, stack);
				mob.setGuaranteedDrop(slot);
				return true;
			}
		}
		return false;
	}

	/** {@code items} hidden in chests buried a block or two under the ground about {@code seat}. */
	private static void bury(ServerLevel level, BlockPos seat, List<ItemStack> items, RandomSource random) {
		int next = 0;
		while (next < items.size()) {
			BlockPos at = stash(level, seat, random);
			if (!level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3) || !(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
				// (nowhere to put them: they lie on the camp's ground)
				for (int i = next; i < items.size(); i++) {
					net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level, seat.getX() + 0.5,
						seat.getY() + 1.0, seat.getZ() + 0.5, items.get(i));
					level.addFreshEntity(drop);
				}
				return;
			}
			for (int slot = 0; slot < chest.getContainerSize() && next < items.size(); slot++) {
				chest.setItem(slot, items.get(next++));
			}
			chest.setChanged();
		}
	}

	/** A buried spot near {@code seat}: two blocks under natural ground, with ground over it; else on the ground there. */
	private static BlockPos stash(ServerLevel level, BlockPos seat, RandomSource random) {
		BlockPos fallback = null;
		for (int i = 0; i < STASH_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = STASH_MIN + random.nextDouble() * (STASH_MAX - STASH_MIN);
			int x = (int)Math.floor(seat.getX() + 0.5 + Math.cos(angle) * r);
			int z = (int)Math.floor(seat.getZ() + 0.5 + Math.sin(angle) * r);
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos surface = new BlockPos(x, top, z);
			if (fallback == null && level.getBlockState(surface).isAir()) {
				fallback = surface;
			}
			BlockPos hole = surface.below(2);
			if (natural(level.getBlockState(hole)) && natural(level.getBlockState(hole.above())) && level.getBlockEntity(hole) == null) {
				return hole;
			}
		}
		return fallback != null ? fallback : seat.above();
	}

	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.CLAY) || state.is(BlockTags.SNOW);
	}
}
