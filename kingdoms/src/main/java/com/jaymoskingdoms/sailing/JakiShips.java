package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.111) Ships drawn by Jackiefr for Jaki Versatile Structures: Sails &amp; Sea (GPLv3: the structure files ship
 * unchanged in data/jaymoskingdoms/structure/jaki, with its licence and a notice in licenses/jaki), sailed by our crews
 * ({@link Fleet}): a pirate's galleon and drakkar, a navy's war trireme, a merchant's cargo ship and trader. Each is
 * read from its file when she's launched and made one of ours: her own crew, the sea in and round her and her
 * decorations' data left out, a helm set on her quarterdeck, the galleon's blackstone guns made ship's cannons (and
 * guns along the rails of the ships that had none), one colour of her sails her crew's, and loot in her barrels from
 * the vanilla shipwreck and outpost tables. Their sails are wool, which catches the wind like sailcloth ({@link Hull}).
 */
public final class JakiShips {
	/**
	 * One of their ships: her file; where her helm goes (in the file's blocks; her bow is the file's +z); the colour of
	 * her sails that turns her crew's; and guns along her rails: the rails' height, their two sides (x), and the places
	 * along her (z) the guns go (none: her own, or none at all).
	 */
	public enum Design {
		GALLEON("illager_galleon", 8, 17, 6, DyeColor.GRAY, -1, 0, 0, new int[0]),
		DRAKKAR("drakkar", 6, 1, 8, DyeColor.BLUE, 2, 3, 9, new int[] {11, 17}),
		WAR_TRIREME("roman_war_trireme", 15, 9, 6, DyeColor.RED, 9, 11, 19, new int[] {10, 16, 22, 28}),
		CARGO_SHIP("cargo_ship", 7, 11, 6, DyeColor.GRAY, -1, 0, 0, new int[0]),
		TRADER("villager_trader_ship", 7, 11, 7, DyeColor.GREEN, -1, 0, 0, new int[0]);

		final String file;
		final BlockPos helm;
		final DyeColor sails;
		final int gunRail, portX, starboardX;
		final int[] gunZ;

		Design(String file, int helmX, int helmY, int helmZ, DyeColor sails, int gunRail, int portX, int starboardX, int[] gunZ) {
			this.file = file;
			this.helm = new BlockPos(helmX, helmY, helmZ);
			this.sails = sails;
			this.gunRail = gunRail;
			this.portX = portX;
			this.starboardX = starboardX;
			this.gunZ = gunZ;
		}
	}

	/** The longest chain rigging (blocks) that ties a loose sail on. */
	static final int LONGEST_RIGGING = 8;

	/** A barrel's loot in their files, and the vanilla table it's filled from instead (any other: left empty). */
	private static final Map<String, ResourceKey<LootTable>> LOOT = Map.of(
		"jvssas:chest/food", BuiltInLootTables.SHIPWRECK_SUPPLY,
		"jvssas:chest/rare", BuiltInLootTables.SHIPWRECK_TREASURE,
		"jvssas:chest/armed", BuiltInLootTables.PILLAGER_OUTPOST);

	/** A design's blocks as read (her keel at y 0, her helm at x 0), each with its barrel's loot; read once. */
	private static final Map<Design, List<Fleet.Part>> READ = new HashMap<>();

	private JakiShips() {
	}

	/** Her blocks, her bow +z, with her sails in {@code look}'s flag colour (if it's a wool); empty if her file won't read. */
	static List<Fleet.Part> parts(ServerLevel level, Design design, Fleet.Look look) {
		List<Fleet.Part> read = READ.computeIfAbsent(design, d -> read(level, d));
		Block sail = Blocks.WOOL.pick(design.sails);
		Block colour = look.flag().defaultBlockState().is(net.minecraft.tags.BlockTags.WOOL) ? look.flag() : sail;
		if (colour == sail) {
			return read;
		}
		List<Fleet.Part> parts = new ArrayList<>(read.size());
		for (Fleet.Part p : read) {
			parts.add(p.state().is(sail) ? new Fleet.Part(p.at(), colour.defaultBlockState(), p.loot()) : p);
		}
		return parts;
	}

	private static List<Fleet.Part> read(ServerLevel level, Design design) {
		net.minecraft.resources.Identifier id = JaymosKingdoms.id("structure/jaki/" + design.file + ".nbt");
		Optional<Resource> resource = level.getServer().getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			JaymosKingdoms.LOGGER.warn("No ship file {}", id);
			return List.of();
		}
		CompoundTag tag;
		try (InputStream in = resource.get().open()) {
			tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read ship file {}", id, e);
			return List.of();
		}
		// (drawn in 1.20.1: brought up to date as the game does its own structures)
		tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(level.getServer().getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500));
		HolderGetter<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		ListTag paletteTag = tag.getListOrEmpty("palette");
		if (paletteTag.isEmpty()) {
			paletteTag = tag.getListOrEmpty("palettes").getListOrEmpty(0);
		}
		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i)));
		}
		Map<BlockPos, BlockState> states = new HashMap<>();
		Map<BlockPos, ResourceKey<LootTable>> loot = new HashMap<>();
		ListTag list = tag.getListOrEmpty("blocks");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty("pos");
			BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			BlockState state = palette.get(b.getIntOr("state", 0));
			if (!keep(state)) {
				continue;
			}
			states.put(at, state);
			String table = b.getCompoundOrEmpty("nbt").getStringOr("LootTable", "");
			if (LOOT.containsKey(table)) {
				loot.put(at, LOOT.get(table));
			}
		}
		guns(states, design);
		// her helm, her bow the way it faces
		states.put(design.helm, Sailing.HELM.defaultBlockState().setValue(HelmBlock.FACING, Direction.SOUTH));
		rigging(states, design.helm);
		int keel = Integer.MAX_VALUE;
		for (BlockPos p : states.keySet()) {
			keel = Math.min(keel, p.getY());
		}
		List<Fleet.Part> parts = new ArrayList<>(states.size());
		for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) {
			BlockPos p = e.getKey();
			parts.add(new Fleet.Part(new BlockPos(p.getX() - design.helm.getX(), p.getY() - keel, p.getZ() - design.helm.getZ()), e.getValue(),
				loot.get(p)));
		}
		JaymosKingdoms.LOGGER.info("Read ship {}: {} blocks", design.file, parts.size());
		return List.copyOf(parts);
	}

	/** Whether a block of their files goes aboard: not the sea, air or a structure's markers, nor heads (their faces are lost). */
	private static boolean keep(BlockState state) {
		return !(state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.SPONGE)
			|| state.is(Blocks.WET_SPONGE) || state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD));
	}

	/**
	 * Her guns: each blackstone gun (a button at its breech, blackstone and walls out to its muzzle) becomes a ship's
	 * cannon where its breech was, the rest of it cleared for a gunport and the button for its gunner; and where a design
	 * gives guns along her rails, a cannon in the rail at each place, both sides, facing out.
	 */
	private static void guns(Map<BlockPos, BlockState> states, Design design) {
		List<BlockPos> buttons = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) {
			if (e.getValue().getBlock() instanceof ButtonBlock && blackstone(e.getValue())) {
				buttons.add(e.getKey());
			}
		}
		for (BlockPos button : buttons) {
			Direction out = states.get(button).getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
			BlockPos breech = button.relative(out);
			if (!blackstone(states.get(breech))) {
				continue;
			}
			states.remove(button);
			states.put(breech, cannon(out));
			for (BlockPos p = breech.relative(out); blackstone(states.get(p)); p = p.relative(out)) {
				states.remove(p);
			}
		}
		if (design.gunRail >= 0) {
			for (int z : design.gunZ) {
				states.put(new BlockPos(design.portX, design.gunRail, z), cannon(Direction.WEST));
				states.put(new BlockPos(design.starboardX, design.gunRail, z), cannon(Direction.EAST));
			}
		}
	}

	/**
	 * Her sails and yards that only touch the rest of her at an edge or a corner (a ship casts off only what's joined to
	 * her helm face to face, {@link Ship#castOff}) are tied on with chain rigging, the shortest way through the air;
	 * anything further off than {@link #LONGEST_RIGGING} is left ashore.
	 */
	private static void rigging(Map<BlockPos, BlockState> states, BlockPos helm) {
		java.util.Set<BlockPos> joined = joined(states.keySet(), helm);
		for (int guard = 0; guard < 200 && joined.size() < states.size(); guard++) {
			BlockPos loose = null;
			for (BlockPos p : states.keySet()) {
				if (!joined.contains(p)) {
					loose = p;
					break;
				}
			}
			java.util.Set<BlockPos> piece = joined(states.keySet().stream().filter(p -> !joined.contains(p)).collect(java.util.stream.Collectors.toSet()), loose);
			// out through the air from the loose piece until her body's reached
			Map<BlockPos, BlockPos> from = new HashMap<>();
			java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
			for (BlockPos p : piece) {
				from.put(p, p);
				open.add(p);
			}
			BlockPos end = null;
			while (!open.isEmpty() && end == null) {
				BlockPos c = open.poll();
				if (!piece.contains(c) && steps(from, c) > LONGEST_RIGGING) {
					break;
				}
				for (Direction d : Direction.values()) {
					BlockPos n = c.relative(d);
					if (joined.contains(n)) {
						end = c;
						break;
					}
					if (!from.containsKey(n) && !states.containsKey(n)) {
						from.put(n, c);
						open.add(n);
					}
				}
			}
			if (end == null) {
				piece.forEach(states::remove);
				continue;
			}
			for (BlockPos c = end; !piece.contains(c); c = from.get(c)) {
				Direction.Axis axis = Direction.getApproximateNearest(c.getX() - from.get(c).getX(), c.getY() - from.get(c).getY(),
					c.getZ() - from.get(c).getZ()).getAxis();
				states.put(c, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
			}
			joined.addAll(joined(states.keySet(), helm));
		}
	}

	/** Every block of {@code cells} joined face to face to {@code start}. */
	private static java.util.Set<BlockPos> joined(java.util.Set<BlockPos> cells, BlockPos start) {
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
		seen.add(start);
		open.add(start);
		while (!open.isEmpty()) {
			BlockPos c = open.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = c.relative(d);
				if (cells.contains(n) && seen.add(n)) {
					open.add(n);
				}
			}
		}
		return seen;
	}

	/** How many cells of air a rigging path has come through to reach {@code c}. */
	private static int steps(Map<BlockPos, BlockPos> from, BlockPos c) {
		int n = 0;
		for (BlockPos p = c; !from.get(p).equals(p); p = from.get(p)) {
			n++;
		}
		return n;
	}

	private static boolean blackstone(@Nullable BlockState state) {
		return state != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("polished_blackstone");
	}

	private static BlockState cannon(Direction out) {
		return Sailing.CANNON.defaultBlockState().setValue(ShipCannonBlock.FACING, out).setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.SHOT);
	}
}
