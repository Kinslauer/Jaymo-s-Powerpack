package com.jaymoskitchen.cooking;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Which slabs players have put down (kept per chunk): only those work as prep boards, so a village's or a ruin's
 * slabs don't. Recorded by {@code BlockMixin} when a player places a slab.
 */
public final class PlacedSlabs {
	private static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(JaymosKitchen.id("placed_slabs"),
		builder -> builder.persistent(Codec.LONG.listOf()).initializer(List::of));

	private PlacedSlabs() {
	}

	/** A single (not double) slab. */
	public static boolean isHalfSlab(BlockState state) {
		return state.getBlock() instanceof SlabBlock && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
	}

	public static void placed(Level level, BlockPos pos) {
		AttachmentTarget chunk = (AttachmentTarget) level.getChunkAt(pos);
		List<Long> placed = new ArrayList<>(chunk.getAttachedOrCreate(PLACED));
		if (!placed.contains(pos.asLong())) {
			placed.add(pos.asLong());
			chunk.setAttached(PLACED, List.copyOf(placed));
		}
	}

	/** A half slab a player put down (server side; forgets slabs that have since gone). */
	public static boolean isPlayerSlab(Level level, BlockPos pos, BlockState state) {
		AttachmentTarget chunk = (AttachmentTarget) level.getChunkAt(pos);
		List<Long> placed = chunk.getAttachedOrCreate(PLACED);
		if (!placed.contains(pos.asLong())) {
			return false;
		}
		if (!(state.getBlock() instanceof SlabBlock)) {
			List<Long> pruned = new ArrayList<>(placed);
			pruned.remove(pos.asLong());
			chunk.setAttached(PLACED, List.copyOf(pruned));
			return false;
		}
		return isHalfSlab(state);
	}
}
