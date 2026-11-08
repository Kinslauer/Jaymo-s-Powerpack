package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "the farm start should start you waking up from the bed, with all your gear in a chest nearby. you should be
 * greeted by the family, theyll tell you they found you and took you in." The Family farmhand ({@link FarmhandHome})
 * wakes lying in one of the house's beds ({@link BedRest#lay}, awake), everything they set out with put away in a chest
 * beside it, and the family come over and tell them, a line at a time over their heads ({@link Speech}), how they found
 * them out past the fields and took them in.
 */
public final class FarmhandWake {
	/** How long the eyes take to open once the loading screen is gone (ticks of blindness). */
	static final int EYES_TICKS = 50;
	/** From waking to the first word (ticks). */
	static final int GREET_DELAY = 40;
	/** Between one line and the next: a base, plus a little per letter, so each can be read (ticks). */
	static final int LINE_BASE_TICKS = 40;
	static final float LINE_PER_CHAR_TICKS = 1.3F;
	/** How near the family come to talk (blocks), at what pace, how often they're steered (ticks). */
	static final double GREET_REACH = 2.5, GREET_SPEED = 0.6;
	static final int STEER_EVERY = 10;
	/** How far round the bed a gear chest may go (blocks), and how many chests at most. */
	static final int CHEST_REACH = 2, MAX_CHESTS = 2;
	/** Given up on if the player isn't seen in the world in this long (ticks). */
	static final int GIVE_UP_TICKS = 20 * 60 * 5;

	/**
	 * The greeting, in order: who says it ({@link #A}: the first grown one, {@link #B}: the second, or the first again,
	 * {@link #CHILD}: a child, skipped with none) and the line's key under {@code story.jaymoskingdoms.farm_wake.}.
	 */
	private static final int A = 0, B = 1, CHILD = 2;
	private static final int[] WHO = {A, B, A, B, A, CHILD, B};
	private static final String[] LINES = {"awake", "found", "carried", "chest", "welcome", "child", "rest"};

	private static final class Wake {
		final BlockPos bed, spot;
		final List<UUID> grown = new ArrayList<>(), young = new ArrayList<>();
		final long since;
		boolean stowed;
		long seenAt = -1, nextAt;
		int line;

		Wake(BlockPos bed, BlockPos spot, long since) {
			this.bed = bed;
			this.spot = spot;
			this.since = since;
		}
	}

	private static final Map<UUID, Wake> WAKES = new HashMap<>();
	/** The family members talking to someone just woken: {@link FarmFamily#step} leaves them to us meanwhile. */
	private static final Set<UUID> GREETERS = new HashSet<>();

	private FarmhandWake() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FarmhandWake::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAKES.clear();
			GREETERS.clear();
		});
	}

	/** {@code player} is taken in by {@code folk} and will wake in the bed whose head is {@code bed} (beside {@code spot}). */
	static void expect(ServerPlayer player, BlockPos bed, BlockPos spot, List<Villager> folk) {
		Wake w = new Wake(bed, spot, player.level().getServer().getTickCount());
		for (Villager v : folk) {
			(v.isBaby() ? w.young : w.grown).add(v.getUUID());
		}
		drop(player.getUUID());
		WAKES.put(player.getUUID(), w);
	}

	/** Whether {@code mob} is greeting a farmhand just woken (so the farm round leaves them be). */
	static boolean greeting(Entity mob) {
		return !GREETERS.isEmpty() && GREETERS.contains(mob.getUUID());
	}

	/**
	 * The life story has kitted {@code player} out ({@link LifeStory}): if they're a farmhand waking in a bed, all of it
	 * goes in a chest beside the bed and they're laid in it.
	 */
	static void stow(ServerPlayer player) {
		Wake w = WAKES.get(player.getUUID());
		if (w == null || w.stowed) {
			return;
		}
		w.stowed = true;
		ServerLevel level = player.level();
		Inventory inv = player.getInventory();
		int slot = 0;
		for (int chests = 0; chests < MAX_CHESTS; chests++) {
			while (slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
				slot++;
			}
			if (slot >= inv.getContainerSize()) {
				break;
			}
			ChestBlockEntity chest = placeChest(level, w);
			if (chest == null) {
				break;
			}
			int into = 0;
			for (; slot < inv.getContainerSize() && into < chest.getContainerSize(); slot++) {
				ItemStack stack = inv.getItem(slot);
				if (!stack.isEmpty()) {
					chest.setItem(into++, inv.removeItemNoUpdate(slot));
				}
			}
			chest.setChanged();
		}
		inv.setChanged();
		lay(player, w);
	}

	/** A chest on the floor beside the bed (not where they'd stand), facing it; null with no room. */
	private static @Nullable ChestBlockEntity placeChest(ServerLevel level, Wake w) {
		BlockState bed = level.getBlockState(w.bed);
		BlockPos foot = bed.getBlock() instanceof BedBlock ? w.bed.relative(bed.getValue(BedBlock.FACING).getOpposite()) : w.bed;
		for (BlockPos half : List.of(w.bed, foot)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				ChestBlockEntity chest = chestAt(level, half.relative(d), d.getOpposite(), w);
				if (chest != null) {
					return chest;
				}
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(w.bed.offset(-CHEST_REACH, 0, -CHEST_REACH), w.bed.offset(CHEST_REACH, 0, CHEST_REACH))) {
			ChestBlockEntity chest = chestAt(level, p.immutable(), Direction.NORTH, w);
			if (chest != null) {
				return chest;
			}
		}
		return null;
	}

	private static @Nullable ChestBlockEntity chestAt(ServerLevel level, BlockPos at, Direction facing, Wake w) {
		if (at.equals(w.spot) || !level.getBlockState(at).isAir()
			|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
			return null;
		}
		level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
		return level.getBlockEntity(at) instanceof ChestBlockEntity chest ? chest : null;
	}

	/** Into the bed, awake, if they're by it and it's free. */
	private static void lay(ServerPlayer player, Wake w) {
		if (player.isSleeping() || !player.isAlive() || player.blockPosition().distSqr(w.bed) > 16.0) {
			return;
		}
		BlockState state = player.level().getBlockState(w.bed);
		if (state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED)) {
			BedRest.lay(player, w.bed);
		}
	}

	private static void drop(UUID player) {
		Wake w = WAKES.remove(player);
		if (w != null) {
			w.grown.forEach(GREETERS::remove);
			w.young.forEach(GREETERS::remove);
		}
	}

	private static void tick(MinecraftServer server) {
		if (WAKES.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Wake>> it = WAKES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Wake> e = it.next();
			Wake w = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || w.seenAt < 0 && now - w.since > GIVE_UP_TICKS) {
				w.grown.forEach(GREETERS::remove);
				w.young.forEach(GREETERS::remove);
				it.remove();
				continue;
			}
			if (!w.stowed) {
				continue;
			}
			if (StoryGround.unseen(player)) {
				// (behind the loading screen: kept lying in the bed, whatever the arrival did to them)
				lay(player, w);
				continue;
			}
			if (w.seenAt < 0) {
				w.seenAt = now;
				w.nextAt = now + GREET_DELAY;
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EYES_TICKS, 0, false, false));
				if (w.grown.isEmpty() && w.young.isEmpty()) {
					player.sendSystemMessage(Component.translatable("story.jaymoskingdoms.farm_wake.alone").withStyle(ChatFormatting.GRAY));
					it.remove();
					continue;
				}
				GREETERS.addAll(w.grown);
				GREETERS.addAll(w.young);
			}
			ServerLevel level = player.level();
			if (now % STEER_EVERY == 0) {
				steer(level, player, w);
			}
			if (now < w.nextAt) {
				continue;
			}
			if (!say(level, player, w)) {
				w.grown.forEach(GREETERS::remove);
				w.young.forEach(GREETERS::remove);
				it.remove();
			}
		}
	}

	/** The family come over to the bed and look at whoever woke in it. */
	private static void steer(ServerLevel level, ServerPlayer player, Wake w) {
		for (List<UUID> ids : List.of(w.grown, w.young)) {
			for (UUID id : ids) {
				if (level.getEntity(id) instanceof PathfinderMob mob && mob.isAlive()) {
					if (mob.distanceTo(player) > GREET_REACH) {
						mob.getNavigation().moveTo(player, GREET_SPEED);
					} else {
						mob.getNavigation().stop();
					}
					mob.getLookControl().setLookAt(player);
				}
			}
		}
	}

	/** The next line of the greeting said, or false once it's all been said. */
	private static boolean say(ServerLevel level, ServerPlayer player, Wake w) {
		while (w.line < LINES.length) {
			int i = w.line++;
			Entity speaker = speaker(level, w, WHO[i]);
			if (speaker == null) {
				continue;
			}
			Component words = Component.translatable("story.jaymoskingdoms.farm_wake." + LINES[i]);
			Speech.sayTo(level, speaker, player, words, Speech.WHITE);
			if (ServerPlayNetworking.canSend(player, Speech.Said.TYPE)) {
				// (and in chat, for whoever looked away)
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), words)
					.withStyle(ChatFormatting.GRAY));
			}
			w.nextAt = level.getServer().getTickCount() + LINE_BASE_TICKS + (int)(words.getString().length() * LINE_PER_CHAR_TICKS);
			return true;
		}
		return false;
	}

	private static @Nullable Entity speaker(ServerLevel level, Wake w, int who) {
		if (who == CHILD) {
			return first(level, w.young, 0);
		}
		Entity grown = first(level, w.grown, who == B ? 1 : 0);
		if (grown == null && who == B) {
			grown = first(level, w.grown, 0);
		}
		// (no one grown about: the child says it all)
		return grown != null ? grown : first(level, w.young, 0);
	}

	/** The {@code skip}+1th of {@code ids} that's alive here, or null. */
	private static @Nullable Entity first(ServerLevel level, List<UUID> ids, int skip) {
		for (UUID id : ids) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive() && skip-- == 0) {
				return e;
			}
		}
		return null;
	}
}
