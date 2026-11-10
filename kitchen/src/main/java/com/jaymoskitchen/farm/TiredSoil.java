package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Tired fields (Andy's TFC Hardrock picks). Farmland keeps three nutrients, one for each kind of crop: grain, roots
 * (carrots, potatoes, beetroot) and the rest (stems, berries, anything else). Each ripe harvest wears the soil's nutrient
 * for that kind ({@link #WEAR}) and lets the other two recover a step, so rotating crops pays. Past {@link #TIRED} a crop
 * of that kind grows at half speed, and past {@link #SPENT} at a quarter. Fertilizer (bone meal or manure, Kitchen's
 * {@link FertilizerItem}) rests the soil at once. Kept per chunk ({@link #WORN}), only for farmland that has been worn.
 */
public final class TiredSoil {
	public enum Kind { GRAIN, ROOTS, OTHER }

	/** Wear one harvest adds, and the wear past which crops slow and slow more. */
	static final int WEAR = 1, TIRED = 3, SPENT = 6, MOST = 9;

	/** Each worn farmland's wear, by "packed position:kind". Saved with the chunk. */
	public static final AttachmentType<Map<String, Integer>> WORN = AttachmentRegistry.create(JaymosKitchen.id("tired_soil"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	private TiredSoil() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && !player.isCreative() && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
				&& server.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock) {
				harvested(server, pos.below(), kind(state));
			}
		});
	}

	static Kind kind(BlockState crop) {
		if (crop.getBlock() instanceof CarrotBlock || crop.getBlock() instanceof PotatoBlock || crop.getBlock() instanceof BeetrootBlock) {
			return Kind.ROOTS;
		}
		return crop.getBlock().getClass() == CropBlock.class ? Kind.GRAIN : Kind.OTHER;
	}

	private static String key(BlockPos soil, Kind kind) {
		return soil.asLong() + ":" + kind.ordinal();
	}

	static int wear(ServerLevel level, BlockPos soil, Kind kind) {
		Map<String, Integer> worn = level.getChunkAt(soil).getAttached(WORN);
		return worn == null ? 0 : worn.getOrDefault(key(soil, kind), 0);
	}

	static void harvested(ServerLevel level, BlockPos soil, Kind kind) {
		LevelChunk chunk = level.getChunkAt(soil);
		Map<String, Integer> worn = new HashMap<>(chunk.getAttachedOrElse(WORN, Map.of()));
		for (Kind k : Kind.values()) {
			String key = key(soil, k);
			int now = worn.getOrDefault(key, 0) + (k == kind ? WEAR : -1);
			if (now <= 0) {
				worn.remove(key);
			} else {
				worn.put(key, Math.min(MOST, now));
			}
		}
		save(chunk, worn);
	}

	/** Rests the soil at {@code soil} (fertilizer). */
	public static void rest(ServerLevel level, BlockPos soil) {
		LevelChunk chunk = level.getChunkAt(soil);
		Map<String, Integer> was = chunk.getAttached(WORN);
		if (was == null) {
			return;
		}
		Map<String, Integer> worn = new HashMap<>(was);
		for (Kind k : Kind.values()) {
			worn.remove(key(soil, k));
		}
		save(chunk, worn);
	}

	private static void save(LevelChunk chunk, Map<String, Integer> worn) {
		if (worn.isEmpty()) {
			chunk.removeAttached(WORN);
		} else {
			chunk.setAttached(WORN, Map.copyOf(worn));
		}
		chunk.markUnsaved();
	}

	/** This growth tick is skipped for tired soil (half the ticks when tired, three in four when spent). */
	public static boolean slows(BlockState crop, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!(crop.getBlock() instanceof CropBlock) || !(level.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock)) {
			return false;
		}
		int wear = wear(level, pos.below(), kind(crop));
		return wear > SPENT ? random.nextInt(4) != 0 : wear > TIRED && random.nextBoolean();
	}
}
