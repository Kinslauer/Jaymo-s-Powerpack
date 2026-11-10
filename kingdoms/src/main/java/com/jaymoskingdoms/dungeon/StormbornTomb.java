package com.jaymoskingdoms.dungeon;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * The Stormborn Tomb ({@code jaymoskingdoms:stormborn_tomb}), ported from Tada_no_ahiru's Dungeon Conquest: Stormborn
 * Tomb (MIT, licence in {@code licenses/stormborn-tomb-MIT.txt}). A barrow hill on the surface; behind the Eye of Ender
 * door in its hall, where DungeonZ's portal stood, a shaft and a tunnel lead down into the tomb, whose halls run on to
 * the boss arena.
 * <p>
 * The original tomb lived in DungeonZ's own dimension, joined piece to piece by jigsaws. Laid out in the overworld it is
 * about 270 blocks across, more than a jigsaw structure may reach (128 from its middle), so its pieces are placed here
 * at fixed offsets ({@link #LAYOUT}, worked out once from the templates' own jigsaws), turned together, with the layout's
 * middle on the structure's chunk (the game looks 8 chunks round for a structure's pieces). The barrow sits on the
 * ground (11 deep, as the original did) and the rest hangs below it.
 */
public final class StormbornTomb extends Structure {
	public static final MapCodec<StormbornTomb> CODEC = simpleCodec(StormbornTomb::new);
	public static StructureType<StormbornTomb> TYPE;

	/** How deep the barrow's base sits in the ground. */
	static final int SINK = 11;
	/** Blocks kept between the tomb's lowest piece and the bottom of the world. */
	static final int FLOOR_MARGIN = 6;

	/** One template, its quarter turns and where its origin goes, in the layout's own frame. */
	record Part(String name, int turns, int x, int y, int z) {
	}

	/** In the order they're placed: the descent last, so it cuts through the barrow's floor and the spawn hall's wall. */
	static final List<Part> LAYOUT = List.of(
		new Part("overworld", 2, 118, 28, 30),
		new Part("overworld_up", 2, 118, 28, -45),
		new Part("spawn", 0, 0, 0, 0),
		new Part("first_hallway", 0, -69, -19, -48),
		new Part("right_hallway", 0, 0, -27, -29),
		new Part("split", 0, 73, -28, -53),
		new Part("right_split", 0, 59, -60, -97),
		new Part("main_right", 0, -27, -45, -92),
		new Part("main", 0, -126, -53, -129),
		new Part("boss", 0, -142, -57, -243),
		new Part("parkour", 0, 68, -18, -97),
		new Part("descent", 0, 61, 15, -36));
	/** The layout's middle column, and the barrow's: its base's height and middle column. */
	static final int MID_X = -12, MID_Z = -107, BARROW_Y = 28, BARROW_X = 69, BARROW_Z = -36;
	/** The lowest any piece reaches, in the layout's frame. */
	static final int LOWEST = -60;

	public StormbornTomb(StructureSettings settings) {
		super(settings);
	}

	public static void register() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("stormborn_tomb"), () -> CODEC);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
	    Rotation turn = Rotation.getRandom(context.random());
	    BlockPos mid = turn(new BlockPos(MID_X, 0, MID_Z), turn);
	    int ax = context.chunkPos().getMinBlockX() + 8 - mid.getX();
	    int az = context.chunkPos().getMinBlockZ() + 8 - mid.getZ();
	    BlockPos barrow = turn(new BlockPos(BARROW_X, 0, BARROW_Z), turn);
	    int bx = ax + barrow.getX(), bz = az + barrow.getZ();
	    int ground = context.chunkGenerator().getFirstFreeHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
	        context.randomState());
	    int ay = ground - SINK - BARROW_Y;
	    if (ay + LOWEST < context.heightAccessor().getMinY() + FLOOR_MARGIN) {
	        return Optional.empty();
	    }
	    BlockPos anchor = new BlockPos(ax, ay, az);
	    return Optional.of(new GenerationStub(new BlockPos(bx, ground, bz), builder -> {
	        for (Part part : LAYOUT) {
	            Rotation rotation = turns(part.turns()).getRotated(turn);
	            BlockPos basePos = anchor.offset(turn(new BlockPos(part.x(), part.y(), part.z()), turn));
	            // Use heightmap to ensure piece sits on terrain rather than floating or leaving solid stone gaps
	                        // Compute the ground height at the piece's position using the same method as the barrow base
	                        BlockPos piecePos = anchor.offset(turn(new BlockPos(part.x(), part.y(), part.z()), turn));
	                        int pieceGround = context.chunkGenerator().getFirstFreeHeight(piecePos.getX(), piecePos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - part.y();
            BlockPos position = anchor.offset(turn(new BlockPos(part.x(), pieceGround, part.z()), turn));
	            SinglePoolElement element = StructurePoolElement.single(JaymosKingdoms.id("stormborn_tomb_" + part.name()).toString())
	                .apply(StructureTemplatePool.Projection.RIGID);
	            BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), position, rotation);
	            builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), element, position, element.getGroundLevelDelta(),
	                rotation, box, LiquidSettings.IGNORE_WATERLOGGING));
	        }
	    }));
	}

	/** {@code pos} turned about the layout's origin the way a template turns about its own. */
	static BlockPos turn(BlockPos pos, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
			case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
			case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
			default -> pos;
		};
	}

	static Rotation turns(int quarter) {
		return Rotation.values()[quarter & 3];
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
