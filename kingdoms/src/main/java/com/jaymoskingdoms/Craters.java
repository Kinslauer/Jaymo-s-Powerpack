package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Craters heal in the wild (village mods patch 2, ideas from Guard Villagers' mending; our own code). A blast away from any settlement
 * leaves the soft ground it took (dirt, grass, podzol, mud: never stone or ore, which stay as they were left) marked, and the land
 * mends it over a few days in three stages ({@link #STAGE_TICKS} each): <b>dirt</b> back into the hole (from the bottom up, onto
 * firm ground), <b>grass</b> over the dirt, then <b>sprouts</b> (short grass, or the flower already growing nearby). Run from
 * {@link Weathering}'s step.
 * <p>The living-things rules: <b>sympathy</b> (it heals the way ground really does: from the rim in, grass only where grass already
 * grows beside it and the sky is open, flowers from seed already blown over from the ones nearby, none in dry lands); <b>feedback
 * loops</b> (rain speeds it ({@link #RAIN_QUICKER}), every healed cell is another one's seed bank, and a fresh blast on the spot sets
 * it back to dirt-less bare ground again); <b>stewardship</b> (bone meal on a healing cell carries it on a stage at once, and
 * nobody is punished for leaving a crater alone: it heals on its own).
 */
final class Craters {
	/** How long each stage waits (ticks, a day), and the chance a due cell moves on in a pass (every second); rain shortens the wait. */
	static final long STAGE_TICKS = 24000L;
	static final float STEP_CHANCE = 0.15F, RAIN_QUICKER = 0.5F;
	/** Cells looked at in one pass for a level, how near a player they must be (blocks), and the most kept for a level. */
	static final int PASS_CELLS = 48, CELLS_MOST = 3000;
	static final double NEAR_PLAYER = 96.0;
	/** Sprouts: the share that are short grass when no flower is near, and how far a flower's seed carries (blocks). */
	static final float TUFT_SHARE = 0.6F;
	static final int SEED_REACH = 4;
	static final int GROW_LIGHT = 9;

	/** A healing cell: where, its stage (0 bare, 1 dirt, 2 grass) and when it last changed. */
	record Cell(long pos, int stage, long at) {
		static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Cell::pos),
			Codec.INT.fieldOf("stage").forGetter(Cell::stage),
			Codec.LONG.fieldOf("at").forGetter(Cell::at)).apply(i, Cell::new));
	}

	/** On each level: the cells still healing. Saved. */
	static final AttachmentType<List<Cell>> CELLS = AttachmentRegistry.create(JaymosKingdoms.id("crater_cells"),
		builder -> builder.persistent(Cell.CODEC.listOf()));

	private Craters() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)
				|| !sp.getMainHandItem().is(Items.BONE_MEAL)) {
				return InteractionResult.PASS;
			}
			return boneMeal(level, sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	/** Soft ground a blast takes. */
	static boolean soft(BlockState state) {
		return state.is(BlockTags.DIRT) && !state.hasBlockEntity();
	}

	/** A blast is about to break {@code targets}: the soft ground among them is marked to heal. */
	static void mark(ServerLevel level, List<BlockPos> targets) {
		List<Cell> cells = null;
		long now = level.getGameTime();
		for (BlockPos pos : targets) {
			if (!soft(level.getBlockState(pos))) {
				continue;
			}
			if (cells == null) {
				cells = new ArrayList<>(level.getAttachedOrElse(CELLS, List.of()));
			}
			long key = pos.asLong();
			cells.removeIf(c -> c.pos() == key);
			cells.add(new Cell(key, 0, now));
		}
		if (cells == null) {
			return;
		}
		while (cells.size() > CELLS_MOST) {
			cells.removeFirst();
		}
		level.setAttached(CELLS, List.copyOf(cells));
	}

	/** From {@link Weathering}'s step: the cells near players, a few at a time, may move on a stage. */
	static void step(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty() || !level.getGameRules().get(Weathering.WEATHERING)) {
				continue;
			}
			List<Cell> cells = level.getAttachedOrElse(CELLS, List.of());
			if (cells.isEmpty()) {
				continue;
			}
			RandomSource random = level.getRandom();
			List<Cell> now = new ArrayList<>(cells);
			boolean changed = false;
			for (int i = 0; i < PASS_CELLS && !now.isEmpty(); i++) {
				int at = random.nextInt(now.size());
				Cell cell = now.get(at);
				BlockPos pos = BlockPos.of(cell.pos());
				if (!level.isPositionEntityTicking(pos) || level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), NEAR_PLAYER, false) == null) {
					continue;
				}
				long wait = level.isRainingAt(pos.above()) ? Math.round(STAGE_TICKS * RAIN_QUICKER) : STAGE_TICKS;
				if (level.getGameTime() - cell.at() < wait || random.nextFloat() >= STEP_CHANCE) {
					continue;
				}
				Cell next = advance(level, cell, pos, random);
				if (next != cell) {
					changed = true;
					if (next == null) {
						now.remove(at);
					} else {
						now.set(at, next);
					}
				}
			}
			if (changed) {
				level.setAttached(CELLS, List.copyOf(now));
			}
		}
	}

	/** {@code cell} one stage on: the cell itself if it can't yet, null if it is done (or something else has taken the spot). */
	private static Cell advance(ServerLevel level, Cell cell, BlockPos pos, RandomSource random) {
		BlockState state = level.getBlockState(pos);
		long now = level.getGameTime();
		switch (cell.stage()) {
			case 0 -> {
				if (state.isAir() || state.canBeReplaced()) {
					BlockPos below = pos.below();
					if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
						|| !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
						return cell;
					}
					level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
					return new Cell(cell.pos(), 1, now);
				}
				return soft(state) ? new Cell(cell.pos(), 1, now) : null;
			}
			case 1 -> {
				if (!state.is(Blocks.DIRT)) {
					return soft(state) ? new Cell(cell.pos(), 2, now) : null;
				}
				BlockPos up = pos.above();
				Holder<Biome> biome = level.getBiome(pos);
				if (!level.getBlockState(up).isAir() || biome.value().getBaseTemperature() >= Weathering.DRY || !biome.value().hasPrecipitation()) {
					// buried, or a land grass doesn't take: bare dirt it stays
					return null;
				}
				if (!level.canSeeSky(up) || level.getMaxLocalRawBrightness(up) < GROW_LIGHT || !grassNear(level, pos)) {
					return cell;
				}
				level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				return new Cell(cell.pos(), 2, now);
			}
			default -> {
				BlockPos up = pos.above();
				if (state.is(Blocks.GRASS_BLOCK) && level.getBlockState(up).isAir()) {
					BlockState sprout = seedNear(level, pos, random);
					if (sprout != null && sprout.canSurvive(level, up)) {
						level.setBlock(up, sprout, Block.UPDATE_ALL);
					}
				}
				return null;
			}
		}
	}

	/** Grass already growing beside {@code pos} (the rim, or a cell healed before it). */
	private static boolean grassNear(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			for (int dy = -1; dy <= 1; dy++) {
				if (level.getBlockState(side.above(dy)).is(Blocks.GRASS_BLOCK)) {
					return true;
				}
			}
		}
		return false;
	}

	/** What grows on healed grass: a flower already growing within {@link #SEED_REACH} (its seed blown over), else short grass most of the time. */
	private static BlockState seedNear(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState found = null;
		int seen = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-SEED_REACH, 0, -SEED_REACH), pos.offset(SEED_REACH, 2, SEED_REACH))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.SMALL_FLOWERS) && random.nextInt(++seen) == 0) {
				found = s;
			}
		}
		if (found != null) {
			return found;
		}
		return random.nextFloat() < TUFT_SHARE ? Blocks.SHORT_GRASS.defaultBlockState() : null;
	}

	/** Bone meal on a healing cell carries it a stage on at once (a steward's help). True if it was used. */
	private static boolean boneMeal(ServerLevel level, ServerPlayer player, BlockPos pos) {
		long key = pos.asLong();
		List<Cell> cells = level.getAttachedOrElse(CELLS, List.of());
		for (int i = 0; i < cells.size(); i++) {
			Cell cell = cells.get(i);
			if (cell.pos() != key) {
				continue;
			}
			Cell next = advance(level, new Cell(cell.pos(), cell.stage(), Long.MIN_VALUE / 2), pos, level.getRandom());
			if (next == cell || next != null && next.stage() == cell.stage()) {
				return false;
			}
			List<Cell> now = new ArrayList<>(cells);
			if (next == null) {
				now.remove(i);
			} else {
				now.set(i, next);
			}
			level.setAttached(CELLS, List.copyOf(now));
			if (!player.hasInfiniteMaterials()) {
				player.getMainHandItem().shrink(1);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
			level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			return true;
		}
		return false;
	}
}
