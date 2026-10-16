package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * (0.5.46) Who placed a block, kept on its chunk: a player breaking (or rummaging in) a block they placed themselves
 * commits no crime, even in a settlement ({@link Theft}). Remembered with the block it was, so a block the settlement
 * later puts in the same spot isn't taken for the player's.
 */
public final class Placed {
	/** A placed block: who, and which block. */
	record Mark(UUID placer, String block) {
		static final Codec<Mark> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("placer").forGetter(Mark::placer),
				Codec.STRING.fieldOf("block").forGetter(Mark::block))
			.apply(i, Mark::new));
	}

	/** Per chunk: packed block position → its mark. Saved. */
	static final AttachmentType<Map<String, Mark>> MARKS = AttachmentRegistry.create(JaymosKingdoms.id("placed_blocks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Mark.CODEC)));

	private Placed() {
	}

	/** {@code player} just placed {@code state} at {@code pos} (from the block item mixin). */
	public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, Mark> marks = new HashMap<>(chunk.getAttachedOrElse(MARKS, Map.of()));
		marks.put(Long.toString(pos.asLong()), new Mark(player.getUUID(), id(state.getBlock())));
		chunk.setAttached(MARKS, marks);
	}

	/** The block at {@code pos} (now {@code state}, or as it was just before it broke) was placed by {@code player}. */
	public static boolean byPlayer(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (mine(player, level, pos, state)) {
			return true;
		}
		// a bed is placed from its foot: its head belongs to whoever placed the foot
		if (state.getBlock() instanceof AbstractBedBlock && state.hasProperty(AbstractBedBlock.PART) && state.getValue(AbstractBedBlock.PART) == BedPart.HEAD) {
			BlockPos foot = pos.relative(AbstractBedBlock.getConnectedDirection(state));
			return mine(player, level, foot, state);
		}
		return false;
	}

	/** (0.7.84) Some player placed the block at {@code pos}. */
	public static boolean any(ServerLevel level, BlockPos pos) {
		Map<String, Mark> marks = level.getChunkAt(pos).getAttached(MARKS);
		return marks != null && marks.containsKey(Long.toString(pos.asLong()));
	}

	/** Forget the mark at {@code pos} (the block's gone). */
	public static void forget(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, Mark> marks = chunk.getAttached(MARKS);
		if (marks != null && marks.containsKey(Long.toString(pos.asLong()))) {
			Map<String, Mark> left = new HashMap<>(marks);
			left.remove(Long.toString(pos.asLong()));
			if (left.isEmpty()) {
				chunk.removeAttached(MARKS);
			} else {
				chunk.setAttached(MARKS, left);
			}
		}
	}

	private static boolean mine(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		Mark mark = level.getChunkAt(pos).getAttachedOrElse(MARKS, Map.of()).get(Long.toString(pos.asLong()));
		return mark != null && mark.placer().equals(player.getUUID()) && mark.block().equals(id(state.getBlock()));
	}

	private static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}
}
