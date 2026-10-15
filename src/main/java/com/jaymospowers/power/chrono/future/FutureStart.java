package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * The arrival square in the future, kept the way an op left it. {@code /future start save [radius]} snapshots the
 * blocks and fixtures (item frames, armour stands, paintings) around the arrival point into the world's future folder;
 * whenever someone leaps into an empty future, the square is put back exactly as saved (anything else there, the dead
 * included, is cleared first). Until a snapshot exists, the map's own signs and billboards there are taken down.
 */
public final class FutureStart {
	/** The square's half-width around the arrival point, unless the save says otherwise. */
	static final int RADIUS = 24, MIN_RADIUS = 8, MAX_RADIUS = 64;
	/** Its height: the map's spawn sits at y 70. */
	static final int MIN_Y = 58, MAX_Y = 112;
	static final String FILE = "start.nbt";

	private FutureStart() {
	}

	static Path file(ServerLevel level) {
		return LosPerrito.dimensionFolder(level.getServer()).resolve(FILE);
	}

	static BoundingBox box(int radius) {
		return new BoundingBox(LosPerrito.SPAWN_X - radius, MIN_Y, LosPerrito.SPAWN_Z - radius,
			LosPerrito.SPAWN_X + radius, MAX_Y, LosPerrito.SPAWN_Z + radius);
	}

	/** {@code /future start save}: snapshot the square (stray mobs and dropped items aren't kept). */
	static int save(ServerLevel level, int radius) throws IOException {
		BoundingBox box = box(radius);
		prepare(level, box);
		for (Entity entity : entities(level, box)) {
			if (entity instanceof Enemy || entity instanceof ItemEntity) {
				entity.discard();
			}
		}
		StructureTemplate template = new StructureTemplate();
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		template.fillFromWorld(level, origin, new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), true, List.of());
		CompoundTag tag = new CompoundTag();
		tag.putInt("radius", radius);
		tag.put("template", template.save(new CompoundTag()));
		Path file = file(level);
		Files.createDirectories(file.getParent());
		NbtIo.writeCompressed(tag, file);
		return box.getXSpan() * box.getYSpan() * box.getZSpan();
	}

	/** {@code /future start clear}: forget the snapshot. */
	static boolean clear(ServerLevel level) throws IOException {
		return Files.deleteIfExists(file(level));
	}

	/** Before the first arrival into an empty future: put the square back as saved, or take the map's signs down. */
	static void restore(ServerLevel level) {
		Path file = file(level);
		if (!Files.isRegularFile(file)) {
			stripSigns(level, box(RADIUS));
			return;
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			int radius = Math.clamp(tag.getIntOr("radius", RADIUS), MIN_RADIUS, MAX_RADIUS);
			BoundingBox box = box(radius);
			StructureTemplate template = new StructureTemplate();
			template.load(level.holderLookup(Registries.BLOCK), tag.getCompoundOrEmpty("template"));
			prepare(level, box);
			for (Entity entity : entities(level, box)) {
				entity.discard();
			}
			BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
			template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setKnownShape(true), level.getRandom(), Block.UPDATE_CLIENTS);
		} catch (IOException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't restore the future's arrival square from {}", file, e);
		}
	}

	/** Load the square's chunks and let them decay first, so the ruins pass never runs over the saved square. */
	private static void prepare(ServerLevel level, BoundingBox box) {
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				LevelChunk chunk = level.getChunk(cx, cz);
				if (!Boolean.TRUE.equals(chunk.getAttached(FutureRuins.RUINED))) {
					FutureRuins.ruin(level, chunk);
				}
			}
		}
	}

	private static List<Entity> entities(ServerLevel level, BoundingBox box) {
		return level.getEntitiesOfClass(Entity.class, AABB.of(box), entity -> !(entity instanceof Player));
	}

	/** No snapshot yet: the map's signs, hanging signs and map-art billboards around the arrival point come down. */
	private static void stripSigns(ServerLevel level, BoundingBox box) {
		prepare(level, box);
		List<BlockPos> signs = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (entity instanceof SignBlockEntity && box.isInside(entity.getBlockPos())) {
						signs.add(entity.getBlockPos());
					}
				}
			}
		}
		for (BlockPos pos : signs) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (Entity entity : entities(level, box)) {
			if (entity instanceof ItemFrame frame && frame.getFramedMapId(frame.getItem()) != null) {
				entity.discard();
			}
		}
	}
}
