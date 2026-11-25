package com.jaymospowers.power.web;

import com.jaymospowers.compat.KingdomsCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Its curiosity about chests (patch 4 of the 1.1.3 picks). From the Integrated stage, every {@link #LOOK_EVERY} ticks it
 * looks at one chest within {@link #REACH} of you:
 * <ul>
 * <li>a <b>dungeon chest</b> (a loot-table chest nobody has opened yet) it opens and loots, the haul laid in your pack
 * ({@link #loot});</li>
 * <li>any other it may peek into, and say what it saw ({@link #peek}), once a day a chest; sometimes it
 * <b>re-sorts</b> what is in it ({@link #sort}, {@link #SORT_CHANCE}), and now and then <b>steals a tool</b> into a free
 * slot of the arm ({@link #steal}, {@link #STEAL_CHANCE}), which then counts as a thing it keeps
 * ({@link WebSymbioteArm}).</li>
 * </ul>
 * It never touches a chest in a village or in land a Kingdoms faction has claimed ({@link #allowed}), a locked chest, a
 * trapped chest, or one somebody has open. (There is no chest ownership in this mod: the towns' land stands for other
 * people's chests.) The Settings screen's small-talk switch ({@link WebSymbioteSettings.Setting#FIDGETS}) keeps it quiet
 * about it, and a symbiote told to hide ({@link WebSymbioteMind#hiding}) leaves chests alone.
 */
public final class WebSymbioteChests {
	/** The stage it gets curious at (Integrated), how far it reaches (blocks across, up and down), and the ticks between looks. */
	static final int CHEST_STAGE = 1, REACH = 5, UP = 3, LOOK_EVERY = 100;
	/** A chest is peeked at no more often than this (ticks), and the table of the ones it has seen holds at most this many. */
	static final long SEEN_FOR = 24000L;
	static final int SEEN_MAX = 64;
	/** What happens at a chest it may peek into, in chances: a re-sort, a stolen tool. */
	static final float SORT_CHANCE = 0.25F, STEAL_CHANCE = 0.06F;
	/** A dungeon chest's haul is laid in your pack at most this many stacks a look (the rest next look). */
	static final int LOOT_STACKS = 27;
	static final int LINES = 3;

	private static final Map<UUID, Map<Long, Long>> SEEN = new HashMap<>();

	private WebSymbioteChests() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % LOOK_EVERY == 0) {
					look(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEEN.remove(handler.player.getUUID()));
	}

	/**
	 * It may work at this spot: you may build there, and it isn't a village or land a Kingdoms faction holds. (A
	 * pick-up of the same rule serves the ore it digs.)
	 */
	static boolean allowed(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!WebSymbioteBody.mayWork(player, pos) || level.isVillage(pos)) {
			return false;
		}
		return KingdomsCompat.call("Factions", "at", new Class<?>[] {net.minecraft.server.MinecraftServer.class, net.minecraft.world.level.Level.class,
			BlockPos.class}, null, level.getServer(), level, pos) == null;
	}

	private static void look(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || WebSymbioteMind.stage(state.bond()) < CHEST_STAGE || !WebSymbioteBody.ready(player) || WebSymbioteMind.hiding(player)) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos base = player.blockPosition();
		long now = level.getGameTime();
		Map<Long, Long> seen = SEEN.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		BlockPos best = null;
		boolean bestLoot = false;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-REACH, -UP, -REACH), base.offset(REACH, UP, REACH))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState block = level.getBlockState(pos);
			if (!(block.getBlock() instanceof ChestBlock) || block.getBlock() instanceof TrappedChestBlock) {
				continue;
			}
			BlockEntity entity = level.getBlockEntity(pos);
			if (!(entity instanceof ChestBlockEntity chest) || chest.isLocked() || !chest.canOpen(player) || ChestBlockEntity.getOpenCount(level, pos) > 0
				|| !allowed(level, player, pos)) {
				continue;
			}
			boolean loot = chest.getLootTable() != null;
			Long was = seen.get(pos.asLong());
			if (!loot && was != null && now - was < SEEN_FOR) {
				continue;
			}
			double distance = pos.distSqr(base);
			// (a dungeon chest first: it is the one worth the walk)
			if (best == null || loot && !bestLoot || loot == bestLoot && distance < bestDistance) {
				best = pos.immutable();
				bestLoot = loot;
				bestDistance = distance;
			}
		}
		if (best == null) {
			return;
		}
		if (seen.size() > SEEN_MAX) {
			seen.clear();
		}
		seen.put(best.asLong(), now);
		BlockState chestState = level.getBlockState(best);
		Container container = ChestBlock.getContainer((ChestBlock)chestState.getBlock(), chestState, level, best, true);
		if (container == null) {
			return;
		}
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + WebSymbioteBody.ARM_OUT_TICKS);
		level.playSound(null, best, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4F, 0.7F);
		if (bestLoot) {
			loot(player, level, best, container);
		} else {
			peek(player, container);
			if (player.getRandom().nextFloat() < STEAL_CHANCE) {
				steal(player, container);
			} else if (player.getRandom().nextFloat() < SORT_CHANCE) {
				sort(player, container);
			}
		}
		container.setChanged();
	}

	private static void loot(ServerPlayer player, ServerLevel level, BlockPos pos, Container container) {
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.unpackLootTable(player);
		}
		int taken = 0;
		for (int i = 0; i < container.getContainerSize() && taken < LOOT_STACKS; i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack copy = stack.copy();
			container.setItem(i, ItemStack.EMPTY);
			if (!player.getInventory().add(copy) && !copy.isEmpty()) {
				Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), copy);
			}
			taken++;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7F, 1.1F);
		if (taken > 0) {
			say(player, "chest.loot");
		}
	}

	private static void peek(ServerPlayer player, Container container) {
		int kinds = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				kinds++;
			}
		}
		if (kinds > 0 && player.getRandom().nextInt(3) == 0) {
			say(player, "chest.peek");
		} else if (kinds == 0 && player.getRandom().nextInt(3) == 0) {
			say(player, "chest.empty");
		}
	}

	/** Puts what is in the chest in order: like with like, the most of a kind first, kinds by name. */
	static void sort(ServerPlayer player, Container container) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack merged = null;
			for (ItemStack other : stacks) {
				if (ItemStack.isSameItemSameComponents(other, stack) && other.getCount() < other.getMaxStackSize()) {
					int move = Math.min(stack.getCount(), other.getMaxStackSize() - other.getCount());
					other.grow(move);
					stack = stack.copyWithCount(stack.getCount() - move);
					if (stack.isEmpty()) {
						merged = other;
						break;
					}
				}
			}
			if (merged == null) {
				stacks.add(stack.copy());
			}
		}
		stacks.sort((a, b) -> {
			int byName = BuiltInRegistries.ITEM.getKey(a.getItem()).toString().compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()).toString());
			return byName != 0 ? byName : Integer.compare(b.getCount(), a.getCount());
		});
		for (int i = 0; i < container.getContainerSize(); i++) {
			container.setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
		say(player, "chest.sort");
	}

	/** A tool out of the chest, into a free slot of the arm. */
	private static void steal(ServerPlayer player, Container container) {
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		if (player.getAttached(WebSymbioteArm.KEPT) != null) {
			return;
		}
		int free = -1;
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS && free < 0; i++) {
			if (arm.get(i).isEmpty()) {
				free = i;
			}
		}
		if (free < 0) {
			return;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && (stack.has(DataComponents.TOOL) || WebSymbioteArm.weapon(stack)) && stack.isDamageableItem()) {
				container.setItem(i, ItemStack.EMPTY);
				WebSymbioteBody.setArm(player, arm.with(free, stack.copy()));
				WebSymbioteArm.keeping(player, free);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7F, 0.8F);
				WebSymbiote.whisper(player, "chest.steal." + (1 + player.getRandom().nextInt(LINES)), stack.getHoverName());
				return;
			}
		}
	}

	private static void say(ServerPlayer player, String base) {
		if (WebSymbioteSettings.off(player, WebSymbioteSettings.Setting.FIDGETS)) {
			return;
		}
		WebSymbiote.whisper(player, base + "." + (1 + player.getRandom().nextInt(LINES)));
	}
}
