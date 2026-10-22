package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** One built-in-code piece of an Aether II structure; {@link #kind} says which builder draws it, a chunk at a time. */
public final class AetherPiece extends StructurePiece {
	public enum Kind {
		OUTPOST, CAMP, TOWER, DEN, RUINS, HENGE, LIBRARY, AQUEDUCT, BREXALLEN, MINE_HUB, MINE_TUNNEL, MINE_ROOM, IRRADIATED, GUARDIAN
	}

	public static StructurePieceType TYPE;

	private final Kind kind;
	private final Direction facing;
	private final long seed;
	private final int variant;

	public AetherPiece(Kind kind, BoundingBox box, Direction facing, long seed, int variant) {
		super(TYPE, 0, box);
		this.kind = kind;
		this.facing = facing;
		this.seed = seed;
		this.variant = variant;
		this.setOrientation(facing);
	}

	public AetherPiece(CompoundTag tag) {
		super(TYPE, tag);
		this.kind = Kind.valueOf(tag.getStringOr("kind", "OUTPOST"));
		this.facing = Direction.from2DDataValue(tag.getIntOr("facing", 0));
		this.seed = tag.getLongOr("seed", 0L);
		this.variant = tag.getIntOr("variant", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", this.kind.name());
		tag.putInt("facing", this.facing.get2DDataValue());
		tag.putLong("seed", this.seed);
		tag.putInt("variant", this.variant);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
		ChunkPos chunkPos, BlockPos pivot) {
		Site s = new Site(level, chunkBox, this.boundingBox, this.facing, this.seed, this.variant);
		try {
			switch (this.kind) {
				case OUTPOST -> Outposts.build(s);
				case CAMP -> Camps.build(s);
				case TOWER -> Towers.build(s);
				case DEN -> Dens.build(s);
				case RUINS -> Veradexian.ruins(s);
				case HENGE -> Veradexian.henge(s);
				case LIBRARY -> Veradexian.library(s);
				case AQUEDUCT -> Veradexian.aqueduct(s);
				case BREXALLEN -> Deep.brexallen(s);
				case MINE_HUB -> Deep.mineHub(s);
				case MINE_TUNNEL -> Deep.mineTunnel(s);
				case MINE_ROOM -> Deep.mineRoom(s);
				case IRRADIATED -> Irradiated.build(s);
				case GUARDIAN -> GuardianTree.build(s);
			}
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.error("Aether structure {} failed at {}", this.kind, this.boundingBox, e);
		}
	}
}
