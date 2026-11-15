package com.jaymoskingdoms.mineshafts;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks: Mineshafts) Moog's Mineshafts Reimagined's shafts (mod id {@value #MOD}, shipped unchanged) tied into
 * our world, through this compat class: settlements near one send miners into it ({@link ShaftMiners}), its chests and
 * barrels carry our goods ({@link ShaftLoot}), quakes bring its unshored ceilings down ({@link ShaftCollapse}), and its
 * dark keeps its own dwellers by depth and land ({@link ShaftDwellers}). The shafts are the structures in
 * {@code #jaymoskingdoms:old_shafts}: its shafts where it is loaded (optional entries), and the game's own mineshafts,
 * which it replaces (so without it the game's own are worked, shored and haunted the same way).
 */
public final class Mineshafts {
	public static final String MOD = "mmr";
	/** Whether Moog's Mineshafts Reimagined is loaded. */
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** The mineshafts: its and the game's. */
	public static final TagKey<Structure> SHAFTS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("old_shafts"));

	private Mineshafts() {
	}

	public static void register() {
		ShaftLoot.register();
		ShaftMiners.register();
		ShaftDwellers.register();
		JaymosKingdoms.LOGGER.debug("shaft attachments: {} {}", ShaftMiners.POST, ShaftMiners.MANNED);
	}

	/** The mineshaft with a piece at {@code pos}, or an invalid start. */
	static StructureStart at(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, SHAFTS);
	}

	/** Whether {@code pos} is in one of a mineshaft's pieces. */
	public static boolean in(ServerLevel level, BlockPos pos) {
		return at(level, pos).isValid();
	}

	/** Whether {@code pos} is in a mineshaft's piece or is the rock just over one (its ceiling: a corridor's box stops under it). */
	static boolean inOrOver(ServerLevel level, BlockPos pos) {
		return in(level, pos) || in(level, pos.below());
	}

	/** The box of the mineshaft piece {@code pos} is in, or null. */
	static @Nullable BoundingBox piece(ServerLevel level, BlockPos pos) {
		StructureStart start = at(level, pos);
		if (start.isValid()) {
			for (StructurePiece p : start.getPieces()) {
				if (p.getBoundingBox().isInside(pos)) {
					return p.getBoundingBox();
				}
			}
		}
		return null;
	}
}
