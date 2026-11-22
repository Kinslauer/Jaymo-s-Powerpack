package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.Patron;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (medieval picks, cv-ruins) The old age's peoples (Lore: the old age's peoples), one to each land, gone before the long
 * defence: the <b>Sunstep</b> folk of the sands (stepped tombs), the <b>Pillared</b> folk of the plains (columned temples),
 * the <b>Mound-kin</b> of the north (barrows and long halls) and the <b>Walled Court</b> of Blossom (a walled palace).
 * Each has its stone and timber, its arms (Jaymo's Powers' {@code armory/OldAgeArms}, found by id), the patron its temple
 * was raised to, its ruler and their guard's look, and its fight's theme. Saved by ordinal: only add at the end.
 */
public enum People {
	SUNSTEP("sunstep", Patron.LANTERN_WARDEN, BossEvent.BossBarColor.YELLOW, "sickle_sword", "", "sickle_sword", ""),
	PILLARED("pillared", Patron.HEARTHKEEPER, BossEvent.BossBarColor.RED, "legion_sword", "tall_shield", "leaf_spear", "old_round_shield"),
	MOUND("mound", Patron.CROWNED_SUN, BossEvent.BossBarColor.WHITE, "bearded_axe", "", "bearded_axe", ""),
	COURT("court", Patron.ASH_WHISPERER, BossEvent.BossBarColor.PURPLE, "broad_sabre", "", "broad_sabre", ""),
	ANVIL("anvil", Patron.ANVIL_FATHER, BossEvent.BossBarColor.WHITE, "anvil_fathers_hammer", "", "anvil_fathers_hammer", ""),
	DEEP_FOLK("deep", Patron.DEEP_MOTHER, BossEvent.BossBarColor.BLUE, "tidecaller", "", "tidecaller", ""),
	GROVE("grove", Patron.GREEN_HUNTSMAN, BossEvent.BossBarColor.GREEN, "huntsmans_spear", "", "huntsmans_spear", ""),
	TRUTH_FOLK("truth", Patron.TRUTH, BossEvent.BossBarColor.WHITE, "plain_word", "", "plain_word", "");

	public final String id;
	/** The patron its temples were raised to (an offering there calls that patron's champion, {@link OldTemples}). */
	public final Patron patron;
	public final BossEvent.BossBarColor bar;
	/** Jaymo's Powers' item ids of its arms: the ruler's and first guard's, and the second guard's (a shield, or none). */
	final String weapon, shield, weapon2, shield2;

	People(String id, Patron patron, BossEvent.BossBarColor bar, String weapon, String shield, String weapon2, String shield2) {
		this.id = id;
		this.patron = patron;
		this.bar = bar;
		this.weapon = weapon;
		this.shield = shield;
		this.weapon2 = weapon2;
		this.shield2 = shield2;
	}

	public static People byId(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : SUNSTEP;
	}

	public static People byName(String id) {
		for (People p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return SUNSTEP;
	}

	/** Its fight's theme (a folder under {@code sounds/foe}, {@code FoeThemes}). */
	public String theme() {
		return "ruler_" + this.id;
	}

	// ---- its stone and timber ----

	/** Its walls. */
	BlockState wall() {
		return switch (this) {
			case SUNSTEP -> Blocks.SANDSTONE.defaultBlockState();
			case PILLARED -> Blocks.STONE_BRICKS.defaultBlockState();
			case MOUND -> Blocks.COBBLESTONE.defaultBlockState();
			case COURT -> Blocks.STONE_BRICKS.defaultBlockState();
			case ANVIL -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
			case DEEP_FOLK -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
			case GROVE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case TRUTH_FOLK -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/** Its walls, worn. */
	BlockState worn() {
		return switch (this) {
			case SUNSTEP -> Blocks.CUT_SANDSTONE.defaultBlockState();
			case PILLARED -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			case MOUND -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case COURT -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case ANVIL -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
			case DEEP_FOLK -> Blocks.DARK_PRISMARINE.defaultBlockState();
			case GROVE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case TRUTH_FOLK -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		};
	}

	/** Its band and trim. */
	BlockState trim() {
		return switch (this) {
			case SUNSTEP -> Blocks.CHISELED_SANDSTONE.defaultBlockState();
			case PILLARED -> Blocks.CALCITE.defaultBlockState();
			case MOUND -> Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
			case COURT -> named("red_terracotta");
			case ANVIL -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			case DEEP_FOLK -> Blocks.SEA_LANTERN.defaultBlockState();
			case GROVE -> Blocks.OAK_LOG.defaultBlockState();
			case TRUTH_FOLK -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		};
	}

	/** Its columns and posts. */
	BlockState column() {
		return switch (this) {
			case SUNSTEP -> Blocks.CUT_SANDSTONE.defaultBlockState();
			case PILLARED -> Blocks.CALCITE.defaultBlockState();
			case MOUND -> Blocks.SPRUCE_LOG.defaultBlockState();
			case COURT -> Blocks.DARK_OAK_LOG.defaultBlockState();
			case ANVIL -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			case DEEP_FOLK -> Blocks.PRISMARINE.defaultBlockState();
			case GROVE -> Blocks.SPRUCE_LOG.defaultBlockState();
			case TRUTH_FOLK -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/** Its floors and paving. */
	BlockState floor() {
		return switch (this) {
			case SUNSTEP -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			case PILLARED -> Blocks.SMOOTH_STONE.defaultBlockState();
			case MOUND -> Blocks.COARSE_DIRT.defaultBlockState();
			case COURT -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			case ANVIL -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			case DEEP_FOLK -> Blocks.PRISMARINE.defaultBlockState();
			case GROVE -> Blocks.MOSS_BLOCK.defaultBlockState();
			case TRUTH_FOLK -> Blocks.STONE.defaultBlockState();
		};
	}

	/** Its roofs (a slab). */
	BlockState roof() {
		return switch (this) {
			case SUNSTEP -> Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState();
			case PILLARED -> Blocks.BRICK_SLAB.defaultBlockState();
			case MOUND -> Blocks.SPRUCE_SLAB.defaultBlockState();
			case COURT -> Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
			case ANVIL -> Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
			case DEEP_FOLK -> Blocks.PRISMARINE_SLAB.defaultBlockState();
			case GROVE -> Blocks.OAK_SLAB.defaultBlockState();
			case TRUTH_FOLK -> Blocks.STONE_SLAB.defaultBlockState();
		};
	}

	/** What its streets are laid with, and its rubble. */
	BlockState rubble() {
		return switch (this) {
			case SUNSTEP -> Blocks.SAND.defaultBlockState();
			case PILLARED -> Blocks.COBBLESTONE.defaultBlockState();
			case MOUND -> Blocks.GRAVEL.defaultBlockState();
			case COURT -> Blocks.COBBLESTONE.defaultBlockState();
			case ANVIL -> Blocks.GRAVEL.defaultBlockState();
			case DEEP_FOLK -> Blocks.SAND.defaultBlockState();
			case GROVE -> Blocks.DIRT.defaultBlockState();
			case TRUTH_FOLK -> Blocks.COBBLESTONE.defaultBlockState();
		};
	}

	/** Its doors and furniture's wood. */
	BlockState planks() {
		return switch (this) {
			case SUNSTEP -> Blocks.JUNGLE_PLANKS.defaultBlockState();
			case PILLARED -> Blocks.OAK_PLANKS.defaultBlockState();
			case MOUND -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case COURT -> Blocks.CHERRY_PLANKS.defaultBlockState();
			case ANVIL -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case DEEP_FOLK -> Blocks.JUNGLE_PLANKS.defaultBlockState();
			case GROVE -> Blocks.OAK_PLANKS.defaultBlockState();
			case TRUTH_FOLK -> Blocks.SPRUCE_PLANKS.defaultBlockState();
		};
	}

	/** Its stairs (down into its tombs). */
	BlockState stairs() {
		return switch (this) {
			case SUNSTEP -> Blocks.SANDSTONE_STAIRS.defaultBlockState();
			case PILLARED, COURT, TRUTH_FOLK -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
			case MOUND -> Blocks.COBBLESTONE_STAIRS.defaultBlockState();
			case ANVIL -> Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState();
			case DEEP_FOLK -> Blocks.PRISMARINE_STAIRS.defaultBlockState();
			case GROVE -> Blocks.OAK_STAIRS.defaultBlockState();
		};
	}

	/** A coloured block (they have no {@code Blocks} field), stone bricks if it's missing. */
	static BlockState named(String path) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));
		return (block == Blocks.AIR ? Blocks.STONE_BRICKS : block).defaultBlockState();
	}
}
