package com.jaymospowers.tinkering.dangers;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.jaymospowers.tinkering.smeltery.SearedBricksBlock;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (GTNH picks, the forge's dangers) Bricks that crack with use. A smeltery's seared bricks crack in place
 * ({@link SearedBricksBlock#CRACKED}); a kiln's bricks crack into their cracked kind (vanilla's cracked nether bricks, and
 * ours for bricks, mud bricks and red nether bricks), which still wall a kiln but let its heat out. A hammer
 * ({@code #jaymospowers:hammers}) used on a cracked brick with grout in hand or pack mends it, a grout a brick.
 */
public final class Cracks {
	public static Block CRACKED_BRICKS, CRACKED_MUD_BRICKS, CRACKED_RED_NETHER_BRICKS;
	/** Each kiln brick and what it cracks into, in order (for JEI). */
	private static final Map<Block, Block> CRACKED = new LinkedHashMap<>();
	private static final Map<Block, Block> WHOLE = new IdentityHashMap<>();

	private Cracks() {
	}

	static void register() {
		CRACKED_BRICKS = block("cracked_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
		CRACKED_MUD_BRICKS = block("cracked_mud_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS));
		CRACKED_RED_NETHER_BRICKS = block("cracked_red_nether_bricks", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.NETHER_BRICKS));
		pair(Blocks.BRICKS, CRACKED_BRICKS);
		pair(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
		pair(Blocks.RED_NETHER_BRICKS, CRACKED_RED_NETHER_BRICKS);
		pair(Blocks.MUD_BRICKS, CRACKED_MUD_BRICKS);
	}

	private static void pair(Block whole, Block cracked) {
		CRACKED.put(whole, cracked);
		WHOLE.put(cracked, whole);
	}

	private static Block block(String id, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** Our own cracked bricks, for the creative tab. */
	static List<Block> ours() {
		return List.of(CRACKED_BRICKS, CRACKED_MUD_BRICKS, CRACKED_RED_NETHER_BRICKS);
	}

	/** Whether this is a cracked seared brick or a cracked kiln brick. */
	public static boolean cracked(BlockState state) {
		return state.is(Tinkering.SEARED_BRICKS) ? state.getValue(SearedBricksBlock.CRACKED) : WHOLE.containsKey(state.getBlock());
	}

	/** A whole seared or kiln brick's cracked state, or null if it can't crack (or is cracked already). */
	public static @Nullable BlockState crackedOf(BlockState state) {
		if (state.is(Tinkering.SEARED_BRICKS)) {
			return state.getValue(SearedBricksBlock.CRACKED) ? null : state.setValue(SearedBricksBlock.CRACKED, true);
		}
		Block cracked = CRACKED.get(state.getBlock());
		return cracked == null ? null : cracked.defaultBlockState();
	}

	/** A cracked brick mended, or null if it isn't cracked. */
	public static @Nullable BlockState mendedOf(BlockState state) {
		if (state.is(Tinkering.SEARED_BRICKS)) {
			return state.getValue(SearedBricksBlock.CRACKED) ? state.setValue(SearedBricksBlock.CRACKED, false) : null;
		}
		Block whole = WHOLE.get(state.getBlock());
		return whole == null ? null : whole.defaultBlockState();
	}

	/** Cracks the brick at {@code pos}, with the sound and dust of it; false if it can't crack. */
	public static boolean crack(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState cracked = crackedOf(state);
		if (cracked == null) {
			return false;
		}
		level.setBlock(pos, cracked, Block.UPDATE_ALL);
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		level.playSound(null, pos, SoundEvents.DECORATED_POT_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F + level.getRandom().nextFloat() * 0.2F);
		return true;
	}

	/** One of the kiln's brick sides (and its top) at random cracks; false if none could. */
	public static boolean crackKiln(ServerLevel level, BlockPos space) {
		List<BlockPos> whole = new ArrayList<>();
		for (Direction side : Direction.values()) {
			BlockPos wall = space.relative(side);
			if (side != Direction.DOWN && level.getBlockState(wall).is(Kiln.KILN_WALLS) && crackedOf(level.getBlockState(wall)) != null) {
				whole.add(wall);
			}
		}
		return !whole.isEmpty() && crack(level, whole.get(level.getRandom().nextInt(whole.size())));
	}

	/** How many of the kiln's walls (and its top) are cracked. */
	public static int crackedWalls(Level level, BlockPos space) {
		int n = 0;
		for (Direction side : Direction.values()) {
			if (side != Direction.DOWN && cracked(level.getBlockState(space.relative(side)))) {
				n++;
			}
		}
		return n;
	}

	/** For JEI: each cracked brick (as an item) and the brick it mends into. */
	public static Map<ItemStack, ItemStack> mendings() {
		Map<ItemStack, ItemStack> out = new LinkedHashMap<>();
		ItemStack searedCracked = new ItemStack(Tinkering.SEARED_BRICKS);
		searedCracked.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SearedBricksBlock.CRACKED, true));
		out.put(searedCracked, new ItemStack(Tinkering.SEARED_BRICKS));
		CRACKED.forEach((whole, cracked) -> out.put(new ItemStack(cracked), new ItemStack(whole)));
		return out;
	}
}
