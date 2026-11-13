package com.jaymospowers.tundra;

import com.jaymospowers.compat.KingdomsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * How sprouted glow lichen lives. By day, in the open, it either breaks the rock it clings to down one step (rock to
 * gravel, gravel to sand or coarse dirt, coarse dirt to dirt) or seeds a plain glow lichen onto some other breakable
 * rock nearby. When there is nothing left to break within reach it stops: the colony runs out of food. A crowd of
 * lichen stops seeding too, so a colony can't carpet a chunk. Land a Kingdoms faction holds ({@link KingdomsCompat#claimed})
 * is never eaten or seeded. What counts as rock is the block tags
 * {@code jaymospowers:lichen/*}; ores and blocks that hold something (a block entity) are never touched.
 *
 * <p>Also the three things a player's hand does to lichen: bone meal sprouts vanilla glow lichen, shears snip vanilla
 * lichen into {@link Tundra#SNIPPED_GLOW_LICHEN} (which never spreads), and shears quiet a sprouted one.
 */
public final class LichenLife {
	// ---- tuning ----
	/** Sky light the lichen needs at its own block (and at the spot it seeds). */
	public static final int SUN_LIGHT = 9;
	/** One random tick in this many does anything. */
	public static final int ACT_CHANCE = 4;
	/** How far it looks for rock to seed, and for other lichen. */
	public static final int SPREAD_RANGE = 8;
	/** With this many lichen of any kind within SPREAD_RANGE, it seeds no more. */
	public static final int MAX_NEAR = 12;
	/** Share of broken gravel that becomes sand; the rest becomes coarse dirt. */
	public static final float SAND_SHARE = 0.6F;
	/** Faces looked at for rock to eat: the floor first. */
	private static final Direction[] FACES = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

	private LichenLife() {
	}

	/** A random tick of a sprouted, unsnipped lichen. */
	static void tick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (!level.isBrightOutside() || level.getBrightness(LightLayer.SKY, pos) < SUN_LIGHT || random.nextInt(ACT_CHANCE) != 0) {
			return;
		}
		// land a kingdom holds is left as it is: the lichen neither eats its rock nor seeds onto it
		if (KingdomsCompat.claimed(level, pos)) {
			return;
		}
		BlockPos host = host(level, pos, state);
		if (host != null && random.nextBoolean()) {
			if (!KingdomsCompat.claimed(level, host)) {
				breakDown(level, host, random);
			}
		} else {
			seed(level, pos, random);
		}
	}

	/** The breakable block this lichen clings to (the one under it before any beside or over it), or null. */
	private static BlockPos host(ServerLevel level, BlockPos pos, BlockState state) {
		for (Direction face : FACES) {
			if (MultifaceBlock.hasFace(state, face) && breakable(level.getBlockState(pos.relative(face)))) {
				return pos.relative(face);
			}
		}
		return null;
	}

	static boolean breakable(BlockState state) {
		return state.is(Tundra.LICHEN_BREAKABLE) && !state.is(BlockTags.ORES) && !state.is(Tundra.BANDED_IRON_ORE) && !state.hasBlockEntity();
	}

	/** One step down: rock to gravel, gravel to sand or coarse dirt, coarse dirt to dirt. */
	private static void breakDown(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState old = level.getBlockState(pos);
		Block next = old.is(Tundra.LICHEN_TO_GRAVEL) ? Blocks.GRAVEL
			: old.is(Tundra.LICHEN_TO_SOIL) ? (random.nextFloat() < SAND_SHARE ? Blocks.SAND : Blocks.COARSE_DIRT)
			: old.is(Tundra.LICHEN_TO_DIRT) ? Blocks.DIRT : null;
		if (next == null) {
			return;
		}
		level.setBlockAndUpdate(pos, next.defaultBlockState());
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(old));
	}

	/**
	 * Plain glow lichen onto the top of one breakable block within reach that has air and daylight over it; nothing if
	 * the neighbourhood is already crowded with lichen, or has no such block left.
	 */
	private static void seed(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos low = pos.offset(-SPREAD_RANGE, -SPREAD_RANGE, -SPREAD_RANGE), high = pos.offset(SPREAD_RANGE, SPREAD_RANGE, SPREAD_RANGE);
		if (!level.hasChunksAt(low, high)) {
			return;
		}
		int lichen = 0, spots = 0;
		BlockPos chosen = null;
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (BlockPos spot : BlockPos.betweenClosed(low, high)) {
			BlockState there = level.getBlockState(spot);
			if (there.is(Tundra.GLOW_LICHENS)) {
				if (++lichen >= MAX_NEAR) {
					return;
				}
			} else if (breakable(there)) {
				above.setWithOffset(spot, Direction.UP);
				// each fitting spot has the same chance of being the one kept
				if (level.getBlockState(above).isAir() && level.getBrightness(LightLayer.SKY, above) >= SUN_LIGHT && random.nextInt(++spots) == 0) {
					chosen = above.immutable();
				}
			}
		}
		if (chosen != null && !KingdomsCompat.claimed(level, chosen)) {
			level.setBlockAndUpdate(chosen, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
			level.gameEvent(null, GameEvent.BLOCK_PLACE, chosen);
		}
	}

	/** Bone meal or shears on a lichen (see the class note); PASS for anything else. */
	static InteractionResult use(Player player, Level level, InteractionHand hand, BlockPos pos) {
		ItemStack stack = player.getItemInHand(hand);
		BlockState state = level.getBlockState(pos);
		boolean shears = stack.is(Items.SHEARS) || stack.getItem() instanceof ShearsItem;
		BlockState result;
		if (state.is(Blocks.GLOW_LICHEN) && stack.is(Items.BONE_MEAL)) {
			result = sameFaces(state, Tundra.SPROUTED_GLOW_LICHEN.defaultBlockState());
		} else if (state.is(Blocks.GLOW_LICHEN) && shears) {
			result = sameFaces(state, Tundra.SNIPPED_GLOW_LICHEN.defaultBlockState());
		} else if (state.is(Tundra.SPROUTED_GLOW_LICHEN) && !state.getValue(SproutedLichenBlock.SNIPPED) && shears) {
			result = state.setValue(SproutedLichenBlock.SNIPPED, true);
		} else {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlockAndUpdate(pos, result);
			level.gameEvent(player, shears ? GameEvent.SHEAR : GameEvent.BLOCK_CHANGE, pos);
			if (shears) {
				level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
				stack.hurtAndBreak(1, player, hand);
			} else {
				level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
				stack.consume(1, player);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** {@code to} with the faces and the water of {@code from}. */
	private static BlockState sameFaces(BlockState from, BlockState to) {
		for (Direction face : Direction.values()) {
			to = to.setValue(MultifaceBlock.getFaceProperty(face), from.getValue(MultifaceBlock.getFaceProperty(face)));
		}
		return to.setValue(MultifaceBlock.WATERLOGGED, from.getValue(MultifaceBlock.WATERLOGGED));
	}
}
