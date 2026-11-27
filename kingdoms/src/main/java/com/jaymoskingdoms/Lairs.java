package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;

/**
 * (0.7.66) Where the newly cursed come to: a vampire wakes from its turning in a crypt ({@link #farCrypt}: dug under a random
 * spot within 1000 blocks), a werewolf from its first moon in a den (dug beneath where they stood), with a bed that's now
 * where they wake after dying, and a ladder up.
 * Only natural ground is dug (stone, dirt, sand, gravel, ores); if there's anything else in the way (a build, a cave,
 * the sea), there's no lair and they wake where they are.
 */
final class Lairs {
	/** How far down the floor of a crypt / a den lies. */
	static final int CRYPT_DEPTH = 9, DEN_DEPTH = 5;
	/** Half widths (the walls are one further out) and inside heights. */
	static final int CRYPT_HALF = 3, CRYPT_HEIGHT = 3, DEN_HALF = 2, DEN_HEIGHT = 2;
	/** The share of a lair's blocks that may be air already (small caves are fine). */
	static final float AIR_ALLOWED = 0.2F;
	/** The far crypt: how far from where they turned (blocks, nearest and furthest), spots tried, chunks that may be made, and how near a settlement's seat it may not be. */
	static final int FAR_MIN = 100, FAR_RANGE = 1000, FAR_TRIES = 40, FAR_CHUNKS = 8;
	static final double FAR_KEEP_OFF = 96.0;

	private Lairs() {
	}

	/** A crypt beneath {@code player}; true if it was built (they're in it). */
	static boolean crypt(ServerLevel level, ServerPlayer player) {
		return crypt(level, player, player.blockPosition().below(CRYPT_DEPTH), player.blockPosition());
	}

	/**
	 * Andy: "it should be dug under a random area within 1000 blocks". The crypt a turned player wakes in: dug under a random
	 * spot {@link #FAR_MIN} to {@link #FAR_RANGE} blocks from where they turned ({@link #farSite}), the coffin theirs. If no
	 * such spot is found (every try fell on sea, a build or a cave, or the land wasn't made), it is dug under where they
	 * stood, as it always was, and if that won't do either there is no crypt. True if one was built (they're in it).
	 */
	static boolean farCrypt(ServerLevel level, ServerPlayer player) {
		Site site = farSite(level, player.blockPosition());
		if (site != null && crypt(level, player, site.floor(), site.top())) {
			return true;
		}
		return crypt(level, player);
	}

	/** Where a far crypt goes: the middle of its floor, and the ground above the ladder's shaft (where the ladder comes out). */
	record Site(BlockPos floor, BlockPos top) {
	}

	/**
	 * A random spot {@link #FAR_MIN}-{@link #FAR_RANGE} blocks from {@code from} (an even spread over the ring) where a crypt
	 * fits: in the Overworld, on land the seed shows dry ({@link LifeStory#foreseenDry}), inside the land Chunky has made
	 * ({@link Pregen}) and the world border, no settlement's seat or structure there, and natural ground all through
	 * ({@link #fits}). The land is made only as the starts make it ({@link LifeStory#reach}, at most {@link #FAR_CHUNKS} chunks).
	 */
	static @org.jspecify.annotations.Nullable Site farSite(ServerLevel level, BlockPos from) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		RandomSource random = level.getRandom();
		int[] made = {FAR_CHUNKS};
		int reach = CRYPT_HALF + 1;
		for (int i = 0; i < FAR_TRIES && made[0] > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = Math.sqrt((double) FAR_MIN * FAR_MIN + random.nextDouble() * ((double) FAR_RANGE * FAR_RANGE - (double) FAR_MIN * FAR_MIN));
			// (the ladder's shaft is the column drawn: the room lies CRYPT_HALF blocks west of it)
			int x = from.getX() + net.minecraft.util.Mth.floor(Math.cos(angle) * d), z = from.getZ() + net.minecraft.util.Mth.floor(Math.sin(angle) * d);
			BlockPos column = new BlockPos(x, 0, z);
			if (!Pregen.inside(level, column) || !level.getWorldBorder().isWithinBounds(column) || !LifeStory.foreseenDry(level, x, z)) {
				continue;
			}
			boolean ready = true;
			for (int dx : new int[] {-CRYPT_HALF - reach, 1}) {
				for (int dz : new int[] {-reach, reach}) {
					ready &= LifeStory.reach(level, x + dx, z + dz, made);
				}
			}
			if (!ready) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getWorldBorder().isWithinBounds(top) || Factions.near(level.getServer(), top, FAR_KEEP_OFF) != null
				|| !level.structureManager().getAllStructuresAt(top.below()).isEmpty()) {
				continue;
			}
			BlockPos floor = new BlockPos(x - CRYPT_HALF, top.getY() - CRYPT_DEPTH, z);
			if (fits(level, floor, CRYPT_HALF, CRYPT_HEIGHT, top)) {
				return new Site(floor, top);
			}
		}
		return null;
	}

	/** A crypt with its floor's middle at {@code floor}, the ladder coming out at {@code top}; true if it was built (they're in it). */
	private static boolean crypt(ServerLevel level, ServerPlayer player, BlockPos floor, BlockPos top) {
		if (!fits(level, floor, CRYPT_HALF, CRYPT_HEIGHT, top)) {
			return false;
		}
		RandomSource random = level.getRandom();
		shell(level, floor, CRYPT_HALF, CRYPT_HEIGHT, (pos, wall) -> {
			if (wall) {
				float r = random.nextFloat();
				return (r < 0.2F ? Blocks.MOSSY_STONE_BRICKS : r < 0.4F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
			}
			return pos.getY() == floor.getY() ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState();
		});
		int y = floor.getY() + 1;
		// the coffin: a black bed boxed in dark oak, at the far end
		BlockPos head = new BlockPos(floor.getX(), y, floor.getZ() - 2), foot = head.south();
		bed(level, head, foot, Direction.NORTH, Blocks.BED.pick(net.minecraft.world.item.DyeColor.BLACK));
		for (BlockPos side : new BlockPos[] {head.east(), head.west(), foot.east(), foot.west()}) {
			level.setBlock(side, Blocks.DARK_OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
		}
		// the shrine at the coffin's head: a skull between two lit candles, and a blood bottle framed on the wall above it
		// (the Blood page's slots and pacts open here, {@link BloodPage})
		BlockPos shrine = head.north();
		level.setBlock(shrine, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 0), Block.UPDATE_ALL);
		for (BlockPos side : new BlockPos[] {shrine.east(), shrine.west()}) {
			level.setBlock(side, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
		}
		net.minecraft.world.item.ItemStack bottle = BloodPage.bottleStack();
		if (bottle != null) {
			net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, shrine.above(), Direction.SOUTH);
			frame.setItem(bottle);
			level.addFreshEntity(frame);
		}
		// candles in the corners, cobwebs and a lantern overhead
		int h = CRYPT_HALF;
		for (int[] c : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
			level.setBlock(new BlockPos(floor.getX() + c[0], y, floor.getZ() + c[1]),
				Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(4)).setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
			if (random.nextBoolean()) {
				level.setBlock(new BlockPos(floor.getX() + c[0], floor.getY() + CRYPT_HEIGHT, floor.getZ() + c[1] + (c[1] < 0 ? 1 : -1)),
					Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(new BlockPos(floor.getX(), floor.getY() + CRYPT_HEIGHT, floor.getZ()), Blocks.SOUL_LANTERN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), Block.UPDATE_ALL);
		ladder(level, floor, CRYPT_HALF, CRYPT_HEIGHT, top);
		CoffinRest.claim(player, level, head);
		wake(level, player, foot, head);
		return true;
	}

	/** A den beneath {@code player}; true if it was built (they're in it). */
	static boolean den(ServerLevel level, ServerPlayer player) {
		BlockPos floor = player.blockPosition().below(DEN_DEPTH);
		if (!fits(level, floor, DEN_HALF, DEN_HEIGHT, player.blockPosition())) {
			return false;
		}
		RandomSource random = level.getRandom();
		shell(level, floor, DEN_HALF, DEN_HEIGHT, (pos, wall) -> {
			if (wall) {
				float r = random.nextFloat();
				return (r < 0.35F ? Blocks.ROOTED_DIRT : r < 0.6F ? Blocks.COARSE_DIRT : Blocks.PACKED_MUD).defaultBlockState();
			}
			return pos.getY() == floor.getY() ? (random.nextBoolean() ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState() : Blocks.AIR.defaultBlockState();
		});
		int y = floor.getY() + 1;
		BlockPos head = new BlockPos(floor.getX() - 1, y, floor.getZ() - 1), foot = head.south();
		bed(level, head, foot, Direction.NORTH, Blocks.STRAW_BED);
		// gnawed bones and a skull, roots hanging from the roof
		level.setBlock(new BlockPos(floor.getX() + 1, y, floor.getZ() - 1), Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(floor.getX() + 1, y, floor.getZ() + 1), Blocks.SKELETON_SKULL.defaultBlockState()
			.setValue(SkullBlock.ROTATION, random.nextInt(16)), Block.UPDATE_ALL);
		for (int i = 0; i < 4; i++) {
			BlockPos root = new BlockPos(floor.getX() + random.nextInt(3) - 1, floor.getY() + DEN_HEIGHT, floor.getZ() + random.nextInt(3) - 1);
			if (level.getBlockState(root).isAir()) {
				level.setBlock(root, Blocks.HANGING_ROOTS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		ladder(level, floor, DEN_HALF, DEN_HEIGHT, player.blockPosition());
		wake(level, player, foot, head);
		return true;
	}

	/** In the Overworld, deep enough, and dug only into natural ground. */
	private static boolean fits(ServerLevel level, BlockPos floor, int half, int height, BlockPos top) {
		if (level.dimension() != Level.OVERWORLD || floor.getY() <= level.getMinY() + 4) {
			return false;
		}
		int air = 0, all = 0;
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-half - 1, 0, -half - 1), floor.offset(half + 1, height + 1, half + 1))) {
			BlockState s = level.getBlockState(p);
			all++;
			if (s.isAir()) {
				air++;
			} else if (!natural(s)) {
				return false;
			}
		}
		// the ladder's shaft, at the room's east wall, up to where they stood
		for (int y = floor.getY() + height + 1; y < top.getY(); y++) {
			BlockState s = level.getBlockState(new BlockPos(floor.getX() + half, y, floor.getZ()));
			if (!s.isAir() && !natural(s)) {
				return false;
			}
		}
		return air <= all * AIR_ALLOWED;
	}

	static boolean natural(BlockState s) {
		return !s.hasBlockEntity() && s.getFluidState().isEmpty() && (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SUBSTRATE_OVERWORLD)
			|| s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(BlockTags.ORES) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)
			|| s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.TERRACOTTA)
			|| s.is(BlockTags.TERRACOTTA));
	}

	private interface Fill {
		BlockState at(BlockPos pos, boolean wall);
	}

	/** The room: a shell one block thick round {@code half}/{@code height} of inside, the floor at {@code floor}'s height. */
	private static void shell(ServerLevel level, BlockPos floor, int half, int height, Fill fill) {
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-half - 1, 0, -half - 1), floor.offset(half + 1, height + 1, half + 1))) {
			int dx = Math.abs(p.getX() - floor.getX()), dz = Math.abs(p.getZ() - floor.getZ()), dy = p.getY() - floor.getY();
			boolean wall = dx > half || dz > half || dy == height + 1;
			level.setBlock(p, fill.at(p, wall), Block.UPDATE_ALL);
		}
	}

	private static void bed(ServerLevel level, BlockPos head, BlockPos foot, Direction facing, Block bed) {
		level.setBlock(foot, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, facing).setValue(AbstractBedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, facing).setValue(AbstractBedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
	}

	/** A ladder up the east side to where they stood (the shaft dug through what's natural). */
	private static void ladder(ServerLevel level, BlockPos floor, int half, int height, BlockPos top) {
		int x = floor.getX() + half, z = floor.getZ();
		for (int y = floor.getY() + 1; y < top.getY(); y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (y > floor.getY() + height && !level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			BlockPos wall = p.east();
			if (!level.getBlockState(wall).isSolid()) {
				level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.setBlock(p, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
		}
	}

	/** They come to beside the bed, which is theirs now: where they'll wake after dying. */
	private static void wake(ServerLevel level, ServerPlayer player, BlockPos foot, BlockPos head) {
		BlockPos stand = foot.south();
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		player.setYRot(0.0F);
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, 180.0F, 0.0F), false), false);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
		player.fallDistance = 0.0;
		level.playSound(null, stand, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
	}
}
