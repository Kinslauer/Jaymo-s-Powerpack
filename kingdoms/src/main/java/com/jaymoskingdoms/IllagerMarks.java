package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The illagers' signs (village mods patch 1): the <b>standard</b> raised at a camp's gate (a tall pole, the ominous banner's black
 * and grey hung from it and a carved board), and the <b>waymarks</b> now and then found beside a road some way from one of their
 * holds. Sneak and use either with an empty hand to read it: the standard gives its camp's name, rank and warlord, the waymark
 * the nearest hold's name, how far and which way. The names read clearly only to one who knows the words of the tongue
 * ({@link IllagerHolds#shown}). Both are drawn from vanilla's own textures (no new art): the standard from dark oak and black
 * and grey wool and spruce planks, the waymark from deepslate bricks.
 */
public final class IllagerMarks {
	// ---- tuning ----
	/** A waymark: the chance a check on a player walking a road puts one up, how often (ticks) it is checked. */
	static final float WAYMARK_CHANCE = 0.12F;
	static final int WAYMARK_EVERY = 200;
	/** A waymark names a hold at least this far and at most this far from it (blocks), and stands this far from the last one. */
	static final double WAYMARK_NEAR = 70.0, WAYMARK_FAR = 260.0, WAYMARK_APART = 110.0;
	/** The most waymarks kept in the list of where they are. */
	static final int WAYMARKS_MOST = 200;
	/** A standard stands this far from the camp's fire (outside its gate) and this far to the gate's right. */
	static final int STANDARD_OUT = IllagerBuildings.RING_RADIUS + 3, STANDARD_SIDE = 3;
	/** A reader beyond this far from the nearest camp or hold (blocks) finds a standard with nothing to say. */
	static final double STANDARD_READS = 200.0;
	/** The distance a waymark tells is rounded to this many blocks. */
	static final int ROUND = 10;

	public static Block STANDARD, WAYMARK;
	public static Item STANDARD_ITEM, WAYMARK_ITEM;

	/** On the Overworld: where waymarks have been raised (block positions), so they stand well apart. Saved. */
	static final AttachmentType<List<Long>> MARKED = AttachmentRegistry.create(JaymosKingdoms.id("illager_waymarks"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private IllagerMarks() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("illager waymarks: {}", MARKED);
		STANDARD = block("illager_standard", new Standard(props("illager_standard", MapColor.COLOR_BLACK, SoundType.WOOD, 2.0F).noOcclusion()));
		WAYMARK = block("illager_waymark", new Waymark(props("illager_waymark", MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, 3.0F)));
		STANDARD_ITEM = item("illager_standard", STANDARD);
		WAYMARK_ITEM = item("illager_waymark", WAYMARK);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(STANDARD_ITEM);
			output.accept(WAYMARK_ITEM);
		});
	}

	private static BlockBehaviour.Properties props(String name, MapColor color, SoundType sound, float strength) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name))).mapColor(color).sound(sound).strength(strength);
	}

	private static Block block(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)), block);
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	// ---- the standard at a camp's gate ------------------------------------------------------------------------------------

	/** Raises a standard outside {@code camp}'s gate (a new camp's, as it is founded). */
	static void raiseStandard(ServerLevel level, BlockPos fire, net.minecraft.core.Direction gate) {
		BlockPos column = fire.relative(gate, STANDARD_OUT).relative(gate.getClockWise(), STANDARD_SIDE);
		if (!Loaded.made(level, column)) {
			return;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (level.getBlockState(at).isAir() && sturdy(level, at.below())) {
			level.setBlock(at, STANDARD.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	// ---- waymarks beside roads -----------------------------------------------------------------------------------------------

	/** Now and then, for a player on a road (a dirt path) some way from one of their holds, a waymark goes up beside it. */
	static void maybeMark(ServerLevel level, ServerPlayer player) {
		if (player.tickCount % WAYMARK_EVERY >= IllagerHolds.CHECK_TICKS || level.getRandom().nextFloat() >= WAYMARK_CHANCE) {
			return;
		}
		BlockPos feet = player.blockPosition();
		if (!level.getBlockState(feet.below()).is(Blocks.DIRT_PATH)) {
			return;
		}
		IllagerHolds.Found hold = IllagerHolds.nearest(level.getServer(), player.position(), WAYMARK_FAR, WAYMARK_NEAR);
		if (hold == null) {
			return;
		}
		List<Long> marked = new ArrayList<>(level.getAttachedOrElse(MARKED, List.of()));
		for (long m : marked) {
			if (BlockPos.of(m).distSqr(feet) < WAYMARK_APART * WAYMARK_APART) {
				return;
			}
		}
		// beside the road: an empty spot next to the path with ground under it
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos at = feet.relative(d, 2);
			if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && sturdy(level, at.below())
				&& !level.getBlockState(at.below()).is(Blocks.DIRT_PATH)) {
				level.setBlock(at, WAYMARK.defaultBlockState(), Block.UPDATE_ALL);
				marked.add(at.asLong());
				while (marked.size() > WAYMARKS_MOST) {
					marked.removeFirst();
				}
				level.setAttached(MARKED, List.copyOf(marked));
				return;
			}
		}
	}

	private static boolean sturdy(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
	}

	// ---- reading ------------------------------------------------------------------------------------------------------------

	/** Whether {@code player} reads a sign this use: sneaking, empty-handed, on the server. */
	private static boolean reads(Player player, Level level) {
		return !level.isClientSide() && player instanceof ServerPlayer && player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
	}

	/** The standard: what its camp is. */
	static final class Standard extends Block {
		private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

		Standard(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!reads(player, level) || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ServerPlayer reader = (ServerPlayer)player;
			int points = IllagerTongue.points(reader);
			IllagerHolds.Found found = IllagerHolds.nearest(server.getServer(), Vec3.atCenterOf(pos), STANDARD_READS, 0.0);
			if (found == null) {
				reader.sendOverlayMessage(Component.translatable("illager_names.jaymoskingdoms.standard.blank").withStyle(ChatFormatting.GRAY));
			} else {
				Component line = Component.translatable("illager_names.jaymoskingdoms.standard.board", IllagerHolds.shown(found.name(), points),
					IllagerHolds.rankText(found.rank(), points));
				if (found.camp() != null) {
					line = Component.translatable("illager_names.jaymoskingdoms.standard.board_warlord", line, IllagerHolds.shown(IllagerHolds.warlordName(found.camp()), points));
				}
				reader.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD));
			}
			server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 0.8F);
			return InteractionResult.SUCCESS;
		}
	}

	/** A waymark: the nearest hold, how far and which way. */
	static final class Waymark extends Block {
		private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

		Waymark(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!reads(player, level) || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ServerPlayer reader = (ServerPlayer)player;
			Vec3 here = Vec3.atCenterOf(pos);
			IllagerHolds.Found found = IllagerHolds.nearest(server.getServer(), here, STANDARD_READS * 2.0, 0.0);
			if (found == null) {
				reader.sendOverlayMessage(Component.translatable("illager_names.jaymoskingdoms.waymark.blank").withStyle(ChatFormatting.GRAY));
			} else {
				int points = IllagerTongue.points(reader);
				double far = Math.sqrt(found.pos().distToCenterSqr(here));
				long rounded = Math.max(ROUND, Math.round(far / ROUND) * ROUND);
				reader.sendSystemMessage(Component.translatable("illager_names.jaymoskingdoms.waymark.board", IllagerHolds.shown(found.name(), points), rounded,
					Directions.heading(here, Vec3.atCenterOf(found.pos()))).withStyle(ChatFormatting.GOLD));
			}
			server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 0.8F);
			return InteractionResult.SUCCESS;
		}
	}
}
