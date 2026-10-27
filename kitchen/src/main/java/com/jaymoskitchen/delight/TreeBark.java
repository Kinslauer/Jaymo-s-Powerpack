package com.jaymoskitchen.delight;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Farmer's Delight's tree bark, MIT; our own code.) Stripping a log with an axe sometimes peels off a piece of tree
 * bark (kindling, and a mulch for the compost heap). It sits beside the Kitchen's own bark for tanning: the two are
 * rolled apart, so one strip may give either, both or neither.
 */
public final class TreeBark {
	/** Chance a stripped log gives a piece of tree bark. */
	static final float BARK_CHANCE = 0.25F;

	/** Logs an axe was just used on, looked at again next tick: did it come off stripped? */
	private record Stripping(ServerLevel level, BlockPos pos, Block was) {
	}

	private static final List<Stripping> STRIPPING = new ArrayList<>();

	private TreeBark() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (player.getItemInHand(hand).is(ItemTags.AXES) && state.is(BlockTags.LOGS)
				&& STRIPPING.stream().noneMatch(s -> s.pos().equals(pos) && s.level() == server)) {
				// the axe does the stripping itself, after this
				STRIPPING.add(new Stripping(server, pos.immutable(), state.getBlock()));
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(TreeBark::tick);
	}

	/** Bark from the logs stripped last tick. */
	private static void tick(MinecraftServer server) {
		if (STRIPPING.isEmpty()) {
			return;
		}
		for (Stripping s : List.copyOf(STRIPPING)) {
			Block now = s.level().getBlockState(s.pos()).getBlock();
			if (now != s.was() && BuiltInRegistries.BLOCK.getKey(now).getPath().startsWith("stripped_")
				&& s.level().getRandom().nextFloat() < BARK_CHANCE) {
				Block.popResource(s.level(), s.pos(), new ItemStack(DelightFarm.TREE_BARK));
			}
		}
		STRIPPING.clear();
	}
}
