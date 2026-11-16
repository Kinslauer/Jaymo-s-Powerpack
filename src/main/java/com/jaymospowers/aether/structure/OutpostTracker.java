package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Loaded;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The outpost campfires a player has lit (kept on the player, across deaths). A player who dies with no bed or respawn anchor to
 * go back to wakes up at the nearest lit campfire that still stands.
 */
public final class OutpostTracker {
	// ---- tuning ----
	/** The most campfires one player keeps (the oldest is forgotten past this). */
	public static final int MAX = 32;
	/** On respawn, at most this many campfires whose chunks aren't in memory are read (each read loads one). */
	static final int UNLOADED_READS = 3;

	public static final AttachmentType<List<GlobalPos>> CAMPFIRES = AttachmentRegistry.create(JaymosPowers.id("outpost_campfires"),
		builder -> builder.persistent(GlobalPos.CODEC.listOf()).copyOnDeath());

	private OutpostTracker() {
	}

	/** Remembers a campfire; false if it was already known. */
	public static boolean add(ServerPlayer player, GlobalPos origin) {
		List<GlobalPos> list = new ArrayList<>(list(player));
		if (list.contains(origin)) {
			return false;
		}
		list.add(origin);
		while (list.size() > MAX) {
			list.remove(0);
		}
		player.setAttached(CAMPFIRES, List.copyOf(list));
		return true;
	}

	public static List<GlobalPos> list(ServerPlayer player) {
		List<GlobalPos> list = player.getAttached(CAMPFIRES);
		return list == null ? List.of() : list;
	}

	static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && !hasHome(newPlayer, oldPlayer)) {
				wakeAtCampfire(newPlayer, oldPlayer);
			}
		});
	}

	/** Whether the player has a bed or anchor (or a forced spawn) to respawn at. */
	private static boolean hasHome(ServerPlayer now, ServerPlayer before) {
		ServerPlayer.RespawnConfig config = before.getRespawnConfig();
		if (config == null) {
			return false;
		}
		if (config.forced()) {
			return true;
		}
		ServerLevel level = now.level().getServer().getLevel(config.respawnData().dimension());
		if (level == null) {
			return false;
		}
		BlockState state = level.getBlockState(config.respawnData().pos());
		return state.is(BlockTags.BEDS) || state.getBlock() instanceof RespawnAnchorBlock;
	}

	/** Puts the player beside the nearest campfire that still stands (in the place they died if there is one, else any). */
	private static void wakeAtCampfire(ServerPlayer player, ServerPlayer before) {
		List<GlobalPos> list = new ArrayList<>(list(player));
		if (list.isEmpty()) {
			return;
		}
		var server = player.level().getServer();
		// nearest first, and stop at the first that still stands: each campfire's chunk not in memory is a blocking load on
		// the server thread, so at most UNLOADED_READS of those are read (the rest are kept, not dropped)
		List<GlobalPos> byScore = new ArrayList<>(list);
		byScore.sort(java.util.Comparator.comparingDouble(at -> score(at, before)));
		GlobalPos best = null;
		int unloadedReads = 0;
		boolean changed = false;
		for (GlobalPos at : byScore) {
			ServerLevel level = server.getLevel(at.dimension());
			if (level != null && !Loaded.made(level, at.pos())) {
				if (unloadedReads >= UNLOADED_READS) {
					continue;
				}
				unloadedReads++;
			}
			if (level == null || !(level.getBlockState(at.pos()).getBlock() instanceof OutpostCampfireBlock)) {
				list.remove(at);
				changed = true;
				continue;
			}
			best = at;
			break;
		}
		if (changed) {
			player.setAttached(CAMPFIRES, List.copyOf(list));
		}
		if (best == null) {
			return;
		}
		ServerLevel level = server.getLevel(best.dimension());
		BlockPos spot = standingSpot(level, best.pos());
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, false);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.outpost.respawn"));
	}

	/** Distance from where the player died; another dimension's campfires come after every one in this dimension. */
	private static double score(GlobalPos at, ServerPlayer before) {
		double d = at.pos().distSqr(before.blockPosition());
		return at.dimension().equals(before.level().dimension()) ? d : d + 1.0E15;
	}

	/** A free spot with solid ground just beside the 2x2 campfire whose north-west part is {@code origin}. */
	private static BlockPos standingSpot(ServerLevel level, BlockPos origin) {
		for (int r = 1; r <= 3; r++) {
			for (int dx = -r; dx <= 1 + r; dx++) {
				for (int dz = -r; dz <= 1 + r; dz++) {
					if (dx > -r && dx < 1 + r && dz > -r && dz < 1 + r) {
						continue;
					}
					for (int dy = 2; dy >= -2; dy--) {
						BlockPos p = origin.offset(dx, dy, dz);
						if (!Loaded.made(level, p)) {
							continue;
						}
						if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolid()) {
							return p;
						}
					}
				}
			}
		}
		return origin.above();
	}
}
