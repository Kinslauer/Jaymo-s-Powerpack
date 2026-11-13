package com.jaymoskingdoms.traps;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Nature's traps (Andy's pick F3). Thin ice over cold lakes is Jaymo's Powers' ({@code frost/FrostFeatures#LakeCrust}); here are
 * the other two kinds:
 * <ul>
 * <li><b>Quicksand</b> in swamps and deserts: pits of the pack's quicksand ({@link com.jaymoskingdoms.creatures.Creatures#QUICKSAND}) laid in the
 * sand or mud, a few blocks across and four deep, looking like the ground round them. A patient eye finds them
 * ({@link com.jaymoskingdoms.Notice}, as for the bog lights'). A place is rolled once for good, like a haunt.</li>
 * <li><b>Three cave moulds</b>, each a living thing that follows the three rules: {@link ColdMouldBlock} (drinks warmth, grows
 * toward fire, dies of cold), {@link SporeMouldBlock} (a cloud you hold your breath past, feeds on damp) and
 * {@link SlimeMouldBlock} (eats wood and metal, burned off or dissolved with vinegar). Each lives on what it feeds on and dies
 * back when it runs out; none hunts anyone, and nobody is punished for leaving one alone.</li>
 * </ul>
 */
public final class Traps {
	// ---- tuning ----
	/** Looked at this often (ticks). */
	static final int EVERY = 200;
	/** A cell (blocks across) is rolled once; the chance it holds a cave mould, a pit of quicksand. */
	static final int CELL = 48;
	static final float MOULD = 0.1F, PIT = 0.3F;
	/** A mould patch: this many blocks to start with (least, most). */
	static final int PATCH_MIN = 2, PATCH_MAX = 5;
	/** How far from the player a cave spot is tried (blocks), and how many tries. */
	static final int CAVE_REACH = 30, CAVE_TRIES = 40;
	/** A pit of quicksand: half-width and depth; how far from the player. */
	static final int PIT_HALF = 1, PIT_DEPTH = 4, PIT_MIN = 24, PIT_MAX = 56;

	public static Block COLD_MOULD, SPORE_MOULD, SLIME_MOULD;
	public static Item COLD_MOULD_ITEM, SPORE_MOULD_ITEM, SLIME_MOULD_ITEM;
	/** A bottle of vinegar: sours and dissolves the slime mould. */
	public static Item VINEGAR;

	static final AttachmentType<List<Long>> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("traps_rolled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static @Nullable Set<Long> rolled;

	private Traps() {
	}

	public static void register() {
		COLD_MOULD = block("cold_mould", new ColdMouldBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.2F).sound(SoundType.MOSS)
			.randomTicks().setId(key("cold_mould"))));
		SPORE_MOULD = block("spore_mould", new SporeMouldBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.8F).sound(SoundType.MOSS)
			.randomTicks().setId(key("spore_mould"))));
		SLIME_MOULD = block("slime_mould", new SlimeMouldBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.8F).sound(SoundType.SLIME_BLOCK)
			.randomTicks().setId(key("slime_mould"))));
		COLD_MOULD_ITEM = blockItem("cold_mould", COLD_MOULD);
		SPORE_MOULD_ITEM = blockItem("spore_mould", SPORE_MOULD);
		SLIME_MOULD_ITEM = blockItem("slime_mould", SLIME_MOULD);
		ResourceKey<Item> vinegar = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("vinegar"));
		VINEGAR = Registry.register(BuiltInRegistries.ITEM, vinegar, new Item(new Item.Properties().setId(vinegar).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(COLD_MOULD_ITEM);
			output.accept(SPORE_MOULD_ITEM);
			output.accept(SLIME_MOULD_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(VINEGAR));
		ServerTickEvents.END_SERVER_TICK.register(Traps::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> rolled = null);
		keepsFoodCold();
	}

	private static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
	}

	private static Block block(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, key(name), block);
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** A cold mould in a cellar keeps Jaymo's Kitchen's food cold (stewardship: a patch kept for it is worth having). */
	private static void keepsFoodCold() {
		try {
			BiPredicate<ServerLevel, BlockPos> cold = (level, pos) -> {
				for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
					if (level.getBlockState(p).is(COLD_MOULD)) {
						return true;
					}
				}
				return false;
			};
			Class.forName("com.jaymoskitchen.fresh.Freshness").getMethod("addCold", BiPredicate.class).invoke(null, cold);
		} catch (ReflectiveOperationException | LinkageError e) {
			// (no Kitchen, or an older one: food is kept cold by the old means only)
		}
	}

	private static Set<Long> rolled(ServerLevel level) {
		if (rolled == null) {
			rolled = new HashSet<>(level.getAttachedOrElse(ROLLED, List.<Long>of()));
		}
		return rolled;
	}

	/** Marks {@code key} rolled; false if it already was. */
	private static boolean roll(ServerLevel level, long key) {
		Set<Long> set = rolled(level);
		if (!set.add(key)) {
			return false;
		}
		level.setAttached(ROLLED, new ArrayList<>(set));
		return true;
	}

	private static long key(int x, int y, int z, int kind) {
		return ((((long) x) << 40) ^ (((long) z & 0xFFFFFL) << 16) ^ ((long) (y & 0xFF) << 4)) * 8L + kind;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			try {
				moulds(level, player);
				pits(level, player);
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("traps scan failed", e);
			}
		}
	}

	// ---- cave moulds ------------------------------------------------------------------------------------------------

	private static void moulds(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		if (player.getY() > 55 || level.canSeeSky(player.blockPosition())) {
			return;
		}
		for (int i = 0; i < CAVE_TRIES; i++) {
			BlockPos at = player.blockPosition().offset(random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH, random.nextInt(21) - 10,
				random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH);
			if (!level.isLoaded(at) || at.distSqr(player.blockPosition()) < 12 * 12 || !Surfaces.inCave(level, at)) {
				continue;
			}
			long key = key(Math.floorDiv(at.getX(), CELL), Math.floorDiv(at.getY(), 24), Math.floorDiv(at.getZ(), CELL), 0);
			if (!roll(level, key)) {
				return;
			}
			if (random.nextFloat() >= MOULD) {
				return;
			}
			// which: slime on a ceiling, spores where it is damp, else the cold
			Block which = Surfaces.ceiling(level, at) && random.nextInt(3) == 0 ? SLIME_MOULD
				: SporeMouldBlock.damp(level, at) ? SPORE_MOULD : random.nextBoolean() ? COLD_MOULD : SLIME_MOULD;
			boolean ceiling = which == SLIME_MOULD;
			if (ceiling && !Surfaces.ceiling(level, at)) {
				which = COLD_MOULD;
				ceiling = false;
			}
			grow(level, at, which, ceiling, random);
			return;
		}
	}

	/** A patch of {@code which} starting at {@code at}, spread over the neighbouring surface. */
	private static void grow(ServerLevel level, BlockPos at, Block which, boolean ceiling, RandomSource random) {
		BlockState state = which.defaultBlockState();
		int count = PATCH_MIN + random.nextInt(PATCH_MAX - PATCH_MIN + 1);
		BlockPos p = at;
		for (int i = 0; i < count && p != null; i++) {
			level.setBlock(p, state, Block.UPDATE_ALL);
			p = Surfaces.neighbour(level, p, null, random, ceiling);
		}
	}

	// ---- quicksand ----------------------------------------------------------------------------------------------------

	private static void pits(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double a = random.nextDouble() * Math.PI * 2;
		int d = PIT_MIN + random.nextInt(PIT_MAX - PIT_MIN + 1);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)));
		if (!level.isLoaded(top)) {
			return;
		}
		boolean desert = level.getBiome(top).is(Biomes.DESERT);
		boolean swamp = level.getBiome(top).is(Biomes.SWAMP) || level.getBiome(top).is(Biomes.MANGROVE_SWAMP);
		if (!desert && !swamp) {
			return;
		}
		if (!roll(level, key(Math.floorDiv(top.getX(), CELL), 0, Math.floorDiv(top.getZ(), CELL), 1)) || random.nextFloat() >= PIT) {
			return;
		}
		BlockPos surface = top.below();
		if (!soft(level.getBlockState(surface), desert) || !level.getFluidState(top).isEmpty()) {
			return;
		}
		for (BlockPos p : BlockPos.betweenClosed(surface.offset(-PIT_HALF, 0, -PIT_HALF), surface.offset(PIT_HALF, 0, PIT_HALF))) {
			if (!soft(level.getBlockState(p), desert) || !level.getBlockState(p.above()).isAir() && !level.getBlockState(p.above()).canBeReplaced()) {
				return;
			}
		}
		if (level.getBlockState(surface.below(PIT_DEPTH + 1)).isAir()) {
			return;
		}
		for (BlockPos p : BlockPos.betweenClosed(surface.offset(-PIT_HALF, -(PIT_DEPTH - 1), -PIT_HALF), surface.offset(PIT_HALF, 0, PIT_HALF))) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && s.getFluidState().isEmpty()) {
				level.setBlock(p, com.jaymoskingdoms.creatures.Creatures.QUICKSAND.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static boolean soft(BlockState s, boolean desert) {
		return s.is(BlockTags.SAND) || !desert && (s.is(BlockTags.DIRT) || s.is(Blocks.MUD));
	}
}
