package com.jaymoskingdoms;

import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.201) Weathering, our own take on Immersive Weathering (LGPL, 1.20.1 only, so nothing is taken from it): the
 * world ages around players, villages too (Andy: "upkeep is already something builders should be doing", see
 * {@link Upkeep}). Every second a few blocks near each player are looked at ({@link #SAMPLES} within {@link #RADIUS},
 * from the surface down {@link #DEPTH}), and an exposed one may change:
 * <ul>
 * <li>Cobblestone and stone bricks (and their stairs, slabs and walls) grow moss where it's wet: by water, beside moss,
 * or out in the rain in a land that has it. Never in deserts.</li>
 * <li>Bricks crack (stone, deepslate, nether and blackstone bricks, deepslate tiles): by frost and thaw under the open
 * sky in cold lands or winter, and beside fire or lava; a little anywhere.</li>
 * <li>Bare dirt in the rain turns to mud, and mud in the sun dries back to dirt.</li>
 * <li>Leaves drop litter on the grass under trees in autumn (a little the rest of the year), and it rots away again out
 * of autumn.</li>
 * <li>Grass in the open grows tufts in spring and summer.</li>
 * </ul>
 * Copper already weathers by itself. Ops switch it off with {@code jaymoskingdoms:weathering}.
 */
public final class Weathering {
	/** Blocks looked at a second for each player, how far out (blocks), and how far below the surface. */
	static final int SAMPLES = 24, RADIUS = 40, DEPTH = 16;
	/** Chances a looked-at block that could change does: moss (doubled when wet beside water or moss). */
	static final float MOSS = 0.2F;
	/** Cracking: frost (cold land or winter, open sky), heat (fire or lava beside), and anywhere. */
	static final float FROST_CRACK = 0.15F, HEAT_CRACK = 0.3F, CRACK = 0.02F;
	/** Lands this cold at their base count as frosty; this hot are too dry for moss. */
	static final float FROSTY = 0.3F, DRY = 1.5F;
	static final float MUD = 0.4F, DRY_MUD = 0.3F;
	/** Leaf litter under trees: in autumn, the rest of the year, and rotting away out of autumn. */
	static final float LITTER_AUTUMN = 0.5F, LITTER = 0.04F, ROT = 0.15F;
	/** How far up a tree's leaves can be over the grass. */
	static final int CANOPY = 6;
	static final float TUFTS = 0.05F;
	static final int TUFT_LIGHT = 9;

	public static GameRule<Boolean> WEATHERING;

	/** What moss turns each block into. */
	private static final Map<Block, Block> MOSSY = Map.of(
		Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
		Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
		Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB,
		Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL,
		Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
		Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
		Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB,
		Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL);
	/** What cracking turns each block into. */
	private static final Map<Block, Block> CRACKED = Map.of(
		Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
		Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
		Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
		Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
		Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);

	private Weathering() {
	}

	static void register() {
		WEATHERING = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.UPDATES).buildAndRegister(JaymosKingdoms.id("weathering"));
		ServerTickEvents.END_SERVER_TICK.register(Weathering::tick);
	}

	/** The block weathering wears {@code state} back from (scrubbed, mended, swept), or null if it's not weathered. */
	static @Nullable BlockState undone(BlockState state) {
		for (Map.Entry<Block, Block> e : MOSSY.entrySet()) {
			if (state.is(e.getValue())) {
				return e.getKey().withPropertiesOf(state);
			}
		}
		for (Map.Entry<Block, Block> e : CRACKED.entrySet()) {
			if (state.is(e.getValue())) {
				return e.getKey().defaultBlockState();
			}
		}
		if (state.is(Blocks.MUD)) {
			return Blocks.DIRT.defaultBlockState();
		}
		if (state.is(Blocks.LEAF_LITTER)) {
			return Blocks.AIR.defaultBlockState();
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (player.isSpectator() || !level.getGameRules().get(WEATHERING)) {
				continue;
			}
			RandomSource random = level.getRandom();
			for (int i = 0; i < SAMPLES; i++) {
				int x = player.getBlockX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
				int z = player.getBlockZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos at = new BlockPos(x, top - random.nextInt(DEPTH), z);
				if (level.isPositionEntityTicking(at)) {
					weather(level, at, random);
				}
			}
		}
	}

	/** One looked-at block: maybe it ages. */
	static void weather(ServerLevel level, BlockPos at, RandomSource random) {
		BlockState state = level.getBlockState(at);
		if (state.isAir() || !exposed(level, at)) {
			return;
		}
		Holder<Biome> biome = level.getBiome(at);
		float base = biome.value().getBaseTemperature();
		String season = KitchenCompat.season(level);
		BlockState next = null;
		Block mossy = MOSSY.get(state.getBlock());
		Block cracked = CRACKED.get(state.getBlock());
		if (mossy != null && base < DRY && biome.value().hasPrecipitation()) {
			boolean wet = touches(level, at, true);
			boolean rained = level.canSeeSky(at.above()) && level.isRainingAt(at.above());
			if ((wet || rained) && random.nextFloat() < (wet ? MOSS * 2.0F : MOSS)) {
				next = mossy.withPropertiesOf(state);
			}
		}
		if (next == null && cracked != null) {
			boolean frosty = (base < FROSTY || "winter".equals(season)) && level.canSeeSky(at.above());
			float chance = touches(level, at, false) ? HEAT_CRACK : frosty ? FROST_CRACK : CRACK;
			if (random.nextFloat() < chance) {
				next = cracked.defaultBlockState();
			}
		}
		if (next == null && state.is(Blocks.DIRT) && level.getBlockState(at.above()).isAir() && level.isRainingAt(at.above())) {
			if (random.nextFloat() < MUD) {
				next = Blocks.MUD.defaultBlockState();
			}
		} else if (next == null && state.is(Blocks.MUD) && level.canSeeSky(at.above()) && !level.isRaining() && level.isBrightOutside()) {
			if (random.nextFloat() < DRY_MUD) {
				next = Blocks.DIRT.defaultBlockState();
			}
		}
		if (next == null && state.is(Blocks.LEAF_LITTER) && season != null && !"autumn".equals(season) && random.nextFloat() < ROT) {
			next = Blocks.AIR.defaultBlockState();
		}
		if (next == null && state.is(Blocks.GRASS_BLOCK)) {
			BlockPos up = at.above();
			if (level.getBlockState(up).isAir()) {
				if (underLeaves(level, up)) {
					float chance = "autumn".equals(season) ? LITTER_AUTUMN : LITTER;
					if (random.nextFloat() < chance) {
						BlockState litter = Blocks.LEAF_LITTER.defaultBlockState()
							.setValue(LeafLitterBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
							.setValue(LeafLitterBlock.AMOUNT, 1 + random.nextInt(4));
						if (litter.canSurvive(level, up)) {
							place(level, up, litter);
						}
					}
				} else if (level.canSeeSky(up) && base < DRY && level.getMaxLocalRawBrightness(up) >= TUFT_LIGHT
					&& ("spring".equals(season) || "summer".equals(season) || season == null) && random.nextFloat() < TUFTS) {
					// tufts aren't anyone's upkeep: placed straight, not noted
					level.setBlock(up, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			return;
		}
		if (next != null) {
			place(level, at, next);
		}
	}

	/** Change the block; in a settlement, its builders note it for upkeep. */
	private static void place(ServerLevel level, BlockPos at, BlockState next) {
		level.setBlock(at, next, Block.UPDATE_ALL);
		if (!next.isAir()) {
			Upkeep.note(level, at);
		}
	}

	/** Open to the air (or water) on some side. */
	private static boolean exposed(ServerLevel level, BlockPos at) {
		for (Direction d : Direction.values()) {
			BlockState side = level.getBlockState(at.relative(d));
			if (side.isAir() || !side.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Beside water or moss ({@code wet}), or beside fire or lava. */
	private static boolean touches(ServerLevel level, BlockPos at, boolean wet) {
		for (Direction d : Direction.values()) {
			BlockPos side = at.relative(d);
			BlockState s = level.getBlockState(side);
			if (wet ? s.getFluidState().is(FluidTags.WATER) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MOSSY_COBBLESTONE)
				|| s.is(Blocks.MOSSY_STONE_BRICKS)
				: s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.CAMPFIRES)) {
				return true;
			}
		}
		return false;
	}

	private static boolean underLeaves(ServerLevel level, BlockPos at) {
		for (int dy = 1; dy <= CANOPY; dy++) {
			BlockState s = level.getBlockState(at.above(dy));
			if (s.is(BlockTags.LEAVES)) {
				return true;
			}
			if (!s.isAir()) {
				return false;
			}
		}
		return false;
	}
}
