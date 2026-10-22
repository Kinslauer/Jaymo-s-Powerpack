package com.jaymospowers.tinkering;

import java.util.List;
import java.util.Map;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * A tool the Tool Station puts together, with Tinkers' Construct's definition for it (1.20.1): its parts in order (the
 * first is the one that names it), base attack and attack speed, stat multipliers, and its own traits.
 */
public enum ToolType {
	PICKAXE("pickaxe", parts(PartType.PICK_HEAD, 1, PartType.TOOL_HANDLE, 1, PartType.TOOL_BINDING, 1),
		0.5F, 1.2F, 1.0F, 1.0F, 1.0F, List.of(BlockTags.MINEABLE_WITH_PICKAXE), Map.of(Trait.PIERCE, 1), Style.TOOL, 0.0F),
	HAND_AXE("hand_axe", parts(PartType.SMALL_AXE_HEAD, 1, PartType.TOOL_HANDLE, 1, PartType.TOOL_BINDING, 1),
		6.0F, 0.9F, 1.0F, 1.0F, 1.0F, List.of(BlockTags.MINEABLE_WITH_AXE), Map.of(Trait.STRIPPING, 1), Style.TOOL, 5.0F),
	MATTOCK("mattock", parts(PartType.SMALL_AXE_HEAD, 0.5F, PartType.TOOL_HANDLE, 1, PartType.ADZE_HEAD, 0.5F),
		1.5F, 0.9F, 1.1F, 1.25F, 1.1F, List.of(BlockTags.MINEABLE_WITH_AXE, BlockTags.MINEABLE_WITH_SHOVEL), Map.of(Trait.TILLING, 1),
		Style.TOOL, 0.0F),
	SWORD("sword", parts(PartType.SMALL_BLADE, 1, PartType.TOOL_HANDLE, 0.5F, PartType.TOOL_HANDLE, 0.5F),
		3.0F, 1.6F, 1.0F, 1.1F, 0.5F, List.of(), Map.of(Trait.SWEEPING, 1), Style.SWORD, 0.0F),
	SLEDGE_HAMMER("sledge_hammer", parts(PartType.HAMMER_HEAD, 0.5F, PartType.TOUGH_HANDLE, 1, PartType.LARGE_PLATE, 0.25F,
		PartType.LARGE_PLATE, 0.25F),
		3.0F, 0.75F, 1.35F, 4.0F, 0.4F, List.of(BlockTags.MINEABLE_WITH_PICKAXE), Map.of(Trait.AREA, 1, Trait.SMITE, 2), Style.TOOL, 0.0F),
	CLEAVER("cleaver", parts(PartType.BROAD_BLADE, 0.75F, PartType.TOUGH_HANDLE, 0.5F, PartType.TOUGH_HANDLE, 0.5F,
		PartType.LARGE_PLATE, 0.25F),
		3.0F, 1.0F, 1.5F, 3.5F, 0.25F, List.of(), Map.of(Trait.SEVERING, 2, Trait.SWEEPING, 1), Style.SWORD, 0.0F),

	// (0.15.x) Tinkers' other melee weapons and big harvest tools (docs/Tinkering-Weapons.md): numbers from its tool definitions
	DAGGER("dagger", parts(PartType.SMALL_BLADE, 1, PartType.TOOL_HANDLE, 1),
		3.0F, 2.0F, 0.65F, 0.75F, 0.75F, List.of(BlockTags.MINEABLE_WITH_HOE), Map.of(Trait.PADDED, 1, Trait.OFFHAND, 1), Style.SWORD, 0.0F),
	KAMA("kama", parts(PartType.SMALL_BLADE, 1, PartType.TOOL_HANDLE, 1, PartType.TOOL_BINDING, 1),
		1.0F, 1.6F, 0.5F, 1.0F, 1.0F, List.of(BlockTags.MINEABLE_WITH_HOE),
		Map.of(Trait.TILLING, 1, Trait.SHEARS, 1, Trait.HARVEST, 1, Trait.AREA, 1), Style.TOOL, 0.0F, Aoe.circle(1, true), TinkerMaterial.Tier.NETHERITE),
	SCYTHE("scythe", parts(PartType.BROAD_BLADE, 1, PartType.TOUGH_HANDLE, 0.5F, PartType.TOUGH_BINDING, 1, PartType.TOUGH_HANDLE, 0.5F),
		1.0F, 0.7F, 1.0F, 2.5F, 0.45F, List.of(BlockTags.MINEABLE_WITH_HOE),
		Map.of(Trait.TILLING, 1, Trait.SHEARS, 1, Trait.HARVEST, 1, Trait.AREA, 1, Trait.SWEEPING, 2), Style.TOOL, 0.0F,
		Aoe.box(1, 1, 1, 2), TinkerMaterial.Tier.NETHERITE),
	BROAD_AXE("broad_axe", parts(PartType.BROAD_AXE_HEAD, 0.75F, PartType.TOUGH_HANDLE, 1, PartType.PICK_HEAD, 0.25F, PartType.TOUGH_BINDING, 1),
		5.0F, 0.6F, 1.65F, 4.25F, 0.3F, List.of(BlockTags.MINEABLE_WITH_AXE), Map.of(Trait.STRIPPING, 1, Trait.AREA, 1, Trait.TIMBER, 1),
		Style.TOOL, 5.0F, Aoe.column(5).felling(), TinkerMaterial.Tier.NETHERITE),
	EXCAVATOR("excavator", parts(PartType.LARGE_PLATE, 1, PartType.TOUGH_HANDLE, 0.5F, PartType.TOUGH_BINDING, 1, PartType.TOUGH_HANDLE, 0.5F),
		1.5F, 1.0F, 1.2F, 3.75F, 0.3F, List.of(BlockTags.MINEABLE_WITH_SHOVEL), Map.of(Trait.KNOCKBACK, 2, Trait.PATHING, 1, Trait.AREA, 1),
		Style.TOOL, 0.0F, Aoe.box(1, 1, 1, 0), TinkerMaterial.Tier.NETHERITE),
	VEIN_HAMMER("vein_hammer", parts(PartType.HAMMER_HEAD, 0.75F, PartType.TOUGH_HANDLE, 1, PartType.TOUGH_BINDING, 1, PartType.LARGE_PLATE, 0.25F),
		3.0F, 0.85F, 1.25F, 5.0F, 0.3F, List.of(BlockTags.MINEABLE_WITH_PICKAXE), Map.of(Trait.PIERCE, 2, Trait.VEIN, 1), Style.TOOL, 0.0F,
		Aoe.VEIN, TinkerMaterial.Tier.NETHERITE),
	// Tinkers caps the pickadze at gold (wood-level) mining, which only a Diamond or Emerald modifier lifts
	PICKADZE("pickadze", parts(PartType.PICK_HEAD, 0.5F, PartType.TOOL_HANDLE, 1, PartType.ADZE_HEAD, 0.5F),
		0.5F, 1.3F, 1.15F, 1.3F, 0.75F, List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL),
		Map.of(Trait.PATHING, 1, Trait.AREA, 1), Style.TOOL, 0.0F, Aoe.box(0, 1, 0, 0), TinkerMaterial.Tier.WOOD),
	// the three below are Tinkers' material-built weapons (head, limb, bowstring / head, head, handle / head, plating), here put
	// together from parts like the rest: a war pick of a pick head, tough handle and binding, and so on
	WAR_PICK("war_pick", parts(PartType.PICK_HEAD, 1, PartType.TOUGH_HANDLE, 1, PartType.TOOL_BINDING, 1),
		0.5F, 1.2F, 1.0F, 1.0F, 1.0F, List.of(BlockTags.MINEABLE_WITH_PICKAXE), Map.of(Trait.WAR_CHARGE, 1, Trait.AREA, 1), Style.TOOL, 0.0F,
		Aoe.circle(1, false), TinkerMaterial.Tier.NETHERITE),
	MINOTAUR_AXE("minotaur_axe", parts(PartType.SMALL_AXE_HEAD, 1, PartType.SMALL_AXE_HEAD, 1, PartType.TOOL_HANDLE, 1),
		3.0F, 0.9F, 1.0F, 1.0F, 1.0F, List.of(BlockTags.MINEABLE_WITH_AXE), Map.of(Trait.CHARGE_ATTACK, 1, Trait.STRIPPING, 1, Trait.AREA, 1),
		Style.TOOL, 5.0F, Aoe.column(5), TinkerMaterial.Tier.NETHERITE),
	BATTLESIGN("battlesign", parts(PartType.LARGE_PLATE, 1, PartType.TOUGH_HANDLE, 1),
		2.0F, 1.2F, 1.0F, 1.0F, 1.0F, List.of(), Map.of(Trait.BLOCKING, 1, Trait.BONKING, 1, Trait.KNOCKBACK, 1), Style.WEAPON, 0.0F);

	/** A slot on the tool: the part it takes and its share of that part kind's stats. */
	public record Slot(PartType part, float scale) {
	}

	/**
	 * {@code TOOL}: mines its blocks. {@code SWORD}: cuts cobwebs and leaves like a sword, wears 2 a block. {@code WEAPON}: a
	 * weapon that digs nothing (the battlesign).
	 */
	public enum Style { TOOL, SWORD, WEAPON }

	/**
	 * (0.15.x) How many blocks a tool takes at once besides the one it broke ({@link TraitEffects#areaBreak}): {@code BOX} (a
	 * box round the broken block, laid out by the face you hit: {@code side} blocks each way across it, {@code up} and {@code down}
	 * along it, {@code depth} into the wall; {@code world} keeps up and down to the world's), {@code CIRCLE} (a disc of radius
	 * {@code side} in the face's plane, or a ball when {@code world}) and {@code VEIN} (connected ore of the same kind). With
	 * {@code tree}, a log that belongs to a tree brings the whole tree down.
	 */
	public record Aoe(Kind kind, int side, int up, int down, int depth, boolean world, boolean tree) {
		public enum Kind { NONE, BOX, CIRCLE, VEIN }

		public static final Aoe NONE = new Aoe(Kind.NONE, 0, 0, 0, 0, false, false);
		public static final Aoe VEIN = new Aoe(Kind.VEIN, 0, 0, 0, 0, false, false);
		/** The 3 by 3 face the sledge hammer's Wide Swing breaks. */
		public static final Aoe FACE_3X3 = box(1, 1, 1, 0);

		public static Aoe box(int side, int up, int down, int depth) {
			return new Aoe(Kind.BOX, side, up, down, depth, false, false);
		}

		/** A column straight up from the block (the broad axe and the minotaur axe on anything but a tree). */
		public static Aoe column(int up) {
			return new Aoe(Kind.BOX, 0, up, 0, 0, true, false);
		}

		public static Aoe circle(int radius, boolean ball) {
			return new Aoe(Kind.CIRCLE, radius, 0, 0, 0, ball, false);
		}

		public Aoe felling() {
			return new Aoe(kind, side, up, down, depth, world, true);
		}
	}

	public final String id;
	public final List<Slot> parts;
	public final float attack, attackSpeed;
	public final float attackMultiplier, durabilityMultiplier, speedMultiplier;
	public final List<TagKey<Block>> mines;
	public final Map<Trait, Integer> traits;
	public final Style style;
	/** Seconds a shield it strikes stays down (axes). */
	public final float disablesShields;
	/** (0.15.x) The blocks it takes at once besides the one it broke, and the best tier its materials can mine at. */
	public final Aoe aoe;
	public final TinkerMaterial.Tier maxTier;

	ToolType(String id, List<Slot> parts, float attack, float attackSpeed, float attackMultiplier, float durabilityMultiplier,
		float speedMultiplier, List<TagKey<Block>> mines, Map<Trait, Integer> traits, Style style, float disablesShields) {
		this(id, parts, attack, attackSpeed, attackMultiplier, durabilityMultiplier, speedMultiplier, mines, traits, style, disablesShields,
			Aoe.NONE, TinkerMaterial.Tier.NETHERITE);
	}

	ToolType(String id, List<Slot> parts, float attack, float attackSpeed, float attackMultiplier, float durabilityMultiplier,
		float speedMultiplier, List<TagKey<Block>> mines, Map<Trait, Integer> traits, Style style, float disablesShields, Aoe aoe,
		TinkerMaterial.Tier maxTier) {
		this.id = id;
		this.parts = parts;
		this.attack = attack;
		this.attackSpeed = attackSpeed;
		this.attackMultiplier = attackMultiplier;
		this.durabilityMultiplier = durabilityMultiplier;
		this.speedMultiplier = speedMultiplier;
		this.mines = mines;
		this.traits = traits;
		this.style = style;
		this.disablesShields = disablesShields;
		this.aoe = aoe;
		this.maxTier = maxTier;
	}

	private static List<Slot> parts(Object... pairs) {
		java.util.ArrayList<Slot> out = new java.util.ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2) {
			out.add(new Slot((PartType) pairs[i], ((Number) pairs[i + 1]).floatValue()));
		}
		return List.copyOf(out);
	}

	public String itemId() {
		return "tinker_" + id;
	}
}
