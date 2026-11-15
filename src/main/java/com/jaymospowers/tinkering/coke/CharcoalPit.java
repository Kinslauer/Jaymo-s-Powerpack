package com.jaymospowers.tinkering.coke;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The charcoal pit (after GregTech's charcoal pile igniter and TFC's log pile, our own code). Stack logs in a heap, cover
 * every face of it with earth (block tag {@code jaymospowers:charcoal_pit_cover}: dirt, grass, clay, mud and the like) but
 * one, and light a log through that gap with flint and steel or a fire charge. The lit log smoulders
 * ({@link SmoulderingLogBlock}) and after {@link #BURN_DAYS} days every log in the heap has become a charred log, which
 * breaks into charcoal. Air must stay out: open the heap anywhere else while it smoulders and it flares up and burns
 * like any wood; a heap that isn't covered when lit just catches fire.
 */
public final class CharcoalPit {
	/** In-game days a heap smoulders, the most logs one heap holds, and how often it's looked over (ticks). */
	public static final int BURN_DAYS = 2, MAX_LOGS = 128, CHECK_TICKS = 100;
	public static final long BURN_TICKS = 24000L * BURN_DAYS;
	/** Charcoal a charred log breaks into (its loot table, tools/gen_coke_and_steel.py CHARCOAL_PER_LOG, says the same). */
	public static final int CHARCOAL_PER_LOG = 2;
	/** What may cover a heap. */
	public static final TagKey<Block> COVER = TagKey.create(Registries.BLOCK, JaymosPowers.id("charcoal_pit_cover"));

	private CharcoalPit() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState clicked = level.getBlockState(pos);
			if (player.isSpectator() || !(held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) || !isLog(clicked)
				|| clicked.is(CokeAndSteel.SMOULDERING_LOG)) {
				return InteractionResult.PASS;
			}
			BlockPos gap = pos.relative(hit.getDirection());
			Set<BlockPos> heap = heap(level, pos);
			if (heap == null || heap.isEmpty() || !level.mayInteract(player, pos) || !player.mayUseItemAt(gap, hit.getDirection(), held)) {
				return InteractionResult.PASS;
			}
			for (BlockPos at : heap) {
				if (level.getBlockState(at).is(CokeAndSteel.SMOULDERING_LOG)) {
					// already lit
					return InteractionResult.PASS;
				}
			}
			List<BlockPos> open = openings(level, heap);
			if (open.size() != 1 || !open.getFirst().equals(gap)) {
				// not buried: it's only a woodpile, and lights like one
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				BlockState log = server.getBlockState(pos);
				server.setBlock(pos, CokeAndSteel.SMOULDERING_LOG.defaultBlockState(), Block.UPDATE_ALL);
				if (server.getBlockEntity(pos) instanceof SmoulderingLogEntity core) {
					core.light(log, server);
				}
				server.playSound(null, pos, held.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
					SoundSource.BLOCKS, 1.0F, 0.9F);
				if (held.is(Items.FIRE_CHARGE)) {
					held.consume(1, player);
				} else {
					held.hurtAndBreak(1, player, hand);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	static boolean isLog(BlockState state) {
		return state.is(net.minecraft.tags.BlockItemTags.LOGS_THAT_BURN.block()) || state.is(CokeAndSteel.SMOULDERING_LOG);
	}

	/**
	 * Every log joined to {@code start} face to face; null if some lie where the world isn't loaded (wait and look again),
	 * empty if there are more than {@link #MAX_LOGS} (no pit, or a broken one).
	 */
	static @Nullable Set<BlockPos> heap(Level level, BlockPos start) {
		Set<BlockPos> heap = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		heap.add(start.immutable());
		todo.add(start.immutable());
		while (!todo.isEmpty()) {
			BlockPos at = todo.poll();
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (!level.isLoaded(next)) {
					return null;
				}
				if (!heap.contains(next) && isLog(level.getBlockState(next))) {
					if (heap.size() >= MAX_LOGS) {
						return Set.of();
					}
					heap.add(next);
					todo.add(next);
				}
			}
		}
		return heap;
	}

	/** The blocks round the heap that let air in: anything beside a log that is neither a log nor earth. */
	static List<BlockPos> openings(Level level, Set<BlockPos> heap) {
		Set<BlockPos> open = new HashSet<>();
		for (BlockPos at : heap) {
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (!heap.contains(next) && !level.getBlockState(next).is(COVER)) {
					open.add(next);
				}
			}
		}
		return new ArrayList<>(open);
	}
}
