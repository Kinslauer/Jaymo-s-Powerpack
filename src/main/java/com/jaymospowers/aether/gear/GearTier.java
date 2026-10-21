package com.jaymospowers.aether.gear;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

/**
 * The five tool metals of the Aether (notes: structures.md section 5): durability, mining speed, attack bonus, enchantability
 * and which blocks are too hard for it. Every tier makes a shortsword, a hammer, a pickaxe, an axe, a shovel and a hoe.
 */
public enum GearTier {
	// ---- tuning: durability, speed, attack bonus, enchantability, hoe damage/speed offsets, repair tag ----
	SKYROOT("skyroot", BlockTags.INCORRECT_FOR_WOODEN_TOOL, 59, 2.0F, 0.0F, 15, -3.0F),
	HOLYSTONE("holystone", BlockTags.INCORRECT_FOR_STONE_TOOL, 131, 4.0F, 1.0F, 5, -2.0F),
	ZANITE("zanite", BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, -1.0F),
	ARKENIUM("arkenium", BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 6.0F, 2.0F, 14, -1.0F),
	GRAVITITE("gravitite", BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 8.0F, 3.0F, 10, 0.0F);

	/** Shortsword: damage added to the material's bonus, swing speed (quick). Hammer: the same, but heavy and slow. */
	public static final float SWORD_DAMAGE = 3.0F, SWORD_SPEED = -2.2F;
	public static final float HAMMER_DAMAGE = 3.0F, HAMMER_SPEED = -3.3F;
	public static final float PICKAXE_DAMAGE = 1.0F, PICKAXE_SPEED = -2.8F;
	public static final float AXE_DAMAGE = 6.0F, AXE_SPEED = -3.1F;
	public static final float SHOVEL_DAMAGE = 1.5F, SHOVEL_SPEED = -3.0F;
	/** Zanite shears: durability (as the vanilla ones). */
	public static final int SHEARS_DURABILITY = 238;

	public final String id;
	public final ToolMaterial material;
	/** The item tag of what repairs and crafts it, {@code jaymospowers:<id>_tool_materials}. */
	public final TagKey<Item> materials;
	/** What a hoe's damage and speed are offset by (vanilla: the harder the metal, the quicker the hoe). */
	public final float hoeSpeed;

	GearTier(String id, TagKey<Block> incorrect, int durability, float speed, float attackBonus, int enchantability, float hoeSpeed) {
		this.id = id;
		this.materials = TagKey.create(Registries.ITEM, JaymosPowers.id(id + "_tool_materials"));
		this.material = new ToolMaterial(incorrect, durability, speed, attackBonus, enchantability, this.materials);
		this.hoeSpeed = hoeSpeed;
	}
}
