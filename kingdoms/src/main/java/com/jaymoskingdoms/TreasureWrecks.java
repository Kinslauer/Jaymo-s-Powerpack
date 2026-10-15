package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Sailing;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.37) Sailing, stage 3: treasure wrecks. A pirate ship's hold may hold a Pirate's Chart (a map with its X out at
 * sea): sail there and the wreck it marks lies on the seabed, her chests full of a pirate's treasure (a chart's X on
 * land is a chest buried under it). Now and then, too, a player sailing the open ocean happens on one of their own: a
 * rare treasure wreck no chart led them to, far from any other.
 */
final class TreasureWrecks {
	/** How far from where it's found a chart's X lies (blocks), and how widely it looks for ocean there. */
	static final int CHART_LEAST = 250, CHART_MOST = 700, CHART_SEARCH = 160;
	/** A charted wreck is laid once a player is this near it (and it's loaded). */
	static final double LAY_RANGE = 72.0;
	/** Happening on one: how often players at sea are looked at (ticks), the chance each look, how far off it lies, how far apart they are. */
	static final int CHANCE_TICKS = 1200;
	static final float WRECK_CHANCE = 0.03F;
	static final int WRECK_NEAREST = 48, WRECK_FURTHEST = 96, WRECK_SPACING = 600;
	/** Water at least this deep for a wreck to lie in. */
	static final int WRECK_DEPTH = 6;
	/** At most this many charted wrecks waiting at once (the oldest are forgotten). */
	static final int MOST_CHARTED = 48;

	/** Charted wrecks still to lay (packed places); wrecks laid (packed places, for spacing). */
	static final AttachmentType<List<Long>> CHARTED = AttachmentRegistry.create(JaymosKingdoms.id("charted_wrecks"),
		builder -> builder.persistent(Codec.LONG.listOf()));
	static final AttachmentType<List<Long>> LAID = AttachmentRegistry.create(JaymosKingdoms.id("laid_wrecks"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private TreasureWrecks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TreasureWrecks::tick);
	}

	// ---- charts ----------------------------------------------------------------------------------------------------

	/** A Pirate's Chart: a map with its X on open ocean some way from {@code near}, where a wreck will be found. */
	static ItemStack chart(ServerLevel level, BlockPos near, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = CHART_LEAST + random.nextInt(CHART_MOST - CHART_LEAST);
		BlockPos guess = new BlockPos(near.getX() + Mth.floor(Math.cos(angle) * r), level.getSeaLevel(), near.getZ() + Mth.floor(Math.sin(angle) * r));
		Pair<BlockPos, Holder<Biome>> ocean = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_OCEAN), guess,
			CHART_SEARCH, 16, 64);
		BlockPos at = ocean != null ? new BlockPos(ocean.getFirst().getX(), level.getSeaLevel(), ocean.getFirst().getZ()) : guess;
		ItemStack map = MapItem.create(level, at.getX(), at.getZ(), (byte)1, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, at, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.pirate_chart"));
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.pirate_chart.lore"))));
		List<Long> charted = new ArrayList<>(level.getAttachedOrElse(CHARTED, List.of()));
		charted.add(at.asLong());
		while (charted.size() > MOST_CHARTED) {
			charted.removeFirst();
		}
		level.setAttached(CHARTED, List.copyOf(charted));
		return map;
	}

	// ---- laying them -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 100 == 0) {
			List<Long> charted = level.getAttachedOrElse(CHARTED, List.of());
			if (!charted.isEmpty()) {
				for (ServerPlayer player : level.players()) {
					for (long packed : charted) {
						BlockPos at = BlockPos.of(packed);
						if (Raids.horizontal(at, player.blockPosition()) < LAY_RANGE * LAY_RANGE && level.isPositionEntityTicking(at)) {
							List<Long> left = new ArrayList<>(level.getAttachedOrElse(CHARTED, List.of()));
							left.remove(packed);
							level.setAttached(CHARTED, List.copyOf(left));
							layCharted(level, at);
							return;
						}
					}
				}
			}
		}
		if (now % CHANCE_TICKS == 0) {
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && random.nextFloat() < WRECK_CHANCE && level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)) {
					happenOn(level, player, random);
				}
			}
		}
	}

	/** A charted wreck laid where its X is (a chest buried, if the X fell on land). */
	private static void layCharted(ServerLevel level, BlockPos at) {
		BlockPos floor = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, at);
		RandomSource random = level.getRandom();
		if (level.getFluidState(floor).is(FluidTags.WATER)) {
			lay(level, floor, random);
			return;
		}
		// buried treasure: under the ground where the X is
		BlockPos chest = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below(3);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(chest) instanceof Container box) {
			stock(box, random);
		}
		note(level, chest);
	}

	/** {@code player}, sailing the open sea, happens on a wreck nobody charted (none other near). */
	private static void happenOn(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (long packed : level.getAttachedOrElse(LAID, List.of())) {
			if (Raids.horizontal(BlockPos.of(packed), player.blockPosition()) < (double)WRECK_SPACING * WRECK_SPACING) {
				return;
			}
		}
		for (int tries = 0; tries < 6; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = WRECK_NEAREST + random.nextInt(WRECK_FURTHEST - WRECK_NEAREST);
			BlockPos column = player.blockPosition().offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos floor = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, column);
			BlockPos top = new BlockPos(column.getX(), level.getSeaLevel() - 1, column.getZ());
			if (level.getFluidState(floor).is(FluidTags.WATER) && top.getY() - floor.getY() >= WRECK_DEPTH && Fleet.deep(level, top.above(), WRECK_DEPTH)) {
				lay(level, floor, random);
				return;
			}
		}
	}

	/** A pirate wreck on the seabed at {@code floor}, her chests stocked with treasure. */
	private static void lay(ServerLevel level, BlockPos floor, RandomSource random) {
		Fleet.Rig rig = random.nextBoolean() ? Fleet.Rig.BRIGANTINE : Fleet.Rig.SLOOP;
		Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE));
		List<BlockPos> chests = Fleet.wreck(level, rig, look, floor, Direction.Plane.HORIZONTAL.getRandomDirection(random), box -> stock(box, random));
		note(level, floor);
		JaymosKingdoms.LOGGER.info("A treasure wreck lies at {} ({} chests)", floor, chests.size());
	}

	private static void note(ServerLevel level, BlockPos at) {
		List<Long> laid = new ArrayList<>(level.getAttachedOrElse(LAID, List.of()));
		laid.add(at.asLong());
		while (laid.size() > 256) {
			laid.removeFirst();
		}
		level.setAttached(LAID, List.copyOf(laid));
	}

	/** A pirate's treasure in {@code box}. */
	static void stock(Container box, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>();
		loot.add(new ItemStack(Items.GOLD_INGOT, 6 + random.nextInt(11)));
		loot.add(new ItemStack(Items.GOLD_NUGGET, 12 + random.nextInt(25)));
		loot.add(new ItemStack(Items.EMERALD, 8 + random.nextInt(17)));
		loot.add(new ItemStack(Items.GUNPOWDER, 3 + random.nextInt(6)));
		loot.add(new ItemStack(Sailing.ROUND_SHOT, 3 + random.nextInt(5)));
		if (random.nextFloat() < 0.5F) {
			loot.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(3)));
		}
		if (random.nextFloat() < 0.35F) {
			loot.add(new ItemStack(Items.HEART_OF_THE_SEA));
		}
		if (random.nextFloat() < 0.3F) {
			loot.add(new ItemStack(Items.SPYGLASS));
		}
		if (random.nextFloat() < 0.4F) {
			loot.add(new ItemStack(Items.AMETHYST_SHARD, 3 + random.nextInt(6)));
		}
		if (random.nextFloat() < 0.25F) {
			loot.add(new ItemStack(Items.NAUTILUS_SHELL, 1 + random.nextInt(2)));
		}
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		java.util.Collections.shuffle(slots, new java.util.Random(random.nextLong()));
		for (int i = 0; i < loot.size() && i < slots.size(); i++) {
			box.setItem(slots.get(i), loot.get(i));
		}
		if (box instanceof BlockEntity be) {
			be.setChanged();
		}
	}

	/** (Testing) a treasure wreck laid now off {@code player}. */
	static boolean layNear(ServerLevel level, ServerPlayer player) {
		List<Long> before = level.getAttachedOrElse(LAID, List.of());
		level.setAttached(LAID, List.of());
		happenOn(level, player, level.getRandom());
		List<Long> after = level.getAttachedOrElse(LAID, List.of());
		List<Long> all = new ArrayList<>(before);
		all.addAll(after);
		level.setAttached(LAID, List.copyOf(all));
		return !after.isEmpty();
	}

	static @Nullable BlockPos lastLaid(ServerLevel level) {
		List<Long> laid = level.getAttachedOrElse(LAID, List.of());
		return laid.isEmpty() ? null : BlockPos.of(laid.getLast());
	}
}
