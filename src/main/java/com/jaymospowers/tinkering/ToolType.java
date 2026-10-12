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
		3.0F, 1.0F, 1.5F, 3.5F, 0.25F, List.of(), Map.of(Trait.SEVERING, 2, Trait.SWEEPING, 1), Style.SWORD, 0.0F);

	/** A slot on the tool: the part it takes and its share of that part kind's stats. */
	public record Slot(PartType part, float scale) {
	}

	/** {@code TOOL}: mines its blocks. {@code SWORD}: cuts cobwebs and leaves like a sword, wears 2 a block. */
	public enum Style { TOOL, SWORD }

	public final String id;
	public final List<Slot> parts;
	public final float attack, attackSpeed;
	public final float attackMultiplier, durabilityMultiplier, speedMultiplier;
	public final List<TagKey<Block>> mines;
	public final Map<Trait, Integer> traits;
	public final Style style;
	/** Seconds a shield it strikes stays down (axes). */
	public final float disablesShields;

	ToolType(String id, List<Slot> parts, float attack, float attackSpeed, float attackMultiplier, float durabilityMultiplier,
		float speedMultiplier, List<TagKey<Block>> mines, Map<Trait, Integer> traits, Style style, float disablesShields) {
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
