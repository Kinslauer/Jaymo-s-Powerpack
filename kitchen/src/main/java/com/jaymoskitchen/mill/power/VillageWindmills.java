package com.jaymoskitchen.mill.power;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mill.MachineBlock;
import com.jaymoskitchen.mill.MachineKind;
import com.jaymoskitchen.mill.Mill;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Real windmills in village windmill towers that are built with stone sails (Andy's ask: the pack's Epic
 * Structures: Villages puts up three). When a listed village piece is placed in world generation, its sails come
 * down and a turning windmill goes up at the hub, with a shaft (and a corner, if it turns) running in to a horse
 * mill set inside, which the windmill then works.
 *
 * <p>The pieces are listed in {@code data/<namespace>/village_windmills/*.json}, by the template they replace and
 * its size (so a template of that name from anyone else, vanilla's own included, is left alone), in the template's
 * own coordinates: the hub, the box of the sail plane and the blocks the sails are made of, the path from the hub to
 * the mill (the last spot; a turn in the path becomes the corner), the way the mill faces, spots to clear and blocks
 * to set. Only the part of the piece in the chunk being generated is touched.
 */
public final class VillageWindmills {
	private static volatile Map<Identifier, Windmill> byTemplate = Map.of();

	private VillageWindmills() {
	}

	/** One listed windmill piece (template coordinates). */
	record Windmill(Identifier template, Vec3i size, BlockPos hub, BlockPos planeFrom, BlockPos planeTo, Set<Block> sails,
		List<BlockPos> path, Direction millFacing, List<BlockPos> clear, Map<BlockPos, Block> set) {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> load(server.getResourceManager()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				load(server.getResourceManager());
			}
		});
	}

	static void load(ResourceManager resources) {
		Map<Identifier, Windmill> found = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : resources.listResources("village_windmills", id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = entry.getValue().openAsReader()) {
				Windmill mill = parse(JsonParser.parseReader(reader).getAsJsonObject());
				found.put(mill.template(), mill);
			} catch (Exception e) {
				JaymosKitchen.LOGGER.warn("Skipping village windmill {}: {}", entry.getKey(), e.getMessage());
			}
		}
		byTemplate = Map.copyOf(found);
	}

	private static Windmill parse(JsonObject json) {
		Identifier template = Identifier.parse(json.get("template").getAsString());
		BlockPos hub = pos(json.get("hub"));
		JsonObject plane = json.getAsJsonObject("sail_plane");
		Set<Block> sails = new HashSet<>();
		for (JsonElement id : json.getAsJsonArray("sail_blocks")) {
			sails.add(block(id.getAsString()));
		}
		List<BlockPos> path = new ArrayList<>();
		for (JsonElement at : json.getAsJsonArray("path")) {
			path.add(pos(at));
		}
		List<BlockPos> clear = new ArrayList<>();
		if (json.has("clear")) {
			for (JsonElement at : json.getAsJsonArray("clear")) {
				clear.add(pos(at));
			}
		}
		Map<BlockPos, Block> set = new HashMap<>();
		if (json.has("set")) {
			for (JsonElement el : json.getAsJsonArray("set")) {
				set.put(pos(el.getAsJsonObject().get("pos")), block(el.getAsJsonObject().get("block").getAsString()));
			}
		}
		Direction facing = Direction.byName(json.has("millstone_facing") ? json.get("millstone_facing").getAsString() : "north");
		if (facing == null || facing.getAxis().isVertical()) {
			throw new IllegalArgumentException("millstone_facing must be north, south, east or west");
		}
		Windmill mill = new Windmill(template, pos(json.get("size")), hub, pos(plane.get("from")), pos(plane.get("to")), sails, path, facing, clear, set);
		check(mill);
		return mill;
	}

	/** The path must be a real shaft run: steps of one block, the first level, at most one turn (level to upright). */
	private static void check(Windmill mill) {
		if (mill.path().size() < 2) {
			throw new IllegalArgumentException("the path needs at least a shaft and the mill");
		}
		BlockPos last = mill.hub();
		Direction lastStep = null;
		int shafts = 0, corners = 0;
		for (int i = 0; i < mill.path().size(); i++) {
			Direction step = step(last, mill.path().get(i));
			if (i == 0 && step.getAxis().isVertical()) {
				throw new IllegalArgumentException("the shaft must leave the hub level");
			}
			if (lastStep != null && step != lastStep) {
				if (step.getAxis().isVertical() == lastStep.getAxis().isVertical()) {
					throw new IllegalArgumentException("a corner joins a level shaft to an upright one");
				}
				corners++;
				shafts--;
			}
			if (i < mill.path().size() - 1) {
				shafts++;
			}
			lastStep = step;
			last = mill.path().get(i);
		}
		if (corners > 1 || shafts > Shafts.SHAFT_MAX) {
			throw new IllegalArgumentException("at most " + Shafts.SHAFT_MAX + " shafts and one corner");
		}
	}

	private static Direction step(BlockPos from, BlockPos to) {
		BlockPos d = to.subtract(from);
		if (Math.abs(d.getX()) + Math.abs(d.getY()) + Math.abs(d.getZ()) != 1) {
			throw new IllegalArgumentException("path steps must be one block: " + from + " to " + to);
		}
		return Direction.getNearest(d.getX(), d.getY(), d.getZ(), Direction.NORTH);
	}

	private static BlockPos pos(JsonElement json) {
		JsonArray a = json.getAsJsonArray();
		return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))
			.orElseThrow(() -> new IllegalArgumentException("no block " + id));
	}

	/**
	 * A village piece was just placed (its part in this chunk, {@code box}): if it's a listed windmill tower, make
	 * the windmill real. Called from world generation; touches only blocks inside {@code box}.
	 */
	public static void placed(Identifier id, StructureTemplate template, WorldGenLevel level, BlockPos origin, Rotation rotation, BoundingBox box) {
		Windmill mill = byTemplate.get(id);
		if (mill == null || !template.getSize().equals(mill.size())) {
			return;
		}
		// the stone sails come down
		for (int x = Math.min(mill.planeFrom().getX(), mill.planeTo().getX()); x <= Math.max(mill.planeFrom().getX(), mill.planeTo().getX()); x++) {
			for (int y = Math.min(mill.planeFrom().getY(), mill.planeTo().getY()); y <= Math.max(mill.planeFrom().getY(), mill.planeTo().getY()); y++) {
				for (int z = Math.min(mill.planeFrom().getZ(), mill.planeTo().getZ()); z <= Math.max(mill.planeFrom().getZ(), mill.planeTo().getZ()); z++) {
					BlockPos at = world(new BlockPos(x, y, z), origin, rotation);
					if (box.isInside(at) && mill.sails().contains(level.getBlockState(at).getBlock())) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		for (BlockPos local : mill.clear()) {
			put(level, box, world(local, origin, rotation), Blocks.AIR.defaultBlockState());
		}
		for (Map.Entry<BlockPos, Block> entry : mill.set().entrySet()) {
			put(level, box, world(entry.getKey(), origin, rotation), entry.getValue().defaultBlockState());
		}

		// the windmill at the hub, the shaft in, and the mill at the end
		List<BlockPos> path = mill.path();
		Direction first = rotation.rotate(step(mill.hub(), path.get(0)));
		put(level, box, world(mill.hub(), origin, rotation), Power.WINDMILL.defaultBlockState().setValue(RotorBlock.FACING, first));
		BlockPos last = mill.hub();
		for (int i = 0; i < path.size() - 1; i++) {
			Direction in = rotation.rotate(step(last, path.get(i)));
			Direction out = rotation.rotate(step(path.get(i), path.get(i + 1)));
			BlockState piece = in == out ? Power.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, in.getAxis()) : corner(in.getOpposite(), out);
			put(level, box, world(path.get(i), origin, rotation), piece);
			last = path.get(i);
		}
		MachineBlock millstone = Mill.MACHINES.get(MachineKind.GRINDSTONE);
		put(level, box, world(path.get(path.size() - 1), origin, rotation),
			millstone.defaultBlockState().setValue(MachineBlock.FACING, rotation.rotate(mill.millFacing())));
	}

	/** A corner with arms toward {@code a} and {@code b} (one level, one upright). */
	private static BlockState corner(Direction a, Direction b) {
		Direction side = a.getAxis().isHorizontal() ? a : b;
		Direction upright = a.getAxis().isHorizontal() ? b : a;
		return Power.CORNER.defaultBlockState().setValue(ShaftCornerBlock.FACING, side).setValue(ShaftCornerBlock.UP, upright == Direction.UP);
	}

	private static BlockPos world(BlockPos local, BlockPos origin, Rotation rotation) {
		return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
	}

	private static void put(WorldGenLevel level, BoundingBox box, @Nullable BlockPos at, BlockState state) {
		if (at != null && box.isInside(at)) {
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
		}
	}
}
